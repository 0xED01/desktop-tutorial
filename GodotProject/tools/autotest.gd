extends Node
## 自动化冒烟测试：真实运行游戏，依次验证移动 / 碰撞 / 跳跃 / 坐下 / 查看屏幕 / 灯光与昼夜 / 菜单，并截图。
## 用法： godot -- --autotest
var game: Node
var shot_dir := "user://shots"
var fails := 0

func run(g: Node) -> void:
	game = g
	process_mode = Node.PROCESS_MODE_ALWAYS
	DirAccess.make_dir_recursive_absolute(shot_dir)
	_go.call_deferred()

func _frames(n: int) -> void:
	for i in n: await get_tree().process_frame

func _wait(sec: float) -> void:
	await get_tree().create_timer(sec, true, true, false).timeout

func _shot(shot_name: String) -> void:
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(shot_dir + "/" + shot_name + ".png")
	print("shot ", shot_name)

func _check(ok: bool, what: String) -> void:
	print(("PASS  " if ok else "FAIL  ") + what)
	if not ok: fails += 1

func _go() -> void:
	var p: Player = game.player
	var inp: TouchInput = game.input
	await _frames(12)
	await _shot("01_menu")
	game.start_exploring()
	await _frames(10)
	await _shot("02_third_person")
	if "--quick" in OS.get_cmdline_user_args():
		p.teleport(Vector3(9.0, 0.05, -15.2), 0.0); game.cam.snap_behind_player()
		game.toggle_view(); game.cam.pitch = deg_to_rad(4)
		await _wait(0.6)
		await _shot("q0_tv")
		game.toggle_view()
		p.teleport(Vector3(5.2, 0.05, -3.2), -PI * 0.25); game.cam.snap_behind_player()
		game.cam.pitch = deg_to_rad(-28)
		await _wait(0.6)
		await _shot("q1_closeup")
		game.toggle_view()
		game.cam.pitch = deg_to_rad(-8)
		await _wait(0.6)
		await _shot("q2_first_person")
		get_tree().quit()
		return

	# --- 行走 ---
	var a := p.global_position
	inp._touch_move = Vector2(0, 1)
	await _wait(1.5)
	inp._touch_move = Vector2.ZERO
	var walked := (p.global_position - a).length()
	_check(walked > 1.5 and walked < 3.5, "walk 1.5s moved %.2fm (expect ~2.5)" % walked)
	await _wait(0.4)
	# --- 奔跑 ---
	p.teleport(Vector3(2.0, 0.05, -3.0), -PI * 0.5); game.cam.snap_behind_player()
	await _wait(0.3)
	a = p.global_position
	inp.toggle_run(); inp._touch_move = Vector2(0, 1)
	await _wait(1.5)
	var ran := (p.global_position - a).length()
	_check(ran > 4.5, "run 1.5s moved %.2fm (expect ~6.5)" % ran)
	await _shot("03_running")
	inp._touch_move = Vector2.ZERO
	await _wait(0.5)
	_check(not inp.run_toggle, "run toggle auto-cancels after stopping")
	# --- 跳跃 ---
	var y0 := p.global_position.y
	inp.jump_queued = true
	var peak := y0
	for i in 40:
		await get_tree().physics_frame
		peak = maxf(peak, p.global_position.y)
	_check(peak - y0 > 0.6 and peak - y0 < 1.2, "jump height %.2fm (expect ~0.9)" % (peak - y0))
	await _wait(0.8)
	_check(p.is_on_floor(), "landed after jump")
	# --- 撞桌子：不能穿过工位 ---
	p.teleport(Vector3(1.6, 0.05, -6.0), -PI * 0.5); game.cam.snap_behind_player()   # 面向 +X，前方是 x=2.9~4.3 的工位
	await _wait(0.3)
	inp._touch_move = Vector2(0, 1)
	await _wait(2.5)
	inp._touch_move = Vector2.ZERO
	_check(p.global_position.x < 2.9, "blocked by chair/desk at x=%.2f (bench starts at 2.9)" % p.global_position.x)
	# --- 撞墙 ---
	p.teleport(Vector3(1.2, 0.05, -10.8), PI * 0.5); game.cam.snap_behind_player()   # 面向 -X，西墙在 x=0
	await _wait(0.3)
	inp._touch_move = Vector2(0, 1)
	await _wait(2.0)
	inp._touch_move = Vector2.ZERO
	_check(p.global_position.x > 0.2, "blocked by west wall at x=%.2f" % p.global_position.x)
	await _shot("04_at_wall_camera_pulled_in")

	# --- 坐下 / 站起 ---
	var seat: Dictionary = game.seats[9]
	var stand := Vector3(seat["p"].x + 0.9, 0.05, seat["p"].z + 0.5)
	p.teleport(stand, 0.0); game.cam.snap_behind_player()
	await _wait(0.5)
	stand = p.global_position
	game._scan()
	_check(game.action == game.Act.SIT, "sit prompt appears near chair")
	game.interact()
	await _wait(1.0)
	_check(p.is_sitting, "player is sitting")
	game.cam.yaw = seat["yaw"] + 2.4; game.cam.pitch = deg_to_rad(-18)
	await _wait(0.6)
	await _shot("05_sitting")
	game._scan(); game.interact()
	await _wait(0.5)
	_check(not p.is_sitting and (p.global_position - stand).length() < 0.3, "stood up back at previous position")

	# --- 查看屏幕 ---
	var sc: Dictionary = game.screens[0]
	var front: Vector3 = sc["p"] + sc["n"] * 1.35
	p.teleport(Vector3(front.x, 0.05, front.z), 0.0)
	var n: Vector3 = sc["n"]
	game.cam.yaw = atan2(n.x, n.z); game.cam.pitch = 0.0
	await _wait(0.5)
	game._scan()
	_check(game.action == game.Act.VIEW_SCREEN or game.action == game.Act.SIT, "screen/seat prompt near monitor (action=%d)" % game.action)
	game.action = game.Act.VIEW_SCREEN; game.action_screen = sc
	game.interact()
	await _wait(1.6)
	_check(game.state == game.State.INSPECTING, "inspecting screen")
	await _shot("06_view_screen")
	game.interact()
	await _wait(0.4)

	# --- 第一人称 ---
	p.teleport(Vector3(5.2, 0.05, -3.2), -PI * 0.25); game.cam.snap_behind_player()
	game.toggle_view()
	await _wait(0.6)
	await _shot("07_first_person")
	game.toggle_view()
	game.cam.pitch = deg_to_rad(-28)
	await _wait(0.6)
	await _shot("08_shadow_closeup")

	# --- 灯光与昼夜 ---
	p.teleport(Vector3(6.5, 0.05, -3.4), -0.6); game.cam.snap_behind_player()
	game.toggle_lights()
	await _wait(1.6); await _frames(6)
	await _shot("09_day_lights_off")
	game.cycle_time()
	await _wait(1.6); await _frames(6)
	await _shot("10_evening_lights_off")
	game.cycle_time()
	await _wait(1.6); await _frames(6)
	await _shot("11_night_lights_off")
	game.toggle_lights()
	await _wait(1.6); await _frames(6)
	await _shot("12_night_lights_on")
	game.cycle_time()
	await _wait(1.6); await _frames(6)

	# --- 菜单 ---
	game.pause()
	await _frames(4)
	_check(get_tree().paused, "paused")
	await _shot("13_pause_menu")
	game.ui._open_panel(game.ui.graphics_panel)
	await _frames(4)
	await _shot("14_graphics_settings")
	game.ui.close_panels(); game.resume()
	game.set_hud_hidden(true)
	await _frames(4)
	await _shot("15_hud_hidden")
	print("TEST DONE fails=", fails)
	get_tree().quit(fails)
