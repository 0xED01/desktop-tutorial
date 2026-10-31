"""批量合成配音：python tts_batch.py <模型.onnx> <任务.json>，任务为 [{"text":..., "out":...}]"""
import json
import sys
import wave

from piper import PiperVoice
from piper.config import SynthesisConfig

voice = PiperVoice.load(sys.argv[1])
cfg = SynthesisConfig(length_scale=0.84, noise_scale=0.6, noise_w_scale=0.7)
for job in json.load(open(sys.argv[2], encoding="utf-8")):
    with wave.open(job["out"], "wb") as wf:
        voice.synthesize_wav(job["text"], wf, syn_config=cfg)
