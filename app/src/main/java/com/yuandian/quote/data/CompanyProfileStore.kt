package com.yuandian.quote.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 本公司信息（报价方），保存在 SharedPreferences。默认值为虚构演示信息。 */
class CompanyProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("company_profile", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<CompanyProfile> = _profile.asStateFlow()

    private fun load() = CompanyProfile(
        name = prefs.getString("name", null) ?: "原点示例五金机电有限公司",
        contact = prefs.getString("contact", null) ?: "李经理",
        phone = prefs.getString("phone", null) ?: "400-000-0000",
        address = prefs.getString("address", null) ?: "示例省示例市工业园区创业路 88 号",
        terms = prefs.getString("terms", null) ?: DEFAULT_TERMS,
    )

    fun save(p: CompanyProfile) {
        prefs.edit()
            .putString("name", p.name.trim())
            .putString("contact", p.contact.trim())
            .putString("phone", p.phone.trim())
            .putString("address", p.address.trim())
            .putString("terms", p.terms.trim())
            .apply()
        _profile.value = load()
    }

    companion object {
        const val DEFAULT_TERMS =
            "1. 以上价格为人民币含税价，有效期内有效；\n" +
                "2. 付款方式：合同签订后预付 30%，发货前付清余款；\n" +
                "3. 交货期：收到预付款后 7 个工作日内发货；\n" +
                "4. 质保期：自交货之日起 12 个月（易损件除外）。"
    }
}
