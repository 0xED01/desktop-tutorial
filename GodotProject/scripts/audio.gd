class_name GameAudio
extends Node
## 音效播放：脚步、起跳 / 落地、灯光开关、按键音、环境底噪（素材全部为烘焙阶段代码合成）

static var inst: GameAudio
var steps: Array[AudioStream] = []
var streams := {}
var players: Array[AudioStreamPlayer] = []
var next := 0
var ambient: AudioStreamPlayer

func _ready() -> void:
	inst = self
	process_mode = Node.PROCESS_MODE_ALWAYS
	for i in 4: steps.append(load("res://baked/sfx_step%d.res" % i))
	for n in ["land", "jump", "switch", "click"]: streams[n] = load("res://baked/sfx_%s.res" % n)
	for i in 6:
		var p := AudioStreamPlayer.new(); add_child(p); players.append(p)
	ambient = AudioStreamPlayer.new()
	ambient.stream = load("res://baked/sfx_ambient.res")
	ambient.volume_db = linear_to_db(0.22)
	add_child(ambient)

static func play(name: String, volume := 1.0) -> void:
	if inst == null: return
	var p := inst.players[inst.next]
	inst.next = (inst.next + 1) % inst.players.size()
	if name == "step":
		p.stream = inst.steps[randi() % inst.steps.size()]
		p.pitch_scale = randf_range(0.92, 1.08)
	else:
		p.stream = inst.streams[name]
		p.pitch_scale = 1.0
	p.volume_db = linear_to_db(clampf(volume, 0.001, 1.0))
	p.play()

static func set_ambient(volume: float) -> void:
	if inst == null: return
	inst.ambient.volume_db = linear_to_db(maxf(volume, 0.001))
	if not inst.ambient.playing: inst.ambient.play()
