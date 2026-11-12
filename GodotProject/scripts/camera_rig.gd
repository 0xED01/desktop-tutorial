class_name CameraRig
extends Node3D
## 相机：第三人称环绕跟随（球形扫掠防穿墙、遇障自动拉近）、第一人称、主菜单环游、查看屏幕特写

enum Mode { MENU, FOLLOW, INSPECT }
const MAX_DISTANCE := 3.4
const MIN_DISTANCE := 0.35
const PROBE_RADIUS := 0.22

var cam: Camera3D
var attrs: CameraAttributesPractical
var player: Player
var input: TouchInput
var mode := Mode.MENU
var first_person := false
var yaw := 0.0
var pitch := deg_to_rad(-12.0)
var distance := 3.2
var pivot_smoothed := Vector3.ZERO
var inspect_xf := Transform3D()
var menu_angle := deg_to_rad(200.0)
var room_center := Vector3.ZERO
var room_extents := Vector3.ONE
var fov := 60.0
var sphere := SphereShape3D.new()
var dof_target := 0.0
var dof_weight := 0.0
var dof_distance := 9.0
var dof_enabled_setting := true

func setup(p: Player, inp: TouchInput, center: Vector3, extents: Vector3) -> void:
	player = p; input = inp; room_center = center; room_extents = extents
	process_mode = Node.PROCESS_MODE_ALWAYS
	process_priority = 100
	cam = Camera3D.new()
	cam.near = 0.08; cam.far = 120.0; cam.fov = 60.0
	attrs = CameraAttributesPractical.new()
	cam.attributes = attrs
	add_child(cam)
	cam.make_current()
	sphere.radius = PROBE_RADIUS
	yaw = p.rotation.y
	pivot_smoothed = _pivot_target()

func _pivot_target() -> Vector3:
	return player.global_position + Vector3.UP * (1.12 if player.is_sitting else 1.52)

func set_mode(m: Mode) -> void:
	mode = m
	if m == Mode.FOLLOW: pivot_smoothed = _pivot_target()

func set_first_person(fp: bool) -> void:
	first_person = fp
	player.set_first_person(fp and mode != Mode.MENU)
	_clamp_pitch()

func _clamp_pitch() -> void:
	pitch = clampf(pitch, deg_to_rad(-80.0 if first_person else -70.0), deg_to_rad(80.0 if first_person else 30.0))

func inspect(xf: Transform3D) -> void:
	inspect_xf = xf
	mode = Mode.INSPECT
	player.show_body()

func snap_behind_player() -> void:
	yaw = player.rotation.y
	pitch = 0.0 if first_person else deg_to_rad(-12.0)
	pivot_smoothed = _pivot_target()
	distance = 3.2

func set_dof(on: bool, dist := 9.0) -> void:
	dof_target = 1.0 if on else 0.0
	if on: dof_distance = dist

func _process(dt: float) -> void:
	dt = minf(dt, 0.1)
	_update_dof(dt)
	if mode == Mode.MENU:
		# 主菜单：沿椭圆轨道缓慢环视办公室
		menu_angle += dt * deg_to_rad(4.5)
		var pos := Vector3(room_center.x + cos(menu_angle) * room_extents.x * 0.5, 1.95, room_center.z + sin(menu_angle) * room_extents.z * 0.55)
		cam.global_position = pos
		cam.look_at(Vector3(room_center.x, 0.9, room_center.z), Vector3.UP)
		cam.fov = 58.0; fov = 58.0
		input.consume_look()
		return
	if mode == Mode.INSPECT:
		var k := 1.0 - exp(-7.0 * dt)
		cam.global_transform = cam.global_transform.interpolate_with(inspect_xf, k)
		fov = lerpf(fov, 38.0, k); cam.fov = fov
		input.consume_look()
		return

	# ---------- 跟随 ----------
	var look := input.consume_look() * Settings.sensitivity
	if not get_tree().paused and not player.frozen:
		yaw -= deg_to_rad(look.x)
		pitch -= deg_to_rad(look.y) * (-1.0 if Settings.invert_y else 1.0)
	_clamp_pitch()
	yaw = wrapf(yaw, -PI, PI)
	player.camera_yaw = yaw
	var rot := Basis.from_euler(Vector3(pitch, yaw, 0.0))
	var target := _pivot_target()
	pivot_smoothed = pivot_smoothed.lerp(target, 1.0 - exp(-18.0 * dt))
	var pivot := Vector3(target.x, pivot_smoothed.y, target.z)
	var pos: Vector3
	var target_fov := 60.0
	if first_person:
		target_fov = 68.0
		if player.is_sitting: pos = player.global_position + Vector3.UP * 1.20 - player.global_transform.basis.z * 0.12
		else: pos = player.global_position + Vector3.UP * 1.64 - Basis(Vector3.UP, yaw).z * 0.10
	else:
		# 从轴心向后做球形扫掠：碰到墙 / 家具立即拉近，离开后平滑恢复
		var back: Vector3 = rot.z
		var origin := pivot
		var shoulder: Vector3 = rot.x * 0.22
		if _cast(pivot, shoulder) >= 1.0: origin += shoulder
		var frac := _cast(origin, back * MAX_DISTANCE)
		var want := maxf(MAX_DISTANCE * frac - 0.03, MIN_DISTANCE) if frac < 1.0 else MAX_DISTANCE
		distance = want if want < distance else lerpf(distance, want, 1.0 - exp(-4.5 * dt))
		pos = origin + back * distance
	fov = lerpf(fov, target_fov, 1.0 - exp(-8.0 * dt))
	cam.fov = fov
	cam.global_transform = Transform3D(rot, pos)

## 球形扫掠，返回可安全移动的比例（1 = 无遮挡）
func _cast(from: Vector3, motion: Vector3) -> float:
	var q := PhysicsShapeQueryParameters3D.new()
	q.shape = sphere
	q.transform = Transform3D(Basis.IDENTITY, from)
	q.motion = motion
	q.collision_mask = 1
	var r := get_world_3d().direct_space_state.cast_motion(q)
	return r[0] if r.size() > 0 else 1.0

func _update_dof(dt: float) -> void:
	dof_weight = move_toward(dof_weight, dof_target if dof_enabled_setting else 0.0, dt * 2.5)
	var on := dof_weight > 0.01
	attrs.dof_blur_far_enabled = on
	if on:
		attrs.dof_blur_far_distance = dof_distance
		attrs.dof_blur_far_transition = dof_distance * 1.6
		attrs.dof_blur_amount = 0.08 * dof_weight
