package com.ebike.dash

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.ebike.dash.databinding.ItemPlaceBinding
import com.ebike.dash.databinding.SheetSearchBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏搜索面板：最近搜索、家 / 公司快捷入口、带距离和类别的结果列表。
 * 点结果直接开始导航；长按结果可设为家 / 公司或删除记录。
 */
class SearchSheet(
    private val act: AppCompatActivity,
    private val prefs: Prefs,
    private val units: Units,
    private val center: DoubleArray,
    private val navDestName: String?,
    private val onPick: (Place) -> Unit,
    private val onEndNav: () -> Unit,
) {
    private val b = SheetSearchBinding.inflate(LayoutInflater.from(act))
    private val dlg = BottomSheetDialog(act)
    private var job: Job? = null

    fun show() {
        dlg.setContentView(b.root)
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.setOnShowListener {
            // 铺满全屏，像独立的搜索页
            val sheet = dlg.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dlg.behavior.skipCollapsed = true
            b.etQuery.requestFocus()
            b.etQuery.postDelayed({ keyboard(true) }, 150)
        }

        b.btnCancel.setOnClickListener { dlg.dismiss() }
        b.btnClear.setOnClickListener { b.etQuery.setText("") }
        b.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val empty = s.isNullOrBlank()
                b.btnClear.isVisible = !empty
                if (empty) showRecent()
            }
        })
        b.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                search(b.etQuery.text.toString().trim()); true
            } else false
        }

        b.navCard.isVisible = navDestName != null
        b.tvNavTo.text = "正在前往  ${navDestName ?: ""}"
        b.btnEndNav.setOnClickListener {
            dlg.dismiss()
            onEndNav()
        }

        bindShortcut(b.btnHome, Prefs.HOME, "家")
        bindShortcut(b.btnWork, Prefs.WORK, "公司")
        showRecent()
        dlg.setOnDismissListener { job?.cancel() }
        dlg.show()
    }

    // ---------------------------------------------------------------- 家 / 公司

    private fun bindShortcut(v: android.widget.TextView, key: String, label: String) {
        val p = prefs.getPlace(key)
        v.text = if (p == null) "设置$label" else label
        v.alpha = if (p == null) 0.6f else 1f
        v.setOnClickListener {
            if (p != null) pick(p, remember = false)
            else toast("搜索地点后，长按结果即可设为$label")
        }
        v.setOnLongClickListener {
            if (p == null) return@setOnLongClickListener false
            MaterialAlertDialogBuilder(act)
                .setTitle(label)
                .setMessage("${p.name}\n${p.address}")
                .setPositiveButton("清除") { _, _ ->
                    prefs.putPlace(key, null)
                    bindShortcut(v, key, label)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    // ---------------------------------------------------------------- 列表

    private fun showRecent() {
        job?.cancel()
        b.progress.visibility = View.INVISIBLE
        val history = prefs.getPlaces(Prefs.HISTORY)
        b.list.removeAllViews()
        if (history.isEmpty()) {
            b.tvSection.text = "输入地名、小区或商家，按键盘上的搜索\n也可以在地图上长按任意位置开始导航"
            return
        }
        b.tvSection.text = "最近"
        history.forEach { addRow(it, R.drawable.ic_history, fromHistory = true) }
    }

    private fun search(q: String) {
        if (q.isEmpty()) return
        keyboard(false)
        job?.cancel()
        b.progress.visibility = View.VISIBLE
        b.tvSection.text = "正在搜索“$q”…"
        b.list.removeAllViews()
        job = act.lifecycleScope.launch {
            val res = try {
                withContext(Dispatchers.IO) { Net.search(q, center[0], center[1]) }
            } catch (e: Exception) {
                b.progress.visibility = View.INVISIBLE
                b.tvSection.text = "网络不太好，请检查网络后再试"
                return@launch
            }
            b.progress.visibility = View.INVISIBLE
            if (res.isEmpty()) {
                b.tvSection.text = "没有找到“$q”\n试试换个说法，或者在前面加上城市名，如“杭州 西湖”"
                return@launch
            }
            b.tvSection.text = "找到 ${res.size} 个地点，按距离排序"
            res.forEach { addRow(it, R.drawable.ic_pin, fromHistory = false) }
        }
    }

    private fun addRow(p: Place, icon: Int, fromHistory: Boolean) {
        val row = ItemPlaceBinding.inflate(LayoutInflater.from(act), b.list, false)
        row.icon.setImageResource(icon)
        row.tvName.text = p.name
        row.tvAddr.text = listOf(p.kind, p.address).filter { it.isNotBlank() }.joinToString("  ·  ")
        row.tvAddr.isVisible = row.tvAddr.text.isNotEmpty()
        row.tvDist.text = units.navDist(Geo.distance(center[0], center[1], p.lat, p.lon)).replace(" ", "")
        row.root.setOnClickListener { pick(p, remember = true) }
        row.root.setOnLongClickListener {
            val actions = mutableListOf("设为家", "设为公司")
            if (fromHistory) actions.add("删除这条记录")
            MaterialAlertDialogBuilder(act)
                .setTitle(p.name)
                .setItems(actions.toTypedArray()) { _, i ->
                    when (i) {
                        0 -> { prefs.putPlace(Prefs.HOME, p); bindShortcut(b.btnHome, Prefs.HOME, "家"); toast("已设为家") }
                        1 -> { prefs.putPlace(Prefs.WORK, p); bindShortcut(b.btnWork, Prefs.WORK, "公司"); toast("已设为公司") }
                        2 -> { prefs.removeHistory(p); showRecent() }
                    }
                }
                .show()
            true
        }
        b.list.addView(row.root)
    }

    private fun pick(p: Place, remember: Boolean) {
        if (remember) prefs.addHistory(p)
        keyboard(false)
        dlg.dismiss()
        onPick(p)
    }

    private fun keyboard(show: Boolean) {
        val imm = act.getSystemService(InputMethodManager::class.java) ?: return
        if (show) imm.showSoftInput(b.etQuery, InputMethodManager.SHOW_IMPLICIT)
        else imm.hideSoftInputFromWindow(b.etQuery.windowToken, 0)
    }

    private fun toast(s: String) = Toast.makeText(act, s, Toast.LENGTH_SHORT).show()
}
