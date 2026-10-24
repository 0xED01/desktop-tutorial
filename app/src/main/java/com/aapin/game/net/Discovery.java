package com.aapin.game.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 局域网房间发现：主机每秒 UDP 广播一次，客户端监听并列出房间。 */
public final class Discovery {
    public static final int UDP_PORT = 37020;
    public static final int TCP_PORT = 37021;
    public static final String PROTO = "AAPIN1";

    public static final class Room {
        public final String ip, name;
        public final int port;
        public long seen;

        Room(String ip, String name, int port) {
            this.ip = ip;
            this.name = name;
            this.port = port;
        }
    }

    /** 每次 start 递增，旧线程发现代数变化后退出。 */
    private volatile int gen;
    private DatagramSocket socket;
    private final Map<String, Room> rooms = Collections.synchronizedMap(new LinkedHashMap<>());

    /** 开始广播本机房间。 */
    public void startBroadcast(final String name) {
        stop();
        final int my = gen;
        Thread t = new Thread(() -> {
            try (DatagramSocket s = new DatagramSocket()) {
                socket = s;
                s.setBroadcast(true);
                byte[] data = (PROTO + "|" + name.replace('|', ' ') + "|" + TCP_PORT).getBytes(StandardCharsets.UTF_8);
                while (gen == my) {
                    for (InetAddress a : broadcastAddrs()) {
                        try {
                            s.send(new DatagramPacket(data, data.length, a, UDP_PORT));
                        } catch (Exception ignored) {
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
            }
        }, "lan-broadcast");
        t.setDaemon(true);
        t.start();
    }

    /** 开始监听房间广播。 */
    public void startListen() {
        stop();
        final int my = gen;
        Thread t = new Thread(() -> {
            try (DatagramSocket s = new DatagramSocket(null)) {
                socket = s;
                s.setReuseAddress(true);
                s.setBroadcast(true);
                s.bind(new InetSocketAddress(UDP_PORT));
                s.setSoTimeout(500);
                byte[] buf = new byte[512];
                while (gen == my) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    try {
                        s.receive(p);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    String[] parts = msg.split("\\|");
                    if (parts.length < 3 || !PROTO.equals(parts[0])) continue;
                    String ip = p.getAddress().getHostAddress();
                    int port;
                    try {
                        port = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    Room r = rooms.get(ip);
                    if (r == null || !r.name.equals(parts[1])) {
                        r = new Room(ip, parts[1], port);
                        rooms.put(ip, r);
                    }
                    r.seen = System.currentTimeMillis();
                }
            } catch (Exception ignored) {
            }
        }, "lan-listen");
        t.setDaemon(true);
        t.start();
    }

    /** 最近 4 秒内发现的房间。 */
    public List<Room> rooms() {
        long now = System.currentTimeMillis();
        List<Room> out = new ArrayList<>();
        synchronized (rooms) {
            for (Room r : rooms.values()) if (now - r.seen < 4000) out.add(r);
        }
        return out;
    }

    public void stop() {
        gen++;
        DatagramSocket s = socket;
        if (s != null) s.close();
        socket = null;
    }

    public static List<String> localIPv4() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static List<InetAddress> broadcastAddrs() {
        List<InetAddress> out = new ArrayList<>();
        try {
            out.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) out.add(ia.getBroadcast());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
