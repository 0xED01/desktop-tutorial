package com.acct.subjects;

import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SubjectAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final float density;
    private List<Subject> items = new ArrayList<>();
    private Set<String> favorites;
    private String query = "";

    SubjectAdapter(android.content.Context ctx) {
        inflater = LayoutInflater.from(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
    }

    void submit(List<Subject> items, String query, Set<String> favorites) {
        this.items = items;
        this.query = query.trim();
        this.favorites = favorites;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Subject getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.item_subject, parent, false);
        Subject s = getItem(position);
        int color = Repo.color(s.cat);

        TextView code = v.findViewById(R.id.code);
        code.setText(s.hasCode() ? s.code : "新");
        code.setTextColor(color);
        code.setBackground(rounded(Repo.tint(color), 12));

        TextView name = v.findViewById(R.id.name);
        name.setText(highlight(s.name, color));

        v.findViewById(R.id.fav).setVisibility(favorites != null && favorites.contains(s.name) ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.desc)).setText(s.desc);

        TextView dir = v.findViewById(R.id.dir);
        int dc = Repo.dirColor(s.dir);
        dir.setText(s.dir);
        dir.setTextColor(dc);
        dir.setBackground(rounded(Repo.tint(dc), 8));
        return v;
    }

    private CharSequence highlight(String text, int color) {
        if (query.isEmpty()) return text;
        int i = text.indexOf(query);
        if (i < 0) return text;
        SpannableString sp = new SpannableString(text);
        sp.setSpan(new ForegroundColorSpan(color), i, i + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private GradientDrawable rounded(int fill, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * density);
        return d;
    }
}
