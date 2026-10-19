package com.sleepwell.app;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

class AppTestHelper {
    static List<View> all(View v) {
        List<View> out = new ArrayList<>();
        walk(v, out);
        return out;
    }

    private static void walk(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), out);
        }
    }
}
