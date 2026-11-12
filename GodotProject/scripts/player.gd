class_name Player
extends CharacterBody3D
## 第三人称 / 第一人称通用角色：行走、奔跑、跳跃（缓冲 + 土狼时间）、坐下 / 站起、越界复位。
## 动画由 AnimationTree 状态机驱动：Loco（站立-行走-奔跑混合）→ Jump → Air → Land，以及 Sit。

const WALK_SPEED := 1.7
const RUN_SPEED := 4.8
const GRAVITY := 22.0
const JUMP_SPEED := 6.3
const HEIGHT := 1.75
const RADIUS := 0.27

var input: TouchInput
var camera_yaw := 0.0
var frozen := false
var first_person := false
var is_sitting := false
var play_area := AABB()

var visual: Node3D
var tree: AnimationTree
var playback: AnimationNodeStateMachinePlayback
var shape: CollisionShape3D
var horizontal := Vector3.ZERO
var last_grounded := 0.0
var jump_buffered := -10.0
var was_on_floor := true
var step_accum := 0.0
var smoothed_speed := 0.0
var anim_state := "Loco"
var pre_sit := Transform3D()
var spawn_pos := Vector3.ZERO
var spawn_yaw := 0.0
var clock := 0.0

func setup(pos: Vector3, yaw: float, area: AABB, inp: TouchInput) -> void:
	input = inp; spawn_pos = pos; spawn_yaw = yaw; play_area = area
	collision_layer = 2; collision_mask = 1
	floor_snap_length = 0.12; floor_max_angle = deg_to_rad(46.0)
	shape = CollisionShape3D.new()
	var cap := CapsuleShape3D.new(); cap.radius = RADIUS; cap.height = HEIGHT
	shape.shape = cap; shape.position = Vector3(0, HEIGHT * 0.5, 0)
	add_child(shape)
	visual = load("res://baked/player_rig.scn").instantiate()
	add_child(visual)
	_build_animation()
	global_position = pos; rotation.y = yaw

func _build_animation() -> void:
	var ap := AnimationPlayer.new(); ap.name = "AnimationPlayer"
	visual.add_child(ap)
	ap.add_animation_library("", load("res://baked/anims.res"))
	var sm := AnimationNodeStateMachine.new()
	var loco := AnimationNodeBlendSpace1D.new()
	loco.min_space = 0.0; loco.max_space = RUN_SPEED; loco.sync = true
	loco.add_blend_point(_anim("Idle"), 0.0)
	loco.add_blend_point(_anim("Walk"), WALK_SPEED)
	loco.add_blend_point(_anim("Run"), RUN_SPEED)
	sm.add_node("Loco", loco)
	for n in ["Jump", "Air", "Land", "Sit"]: sm.add_node(n, _anim(n))
	_link(sm, "Loco", "Jump", 0.06); _link(sm, "Loco", "Air", 0.16); _link(sm, "Loco", "Sit", 0.3)
	_link(sm, "Jump", "Air", 0.10, true); _link(sm, "Jump", "Land", 0.05)
	_link(sm, "Air", "Land", 0.05); _link(sm, "Air", "Sit", 0.2)
	_link(sm, "Land", "Loco", 0.14, true); _link(sm, "Land", "Jump", 0.05); _link(sm, "Land", "Air", 0.1); _link(sm, "Land", "Sit", 0.2)
	_link(sm, "Sit", "Loco", 0.3)
	var start := AnimationNodeStateMachineTransition.new()
	start.advance_mode = AnimationNodeStateMachineTransition.ADVANCE_MODE_AUTO
	sm.add_transition("Start", "Loco", start)
	tree = AnimationTree.new(); tree.name = "AnimationTree"
	visual.add_child(tree)
	tree.tree_root = sm
	tree.anim_player = tree.get_path_to(ap)
	tree.active = true
	playback = tree.get("parameters/playback")

func _anim(n: String) -> AnimationNodeAnimation:
	var a := AnimationNodeAnimation.new(); a.animation = n
	return a

func _link(sm: AnimationNodeStateMachine, from: String, to: String, xfade: float, at_end := false) -> void:
	var t := AnimationNodeStateMachineTransition.new()
	t.xfade_time = xfade
	if at_end:
		t.switch_mode = AnimationNodeStateMachineTransition.SWITCH_MODE_AT_END
		t.advance_mode = AnimationNodeStateMachineTransition.ADVANCE_MODE_AUTO
	else:
		t.advance_mode = AnimationNodeStateMachineTransition.ADVANCE_MODE_ENABLED
	sm.add_transition(from, to, t)

func _go(state: String) -> void:
	if anim_state == state: return
	anim_state = state
	playback.travel(state)

func set_first_person(fp: bool) -> void:
	first_person = fp
	_set_body_hidden(fp)

func _set_body_hidden(hidden: bool) -> void:
	for mi in visual.find_children("*", "MeshInstance3D", true, false):
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY if hidden else GeometryInstance3D.SHADOW_CASTING_SETTING_ON

func show_body() -> void:
	_set_body_hidden(false)

func teleport(pos: Vector3, yaw: float) -> void:
	global_position = pos; rotation.y = yaw
	velocity = Vector3.ZERO; horizontal = Vector3.ZERO

func respawn() -> void:
	if is_sitting:
		is_sitting = false; shape.disabled = false
	teleport(spawn_pos, spawn_yaw)

func sit(seat: Dictionary) -> void:
	if is_sitting: return
	pre_sit = global_transform
	is_sitting = true
	shape.disabled = true
	global_position = seat["p"]; rotation.y = seat["yaw"]
	velocity = Vector3.ZERO; horizontal = Vector3.ZERO
	_go("Sit")

func stand_up() -> void:
	if not is_sitting: return
	is_sitting = false
	global_transform = pre_sit
	shape.disabled = false
	_go("Loco")

func _physics_process(dt: float) -> void:
	clock += dt
	if is_sitting:
		input.consume_jump()
		tree.set("parameters/Loco/blend_position", 0.0)
		return
	var mv := Vector2.ZERO if frozen else input.move
	var running := (not frozen) and input.run
	var basis_yaw := Basis(Vector3.UP, camera_yaw)
	var wish: Vector3 = basis_yaw.x * mv.x - basis_yaw.z * mv.y
	var mag := clampf(mv.length(), 0.0, 1.0)
	var target_speed := RUN_SPEED if running else WALK_SPEED * lerpf(0.45, 1.0, mag)
	var desired := wish.normalized() * target_speed if mag > 0.01 else Vector3.ZERO
	var on_floor := is_on_floor()
	horizontal = horizontal.move_toward(desired, (26.0 if on_floor else 7.0) * dt)

	var vy := velocity.y
	if input.consume_jump() and not frozen: jump_buffered = clock
	if on_floor: last_grounded = clock
	var jumped := false
	if (on_floor or clock - last_grounded < 0.12) and clock - jump_buffered < 0.14 and vy <= 0.01:
		vy = JUMP_SPEED
		jump_buffered = -10.0; last_grounded = -10.0
		jumped = true
		GameAudio.play("jump", 0.5)
	if not on_floor or jumped: vy -= GRAVITY * dt
	else: vy = -1.0
	var fall_speed := vy

	var before := global_position
	velocity = Vector3(horizontal.x, vy, horizontal.z)
	move_and_slide()
	var moved := global_position - before
	var flat := Vector3(moved.x, 0, moved.z)
	var actual_speed := flat.length() / dt
	var now_floor := is_on_floor()
	if is_on_wall(): horizontal = horizontal.limit_length(maxf(actual_speed, 0.6))
	if now_floor and not was_on_floor and fall_speed < -3.5:
		GameAudio.play("land", inverse_lerp(-3.0, -9.0, fall_speed) * 0.7 + 0.3)

	# 朝向：第一人称跟随镜头，第三人称转向移动方向
	if first_person: rotation.y = camera_yaw
	elif horizontal.length_squared() > 0.04:
		rotation.y = lerp_angle(rotation.y, atan2(-horizontal.x, -horizontal.z), 1.0 - exp(-13.0 * dt))

	# 动画状态
	smoothed_speed = lerpf(smoothed_speed, actual_speed, 1.0 - exp(-12.0 * dt))
	tree.set("parameters/Loco/blend_position", smoothed_speed)
	var grounded_anim := now_floor or clock - last_grounded < 0.1
	if jumped: _go("Jump")
	elif not grounded_anim:
		if anim_state != "Jump" or playback.get_current_node() == "Air": _go("Air")
	elif anim_state == "Air" or anim_state == "Jump":
		_go("Land")
	elif anim_state == "Land" and playback.get_current_node() == "Loco":
		anim_state = "Loco"
	was_on_floor = now_floor

	# 脚步声按位移累计触发，与步幅一致
	if now_floor and actual_speed > 0.4:
		step_accum += flat.length()
		var stride := lerpf(0.78, 1.45, inverse_lerp(WALK_SPEED, RUN_SPEED, clampf(actual_speed, WALK_SPEED, RUN_SPEED)))
		if step_accum >= stride:
			step_accum = 0.0
			GameAudio.play("step", lerpf(0.30, 0.55, actual_speed / RUN_SPEED))
	elif actual_speed < 0.2: step_accum = 0.4

	# 安全网：离开可活动范围则复位
	var p := global_position
	if p.y < -3.0 or p.y > 6.0 or not play_area.grow(1.0).has_point(Vector3(p.x, play_area.position.y + 0.1, p.z)):
		respawn()
