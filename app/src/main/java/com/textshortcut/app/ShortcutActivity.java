package com.textshortcut.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** 点击桌面文字图标后打开的界面：如果设置了网址就打开网址，否则什么都不做。 */
public class ShortcutActivity extends Activity {
    public static final String EXTRA_URL = "url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url != null && !url.trim().isEmpty()) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
        finish();
    }
}
