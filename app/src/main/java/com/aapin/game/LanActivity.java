package com.aapin.game;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.aapin.game.net.Discovery;
import com.aapin.game.net.NetLink;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/** 局域网大厅：创建房间 / 发现房间 / 手动输入 IP 加入。 */
public final class LanActivity extends BaseActivity {
    private static final int VERSION = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Discovery disc = new Discovery();
    private WifiManager.MulticastLock lock;
    private ServerSocket server;
    private NetLink pending;
    private boolean hosting, busy, launching;

    private LinearLayout roomList;
    private TextView status;
    private String lastRooms = "";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshRooms();
            ui.postDelayed(this, 800);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        launching = false;
        hosting = busy = false;
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            lock = wm.createMulticastLock("aapin");
            lock.setReferenceCounted(false);
            lock.acquire();
        }
        build();
        disc.startListen();
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacksAndMessages(null);
        disc.stop();
        closeServer();
        if (!launching && pending != null) {
            pending.close();
            pending = null;
        }
        if (lock != null && lock.isHeld()) lock.release();
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.background(th));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 32));
        sv.addView(col);
        int full = ViewGroup.LayoutParams.MATCH_PARENT, wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        int m = Ui.dp(this, 6);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹ 返回", 18, th.text, true);
        back.setPadding(0, Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        back.setOnClickListener(v -> finish());
        header.addView(back);
        header.addView(Ui.text(this, "局域网联机", 24, th.text, true));
        col.addView(header);

        List<String> ips = Discovery.localIPv4();
        String info = "昵称：" + Prefs.nick(this) + "\n本机 IP：" + (ips.isEmpty() ? "未连接网络" : android.text.TextUtils.join("  ", ips));
        TextView infoT = Ui.text(this, info, 15, Ui.sub(th), false);
        infoT.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        col.addView(infoT);

        status = Ui.text(this, "", 16, th.accent, true);
        status.setGravity(Gravity.CENTER);
        col.addView(status, Ui.lp(full, wrap, m));

        if (hosting) {
            status.setText("房间已创建，等待对手加入…\n对方可在“附近的房间”中看到你\n或手动输入你的 IP 加入");
            col.addView(Ui.button(this, "取消房间", 0xFF999999, 0xFFFFFFFF, v -> cancelHost()), Ui.lp(full, wrap, m));
        } else {
            col.addView(Ui.button(this, "创建房间（我当主机）", th.accent, 0xFFFFFFFF, v -> host()),
                    Ui.lp(full, wrap, m));

            TextView sec = Ui.text(this, "附近的房间", 19, th.text, true);
            sec.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
            col.addView(sec);
            roomList = new LinearLayout(this);
            roomList.setOrientation(LinearLayout.VERTICAL);
            col.addView(roomList, Ui.lp(full, wrap, 0));
            lastRooms = null;
            refreshRooms();

            TextView sec2 = Ui.text(this, "手动输入 IP 加入", 19, th.text, true);
            sec2.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
            col.addView(sec2);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText ip = new EditText(this);
            ip.setHint("例如 192.168.1.5");
            ip.setSingleLine(true);
            ip.setInputType(InputType.TYPE_CLASS_PHONE);
            ip.setTextColor(th.dark() ? 0xFFFFFFFF : 0xFF222222);
            ip.setHintTextColor(0xFF999999);
            ip.setBackground(Ui.round(th.panel, Ui.dp(this, 12)));
            ip.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            row.addView(ip, new LinearLayout.LayoutParams(0, wrap, 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(wrap, wrap);
            bp.leftMargin = m * 2;
            row.addView(Ui.button(this, "加入", th.p1, 0xFFFFFFFF, v -> {
                String s = ip.getText().toString().trim();
                if (s.isEmpty()) {
                    Toast.makeText(this, "请输入主机 IP", Toast.LENGTH_SHORT).show();
                    return;
                }
                join(s, Discovery.TCP_PORT);
            }), bp);
            col.addView(row, Ui.lp(full, wrap, 0));
        }

        TextView tips = Ui.text(this, "提示：\n1. 两台手机连接同一个 WiFi，或一台开热点、另一台连接该热点。\n"
                + "2. 一方点“创建房间”，另一方在列表中点击房间加入。\n"
                + "3. 列表里看不到时，可以手动输入主机显示的 IP。\n"
                + "4. 主机在下方蓝色，加入者在下方红色，双方各自看到自己在下方。", 13, Ui.sub(th), false);
        tips.setLineSpacing(Ui.dp(this, 3), 1f);
        tips.setPadding(0, Ui.dp(this, 22), 0, 0);
        col.addView(tips);
        setContentView(sv);
    }

    private void refreshRooms() {
        if (roomList == null || hosting) return;
        List<Discovery.Room> rooms = disc.rooms();
        StringBuilder key = new StringBuilder();
        for (Discovery.Room r : rooms) key.append(r.ip).append(r.name).append(';');
        if (key.toString().equals(lastRooms)) return;
        lastRooms = key.toString();
        roomList.removeAllViews();
        if (rooms.isEmpty()) {
            TextView t = Ui.text(this, "正在搜索… 还没有发现房间", 15, Ui.sub(th), false);
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            roomList.addView(t);
            return;
        }
        for (Discovery.Room r : rooms) {
            roomList.addView(Ui.button(this, "加入  " + r.name + "  (" + r.ip + ")", th.p2, 0xFFFFFFFF,
                    v -> join(r.ip, r.port)), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(this, 4)));
        }
    }

    // ---------------- 主机 ----------------

    private void host() {
        if (busy) return;
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(Discovery.TCP_PORT));
            server = s;
        } catch (IOException e) {
            Toast.makeText(this, "创建房间失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        hosting = true;
        disc.startBroadcast(Prefs.nick(this));
        build();
        final ServerSocket s = server;
        new Thread(() -> {
            try {
                Socket c = s.accept();
                NetLink link = new NetLink(c, true);
                link.start();
                ui.post(() -> {
                    if (!hosting) {
                        link.close();
                        return;
                    }
                    disc.stop();
                    closeServer();
                    handshake(link);
                });
            } catch (IOException ignored) {
            }
        }, "lan-accept").start();
    }

    private void cancelHost() {
        hosting = false;
        closeServer();
        disc.startListen();
        build();
    }

    private void closeServer() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
            server = null;
        }
    }

    // ---------------- 加入 ----------------

    private void join(String ip, int port) {
        if (busy || hosting) return;
        busy = true;
        status.setText("正在连接 " + ip + " …");
        new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, port), 3000);
                NetLink link = new NetLink(s, false);
                link.start();
                link.send("HELLO|" + Prefs.nick(this) + "|" + VERSION);
                ui.post(() -> handshake(link));
            } catch (Exception e) {
                ui.post(() -> {
                    busy = false;
                    if (status != null) status.setText("");
                    Toast.makeText(this, "连接失败，请确认对方已创建房间且在同一网络", Toast.LENGTH_LONG).show();
                });
            }
        }, "lan-connect").start();
    }

    // ---------------- 握手 ----------------

    private void handshake(NetLink link) {
        pending = link;
        final long start = System.currentTimeMillis();
        if (status != null) status.setText("已连接，正在握手…");
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (pending != link) return;
                String m;
                while ((m = link.poll()) != null) {
                    String[] p = m.split("\\|");
                    if (link.host && p[0].equals("HELLO") && p.length >= 2) {
                        link.peerName = p[1];
                        link.send("WELCOME|" + Prefs.nick(LanActivity.this) + "|" + VERSION);
                        launch(link);
                        return;
                    }
                    if (!link.host && p[0].equals("WELCOME") && p.length >= 2) {
                        link.peerName = p[1];
                        launch(link);
                        return;
                    }
                }
                if (link.isClosed() || System.currentTimeMillis() - start > 6000) {
                    link.close();
                    pending = null;
                    busy = false;
                    Toast.makeText(LanActivity.this, "握手失败，请重试", Toast.LENGTH_SHORT).show();
                    if (link.host) cancelHost();
                    else if (status != null) status.setText("");
                    return;
                }
                ui.postDelayed(this, 50);
            }
        });
    }

    private void launch(NetLink link) {
        pending = null;
        launching = true;
        busy = false;
        hosting = false;
        NetLink.current = link;
        Sfx.play(Sfx.GO);
        GameActivity.start(this, "lan", 0);
    }
}
