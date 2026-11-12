class_name GameUI
extends CanvasLayer
## 中文界面：主菜单、暂停菜单、画质设置、操作设置、触控 HUD（摇杆、跳跃 / 奔跑 / 视角 / 交互、灯光与时间快捷键）

const C_CARD := Color(0.13, 0.14, 0.17, 0.96)
const C_ACCENT := Color(0.22, 0.53, 0.96)
const C_SOFT := Color(1, 1, 1, 0.10)
const C_HUD := Color(0.06, 0.07, 0.09, 0.50)
const C_DIM := Color(0.78, 0.80, 0.84)

var game: Node
var root: Control
var hud: Control
var main_menu: Control
var pause_menu: Control
var graphics_panel: Control
var controls_panel: Control
var show_hud_btn: Control
var back_btn: Control
var interact_btn: Control
var joy_base: CircleShape
var joy_knob: CircleShape
var run_btn: CircleShape
var interact_label: Label
var light_label: Label
var time_label: Label
var view_label: Label
var fps_label: Label
var toast: Panel
var toast_label: Label
var toast_timer := 0.0
var pause_light: Button
var pause_time: Button
var refreshers: Array[Callable] = []
var hud_buttons: Array = []          # [{node, action}]
var font: Font

func setup(g: Node) -> void:
	game = g
	layer = 10
	process_mode = Node.PROCESS_MODE_ALWAYS
	font = load("res://fonts/NotoSansSC-Regular.otf")
	var theme := Theme.new()
	theme.default_font = font
	theme.default_font_size = 32
	root = Control.new()
	root.theme = theme
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(root)
	_build_hud()
	_build_overlays()
	main_menu = _build_main_menu()
	pause_menu = _build_pause_menu()
	graphics_panel = _build_graphics_panel()
	controls_panel = _build_controls_panel()
	graphics_panel.visible = false; controls_panel.visible = false

# ---------------- 基础控件 ----------------
func _box(color: Color, radius: int, border := Color(0, 0, 0, 0), border_w := 0) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color = color
	sb.set_corner_radius_all(radius)
	sb.corner_detail = 12
	sb.anti_aliasing = true
	if border_w > 0:
		sb.border_color = border; sb.set_border_width_all(border_w)
	return sb

func _place(c: Control, anchor: Vector2, center: Vector2, size: Vector2) -> void:
	c.anchor_left = anchor.x; c.anchor_right = anchor.x; c.anchor_top = anchor.y; c.anchor_bottom = anchor.y
	c.offset_left = center.x - size.x * 0.5; c.offset_right = center.x + size.x * 0.5
	c.offset_top = center.y - size.y * 0.5; c.offset_bottom = center.y + size.y * 0.5

func _label(parent: Control, text: String, size: int, color := Color.WHITE, align := HORIZONTAL_ALIGNMENT_CENTER) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	l.horizontal_alignment = align
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	l.set_anchors_preset(Control.PRESET_FULL_RECT)
	parent.add_child(l)
	return l

func _button(parent: Control, text: String, size: int, color: Color, action: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.focus_mode = Control.FOCUS_NONE
	b.add_theme_font_size_override("font_size", size)
	b.add_theme_stylebox_override("normal", _box(color, 22))
	b.add_theme_stylebox_override("hover", _box(color.lightened(0.08), 22))
	b.add_theme_stylebox_override("pressed", _box(color.darkened(0.25), 22))
	b.add_theme_stylebox_override("focus", StyleBoxEmpty.new())
	b.pressed.connect(func():
		GameAudio.play("click", 0.6)
		action.call())
	parent.add_child(b)
	return b

## HUD 按钮：自己做命中检测以支持多点触控（一指推摇杆的同时另一指按跳跃）
func _hud_button(text: String, font_size: int, anchor: Vector2, center: Vector2, size: Vector2, round: bool, color: Color, action: Callable) -> Control:
	var p: Control
	if round:
		var c := CircleShape.new(); c.fill = color; c.ring = Color(1, 1, 1, 0.38); c.ring_width = 3.0
		p = c
	else:
		var pn := Panel.new()
		pn.add_theme_stylebox_override("panel", _box(color, 22))
		p = pn
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hud.add_child(p)
	_place(p, anchor, center, size)
	_label(p, text, font_size)
	hud_buttons.append({"node": p, "action": action})
	return p

func hud_hit(pos: Vector2) -> bool:
	if not hud.visible: return false
	for b in hud_buttons:
		var n: Control = b["node"]
		if n.is_visible_in_tree() and n.get_global_rect().grow(8.0).has_point(pos):
			b["action"].call()
			return true
	return false

# ---------------- HUD ----------------
func _build_hud() -> void:
	hud = Control.new()
	hud.set_anchors_preset(Control.PRESET_FULL_RECT)
	hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(hud)
	joy_base = CircleShape.new(); joy_base.mouse_filter = Control.MOUSE_FILTER_IGNORE
	joy_base.fill = Color(0, 0, 0, 0.16); joy_base.ring = Color(1, 1, 1, 0.32); joy_base.ring_width = 4.0
	joy_base.size = Vector2(290, 290); hud.add_child(joy_base)
	joy_knob = CircleShape.new(); joy_knob.mouse_filter = Control.MOUSE_FILTER_IGNORE
	joy_knob.fill = Color(1, 1, 1, 0.55)
	joy_knob.size = Vector2(120, 120); hud.add_child(joy_knob)

	var br := Vector2(1, 1)
	_hud_button("跳跃", 38, br, Vector2(-200, -200), Vector2(180, 180), true, C_HUD, func(): game.input.jump_queued = true)
	run_btn = _hud_button("奔跑", 32, br, Vector2(-410, -150), Vector2(140, 140), true, C_HUD, func(): game.input.toggle_run()) as CircleShape
	var view := _hud_button("第一\n人称", 26, br, Vector2(-150, -420), Vector2(120, 120), true, C_HUD, func(): game.toggle_view())
	view_label = view.get_child(0)
	interact_btn = _hud_button("坐下", 36, br, Vector2(-440, -350), Vector2(250, 104), false, C_ACCENT, func(): game.interact())
	interact_label = interact_btn.get_child(0)
	interact_btn.visible = false

	var tr := Vector2(1, 0)
	_hud_button("菜单", 30, tr, Vector2(-100, 64), Vector2(150, 76), false, C_HUD, func(): game.pause())
	_hud_button("隐藏界面", 28, tr, Vector2(-282, 64), Vector2(190, 76), false, C_HUD, func(): game.set_hud_hidden(true))
	time_label = _hud_button("白天", 28, tr, Vector2(-474, 64), Vector2(170, 76), false, C_HUD, func(): game.cycle_time()).get_child(0)
	light_label = _hud_button("关灯", 28, tr, Vector2(-646, 64), Vector2(150, 76), false, C_HUD, func(): game.toggle_lights()).get_child(0)

func _build_overlays() -> void:
	var show := _button(root, "显示界面", 26, Color(0, 0, 0, 0.22), func(): game.set_hud_hidden(false))
	show.add_theme_color_override("font_color", Color(1, 1, 1, 0.55))
	_place(show, Vector2(1, 0), Vector2(-120, 58), Vector2(190, 68))
	show_hud_btn = show; show.visible = false
	var back := _button(root, "返回", 36, C_ACCENT, func(): game.interact())
	_place(back, Vector2(1, 1), Vector2(-220, -130), Vector2(260, 104))
	back_btn = back; back.visible = false
	fps_label = Label.new()
	fps_label.add_theme_font_size_override("font_size", 26)
	fps_label.add_theme_color_override("font_color", Color(0.6, 1, 0.7, 0.9))
	fps_label.position = Vector2(40, 24); fps_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(fps_label)
	toast = Panel.new(); toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	toast.add_theme_stylebox_override("panel", _box(Color(0, 0, 0, 0.55), 22))
	root.add_child(toast)
	_place(toast, Vector2(0.5, 0), Vector2(0, 150), Vector2(520, 76))
	toast_label = _label(toast, "", 30)
	toast.modulate.a = 0.0

# ---------------- 菜单 ----------------
func _dim(alpha: float) -> ColorRect:
	var d := ColorRect.new()
	d.color = Color(0, 0, 0, alpha)
	d.set_anchors_preset(Control.PRESET_FULL_RECT)
	d.mouse_filter = Control.MOUSE_FILTER_STOP
	root.add_child(d)
	return d

func _card(dim: Control, size: Vector2, title: String) -> Panel:
	var card := Panel.new()
	card.add_theme_stylebox_override("panel", _box(C_CARD, 28))
	dim.add_child(card)
	_place(card, Vector2(0.5, 0.5), Vector2.ZERO, size)
	var t := _label(card, title, 46)
	t.set_anchors_preset(Control.PRESET_TOP_WIDE)
	t.offset_top = 28; t.offset_bottom = 98
	return card

func _menu_button(parent: Control, text: String, y: float, action: Callable, primary := false, width := 520.0) -> Button:
	var b := _button(parent, text, 36, C_ACCENT if primary else C_SOFT, action)
	_place(b, Vector2(0.5, 0), Vector2(0, y + 46), Vector2(width, 92))
	return b

func _build_main_menu() -> Control:
	var dim := _dim(0.38)
	var title := _label(dim, "办公室自由探索", 104)
	title.add_theme_color_override("font_shadow_color", Color(0, 0, 0, 0.6))
	title.add_theme_constant_override("shadow_offset_x", 3); title.add_theme_constant_override("shadow_offset_y", 3)
	_place(title, Vector2(0.5, 0), Vector2(0, 190), Vector2(1400, 140))
	var sub := _label(dim, "一间可以自由走动的 3D 办公室 · 无任务 · 无倒计时 · 完全离线", 32, C_DIM)
	_place(sub, Vector2(0.5, 0), Vector2(0, 290), Vector2(1500, 50))
	var col := Control.new(); col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	dim.add_child(col)
	_place(col, Vector2(0.5, 0.5), Vector2(0, 150), Vector2(560, 480))
	_menu_button(col, "开始探索", 0, func(): game.start_exploring(), true)
	_menu_button(col, "画质设置", 116, func(): _open_panel(graphics_panel))
	_menu_button(col, "操作设置", 232, func(): _open_panel(controls_panel))
	_menu_button(col, "退出游戏", 348, func(): game.quit_game())
	return dim

func _build_pause_menu() -> Control:
	var dim := _dim(0.55)
	var card := _card(dim, Vector2(680, 900), "已暂停")
	_menu_button(card, "继续探索", 120, func(): game.resume(), true)
	_menu_button(card, "重置人物位置", 226, func(): game.reset_player())
	pause_light = _menu_button(card, "关闭灯光", 332, func(): game.toggle_lights())
	pause_time = _menu_button(card, "时间：白天", 438, func(): game.cycle_time())
	_menu_button(card, "画质设置", 544, func(): _open_panel(graphics_panel))
	_menu_button(card, "操作设置", 650, func(): _open_panel(controls_panel))
	_menu_button(card, "返回主菜单", 756, func(): game.return_to_menu())
	dim.visible = false
	return dim

func _row(card: Control, text: String, index: int) -> Control:
	var row := Control.new(); row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	card.add_child(row)
	row.anchor_left = 0; row.anchor_right = 1; row.anchor_top = 0; row.anchor_bottom = 0
	row.offset_left = 50; row.offset_right = -50
	row.offset_top = 116 + index * 78; row.offset_bottom = row.offset_top + 70
	var l := _label(row, text, 32, C_DIM, HORIZONTAL_ALIGNMENT_LEFT)
	l.anchor_right = 0.45
	return row

func _cycle_row(card: Control, text: String, index: int, options: Array, getter: Callable, setter: Callable) -> void:
	var row := _row(card, text, index)
	var value := _label(row, "", 32)
	value.anchor_left = 0.5; value.offset_left = 48; value.offset_right = -88
	var step := func(d: int):
		var n := options.size()
		setter.call(((int(getter.call()) + d) % n + n) % n)
		_on_setting_changed()
	var prev := _button(row, "<", 40, C_SOFT, func(): step.call(-1))
	_place(prev, Vector2(0.5, 0.5), Vector2(0, 0), Vector2(84, 66))
	var next := _button(row, ">", 40, C_SOFT, func(): step.call(1))
	_place(next, Vector2(1, 0.5), Vector2(-42, 0), Vector2(84, 66))
	refreshers.append(func(): value.text = options[clampi(int(getter.call()), 0, options.size() - 1)])

func _slider_row(card: Control, text: String, index: int, minv: float, maxv: float, stepv: float, getter: Callable, setter: Callable, fmt: Callable) -> void:
	var row := _row(card, text, index)
	var value := _label(row, "", 30, Color.WHITE, HORIZONTAL_ALIGNMENT_RIGHT)
	value.anchor_left = 0.86
	var s := HSlider.new()
	s.min_value = minv; s.max_value = maxv; s.step = stepv
	s.focus_mode = Control.FOCUS_NONE
	var track := _box(Color(1, 1, 1, 0.16), 8); track.content_margin_top = 8; track.content_margin_bottom = 8
	var filled := _box(C_ACCENT, 8); filled.content_margin_top = 8; filled.content_margin_bottom = 8
	s.add_theme_stylebox_override("slider", track)
	s.add_theme_stylebox_override("grabber_area", filled)
	s.add_theme_stylebox_override("grabber_area_highlight", filled)
	var knob := GradientTexture2D.new()
	knob.width = 52; knob.height = 52; knob.fill = GradientTexture2D.FILL_RADIAL
	knob.fill_from = Vector2(0.5, 0.5); knob.fill_to = Vector2(1.0, 0.5)
	var grad := Gradient.new(); grad.offsets = PackedFloat32Array([0.0, 0.86, 0.96]); grad.colors = PackedColorArray([Color.WHITE, Color.WHITE, Color(1, 1, 1, 0)])
	knob.gradient = grad
	s.add_theme_icon_override("grabber", knob); s.add_theme_icon_override("grabber_highlight", knob)
	row.add_child(s)
	s.anchor_left = 0.42; s.anchor_right = 0.84; s.anchor_top = 0; s.anchor_bottom = 1
	s.offset_left = 0; s.offset_right = 0; s.offset_top = 0; s.offset_bottom = 0
	s.value = getter.call()
	s.value_changed.connect(func(v: float):
		setter.call(v)
		value.text = fmt.call(v)
		_on_setting_changed(false))
	refreshers.append(func():
		s.set_value_no_signal(getter.call())
		value.text = fmt.call(getter.call()))

func _on_setting_changed(refresh := true) -> void:
	game.apply_settings()
	Settings.save_settings()
	if refresh: _refresh_rows()

func _refresh_rows() -> void:
	for r in refreshers: r.call()

func _build_graphics_panel() -> Control:
	var dim := _dim(0.6)
	var card := _card(dim, Vector2(1180, 960), "画质设置")
	var i := 0
	_cycle_row(card, "画质预设", i, Settings.PRESET_NAMES, func(): return Settings.preset, func(v):
		if v < 4: Settings.apply_preset(v)
		else: Settings.preset = 4); i += 1
	_slider_row(card, "渲染分辨率", i, 0.5, 1.0, 0.05, func(): return Settings.render_scale, func(v): Settings.render_scale = v; Settings.preset = 4, func(v): return "%d%%" % roundi(v * 100.0)); i += 1
	_cycle_row(card, "阴影质量", i, Settings.SHADOW_NAMES, func(): return Settings.shadow_quality, func(v): Settings.shadow_quality = v; Settings.preset = 4); i += 1
	_cycle_row(card, "抗锯齿", i, Settings.AA_NAMES, func(): return Settings.aa, func(v): Settings.aa = v; Settings.preset = 4); i += 1
	_cycle_row(card, "环境光遮蔽 (SSAO)", i, Settings.ON_OFF, func(): return int(Settings.ao), func(v): Settings.ao = v == 1; Settings.preset = 4); i += 1
	_cycle_row(card, "后期特效 (辉光 / 景深)", i, Settings.ON_OFF, func(): return int(Settings.post), func(v): Settings.post = v == 1; Settings.preset = 4); i += 1
	_cycle_row(card, "灯光与细节", i, Settings.DETAIL_NAMES, func(): return Settings.detail, func(v): Settings.detail = v; Settings.preset = 4); i += 1
	_cycle_row(card, "最大帧率", i, Settings.FPS_NAMES, func(): return Settings.fps_index, func(v): Settings.fps_index = v); i += 1
	_cycle_row(card, "显示帧率", i, Settings.ON_OFF, func(): return int(Settings.show_fps), func(v): Settings.show_fps = v == 1); i += 1
	_menu_button(card, "返回", 116 + i * 78 + 16, func(): close_panels(), true, 360)
	return dim

func _build_controls_panel() -> Control:
	var dim := _dim(0.6)
	var card := _card(dim, Vector2(1180, 900), "操作设置")
	_slider_row(card, "镜头灵敏度", 0, 0.3, 2.5, 0.05, func(): return Settings.sensitivity, func(v): Settings.sensitivity = v, func(v): return "%.2f" % v)
	_cycle_row(card, "反转垂直视角", 1, Settings.ON_OFF, func(): return int(Settings.invert_y), func(v): Settings.invert_y = v == 1)
	_slider_row(card, "音量", 2, 0.0, 1.0, 0.05, func(): return Settings.volume, func(v): Settings.volume = v, func(v): return "%d%%" % roundi(v * 100.0))
	var help := _label(card, "· 屏幕左侧：按住并拖动 = 虚拟摇杆，控制人物移动\n· 屏幕右侧：滑动 = 转动镜头（360° 环视、抬头 / 低头）\n· 右下角：跳跃、奔跑（点按切换，停下自动取消）、第一 / 第三人称切换\n· 靠近办公椅出现「坐下」，靠近亮着的显示器出现「查看屏幕」，靠近墙上开关可开关灯\n· 右上角：开关灯、切换白天 / 傍晚 / 夜间、隐藏界面、菜单", 27, C_DIM, HORIZONTAL_ALIGNMENT_LEFT)
	help.vertical_alignment = VERTICAL_ALIGNMENT_TOP
	help.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	help.add_theme_constant_override("line_spacing", 10)
	help.offset_left = 60; help.offset_right = -60; help.offset_top = 380; help.offset_bottom = -150
	_menu_button(card, "返回", 770, func(): close_panels(), true, 360)
	return dim

func _open_panel(panel: Control) -> void:
	_refresh_rows()
	main_menu.visible = false; pause_menu.visible = false
	panel.visible = true

func sub_panel_open() -> bool:
	return graphics_panel.visible or controls_panel.visible

func close_panels() -> void:
	graphics_panel.visible = false; controls_panel.visible = false
	refresh()

# ---------------- 状态刷新 ----------------
func show_toast(msg: String) -> void:
	toast_label.text = msg
	toast_timer = 1.8

func set_interact(text: String) -> void:
	interact_btn.visible = text != ""
	if text != "": interact_label.text = text

func refresh() -> void:
	var sub := sub_panel_open()
	var st: int = game.state
	main_menu.visible = st == game.State.MAIN_MENU and not sub
	pause_menu.visible = st == game.State.PAUSED and not sub
	var playing: bool = st == game.State.PLAYING
	hud.visible = playing and not game.hud_hidden
	show_hud_btn.visible = playing and game.hud_hidden
	back_btn.visible = st == game.State.INSPECTING
	var on: bool = game.lighting.lights_on
	light_label.text = "关灯" if on else "开灯"
	pause_light.text = "关闭灯光" if on else "打开灯光"
	var tn: String = Lighting.TIME_NAMES[game.lighting.time_mode]
	time_label.text = tn; pause_time.text = "时间：" + tn
	view_label.text = "第三\n人称" if game.cam.first_person else "第一\n人称"

func _process(dt: float) -> void:
	# 适配刘海 / 挖孔屏安全区
	var win := Vector2(DisplayServer.window_get_size())
	var vp := root.get_viewport_rect().size
	if win.x > 0 and win.y > 0:
		var safe := DisplayServer.get_display_safe_area()
		var k := vp.x / win.x
		if safe.size.x > 0 and safe.size.x <= win.x:
			root.offset_left = maxf(0.0, safe.position.x) * k
			root.offset_right = -maxf(0.0, win.x - safe.end.x) * k
	if hud.visible:
		var inp: TouchInput = game.input
		var idle := Vector2(root.offset_left + 270.0, vp.y - 260.0)
		var bp: Vector2 = inp.joystick_origin if inp.joystick_active else idle
		var kp: Vector2 = inp.joystick_knob if inp.joystick_active else idle
		joy_base.global_position = bp - joy_base.size * 0.5
		joy_knob.global_position = kp - joy_knob.size * 0.5
		var want := C_ACCENT if inp.run_toggle else C_HUD
		if run_btn.fill != want:
			run_btn.fill = want; run_btn.queue_redraw()
	toast_timer -= dt
	toast.modulate.a = move_toward(toast.modulate.a, 1.0 if toast_timer > 0.0 else 0.0, dt * 5.0)
	fps_label.text = "FPS %d" % Engine.get_frames_per_second() if Settings.show_fps else ""
