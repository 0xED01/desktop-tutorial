package com.yuandian.quote.pdf

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.yuandian.quote.data.CompanyProfile
import com.yuandian.quote.data.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF 的生成、保存、分享与预览。
 * - 保存：通过 MediaStore 写入 “下载/原点报价” 目录（Android 10+ 无需任何存储权限）。
 * - 分享：通过 FileProvider 生成临时授权链接，交给系统分享面板（微信、QQ、邮件等）。
 */
class PdfExporter(private val context: Context) {

    private val generator = QuotePdfGenerator()

    fun fileName(q: Quote): String {
        val safeCustomer = q.customerCompany.replace(Regex("[\\\\/:*?\"<>|\\s]"), "")
        return "报价单_${q.quoteNo}_${safeCustomer}.pdf"
    }

    /** 生成到应用缓存目录，返回文件 */
    suspend fun generate(q: Quote, company: CompanyProfile): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        // 清理旧的缓存文件
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }
        val f = File(dir, fileName(q))
        generator.generate(q, company, f)
        f
    }

    /** 复制到公共“下载”目录，返回显示给用户的路径 */
    suspend fun saveToDownloads(file: File): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val subDir = Environment.DIRECTORY_DOWNLOADS + "/原点报价"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建文件")
        try {
            resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
                ?: error("无法写入文件")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        "下载/原点报价/${file.name}"
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    fun shareIntent(file: File, subject: String): Intent {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            // 显式设置 ClipData，确保系统分享面板把读取权限传递给微信等目标应用
            clipData = ClipData.newRawUri(file.name, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "发送报价单").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun viewIntent(file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** 把 PDF 每一页渲染成位图，用于 App 内预览 */
    suspend fun renderPreview(file: File, widthPx: Int): List<Bitmap> = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        val h = (widthPx.toFloat() * page.height / page.width).toInt()
                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }
    }
}
