package com.yuandian.quote

import android.app.Application
import android.content.Context
import com.yuandian.quote.data.AppDatabase
import com.yuandian.quote.data.CompanyProfileStore
import com.yuandian.quote.data.QuoteRepository
import com.yuandian.quote.pdf.PdfExporter

/** 简单的依赖容器：整个 App 共享同一个数据库与仓库实例 */
class AppContainer(context: Context) {
    val repository = QuoteRepository(AppDatabase(context))
    val companyStore = CompanyProfileStore(context)
    val pdfExporter = PdfExporter(context)
}

class YuandianApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
