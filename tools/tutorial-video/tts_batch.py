"""批量合成配音（Kokoro v1.1 中文，Apache-2.0，经 sherpa-onnx 离线运行）。
用法：python tts_batch.py <kokoro 模型目录> <任务.json>，任务为 [{"text":..., "out":...}]
"""
import json
import re
import sys
from pathlib import Path

import sherpa_onnx
import numpy as np
import soundfile as sf

M = Path(sys.argv[1])
SPEAKER = 3     # zf_001 女声
SPEED = 1.18

cfg = sherpa_onnx.OfflineTtsConfig(
    model=sherpa_onnx.OfflineTtsModelConfig(
        kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
            model=str(M / "model.onnx"),
            voices=str(M / "voices.bin"),
            tokens=str(M / "tokens.txt"),
            data_dir=str(M / "espeak-ng-data"),
            dict_dir=str(M / "dict"),
            lexicon=f"{M / 'lexicon-us-en.txt'},{M / 'lexicon-zh.txt'}",
        ),
        num_threads=4,
    ),
    rule_fsts=f"{M / 'phone-zh.fst'},{M / 'date-zh.fst'},{M / 'number-zh.fst'}",
    max_num_sentences=1,
)
tts = sherpa_onnx.OfflineTts(cfg)

# 按标点切分，逐段合成后插入固定停顿，保证断句准确
PAUSE = {"、": 0.12, "，": 0.22, "：": 0.22, "；": 0.3, "。": 0.4, "！": 0.4, "？": 0.4}


def trim(x, sr, thr=0.008, keep=0.03):
    idx = np.flatnonzero(np.abs(x) > thr)
    if len(idx) == 0:
        return x[:0]
    k = int(keep * sr)
    return x[max(0, idx[0] - k): idx[-1] + k]


for job in json.load(open(sys.argv[2], encoding="utf-8")):
    parts, sr = [], 24000
    clauses = re.findall(r"[^，。、！？；：]+[，。、！？；：]?", job["text"])
    for i, c in enumerate(clauses):
        body, mark = c.rstrip("，。、！？；：").strip(), c[-1]
        if not body:
            continue
        audio = tts.generate(body, sid=SPEAKER, speed=SPEED)
        sr = audio.sample_rate
        parts.append(trim(np.asarray(audio.samples, dtype=np.float32), sr))
        if i < len(clauses) - 1:
            parts.append(np.zeros(int(PAUSE.get(mark, 0.15) * sr), np.float32))
    sf.write(job["out"], np.concatenate(parts), sr, subtype="PCM_16")
