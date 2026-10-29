package com.acct.subjects;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String ALL = "全部";
    private static final String FAV = "★ 收藏";

    private List<Subject> all;
    private SubjectAdapter adapter;
    private EditText search;
    private TextView empty;
    private ListView list;
    private LinearLayout chips;
    private final Map<String, TextView> chipViews = new LinkedHashMap<>();
    private String category = ALL;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        all = Repo.all(this);

        ((TextView) findViewById(R.id.subtitle)).setText("初级会计实务 · 共 " + all.size() + " 个常用科目");

        search = findViewById(R.id.search);
        View clear = findViewById(R.id.clear);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);
        chips = findViewById(R.id.chips);

        adapter = new SubjectAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent it = new Intent(this, DetailActivity.class);
            it.putExtra(DetailActivity.EXTRA_NAME, adapter.getItem(pos).name);
            startActivity(it);
        });
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(android.widget.AbsListView view, int state) {
                if (state == SCROLL_STATE_TOUCH_SCROLL) hideKeyboard();
            }
            @Override public void onScroll(android.widget.AbsListView v, int a, int b, int c) {}
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                refresh();
            }
        });
        search.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); return true; }
            return false;
        });
        clear.setOnClickListener(v -> search.setText(""));

        if (savedInstanceState != null) category = savedInstanceState.getString("cat", ALL);
        buildChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(); // 收藏状态可能在详情页改变
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("cat", category);
    }

    private void buildChips() {
        List<String> names = new ArrayList<>();
        names.add(ALL);
        names.add(FAV);
        for (String c : Repo.CATS) names.add(c);
        for (String n : names) {
            TextView chip = new TextView(this);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            int ph = dp(14), pv = dp(7);
            chip.setPadding(ph, pv, ph, pv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setOnClickListener(v -> { category = n; refresh(); });
            chips.addView(chip, lp);
            chipViews.put(n, chip);
        }
    }

    private void refresh() {
        if (adapter == null) return;
        String q = search.getText().toString();
        Set<String> favs = Repo.favorites(this);
        List<Subject> matched = Search.run(all, q);

        // 各分类下的匹配数量
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(ALL, matched.size());
        int favCount = 0;
        for (Subject s : matched) {
            counts.merge(s.cat, 1, Integer::sum);
            if (favs.contains(s.name)) favCount++;
        }
        counts.put(FAV, favCount);

        List<Subject> shown = new ArrayList<>();
        for (Subject s : matched) {
            if (category.equals(ALL)
                    || (category.equals(FAV) && favs.contains(s.name))
                    || category.equals(s.cat)) shown.add(s);
        }

        for (Map.Entry<String, TextView> e : chipViews.entrySet()) {
            String n = e.getKey();
            TextView chip = e.getValue();
            Integer c = counts.get(n);
            chip.setText(n + "  " + (c == null ? 0 : c));
            boolean sel = n.equals(category);
            int color = n.equals(ALL) ? 0xFF1C2430 : n.equals(FAV) ? 0xFFF5A524 : Repo.color(n);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            if (sel) {
                bg.setColor(color);
                chip.setTextColor(0xFFFFFFFF);
            } else {
                bg.setColor(0xFFFFFFFF);
                bg.setStroke(dp(1), 0xFFE0E5EC);
                chip.setTextColor(0xFF5E6A7D);
            }
            chip.setBackground(bg);
        }

        adapter.submit(shown, q, favs);
        list.setSelection(0);
        if (shown.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText(category.equals(FAV) && q.trim().isEmpty()
                    ? "还没有收藏的科目\n在详情页点右上角 ☆ 即可收藏"
                    : "没有找到相关科目\n试试科目编号、名称或拼音首字母");
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
