package com.aapin.game.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 一条 TCP 连接，按行收发文本消息。读写各用一个线程，界面线程通过 send/poll 非阻塞地使用。
 * 写线程空闲 1 秒会自动发送心跳 "P"，读超时 6 秒视为断线。
 */
public final class NetLink {
    public static volatile NetLink current;

    public final boolean host;
    public volatile String peerName = "对手";

    private final Socket sock;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    public NetLink(Socket s, boolean host) throws IOException {
        this.sock = s;
        this.host = host;
        s.setTcpNoDelay(true);
        s.setSoTimeout(6000);
        in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    public void start() {
        Thread r = new Thread(this::readLoop, "net-read");
        r.setDaemon(true);
        r.start();
        Thread w = new Thread(this::writeLoop, "net-write");
        w.setDaemon(true);
        w.start();
    }

    private void readLoop() {
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if ("P".equals(line)) continue;
                if ("BYE".equals(line)) break;
                inbox.add(line);
            }
        } catch (IOException ignored) {
        }
        close();
    }

    private void writeLoop() {
        try {
            while (!closed) {
                String msg = outbox.poll(1, TimeUnit.SECONDS);
                if (msg == null) msg = "P";
                out.write(msg);
                out.write('\n');
                out.flush();
                if ("BYE".equals(msg)) break;
            }
        } catch (IOException | InterruptedException ignored) {
        }
        close();
    }

    public void send(String msg) {
        if (closed) return;
        // 积压时丢弃过期的状态包，避免延迟越来越大
        if (msg.startsWith("S|") && outbox.size() > 6) return;
        outbox.offer(msg);
    }

    public String poll() {
        return inbox.poll();
    }

    public boolean isClosed() {
        return closed;
    }

    /** 通知对方后关闭。 */
    public void bye() {
        if (closed) return;
        outbox.clear();
        outbox.offer("BYE");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            close();
        });
        t.setDaemon(true);
        t.start();
    }

    public void close() {
        closed = true;
        try {
            sock.close();
        } catch (IOException ignored) {
        }
    }
}
