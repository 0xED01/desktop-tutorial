class_name TouchInput
extends Node
## 统一输入：多点触控（左半屏浮动摇杆、右半屏滑动转镜头、HUD 按钮）+ 键鼠调试

var enabled := false
var move := Vector2.ZERO            # x 右、y 前，长度 0~1
var look_delta := Vector2.ZERO      # 尚未被相机消费的转动量（度）
var run := false
var run_toggle := false
var moved_while_running := false
var jump_queued := false

var joystick_active := false
var joystick_origin := Vector2.ZERO
var joystick_knob := Vector2.ZERO

var move_finger := -1
var look_finger := -1
var hud_hit: Callable               # (pos: Vector2) -> bool，由界面提供

signal interact_key
signal view_key
signal pause_key

func joystick_radius() -> float:
	return 140.0

func consume_jump() -> bool:
	var j := jump_queued
	jump_queued = false
	return j

func consume_look() -> Vector2:
	var l := look_delta
	look_delta = Vector2.ZERO
	return l

func toggle_run() -> void:
	run_toggle = not run_toggle
	moved_while_running = false

func reset() -> void:
	move = Vector2.ZERO; look_delta = Vector2.ZERO
	move_finger = -1; look_finger = -1; joystick_active = false; run = false

func _input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo:
		match event.physical_keycode:
			KEY_ESCAPE: pause_key.emit()
			KEY_SPACE: if enabled: jump_queued = true
			KEY_E: if enabled: interact_key.emit()
			KEY_V: if enabled: view_key.emit()
	if event is InputEventKey and event.pressed and event.keycode == KEY_BACK:
		pause_key.emit()
	if not enabled:
		return
	var vp_size := get_viewport().get_visible_rect().size
	if event is InputEventScreenTouch:
		if event.pressed:
			if hud_hit.is_valid() and hud_hit.call(event.position):
				return
			if event.position.x < vp_size.x * 0.42 and move_finger < 0:
				move_finger = event.index
				joystick_origin = event.position; joystick_knob = event.position
				joystick_active = true
			elif look_finger < 0:
				look_finger = event.index
		else:
			if event.index == move_finger:
				move_finger = -1; joystick_active = false; _touch_move = Vector2.ZERO
			elif event.index == look_finger:
				look_finger = -1
	elif event is InputEventScreenDrag:
		if event.index == move_finger:
			var r := joystick_radius()
			var d: Vector2 = event.position - joystick_origin
			# 手指拖出范围时底座跟随，保持方向灵敏
			if d.length() > r * 1.25:
				joystick_origin = event.position - d.normalized() * r * 1.25
				d = event.position - joystick_origin
			var v := (d / r).limit_length(1.0)
			var m := v.length()
			_touch_move = Vector2.ZERO if m < 0.12 else v.normalized() * inverse_lerp(0.12, 1.0, m)
			_touch_move.y = -_touch_move.y        # 屏幕向上 = 向前
			joystick_knob = joystick_origin + d.limit_length(r)
		elif event.index == look_finger:
			look_delta += event.relative * (150.0 / maxf(vp_size.y, 1.0))

var _touch_move := Vector2.ZERO

func _process(_dt: float) -> void:
	if not enabled:
		reset()
		return
	var kb := Vector2(
		float(Input.is_physical_key_pressed(KEY_D)) - float(Input.is_physical_key_pressed(KEY_A)),
		float(Input.is_physical_key_pressed(KEY_W)) - float(Input.is_physical_key_pressed(KEY_S)))
	move = kb.normalized() if kb.length_squared() > 0.0 else _touch_move
	# 奔跑键可在静止时预先按下；跑起来之后一旦停下自动取消
	if run_toggle and move.length_squared() > 0.01: moved_while_running = true
	if run_toggle and moved_while_running and move.length_squared() < 0.01 and not joystick_active:
		run_toggle = false; moved_while_running = false
	run = run_toggle or Input.is_physical_key_pressed(KEY_SHIFT)
