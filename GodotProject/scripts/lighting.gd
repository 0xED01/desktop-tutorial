class_name Lighting
extends Node
## 光照总控：白天 / 傍晚 / 夜间 × 顶灯开 / 关 共六种状态，平滑过渡。
## · 顶灯开：主光为模拟灯盘阵列的竖直柔光（给人物与家具投实时软阴影），聚光补光，灯盘自发光 + 辉光
## · 顶灯关：主光变为从窗户射入的阳光 / 夕阳 / 月光，屋面只投影，地面出现窗格光斑
## 同时管理环境（SSAO、辉光、色调映射、颜色校正）与反射探针刷新。

enum TimeMode { DAY, EVENING, NIGHT }
const TIME_NAMES := ["白天", "傍晚", "夜间"]

var time_mode := TimeMode.DAY
var lights_on := true

var env: Environment
var main: DirectionalLight3D
var fills: Array = []
var windows: Array = []
var probes: Array = []
var roof: Node3D
var m_panel: StandardMaterial3D
var m_ceiling: StandardMaterial3D
var m_backdrop: StandardMaterial3D
var m_shaft: StandardMaterial3D
var m_contact: StandardMaterial3D
var tex_day: Texture2D
var tex_night: Texture2D

var from := {}
var to := {}
var cur := {}
var blend := 1.0
var fill_level := 2
var shadows_on := true
var probe_frames := 0

func setup(office: Node3D) -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	main = office.get_node("Lights/Main")
	fills = get_tree().get_nodes_in_group("fill_lights")
	windows = get_tree().get_nodes_in_group("window_lights")
	probes = get_tree().get_nodes_in_group("probes")
	roof = office.get_node("RoofShadowBlocker")
	for l in fills + windows:
		l.spot_attenuation = 2.0
		l.light_specular = 0.3
	main.shadow_enabled = true
	main.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	main.directional_shadow_max_distance = 26.0
	main.directional_shadow_fade_start = 0.9
	main.shadow_bias = 0.04
	main.shadow_normal_bias = 1.2
	main.shadow_blur = 1.0
	main.light_angular_distance = 0.0
	main.light_cull_mask = 0xFFFFF
	m_panel = load("res://baked/mat/LightPanel.tres")
	m_ceiling = load("res://baked/mat/Ceiling.tres")
	m_backdrop = load("res://baked/mat/Backdrop.tres")
	m_shaft = load("res://baked/mat/Shaft.tres")
	m_contact = load("res://baked/mat/ContactShadow.tres")
	tex_day = load("res://baked/mat/tex_SkylineDay.res")
	tex_night = load("res://baked/mat/tex_SkylineNight.res")
	m_shaft.cull_mode = BaseMaterial3D.CULL_DISABLED
	m_shaft.no_depth_test = false
	m_contact.render_priority = -1
	var glass: StandardMaterial3D = load("res://baked/mat/Glass.tres")
	glass.roughness = 0.12; glass.metallic_specular = 0.35; glass.albedo_color.a = 0.10

	env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.02, 0.02, 0.03)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.reflected_light_source = Environment.REFLECTION_SOURCE_DISABLED
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_white = 1.6
	env.ssao_enabled = true
	env.ssao_radius = 0.7
	env.ssao_intensity = 2.2
	env.ssao_power = 1.4
	env.ssao_light_affect = 0.25
	env.glow_enabled = true
	env.glow_intensity = 0.55
	env.glow_bloom = 0.03
	env.glow_hdr_threshold = 1.05
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_SCREEN
	env.adjustment_enabled = true
	env.adjustment_contrast = 1.06
	env.adjustment_saturation = 1.05
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)

	to = _state(time_mode, lights_on)
	cur = to.duplicate()
	_push(cur)
	refresh_probes()

# ---------- 六种光照状态 ----------
func _state(t: int, on: bool) -> Dictionary:
	var s := {"sun": not on, "night": t == TimeMode.NIGHT}
	if on:
		s.rot = Quaternion.from_euler(Vector3(deg_to_rad(-60), deg_to_rad(-115), 0))
		s.main_color = Color(1, 0.985, 0.96); s.main = 0.85; s.fill = 1.2
		s.panel = 3.2; s.ceiling = 0.22; s.exposure = 1.05; s.contact = 0.30
	else:
		s.fill = 0.0; s.panel = 0.02; s.contact = 0.14
	match t:
		TimeMode.DAY:
			s.window_color = Color(0.86, 0.93, 1.0); s.backdrop = 1.5; s.backdrop_tint = Color(1, 1, 1)
			if on:
				s.ambient = Color(0.42, 0.43, 0.45); s.window = 1.2; s.shaft = 0.06
			else:
				s.rot = Quaternion.from_euler(Vector3(deg_to_rad(-34), deg_to_rad(-18), 0))
				s.main_color = Color(1, 0.96, 0.88); s.main = 2.4
				s.ambient = Color(0.27, 0.30, 0.34); s.window = 2.6; s.shaft = 0.20; s.exposure = 1.35; s.ceiling = 0.03
		TimeMode.EVENING:
			s.window_color = Color(1.0, 0.62, 0.36); s.backdrop = 1.3; s.backdrop_tint = Color(1.15, 0.70, 0.42)
			if on:
				s.ambient = Color(0.42, 0.41, 0.41); s.window = 1.1; s.shaft = 0.10
			else:
				s.rot = Quaternion.from_euler(Vector3(deg_to_rad(-15), deg_to_rad(-30), 0))
				s.main_color = Color(1, 0.56, 0.28); s.main = 2.4
				s.ambient = Color(0.20, 0.16, 0.16); s.window = 2.4; s.shaft = 0.28; s.exposure = 1.5; s.ceiling = 0.02
		_:
			s.window_color = Color(0.50, 0.62, 1.0); s.backdrop = 1.0; s.backdrop_tint = Color(1, 1, 1)
			if on:
				s.ambient = Color(0.41, 0.42, 0.44); s.window = 0.1; s.shaft = 0.0
			else:
				s.rot = Quaternion.from_euler(Vector3(deg_to_rad(-42), deg_to_rad(22), 0))
				s.main_color = Color(0.55, 0.66, 1.0); s.main = 0.40
				s.ambient = Color(0.035, 0.04, 0.058); s.window = 0.4; s.shaft = 0.03; s.exposure = 2.1; s.ceiling = 0.0
	return s

func _mix(a: Dictionary, b: Dictionary, t: float) -> Dictionary:
	var r := {}
	for k in ["main", "fill", "panel", "ceiling", "exposure", "contact", "window", "shaft", "backdrop"]:
		r[k] = lerpf(a[k], b[k], t)
	for k in ["main_color", "window_color", "ambient", "backdrop_tint"]:
		r[k] = (a[k] as Color).lerp(b[k], t)
	r.sun = a.sun if t < 0.5 else b.sun
	r.night = a.night if t < 0.5 else b.night
	if a.sun != b.sun:
		# 主光在"顶灯 ↔ 日光"之间切换时先暗后亮，避免阴影方向扫过整个房间
		r.main *= absf(t * 2.0 - 1.0)
		r.rot = a.rot if t < 0.5 else b.rot
	else:
		r.rot = (a.rot as Quaternion).slerp(b.rot, t)
	return r

func _push(s: Dictionary) -> void:
	main.quaternion = s.rot
	main.light_color = s.main_color
	main.light_energy = s.main
	main.shadow_enabled = shadows_on and s.main > 0.01
	main.shadow_opacity = 0.92 if s.sun else 0.80
	if roof.visible != s.sun: roof.visible = s.sun
	# 灯光细节较低时补光数量减少，用环境光补偿亮度
	var boost: float = 0.0 if s.sun else ([0.20, 0.09, 0.0][fill_level])
	env.ambient_light_color = s.ambient + Color(boost, boost, boost, 0)
	env.ambient_light_energy = 1.0
	env.tonemap_exposure = s.exposure
	for i in fills.size():
		var l: SpotLight3D = fills[i]
		l.visible = s.fill > 0.01 and (fill_level == 2 or (fill_level == 1 and i % 2 == 0))
		l.light_energy = s.fill * (1.5 if fill_level == 1 else 1.0)
	for l in windows:
		l.visible = s.window > 0.05 and fill_level > 0
		l.light_energy = s.window
		l.light_color = s.window_color
	m_panel.emission_energy_multiplier = s.panel
	m_panel.albedo_color = Color(0.75, 0.76, 0.78).lerp(Color.WHITE, clampf(s.panel, 0.0, 1.0))
	m_ceiling.emission_energy_multiplier = s.ceiling
	var b: float = s.backdrop
	var bt: Color = s.backdrop_tint
	m_backdrop.albedo_color = Color(bt.r * b, bt.g * b, bt.b * b)
	var tex := tex_night if s.night else tex_day
	if m_backdrop.albedo_texture != tex: m_backdrop.albedo_texture = tex
	var wc: Color = s.window_color
	m_shaft.albedo_color = Color(wc.r, wc.g, wc.b, s.shaft)
	m_contact.albedo_color = Color(0, 0, 0, s.contact)

func cycle_time() -> void:
	time_mode = (time_mode + 1) % 3 as TimeMode
	_begin()

func toggle_lights() -> void:
	lights_on = not lights_on
	_begin()

func _begin() -> void:
	from = cur.duplicate()
	to = _state(time_mode, lights_on)
	blend = 0.0

func apply_quality(shadow_quality: int, detail: int, ao: bool, post: bool) -> void:
	fill_level = detail
	shadows_on = shadow_quality > 0
	env.ssao_enabled = ao
	env.glow_enabled = post
	_push(cur)
	refresh_probes()

func refresh_probes() -> void:
	probe_frames = 4
	for p in probes: p.update_mode = ReflectionProbe.UPDATE_ALWAYS

func _process(dt: float) -> void:
	if blend < 1.0:
		blend = minf(1.0, blend + minf(dt, 0.1) / 0.9)
		cur = _mix(from, to, smoothstep(0.0, 1.0, blend))
		_push(cur)
		if blend >= 1.0: refresh_probes()
	# 反射探针只在光照变化后重渲染几帧，平时零开销
	if probe_frames > 0:
		probe_frames -= 1
		if probe_frames == 0:
			for p in probes: p.update_mode = ReflectionProbe.UPDATE_ONCE
