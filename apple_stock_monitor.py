"""苹果库存监控 —— Windows 桌面版（监控中国大陆 Apple 官网门店取货库存）。"""

import json
import os
import queue
import sys
import threading
import time
import tkinter as tk
import webbrowser
from tkinter import messagebox, scrolledtext, ttk

import monitor_core as mc

APP_NAME = "苹果库存监控"
VERSION = "1.3.0"
IS_WIN = sys.platform.startswith("win")

DATA_DIR = os.path.join(os.environ.get("APPDATA") or os.path.expanduser("~"), "AppleStockMonitor")
CONFIG_FILE = os.path.join(DATA_DIR, "config.json")
LOG_FILE = os.path.join(DATA_DIR, "monitor.log")

DEFAULT_CONFIG = {
    "parts": "",
    "location": "上海 上海 黄浦区",
    "store_filter": "",
    "interval": 30,
    "repeat_minutes": 5,
    "sound": True,
    "popup": True,
    "toast": True,
    "open_browser": True,
    "prevent_sleep": True,
    "open_bag": False,
    "serverchan_key": "",
    "pushplus_token": "",
    "bark_url": "",
}

PARTS_HINT = ("每行一个型号代码，后面可写备注，例如：\n"
              "MG8L4CH/A  iPhone17Pro 256G 深蓝\n"
              "不知道型号？点右边「型号查询」")


def load_config():
    cfg = dict(DEFAULT_CONFIG)
    try:
        with open(CONFIG_FILE, encoding="utf-8") as f:
            cfg.update(json.load(f))
    except (OSError, ValueError):
        pass
    return cfg


def save_config(cfg):
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(cfg, f, ensure_ascii=False, indent=2)
    except OSError:
        pass


def set_keep_awake(on):
    """监控期间阻止 Windows 自动睡眠（屏幕可以关）。"""
    if not IS_WIN:
        return
    import ctypes
    ES_CONTINUOUS, ES_SYSTEM_REQUIRED = 0x80000000, 0x00000001
    flags = ES_CONTINUOUS | (ES_SYSTEM_REQUIRED if on else 0)
    ctypes.windll.kernel32.SetThreadExecutionState(flags)


class Alarm:
    """持续响铃，直到点击停止或超过 10 分钟。"""

    def __init__(self):
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()

    def _run(self):
        end = time.time() + 600
        while not self._stop.is_set() and time.time() < end:
            if IS_WIN:
                import winsound
                for freq in (1800, 1200, 1800, 1200):
                    if self._stop.is_set():
                        return
                    winsound.Beep(freq, 250)
            else:
                print("\a", end="", flush=True)
            self._stop.wait(0.6)


def show_toast(title, body, url):
    if not IS_WIN:
        return
    try:
        from winotify import Notification, audio
        n = Notification(app_id=APP_NAME, title=title, msg=body[:250], duration="long", launch=url)
        n.set_audio(audio.LoopingAlarm, loop=False)
        n.add_actions(label="去抢购", launch=url)
        n.show()
    except Exception:
        pass


class PartLookupDialog(tk.Toplevel):
    def __init__(self, app):
        super().__init__(app.root)
        self.app = app
        self.title("型号查询助手")
        self.geometry("620x460")
        self.transient(app.root)

        ttk.Label(self, text="粘贴 Apple 官网购买页地址（选机型的那个页面），点「获取型号」：").pack(anchor="w", padx=10, pady=(10, 2))
        row = ttk.Frame(self)
        row.pack(fill="x", padx=10)
        self.url = tk.StringVar(value="https://www.apple.com.cn/shop/buy-iphone/iphone-17-pro")
        ttk.Entry(row, textvariable=self.url).pack(side="left", fill="x", expand=True)
        self.btn = ttk.Button(row, text="获取型号", command=self.fetch)
        self.btn.pack(side="left", padx=(6, 0))

        self.tree = ttk.Treeview(self, columns=("part", "desc"), show="headings", selectmode="extended")
        self.tree.heading("part", text="型号代码")
        self.tree.heading("desc", text="配置")
        self.tree.column("part", width=130, stretch=False)
        self.tree.pack(fill="both", expand=True, padx=10, pady=8)
        self.tree.bind("<Double-1>", lambda e: self.add())

        bottom = ttk.Frame(self)
        bottom.pack(fill="x", padx=10, pady=(0, 10))
        self.status = ttk.Label(bottom, text="可多选（按住 Ctrl），双击或点「添加」加入监控列表")
        self.status.pack(side="left")
        ttk.Button(bottom, text="添加选中型号", command=self.add).pack(side="right")

    def fetch(self):
        url = self.url.get().strip()
        if not url.startswith("http"):
            return
        self.btn.state(["disabled"])
        self.status.config(text="正在获取……")

        def work():
            try:
                items = mc.AppleClient().lookup_parts(url)
                err = None
            except Exception as e:
                items, err = [], e
            self.after(0, lambda: self.show(items, err))

        threading.Thread(target=work, daemon=True).start()

    def show(self, items, err):
        self.btn.state(["!disabled"])
        self.tree.delete(*self.tree.get_children())
        for part, desc in items:
            self.tree.insert("", "end", values=(part, desc))
        if err:
            self.status.config(text=f"获取失败：{err.__class__.__name__}（请确认没开 VPN）")
        elif not items:
            self.status.config(text="没找到型号，可换个页面地址，或参考说明手动查找")
        else:
            self.status.config(text=f"找到 {len(items)} 个型号，选中后点「添加」")

    def add(self):
        lines = []
        for iid in self.tree.selection():
            part, desc = self.tree.item(iid, "values")
            lines.append(f"{part}  {desc}".strip())
        if lines:
            self.app.append_parts(lines)
            self.status.config(text=f"已添加 {len(lines)} 个型号")


class App:
    def __init__(self, root):
        self.root = root
        self.cfg = load_config()
        self.events = queue.Queue()
        self.monitor = None
        self.alarm = Alarm()
        self.rows = {}

        root.title(f"{APP_NAME} v{VERSION} —— Apple 中国大陆官网门店取货")
        root.geometry("980x720")
        root.minsize(860, 600)
        root.protocol("WM_DELETE_WINDOW", self.on_close)
        self.build_ui()
        root.after(200, self.poll_events)

    # ---------- 界面 ----------
    def build_ui(self):
        pad = {"padx": 8, "pady": 4}
        top = ttk.Frame(self.root)
        top.pack(fill="x", padx=8, pady=6)

        # 监控设置
        s = ttk.LabelFrame(top, text="监控设置")
        s.pack(side="left", fill="both", expand=True)
        ttk.Label(s, text="监控型号：").grid(row=0, column=0, sticky="nw", **pad)
        self.parts_text = tk.Text(s, height=5, width=46, font=("Microsoft YaHei UI", 10))
        self.parts_text.grid(row=0, column=1, sticky="nsew", **pad)
        self.parts_text.insert("1.0", self.cfg["parts"] or "")
        side = ttk.Frame(s)
        side.grid(row=0, column=2, sticky="n", **pad)
        ttk.Button(side, text="型号查询", command=lambda: PartLookupDialog(self)).pack(fill="x")
        ttk.Button(side, text="格式说明", command=lambda: messagebox.showinfo("型号格式", PARTS_HINT)).pack(fill="x", pady=4)

        self.location = tk.StringVar(value=self.cfg["location"])
        self.store_filter = tk.StringVar(value=self.cfg["store_filter"])
        self.interval = tk.IntVar(value=self.cfg["interval"])
        self.repeat = tk.IntVar(value=self.cfg["repeat_minutes"])
        ttk.Label(s, text="取货地区：").grid(row=1, column=0, sticky="w", **pad)
        ttk.Entry(s, textvariable=self.location).grid(row=1, column=1, sticky="ew", **pad)
        ttk.Label(s, text="格式：省 市 区，如 北京 北京 朝阳区", foreground="gray").grid(row=1, column=2, sticky="w")
        ttk.Label(s, text="只看门店：").grid(row=2, column=0, sticky="w", **pad)
        ttk.Entry(s, textvariable=self.store_filter).grid(row=2, column=1, sticky="ew", **pad)
        ttk.Label(s, text="门店名关键字，逗号分隔，留空=全部", foreground="gray").grid(row=2, column=2, sticky="w")
        row3 = ttk.Frame(s)
        row3.grid(row=3, column=1, columnspan=2, sticky="w", **pad)
        ttk.Label(s, text="检查间隔：").grid(row=3, column=0, sticky="w", **pad)
        ttk.Spinbox(row3, from_=15, to=600, increment=5, width=6, textvariable=self.interval).pack(side="left")
        ttk.Label(row3, text="秒（建议 20~60，太快会被限制）    仍有货时每").pack(side="left")
        ttk.Spinbox(row3, from_=0, to=120, width=4, textvariable=self.repeat).pack(side="left")
        ttk.Label(row3, text="分钟再提醒一次（0=不重复）").pack(side="left")
        s.columnconfigure(1, weight=1)

        # 提醒方式
        n = ttk.LabelFrame(top, text="有货提醒方式")
        n.pack(side="left", fill="y", padx=(8, 0))
        self.flags = {}
        for key, text in (("sound", "电脑持续响铃"), ("popup", "弹窗置顶提醒"), ("toast", "Windows 通知"),
                          ("open_browser", "自动打开购买页"),
                          ("open_bag", "去抢购直接打开购物袋（需提前加购）"), ("prevent_sleep", "监控时禁止电脑睡眠")):
            v = tk.BooleanVar(value=self.cfg[key])
            self.flags[key] = v
            ttk.Checkbutton(n, text=text, variable=v).pack(anchor="w", padx=8)
        ttk.Separator(n).pack(fill="x", pady=4)
        self.push_vars = {}
        for key, text in (("serverchan_key", "Server酱 SendKey（微信）"), ("pushplus_token", "PushPlus Token（微信）"),
                          ("bark_url", "Bark 地址（iPhone）")):
            ttk.Label(n, text=text).pack(anchor="w", padx=8)
            v = tk.StringVar(value=self.cfg[key])
            self.push_vars[key] = v
            ttk.Entry(n, textvariable=v, width=34).pack(anchor="w", padx=8)
        ttk.Button(n, text="测试提醒", command=self.test_alert).pack(fill="x", padx=8, pady=6)

        # 控制栏
        bar = ttk.Frame(self.root)
        bar.pack(fill="x", padx=8)
        self.start_btn = ttk.Button(bar, text="▶ 开始监控", command=self.start)
        self.start_btn.pack(side="left")
        self.stop_btn = ttk.Button(bar, text="■ 停止监控", command=self.stop, state="disabled")
        self.stop_btn.pack(side="left", padx=6)
        ttk.Button(bar, text="🔕 停止响铃", command=self.alarm.stop).pack(side="left")
        ttk.Button(bar, text="打开日志文件夹", command=self.open_data_dir).pack(side="right")
        self.status = ttk.Label(bar, text="未开始", foreground="gray")
        self.status.pack(side="left", padx=12)

        # 结果表 + 日志
        pane = ttk.PanedWindow(self.root, orient="vertical")
        pane.pack(fill="both", expand=True, padx=8, pady=6)
        tf = ttk.Frame(pane)
        cols = ("part", "product", "store", "status", "quote", "time")
        self.tree = ttk.Treeview(tf, columns=cols, show="headings", height=10)
        for c, t, w in zip(cols, ("型号", "商品", "门店", "状态", "说明", "更新时间"), (100, 230, 150, 60, 200, 80)):
            self.tree.heading(c, text=t)
            self.tree.column(c, width=w, stretch=c in ("product", "quote"))
        self.tree.tag_configure("yes", background="#c8f7c5", foreground="#0a5d00")
        self.tree.tag_configure("no", foreground="#777777")
        self.tree.bind("<Double-1>", self.on_row_double_click)
        vs = ttk.Scrollbar(tf, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=vs.set)
        self.tree.pack(side="left", fill="both", expand=True)
        vs.pack(side="right", fill="y")
        pane.add(tf, weight=3)
        self.log_box = scrolledtext.ScrolledText(pane, height=8, state="disabled", font=("Consolas", 9))
        pane.add(self.log_box, weight=2)
        self.log(f"欢迎使用{APP_NAME}。填写型号和地区后点「开始监控」。双击表格某行可打开购买页。")
        self.log(f"注意：请关闭 VPN/代理，否则苹果中国官网会拒绝请求。日志保存在 {LOG_FILE}")

    # ---------- 配置 ----------
    def collect_config(self):
        cfg = dict(self.cfg)
        cfg["parts"] = self.parts_text.get("1.0", "end").strip()
        cfg["location"] = self.location.get().strip()
        cfg["store_filter"] = self.store_filter.get().strip()
        for name, var, default in (("interval", self.interval, 30), ("repeat_minutes", self.repeat, 5)):
            try:
                cfg[name] = int(var.get())
            except (tk.TclError, ValueError):
                cfg[name] = default
        for k, v in self.flags.items():
            cfg[k] = bool(v.get())
        for k, v in self.push_vars.items():
            cfg[k] = v.get().strip()
        self.cfg = cfg
        save_config(cfg)
        return cfg

    def append_parts(self, lines):
        cur = self.parts_text.get("1.0", "end").strip()
        existing = {p for p, _ in mc.parse_parts(cur)}
        new = [l for l in lines if mc.parse_parts(l) and mc.parse_parts(l)[0][0] not in existing]
        if new:
            self.parts_text.insert("end", ("\n" if cur else "") + "\n".join(new))

    # ---------- 监控 ----------
    def start(self):
        cfg = self.collect_config()
        parts = mc.parse_parts(cfg["parts"])
        if not parts:
            messagebox.showwarning(APP_NAME, "请先填写至少一个型号代码（如 MG8L4CH/A）。\n\n" + PARTS_HINT)
            return
        if not cfg["location"]:
            messagebox.showwarning(APP_NAME, "请填写取货地区，例如：上海 上海 黄浦区")
            return
        interval = max(15, cfg["interval"])
        self.tree.delete(*self.tree.get_children())
        self.rows.clear()
        self.monitor = mc.Monitor(parts, cfg["location"], cfg["store_filter"], interval,
                                  emit=lambda e, p: self.events.put((e, p)),
                                  repeat_minutes=cfg["repeat_minutes"])
        self.monitor.start()
        if cfg["prevent_sleep"]:
            set_keep_awake(True)
        self.start_btn.state(["disabled"])
        self.stop_btn.state(["!disabled"])
        self.status.config(text="监控中……", foreground="#0a7d00")

    def stop(self):
        if self.monitor:
            self.monitor.stop()
            self.monitor = None
        set_keep_awake(False)
        self.start_btn.state(["!disabled"])
        self.stop_btn.state(["disabled"])
        self.status.config(text="已停止", foreground="gray")

    def poll_events(self):
        try:
            while True:
                event, payload = self.events.get_nowait()
                if event == "log":
                    self.log(payload)
                elif event == "row":
                    self.update_row(payload)
                elif event == "round" and self.monitor:
                    self.status.config(text=f"监控中…… 已检查 {payload['count']} 轮，最近 {payload['time']}",
                                       foreground="#0a7d00")
                elif event == "alert":
                    self.fire_alert(payload)

        except queue.Empty:
            pass
        self.root.after(200, self.poll_events)

    def update_row(self, r):
        key = (r["part"], r["store"])
        vals = (r["part"], r["note"] or r["title"], f'{r["name"]}（{r["city"]}）' if r["city"] else r["name"],
                "有货" if r["available"] else "无货", r["quote"], r["time"])
        tag = ("yes",) if r["available"] else ("no",)
        if key in self.rows:
            self.tree.item(self.rows[key], values=vals, tags=tag)
        else:
            self.rows[key] = self.tree.insert("", "end", values=vals, tags=tag)
        if r["available"]:
            self.tree.move(self.rows[key], "", 0)

    def on_row_double_click(self, _event):
        sel = self.tree.selection()
        if sel:
            webbrowser.open(mc.product_url(self.tree.item(sel[0], "values")[0]))

    # ---------- 提醒 ----------
    def fire_alert(self, info):
        if self.cfg.get("open_bag"):
            info = dict(info, url=mc.BAG_URL)
        title, body = mc.build_alert_text(info)
        self.log("★★★ " + title + " ★★★ " + body.replace("\n", " "))
        cfg = self.cfg
        threading.Thread(target=mc.send_push, args=(cfg, title, body, info["url"]),
                         kwargs={"log": lambda s: self.events.put(("log", s))}, daemon=True).start()
        if cfg["sound"]:
            self.alarm.start()
        if cfg["toast"]:
            threading.Thread(target=show_toast, args=(title, body, info["url"]), daemon=True).start()
        if cfg["open_browser"] and info.get("new"):
            webbrowser.open(info["url"])
        if cfg["popup"]:
            self.show_popup(title, body, info["url"])

    def show_popup(self, title, body, url):
        w = tk.Toplevel(self.root)
        w.title(title)
        w.attributes("-topmost", True)
        w.configure(bg="#fff3cd")
        tk.Label(w, text=title, font=("Microsoft YaHei UI", 16, "bold"), bg="#fff3cd", fg="#c00000").pack(padx=20, pady=(16, 6))
        tk.Label(w, text=body, font=("Microsoft YaHei UI", 10), bg="#fff3cd", wraplength=460, justify="left").pack(padx=20)
        bf = tk.Frame(w, bg="#fff3cd")
        bf.pack(pady=14)

        def go():
            self.alarm.stop()
            webbrowser.open(url)
            w.destroy()

        def close():
            self.alarm.stop()
            w.destroy()

        if url:
            tk.Button(bf, text="去抢购", font=("Microsoft YaHei UI", 11, "bold"), bg="#0071e3", fg="white", width=10, command=go).pack(side="left", padx=6)
        tk.Button(bf, text="停止响铃并关闭", width=14, command=close).pack(side="left", padx=6)
        w.protocol("WM_DELETE_WINDOW", close)
        self.root.deiconify()
        w.lift()
        w.focus_force()

    def test_alert(self):
        self.collect_config()
        self.fire_alert({
            "part": "TEST", "note": "测试提醒", "title": "",
            "stores": [{"name": "测试门店", "quote": "今天可取货"}],
            "new": False, "url": mc.BASE_URL + "/shop/buy-iphone",
        })

    # ---------- 其他 ----------
    def log(self, text):
        line = f"{time.strftime('%m-%d %H:%M:%S')}  {text}"
        self.log_box.configure(state="normal")
        self.log_box.insert("end", line + "\n")
        if int(self.log_box.index("end-1c").split(".")[0]) > 2000:
            self.log_box.delete("1.0", "500.0")
        self.log_box.see("end")
        self.log_box.configure(state="disabled")
        try:
            os.makedirs(DATA_DIR, exist_ok=True)
            with open(LOG_FILE, "a", encoding="utf-8") as f:
                f.write(line + "\n")
        except OSError:
            pass

    def open_data_dir(self):
        os.makedirs(DATA_DIR, exist_ok=True)
        if IS_WIN:
            os.startfile(DATA_DIR)

    def on_close(self):
        if self.monitor and not messagebox.askyesno(APP_NAME, "正在监控中，确定要退出吗？\n（最小化窗口即可在后台继续监控）"):
            return
        self.collect_config()
        self.stop()
        self.alarm.stop()
        self.root.destroy()


def main():
    if IS_WIN:
        try:
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    root = tk.Tk()
    App(root)
    if os.environ.get("ASM_SMOKE_TEST"):  # CI 冒烟测试：启动后自动退出
        root.after(3000, root.destroy)
    root.mainloop()


if __name__ == "__main__":
    main()
