class_name Settings
## 画质 / 操作设置：预设 + 逐项自定义，保存到 user://settings.cfg

const PRESET_NAMES := ["极高", "高", "中", "低", "自定义"]
const SHADOW_NAMES := ["关闭", "低", "中", "高"]
const AA_NAMES := ["关闭", "FXAA", "MSAA 2x", "MSAA 4x", "TAA"]
const DETAIL_NAMES := ["低", "中", "高"]
const ON_OFF := ["关闭", "开启"]
const FPS_OPTIONS := [30, 60, 90, 120]
const FPS_NAMES := ["30", "60", "90", "120"]
const PATH := "user://settings.cfg"

static var preset := 1
static var render_scale := 0.85
static var shadow_quality := 2
static var aa := 2
static var ao := true
static var post := true
static var detail := 2
static var fps_index := 1
static var show_fps := false
static var sensitivity := 1.0
static var invert_y := false
static var volume := 0.8

static func apply_preset(p: int) -> void:
	preset = p
	match p:
		0: render_scale = 1.0; shadow_quality = 3; aa = 3; ao = true; post = true; detail = 2
		1: render_scale = 0.85; shadow_quality = 2; aa = 2; ao = true; post = true; detail = 2
		2: render_scale = 0.7; shadow_quality = 2; aa = 1; ao = false; post = true; detail = 1
		3: render_scale = 0.55; shadow_quality = 1; aa = 0; ao = false; post = false; detail = 0

static func load_settings() -> void:
	apply_preset(1)
	var cf := ConfigFile.new()
	if cf.load(PATH) != OK: return
	preset = cf.get_value("g", "preset", preset)
	render_scale = cf.get_value("g", "scale", render_scale)
	shadow_quality = cf.get_value("g", "shadow", shadow_quality)
	aa = cf.get_value("g", "aa", aa)
	ao = cf.get_value("g", "ao", ao)
	post = cf.get_value("g", "post", post)
	detail = cf.get_value("g", "detail", detail)
	fps_index = clampi(cf.get_value("g", "fps", fps_index), 0, FPS_OPTIONS.size() - 1)
	show_fps = cf.get_value("g", "show_fps", show_fps)
	sensitivity = cf.get_value("c", "sens", sensitivity)
	invert_y = cf.get_value("c", "invert_y", invert_y)
	volume = cf.get_value("c", "volume", volume)

static func save_settings() -> void:
	var cf := ConfigFile.new()
	cf.set_value("g", "preset", preset); cf.set_value("g", "scale", render_scale)
	cf.set_value("g", "shadow", shadow_quality); cf.set_value("g", "aa", aa)
	cf.set_value("g", "ao", ao); cf.set_value("g", "post", post); cf.set_value("g", "detail", detail)
	cf.set_value("g", "fps", fps_index); cf.set_value("g", "show_fps", show_fps)
	cf.set_value("c", "sens", sensitivity); cf.set_value("c", "invert_y", invert_y); cf.set_value("c", "volume", volume)
	cf.save(PATH)

## 把当前设置应用到视口、渲染服务器与场景
static func apply(game: Node) -> void:
	var vp := game.get_viewport()
	Engine.max_fps = FPS_OPTIONS[fps_index]
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(volume, 0.0001)))
	AudioServer.set_bus_mute(0, volume <= 0.001)
	vp.scaling_3d_mode = Viewport.SCALING_3D_MODE_BILINEAR
	vp.scaling_3d_scale = clampf(render_scale, 0.5, 1.0)
	vp.msaa_3d = Viewport.MSAA_2X if aa == 2 else (Viewport.MSAA_4X if aa == 3 else Viewport.MSAA_DISABLED)
	vp.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if aa == 1 else Viewport.SCREEN_SPACE_AA_DISABLED
	vp.use_taa = aa == 4
	match shadow_quality:
		1:
			RenderingServer.directional_shadow_atlas_set_size(1024, true)
			RenderingServer.directional_soft_shadow_filter_set_quality(RenderingServer.SHADOW_QUALITY_HARD)
		2:
			RenderingServer.directional_shadow_atlas_set_size(2048, true)
			RenderingServer.directional_soft_shadow_filter_set_quality(RenderingServer.SHADOW_QUALITY_SOFT_LOW)
		3:
			RenderingServer.directional_shadow_atlas_set_size(4096, true)
			RenderingServer.directional_soft_shadow_filter_set_quality(RenderingServer.SHADOW_QUALITY_SOFT_MEDIUM)
	game.lighting.apply_quality(shadow_quality, detail, ao, post)
	var range_end: float = [9.0, 14.0, 22.0][detail]
	for n in game.get_tree().get_nodes_in_group("detail"):
		n.visibility_range_end = range_end
