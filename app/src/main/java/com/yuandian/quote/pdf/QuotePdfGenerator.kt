package com.yuandian.quote.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yuandian.quote.data.CompanyProfile
import com.yuandian.quote.data.Quote
import com.yuandian.quote.util.Dates
import com.yuandian.quote.util.Money
import java.io.File
import java.io.FileOutputStream

/**
 * 使用 Android 系统自带的 [PdfDocument] 生成 A4 报价单。
 *
 * - 中文使用系统字体（Android 内置思源黑体/Noto Sans CJK），生成时字形会嵌入 PDF，任何设备打开都正常显示。
 * - 表格单元格内容通过 [StaticLayout] 自动换行，行高按最高单元格计算，保证不溢出。
 * - 明细超过一页时自动分页，并在新页重复表头；页脚显示“第 x 页 / 共 y 页”。
 */
class QuotePdfGenerator {

    private companion object {
        const val PAGE_W = 595   // A4 宽度（1/72 英寸）
        const val PAGE_H = 842   // A4 高度
        const val MARGIN = 40f
        const val CONTENT_W = PAGE_W - MARGIN * 2
        const val FOOTER_H = 30f
        const val CELL_PAD_X = 4f
        const val CELL_PAD_Y = 5f

        val GREEN = Color.rgb(0x1F, 0x9D, 0x6B)
        val TEXT = Color.rgb(0x1F, 0x23, 0x29)
        val GRAY = Color.rgb(0x64, 0x6A, 0x73)
        val LINE = Color.rgb(0xD9, 0xDC, 0xE0)
        val HEADER_BG = Color.rgb(0xF2, 0xF4, 0xF5)
        val TOTAL_BG = Color.rgb(0xEC, 0xF7, 0xF2)
    }

    private enum class Align { LEFT, CENTER, RIGHT }
    private class Col(val title: String, val width: Float, val align: Align)

    // 列宽合计 = 515 (= A4 宽 595 - 左右边距 80)
    private val cols = listOf(
        Col("序号", 28f, Align.CENTER),
        Col("商品名称", 108f, Align.LEFT),
        Col("型号", 72f, Align.LEFT),
        Col("规格", 86f, Align.LEFT),
        Col("单位", 32f, Align.CENTER),
        Col("数量", 52f, Align.RIGHT),
        Col("单价(元)", 66f, Align.RIGHT),
        Col("金额(元)", 71f, Align.RIGHT),
    )

    private val regular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paint(size: Float, color: Int = TEXT, isBold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (isBold) bold else regular
        // 部分设备的粗体中文字体缺失时用伪粗体兜底
        isFakeBoldText = isBold && typeface?.isBold != true
    }

    private val linePaint = Paint().apply { color = LINE; strokeWidth = 0.6f; style = Paint.Style.STROKE }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }

    fun generate(quote: Quote, company: CompanyProfile, out: File) {
        // 第一遍：统计总页数；第二遍：带“共 N 页”正式输出
        val pages = render(quote, company, null, null)
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { render(quote, company, pages, it) }
    }

    private fun layout(text: String, p: TextPaint, width: Float, align: Align = Align.LEFT): StaticLayout {
        val a = when (align) {
            Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(a)
            .setLineSpacing(1.5f, 1f)
            .setIncludePad(false)
            .build()
    }

    private fun Canvas.drawLayout(l: StaticLayout, x: Float, y: Float) {
        save(); translate(x, y); l.draw(this); restore()
    }

    /** 返回总页数；out 为 null 时只计算不输出。 */
    private fun render(quote: Quote, company: CompanyProfile, totalPages: Int?, out: FileOutputStream?): Int {
        val doc = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f
        val bottom = PAGE_H - MARGIN - FOOTER_H

        fun finishPage() {
            // 页脚
            val fp = paint(8f, GRAY)
            canvas.drawLine(MARGIN, PAGE_H - MARGIN - 14f, PAGE_W - MARGIN, PAGE_H - MARGIN - 14f, linePaint)
            canvas.drawText("本报价单由「原点报价助手」生成", MARGIN, PAGE_H - MARGIN, fp)
            val pageText = if (totalPages != null) "第 $pageNo 页 / 共 $totalPages 页" else "第 $pageNo 页"
            canvas.drawText(pageText, PAGE_W - MARGIN - fp.measureText(pageText), PAGE_H - MARGIN, fp)
            doc.finishPage(page)
        }

        fun newPage() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page.canvas
            y = MARGIN
        }

        // ---------- 抬头 ----------
        newPage()
        val titlePaint = paint(22f, GREEN, true)
        val title = "报 价 单"
        val titleW = titlePaint.measureText(title)
        val companyMaxW = CONTENT_W - titleW - 20f
        val companyLayout = layout(company.name.ifBlank { "（未设置公司名称）" }, paint(15f, TEXT, true), companyMaxW)
        canvas.drawLayout(companyLayout, MARGIN, y)
        canvas.drawText(title, PAGE_W - MARGIN - titleW, y + 22f, titlePaint)
        y += maxOf(companyLayout.height.toFloat(), 26f) + 4f

        val contactLine = buildList {
            if (company.contact.isNotBlank()) add("联系人：${company.contact}")
            if (company.phone.isNotBlank()) add("电话：${company.phone}")
            if (company.address.isNotBlank()) add("地址：${company.address}")
        }.joinToString("    ")
        if (contactLine.isNotEmpty()) {
            val l = layout(contactLine, paint(8.5f, GRAY), CONTENT_W)
            canvas.drawLayout(l, MARGIN, y); y += l.height + 6f
        }
        fillPaint.color = GREEN
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 2f, fillPaint)
        y += 14f

        // ---------- 客户与报价信息（两栏） ----------
        val labelP = paint(9.5f, GRAY)
        val valueP = paint(9.5f, TEXT)
        val half = CONTENT_W / 2f
        val labelW = 56f
        val left = listOf(
            "客户名称" to quote.customerCompany,
            "联 系 人" to quote.customerContact.ifBlank { "—" },
            "联系电话" to quote.customerPhone.ifBlank { "—" },
        )
        val right = listOf(
            "报价编号" to quote.quoteNo,
            "报价日期" to Dates.chinese(quote.quoteDate),
            "有效期至" to Dates.chinese(quote.validUntil),
        )
        for (i in left.indices) {
            val lv = layout(left[i].second, valueP, half - labelW - 12f)
            val rv = layout(right[i].second, valueP, half - labelW)
            canvas.drawText(left[i].first + "：", MARGIN, y + 10f, labelP)
            canvas.drawLayout(lv, MARGIN + labelW, y)
            canvas.drawText(right[i].first + "：", MARGIN + half, y + 10f, labelP)
            canvas.drawLayout(rv, MARGIN + half + labelW, y)
            y += maxOf(lv.height, rv.height, 12) + 7f
        }
        y += 8f

        // ---------- 明细表格 ----------
        val headP = paint(9f, TEXT, true)
        val cellP = paint(9f, TEXT)

        fun drawRow(cells: List<String>, p: TextPaint, bg: Int?): Float {
            val layouts = cells.mapIndexed { i, s -> layout(s, p, cols[i].width - CELL_PAD_X * 2, cols[i].align) }
            val h = layouts.maxOf { it.height } + CELL_PAD_Y * 2
            if (bg != null) {
                fillPaint.color = bg
                canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + h, fillPaint)
            }
            var x = MARGIN
            layouts.forEachIndexed { i, l ->
                canvas.drawLayout(l, x + CELL_PAD_X, y + CELL_PAD_Y)
                canvas.drawLine(x, y, x, y + h, linePaint)
                x += cols[i].width
            }
            canvas.drawLine(PAGE_W - MARGIN, y, PAGE_W - MARGIN, y + h, linePaint)
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            canvas.drawLine(MARGIN, y + h, PAGE_W - MARGIN, y + h, linePaint)
            y += h
            return h
        }

        fun measureRow(cells: List<String>, p: TextPaint): Float =
            cells.mapIndexed { i, s -> layout(s, p, cols[i].width - CELL_PAD_X * 2, cols[i].align).height }
                .max() + CELL_PAD_Y * 2

        val headerCells = cols.map { it.title }
        drawRow(headerCells, headP, HEADER_BG)

        quote.items.forEachIndexed { idx, item ->
            val cells = listOf(
                (idx + 1).toString(),
                item.name,
                item.model.ifBlank { "—" },
                item.spec.ifBlank { "—" },
                item.unit,
                Money.formatQuantity(item.quantity),
                Money.format(item.unitPriceCents),
                Money.format(item.amountCents),
            )
            val h = measureRow(cells, cellP)
            if (y + h > bottom) {
                finishPage(); newPage()
                canvas.drawText("${quote.quoteNo}（续）", MARGIN, y + 10f, paint(9f, GRAY))
                y += 20f
                drawRow(headerCells, headP, HEADER_BG)
            }
            drawRow(cells, cellP, null)
        }

        // ---------- 合计 ----------
        val totalCents = quote.items.sumOf { it.amountCents }
        val upperP = paint(9.5f, TEXT, true)
        val totalP = paint(12f, GREEN, true)
        val totalText = Money.formatWithSymbol(totalCents)
        val totalTextW = totalP.measureText(totalText)
        val upperLayout = layout("合计金额（大写）：人民币" + Money.toChineseUppercase(totalCents),
            upperP, CONTENT_W - totalTextW - CELL_PAD_X * 4 - 40f)
        val totalH = maxOf(upperLayout.height.toFloat(), 16f) + CELL_PAD_Y * 2 + 4f
        if (y + totalH > bottom) { finishPage(); newPage() }
        fillPaint.color = TOTAL_BG
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + totalH, fillPaint)
        canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + totalH, linePaint)
        canvas.drawLayout(upperLayout, MARGIN + CELL_PAD_X * 2, y + (totalH - upperLayout.height) / 2f)
        val totalLabelP = paint(9.5f, GRAY)
        val totalLabel = "合计："
        val baseline = y + totalH / 2f + 4.5f
        canvas.drawText(totalText, PAGE_W - MARGIN - CELL_PAD_X * 2 - totalTextW, baseline, totalP)
        canvas.drawText(totalLabel, PAGE_W - MARGIN - CELL_PAD_X * 2 - totalTextW - totalLabelP.measureText(totalLabel),
            baseline, totalLabelP)
        y += totalH + 16f

        // ---------- 备注与条款 ----------
        fun section(heading: String, body: String) {
            if (body.isBlank()) return
            val hp = paint(10f, TEXT, true)
            val bl = layout(body.trim(), paint(9f, TEXT), CONTENT_W)
            // 段落整体放不下时，按行拆分换页
            if (y + 16f + minOf(bl.height.toFloat(), 40f) > bottom) { finishPage(); newPage() }
            canvas.drawText(heading, MARGIN, y + 10f, hp)
            y += 16f
            for (line in 0 until bl.lineCount) {
                val top = bl.getLineTop(line).toFloat()
                val lh = bl.getLineBottom(line) - top
                if (y + lh > bottom) { finishPage(); newPage() }
                canvas.save()
                canvas.translate(MARGIN, y - top)
                canvas.clipRect(0f, top, CONTENT_W, top + lh)
                bl.draw(canvas)
                canvas.restore()
                y += lh
            }
            y += 12f
        }
        section("备注", quote.remark)
        section("报价条款", company.terms)

        // ---------- 签章区 ----------
        val signH = 60f
        if (y + signH > bottom) { finishPage(); newPage() }
        y += 10f
        val sp = paint(9.5f, TEXT)
        canvas.drawText("报价方（盖章）：", MARGIN, y + 10f, sp)
        canvas.drawText("客户确认（签字盖章）：", MARGIN + half, y + 10f, sp)
        y += 36f
        canvas.drawText("日期：", MARGIN, y + 10f, sp)
        canvas.drawText("日期：", MARGIN + half, y + 10f, sp)

        finishPage()
        if (out != null) doc.writeTo(out)
        doc.close()
        return pageNo
    }
}
