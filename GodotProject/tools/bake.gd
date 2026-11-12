extends SceneTree
# 烘焙工具：把 bake_source/（由场景生成代码导出的数据）转换为 Godot 资源：
#   baked/office.scn      办公室场景（网格、材质、碰撞体、灯光、反射探针、交互点）
#   baked/player_rig.scn  人物骨架与分段网格
#   baked/anims.res       站立 / 行走 / 奔跑 / 起跳 / 滞空 / 落地 / 坐姿 动画库
#   baked/sfx_*.res       合成音效
# 用法： godot --headless -s tools/bake.gd

const SRC := "res://bake_source/"
var textures := {}
var materials: Array = []

func _init() -> void:
	DirAccess.make_dir_recursive_absolute("res://baked/mat")
	var data: Dictionary = JSON.parse_string(FileAccess.get_file_as_string(SRC + "scene.json"))
	_bake_materials(data["materials"])
	_tex("SkylineNight"); _tex("SkylineDay")
	_bake_office(data)
	_bake_rig(data)
	_bake_anims(data["anims"], data["rig"])
	_bake_audio()
	_bake_icon()
	print("BAKE DONE")
	quit()

func _tex(name: String, srgb_hint := true) -> Texture2D:
	if name == "": return null
	if textures.has(name): return textures[name]
	var img := Image.load_from_file(ProjectSettings.globalize_path(SRC + "textures/" + name + ".png"))
	if img.detect_alpha() == Image.ALPHA_NONE: img.convert(Image.FORMAT_RGB8)
	img.generate_mipmaps()
	var t := ImageTexture.create_from_image(img)
	t.resource_name = name
	var path := "res://baked/mat/tex_" + name + ".res"
	ResourceSaver.save(t, path, ResourceSaver.FLAG_COMPRESS)
	t = load(path)
	textures[name] = t
	return t

func _bake_materials(list: Array) -> void:
	for m in list:
		var mat := StandardMaterial3D.new()
		mat.resource_name = m["name"]
		var c: Array = m["color"]
		mat.albedo_color = Color(c[0], c[1], c[2], c[3])
		mat.albedo_texture = _tex(m["tex"])
		mat.metallic = m["metallic"]
		mat.roughness = clampf(1.0 - float(m["smooth"]), 0.04, 1.0)
		mat.metallic_specular = 0.5
		mat.texture_repeat = m["repeat"] or m["normal"] != ""
		mat.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
		if m["normal"] != "":
			mat.normal_enabled = true
			mat.normal_texture = _tex(m["normal"])
			mat.normal_scale = m["bump"]
		if m["emissive"]:
			var e: Array = m["emission"]
			var peak: float = maxf(maxf(e[0], e[1]), maxf(e[2], 0.0001))
			mat.emission_enabled = true
			mat.emission = Color(e[0] / peak, e[1] / peak, e[2] / peak)
			mat.emission_energy_multiplier = peak
			if m["emissionTex"] != "":
				mat.emission_texture = _tex(m["emissionTex"])
				mat.emission_operator = BaseMaterial3D.EMISSION_OP_MULTIPLY
		if m["unlit"]:
			mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		if m["transparent"]:
			mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			mat.cull_mode = BaseMaterial3D.CULL_BACK
			if m["additive"]: mat.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
		if m["cullOff"]: mat.cull_mode = BaseMaterial3D.CULL_DISABLED
		var path: String = "res://baked/mat/" + m["name"] + ".tres"
		ResourceSaver.save(mat, path)
		materials.append(load(path))

func _load_mesh(rec: Dictionary) -> ArrayMesh:
	var f := FileAccess.open(SRC + "meshes/" + rec["file"] + ".bin", FileAccess.READ)
	var vc := f.get_32(); var ic := f.get_32()
	var pos := f.get_buffer(vc * 12).to_float32_array()
	var nrm := f.get_buffer(vc * 12).to_float32_array()
	var uv := f.get_buffer(vc * 8).to_float32_array()
	var idx := f.get_buffer(ic * 4).to_int32_array()
	var pv := PackedVector3Array(); pv.resize(vc)
	var nv := PackedVector3Array(); nv.resize(vc)
	var uvv := PackedVector2Array(); uvv.resize(vc)
	for i in vc:
		pv[i] = Vector3(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
		nv[i] = Vector3(nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2])
		uvv[i] = Vector2(uv[i * 2], uv[i * 2 + 1])
	var arrays := []; arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = pv; arrays[Mesh.ARRAY_NORMAL] = nv; arrays[Mesh.ARRAY_TEX_UV] = uvv; arrays[Mesh.ARRAY_INDEX] = idx
	var mesh := ArrayMesh.new()
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	var mat: StandardMaterial3D = materials[int(rec["mat"])]
	if mat.normal_enabled:
		var st := SurfaceTool.new()
		st.create_from(mesh, 0); st.generate_tangents()
		mesh = st.commit()
	mesh.surface_set_material(0, mat)
	return mesh

func _mesh_instance(rec: Dictionary) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	mi.name = String(rec["name"]).replace(".", "_")
	mi.mesh = _load_mesh(rec)
	var cast := int(rec["cast"])
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if cast == 1 else (GeometryInstance3D.SHADOW_CASTING_SETTING_SHADOWS_ONLY if cast == 2 else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF)
	mi.gi_mode = GeometryInstance3D.GI_MODE_DISABLED
	return mi

func _own(node: Node, owner: Node) -> void:
	for c in node.get_children():
		c.owner = owner
		_own(c, owner)

func _v3(a: Array) -> Vector3: return Vector3(a[0], a[1], a[2])
func _quat(a: Array) -> Quaternion: return Quaternion(a[0], a[1], a[2], a[3])

func _bake_office(data: Dictionary) -> void:
	var root := Node3D.new(); root.name = "Office"
	var groups := {}
	for rec in data["meshes"]:
		var gname: String = String(rec["group"]).replace(".", "_")
		if not groups.has(gname):
			var g := Node3D.new(); g.name = gname; root.add_child(g); groups[gname] = g
		var mi := _mesh_instance(rec)
		if rec["detail"]:
			mi.add_to_group("detail", true)
			mi.visibility_range_end = 16.0
			mi.visibility_range_end_margin = 2.0
			mi.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_DISABLED
		groups[gname].add_child(mi)

	var body := StaticBody3D.new(); body.name = "Colliders"; root.add_child(body)
	for c in data["colliders"]:
		var cs := CollisionShape3D.new()
		var box := BoxShape3D.new(); box.size = _v3(c["s"])
		cs.shape = box; cs.position = _v3(c["p"]); cs.quaternion = _quat(c["q"])
		body.add_child(cs)

	var lights := Node3D.new(); lights.name = "Lights"; root.add_child(lights)
	var main := DirectionalLight3D.new(); main.name = "Main"; lights.add_child(main)
	var i := 0
	for kind in ["fills", "windows"]:
		for l in data[kind]:
			var s := SpotLight3D.new()
			s.name = ("Fill" if kind == "fills" else "Window") + str(i); i += 1
			s.position = _v3(l["p"]); s.quaternion = _quat(l["q"])
			s.spot_range = l["range"]; s.spot_angle = minf(float(l["angle"]) * 0.5, 89.0)
			s.spot_angle_attenuation = 0.6; s.spot_attenuation = 1.0
			s.shadow_enabled = false
			s.add_to_group("fill_lights" if kind == "fills" else "window_lights", true)
			lights.add_child(s)
	i = 0
	for p in data["probes"]:
		var rp := ReflectionProbe.new(); rp.name = "Probe" + str(i); i += 1
		rp.position = _v3(p["p"]); rp.size = _v3(p["s"])
		rp.box_projection = true; rp.interior = true; rp.enable_shadows = false
		rp.update_mode = ReflectionProbe.UPDATE_ONCE
		rp.ambient_mode = ReflectionProbe.AMBIENT_DISABLED
		rp.cull_mask = 1
		rp.add_to_group("probes", true)
		root.add_child(rp)

	var seats := []; for s in data["seats"]: seats.append({"p": _v3(s["p"]), "yaw": deg_to_rad(float(s["yaw"]))})
	var screens := []; for s in data["screens"]: screens.append({"p": _v3(s["p"]), "n": _v3(s["n"])})
	var switches := []; for s in data["switches"]: switches.append({"p": _v3(s["p"]), "n": _v3(s["n"])})
	root.set_meta("seats", seats); root.set_meta("screens", screens); root.set_meta("switches", switches)
	root.set_meta("spawn_pos", _v3(data["spawn"]["p"])); root.set_meta("spawn_yaw", deg_to_rad(float(data["spawn"]["yaw"])))
	root.set_meta("room", Vector3(data["room"]["L"], data["room"]["H"], data["room"]["D"]))
	_own(root, root)
	var ps := PackedScene.new(); ps.pack(root)
	ResourceSaver.save(ps, "res://baked/office.scn", ResourceSaver.FLAG_COMPRESS)
	print("office: groups=", groups.size(), " colliders=", data["colliders"].size(), " seats=", seats.size())
	root.free()

func _joint_path(rig: Array, j: int) -> String:
	var p: String = rig[j]["name"]
	var k := int(rig[j]["parent"])
	while k >= 0:
		p = String(rig[k]["name"]) + "/" + p
		k = int(rig[k]["parent"])
	return p

func _bake_rig(data: Dictionary) -> void:
	var rig: Array = data["rig"]
	var root := Node3D.new(); root.name = "Visual"
	var nodes := []
	for j in rig.size():
		var n := Node3D.new(); n.name = rig[j]["name"]; n.position = _v3(rig[j]["p"])
		var parent: Node = root if int(rig[j]["parent"]) < 0 else nodes[int(rig[j]["parent"])]
		parent.add_child(n); nodes.append(n)
		for rec in rig[j]["meshes"]:
			var mi := _mesh_instance(rec)
			mi.name = "Geo_" + mi.name
			mi.layers = 2            # 第 2 渲染层：反射探针不拍人物
			n.add_child(mi)
	_own(root, root)
	var ps := PackedScene.new(); ps.pack(root)
	ResourceSaver.save(ps, "res://baked/player_rig.scn", ResourceSaver.FLAG_COMPRESS)
	root.free()

func _bake_anims(anims: Dictionary, rig: Array) -> void:
	var lib := AnimationLibrary.new()
	for clip_name in anims:
		var a: Dictionary = anims[clip_name]
		var anim := Animation.new()
		anim.length = a["length"]
		anim.loop_mode = Animation.LOOP_LINEAR if a["loop"] else Animation.LOOP_NONE
		var frames: Array = a["frames"]
		var pt := anim.add_track(Animation.TYPE_POSITION_3D)
		anim.track_set_path(pt, NodePath("Hips"))
		var rt := []
		for j in rig.size():
			var t := anim.add_track(Animation.TYPE_ROTATION_3D)
			anim.track_set_path(t, NodePath(_joint_path(rig, j)))
			rt.append(t)
		for f in frames.size():
			var time: float = float(a["length"]) * f / (frames.size() - 1)
			anim.position_track_insert_key(pt, time, _v3(frames[f]["h"]))
			for j in rig.size():
				anim.rotation_track_insert_key(rt[j], time, _quat(frames[f]["r"][j]).normalized())
		lib.add_animation(clip_name, anim)
	ResourceSaver.save(lib, "res://baked/anims.res", ResourceSaver.FLAG_COMPRESS)

# ---------------- 音效合成（不含任何录音素材） ----------------
const RATE := 44100
var rng := RandomNumberGenerator.new()

func _wav(samples: PackedFloat32Array, name: String, loop := false) -> void:
	var bytes := PackedByteArray(); bytes.resize(samples.size() * 2)
	for i in samples.size():
		bytes.encode_s16(i * 2, int(clampf(samples[i], -1.0, 1.0) * 32767.0))
	var w := AudioStreamWAV.new()
	w.format = AudioStreamWAV.FORMAT_16_BITS; w.mix_rate = RATE; w.stereo = false; w.data = bytes
	if loop:
		w.loop_mode = AudioStreamWAV.LOOP_FORWARD; w.loop_begin = 0; w.loop_end = samples.size()
	ResourceSaver.save(w, "res://baked/sfx_" + name + ".res", ResourceSaver.FLAG_COMPRESS)

func _fade(d: PackedFloat32Array) -> void:
	var f: int = mini(200, d.size() / 4)
	for i in f: d[d.size() - 1 - i] *= float(i) / f

func _thud(length: float, cutoff: float, gain: float, decay: float, body: float) -> PackedFloat32Array:
	var n := int(length * RATE); var d := PackedFloat32Array(); d.resize(n)
	var lp := 0.0; var a := 1.0 - exp(-TAU * cutoff / RATE)
	for i in n:
		var t := float(i) / RATE
		var env: float = minf(1.0, t * 400.0) * exp(-decay * t)
		lp += (rng.randf_range(-1.0, 1.0) - lp) * a
		d[i] = (lp * 2.2 + sin(TAU * (95.0 - 60.0 * t) * t) * body) * env * gain
	_fade(d); return d

func _bake_audio() -> void:
	rng.seed = 7
	for i in 4: _wav(_thud(0.13, 620.0 + i * 70.0, 0.55, 30.0, 0.25), "step" + str(i))
	_wav(_thud(0.24, 300.0, 0.9, 16.0, 0.5), "land")
	_wav(_thud(0.10, 900.0, 0.35, 38.0, 0.1), "jump")
	var n := int(0.09 * RATE); var d := PackedFloat32Array(); d.resize(n)
	for i in n:
		var t := float(i) / RATE
		var a := exp(-900.0 * t) * sin(TAU * 2600.0 * t)
		var t2 := t - 0.045
		var b: float = exp(-700.0 * t2) * sin(TAU * 1900.0 * t2) * 0.8 if t2 > 0.0 else 0.0
		d[i] = (a + b + rng.randf_range(-0.5, 0.5) * 0.25 * exp(-500.0 * t)) * 0.6
	_fade(d); _wav(d, "switch")
	n = int(0.06 * RATE); d = PackedFloat32Array(); d.resize(n)
	for i in n:
		var t := float(i) / RATE
		d[i] = sin(TAU * (820.0 + 500.0 * t) * t) * exp(-60.0 * t) * minf(1.0, t * 600.0) * 0.35
	_fade(d); _wav(d, "click")
	# 6 秒无缝循环的空调 / 办公室底噪
	n = 6 * RATE; var xf := RATE / 2
	var raw := PackedFloat32Array(); raw.resize(n + xf)
	var lp1 := 0.0; var lp2 := 0.0; var slow := 0.0
	for i in raw.size():
		var t := float(i) / RATE
		var noise := rng.randf_range(-1.0, 1.0)
		lp1 += (noise - lp1) * 0.035; lp2 += (noise - lp2) * 0.004
		slow += (rng.randf_range(-0.5, 0.5) - slow) * 0.0002
		raw[i] = lp1 * (1.1 + slow * 30.0) + lp2 * 3.0 + sin(TAU * 100.0 * t) * 0.012 + sin(TAU * 50.0 * t) * 0.01
	d = PackedFloat32Array(); d.resize(n)
	for i in n:
		if i < xf:
			var k := float(i) / xf
			d[i] = (raw[i] * k + raw[n + i] * (1.0 - k)) * 0.55
		else: d[i] = raw[i] * 0.55
	_wav(d, "ambient", true)

func _bake_icon() -> void:
	var img := Image.create(192, 192, false, Image.FORMAT_RGBA8)
	img.fill(Color(0.16, 0.42, 0.86))
	img.fill_rect(Rect2i(30, 96, 132, 14), Color(0.86, 0.72, 0.50))       # 桌面
	img.fill_rect(Rect2i(40, 110, 8, 52), Color(0.08, 0.08, 0.09)); img.fill_rect(Rect2i(144, 110, 8, 52), Color(0.08, 0.08, 0.09))
	img.fill_rect(Rect2i(62, 40, 68, 44), Color(0.06, 0.06, 0.07)); img.fill_rect(Rect2i(67, 45, 58, 34), Color(0.75, 0.90, 1.0))
	img.fill_rect(Rect2i(92, 84, 8, 12), Color(0.06, 0.06, 0.07))
	img.save_png("res://icon.png")
