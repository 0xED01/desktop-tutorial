// Generates docs/LEVELS.md (level + trap catalogue) from the level data.
import fs from 'node:fs';
import { LEVELS } from '../TrustNothing/assets/scripts/levels/index';
import { PRESETS, REQUIRED_TRAPS, ORIGINAL_TRAPS } from '../TrustNothing/assets/scripts/core/presets';
import { CHAPTERS } from '../TrustNothing/assets/scripts/core/services';
const sols = JSON.parse(fs.readFileSync('tests/solutions.json', 'utf8'));
let md = '# 别信这关 · 关卡与机关目录（自动生成）\n\n';
md += '## 机关库\n\n### 25 种必备机关\n\n| # | 预设名 | 中文 | 说明 | 使用关卡 |\n|---|---|---|---|---|\n';
const usedIn = (t: string) => LEVELS.filter(l => Object.values(l.defs || {}).some((d: any) => d.t === t)).map(l => l.id);
REQUIRED_TRAPS.forEach((t, i) => { const p = PRESETS[t]; md += `| ${i + 1} | \`${t}\` | ${p.cn} | ${p.desc} | ${usedIn(t).join(', ') || '（通过触发器/动作组合使用）'} |\n`; });
md += '\n### 原创机关\n\n| # | 预设名 | 中文 | 说明 | 使用关卡 |\n|---|---|---|---|---|\n';
ORIGINAL_TRAPS.forEach((t, i) => { const p = PRESETS[t]; md += `| ${i + 1} | \`${t}\` | ${p.cn} | ${p.desc} | ${usedIn(t).join(', ')} |\n`; });
md += '\n## 80 关一览（括号内为求解器找到的参考通关用时）\n';
CHAPTERS.forEach((c, ci) => {
  md += `\n### 第 ${ci + 1} 章：${c.name}\n\n| 关 | 名称 | 机关 | 通关思路（提示） | 参考用时 |\n|---|---|---|---|---|\n`;
  for (const l of LEVELS.slice(ci * 10, ci * 10 + 10)) {
    const traps = [...new Set(Object.values(l.defs || {}).map((d: any) => PRESETS[d.t]?.cn).filter(Boolean))].join('、');
    md += `| ${l.id} | ${l.name} | ${traps} | ${l.hint} | ${sols[l.id] ? (sols[l.id].length * 0.1).toFixed(1) + 's' : '-'} |\n`;
  }
});
fs.writeFileSync('docs/LEVELS.md', md);
console.log('docs/LEVELS.md written');
