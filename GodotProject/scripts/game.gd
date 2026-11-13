extends Node3D
## 游戏入口与总控：加载烘焙好的办公室场景，创建人物、相机、灯光、音频与界面，
## 管理 主菜单 / 探索 / 暂停 / 查看屏幕 四个状态以及全部交互。

enum State { MAIN_MENU, PLAYING, PAUSED, INSPECTING }
enum Act { NONE, SIT, STAND, VIEW_SCREEN, SWITCH }

var state := State.MAIN_MENU
var hud_hidden := false
var office: Node3D
var player: Player
var cam: CameraRig
var lighting: Lighting
var input: TouchInput
var ui: GameUI

var seats: Array = []
var screens: Array = []
var switches: Array = []
var action := Act.NONE
var action_seat := {}
var action_screen := {}
var scan_timer := 0.0
var was_first_person := false

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	DisplayServer.screen_set_keep_on(true)
	Settings.load_settings()

	office = load("res://baked/office.scn").instantiate()
	add_child(office)
	seats = office.get_meta("seats"); screens = office.get_meta("screens"); switches = office.get_meta("switches")
	var room: Vector3 = office.get_meta("room")          # x = 长, y = 高, z = 进深（Godot 中进深沿 -Z）
	var center := Vector3(room.x * 0.5, room.y * 0.5, -room.z * 0.5)
	var area := AABB(Vector3(0, 0, -room.z), room)

	_add_tv_photos(room)
	var audio := GameAudio.new(); add_child(audio)
	input = TouchInput.new(); add_child(input)
	lighting = Lighting.new(); add_child(lighting); lighting.setup(office)

	player = Player.new(); player.name = "Player"
	player.process_mode = Node.PROCESS_MODE_PAUSABLE
	add_child(player)
	player.setup(office.get_meta("spawn_pos"), office.get_meta("spawn_yaw"), area, input)

	cam = CameraRig.new(); add_child(cam)
	cam.setup(player, input, center, Vector3(room.x, room.y, room.z) * 0.5)

	ui = GameUI.new(); add_child(ui); ui.setup(self)
	input.hud_hit = ui.hud_hit
	input.interact_key.connect(interact)
	input.view_key.connect(toggle_view)
	input.pause_key.connect(_on_pause_key)

	apply_settings()
	_enter_menu()
	if "--autotest" in OS.get_cmdline_user_args():
		var t = load("res://tools/autotest.gd").new()
		add_child(t)
		t.run(self)

## 北墙双联电视上的自定义图片：把图片放到 res://tv/tv_left.jpg、tv_right.jpg（16:9）即可；没有则保持默认看板画面
func _add_tv_photos(room: Vector3) -> void:
	var files := ["res://tv/tv_left.jpg", "res://tv/tv_right.jpg"]
	var xs := [8.25, 9.78]
	for i in 2:
		if not ResourceLoader.exists(files[i]): continue
		var tex: Texture2D = load(files[i])
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.03, 0.03, 0.03)
		mat.roughness = 0.15
		mat.emission_enabled = true
		mat.emission = Color.WHITE
		mat.emission_texture = tex
		mat.emission_operator = BaseMaterial3D.EMISSION_OP_MULTIPLY
		mat.emission_energy_multiplier = 0.95
		mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
		var quad := QuadMesh.new()
		quad.size = Vector2(1.45, 0.82)
		quad.material = mat
		var mi := MeshInstance3D.new()
		mi.name = "TvPhoto%d" % i
		mi.mesh = quad
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		mi.position = Vector3(xs[i], 1.72, -room.z + 0.061)     # 贴在电视屏幕前 4 毫米，朝向室内（+Z）
		add_child(mi)

# ---------------- 状态切换 ----------------
func _enter_menu() -> void:
	state = State.MAIN_MENU
	get_tree().paused = false
	input.enabled = false
	player.frozen = true
	cam.set_mode(CameraRig.Mode.MENU)
	player.show_body()
	cam.set_dof(true, 7.0)
	GameAudio.set_ambient(0.14)
	ui.set_interact("")
	ui.refresh()

func start_exploring() -> void:
	state = State.PLAYING
	get_tree().paused = false
	input.enabled = true
	player.frozen = false
	cam.set_mode(CameraRig.Mode.FOLLOW)
	cam.set_first_person(cam.first_person)
	cam.snap_behind_player()
	cam.set_dof(false)
	GameAudio.set_ambient(0.22)
	ui.refresh()

func pause() -> void:
	if state != State.PLAYING: return
	state = State.PAUSED
	get_tree().paused = true
	input.enabled = false
	ui.refresh()

func resume() -> void:
	if state != State.PAUSED: return
	state = State.PLAYING
	get_tree().paused = false
	input.enabled = true
	ui.refresh()

func _on_pause_key() -> void:
	if ui.sub_panel_open(): ui.close_panels()
	elif state == State.PLAYING: pause()
	elif state == State.PAUSED: resume()
	elif state == State.INSPECTING: interact()

func return_to_menu() -> void:
	if player.is_sitting: player.stand_up()
	player.respawn()
	_enter_menu()

func quit_game() -> void:
	Settings.save_settings()
	get_tree().quit()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_PAUSED and state == State.PLAYING:
		pause()
	elif what == NOTIFICATION_WM_GO_BACK_REQUEST:
		_on_pause_key()

# ---------------- 功能 ----------------
func apply_settings() -> void:
	Settings.apply(self)
	cam.dof_enabled_setting = Settings.post

func reset_player() -> void:
	if player.is_sitting: player.stand_up()
	player.respawn()
	cam.snap_behind_player()
	ui.show_toast("已回到出生点")
	if state == State.PAUSED: resume()

func toggle_lights() -> void:
	lighting.toggle_lights()
	GameAudio.play("switch", 0.8)
	ui.show_toast("灯光已打开" if lighting.lights_on else "灯光已关闭")
	ui.refresh()

func cycle_time() -> void:
	lighting.cycle_time()
	ui.show_toast("时间：" + Lighting.TIME_NAMES[lighting.time_mode])
	ui.refresh()

func toggle_view() -> void:
	if state != State.PLAYING: return
	cam.set_first_person(not cam.first_person)
	ui.refresh()

func set_hud_hidden(hidden: bool) -> void:
	hud_hidden = hidden
	ui.refresh()

## 交互键：坐下 / 站起 / 查看屏幕 / 返回 / 开关灯
func interact() -> void:
	if state == State.INSPECTING:
		state = State.PLAYING
		player.frozen = false
		cam.set_mode(CameraRig.Mode.FOLLOW)
		cam.set_first_person(was_first_person)
		cam.set_dof(false)
		ui.refresh()
		return
	if state != State.PLAYING: return
	match action:
		Act.SIT: player.sit(action_seat)
		Act.STAND: player.stand_up()
		Act.SWITCH: toggle_lights()
		Act.VIEW_SCREEN:
			state = State.INSPECTING
			player.frozen = true
			was_first_person = cam.first_person
			var c: Vector3 = action_screen["p"]
			var eye: Vector3 = c + action_screen["n"] * 0.62 + Vector3.UP * 0.03
			cam.inspect(Transform3D(Basis.IDENTITY, eye).looking_at(c, Vector3.UP))
			cam.set_dof(true, 1.6)
			ui.set_interact("")
			ui.refresh()
	scan_timer = 0.0

func _process(dt: float) -> void:
	if state != State.PLAYING: return
	scan_timer -= dt
	if scan_timer > 0.0: return
	scan_timer = 0.1
	_scan()

func _scan() -> void:
	action = Act.NONE
	var label := ""
	if player.is_sitting:
		action = Act.STAND; label = "站起"
	elif player.is_on_floor():
		var p := player.global_position
		var look: Vector3 = -Basis(Vector3.UP, cam.yaw).z
		var best := INF
		for s in switches:
			var d: Vector3 = s["p"] - p; d.y = 0
			var dist := d.length()
			if dist < 1.5 and dist * 0.7 < best:
				best = dist * 0.7; action = Act.SWITCH; label = "关灯" if lighting.lights_on else "开灯"
		for s in screens:
			var d: Vector3 = p - s["p"]; d.y = 0
			var dist := d.length()
			# 必须站在屏幕正面且大致面向它
			if dist < 1.9 and d.normalized().dot(s["n"]) > 0.45 and look.dot(-d.normalized()) > 0.2 and dist * 0.85 < best:
				best = dist * 0.85; action = Act.VIEW_SCREEN; action_screen = s; label = "查看屏幕"
		for s in seats:
			var d: Vector3 = s["p"] - p; d.y = 0
			var dist := d.length()
			if dist < 1.25 and dist < best:
				best = dist; action = Act.SIT; action_seat = s; label = "坐下"
	ui.set_interact(label)
