class_name CircleShape
extends Control
## 抗锯齿圆形（摇杆与圆形按钮）
var fill := Color(0, 0, 0, 0.5)
var ring := Color(0, 0, 0, 0)
var ring_width := 0.0

func _draw() -> void:
	var c := size * 0.5
	var r := minf(size.x, size.y) * 0.5
	draw_circle(c, r - ring_width, fill, true, -1.0, true)
	if ring_width > 0.0:
		draw_arc(c, r - ring_width * 0.5, 0.0, TAU, 72, ring, ring_width, true)
