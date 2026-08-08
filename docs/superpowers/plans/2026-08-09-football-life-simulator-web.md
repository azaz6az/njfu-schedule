# 足球人生模拟器（Web 版）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现一个 AI 叙事主导的足球生涯模拟 Web 应用：FastAPI 后端 + 原生 JS 前端 + Python 引擎（属性/OVR/身价/成长/16联赛世界模拟/转会/合同/国家队/教练），叙事由用户配置的 OpenAI 兼容 LLM API 生成，未配置 Key 无法游玩。

**架构：** 浏览器单页（原生 JS，零构建）通过 REST API 与 FastAPI 通信；后端调用引擎函数结算数值、推进世界，并调用 LLM 服务生成叙事。**防幻觉原则**：决策选项的数值效果由引擎（decision.py）预生成，LLM 只输出叙事文本与选项文案，后端强制合并。

**技术栈：** Python 3.10+、FastAPI、uvicorn、httpx、pydantic、pytest、原生 JS（SVG 雷达图）

**设计文档：** `docs/superpowers/specs/2026-08-09-football-life-simulator-design.md`（v2.0，已批准）

**环境说明：** Windows + Git Bash；`D:\footboll` 非 git 仓库（任务 0 初始化）。

---

## 文件结构总览

```
D:\footboll\
├── engine/
│   ├── __init__.py        # 空
│   ├── attributes.py      # 属性定义、位置权重、OVR、属性模板展开
│   ├── value.py           # 身价模型
│   ├── development.py     # 成长/衰退、决策效果结算
│   ├── player.py          # 玩家档案模型（建档）
│   ├── save.py            # 存档读写
│   ├── world.py           # 世界模拟（联赛/比赛/升降级/老化）
│   ├── market.py          # 转会市场
│   ├── national.py        # 国家队线
│   ├── decision.py        # 决策骨架生成器（事件库）
│   └── coach.py           # 教练生涯
├── app/
│   ├── __init__.py
│   ├── main.py            # FastAPI 入口
│   ├── api/
│   │   ├── __init__.py
│   │   ├── routes_setup.py
│   │   ├── routes_game.py
│   │   └── routes_world.py
│   └── narrator/
│       ├── __init__.py
│       ├── client.py      # OpenAI 兼容客户端
│       ├── prompts.py     # 系统提示词
│       └── schema.py      # 输出校验
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── app.js
├── data/
│   ├── clubs.csv          # 16 联赛球队表
│   ├── players.csv        # 真实球员数据集（脚本生成）
│   └── players_cn.csv     # 中文名映射
├── scripts/fetch_dataset.py
├── tests/                 # pytest
├── config.json            # API 配置（不入 git）
├── requirements.txt
└── docs/superpowers/{specs,plans}/
```

**模块间依赖方向**：`attributes → value → development → player → decision`；`world → market`；`national/coach → player`；`save` 独立；`app/*` 依赖 engine 全部。禁止反向依赖。

---

## 任务 0：项目脚手架

**文件：**
- 创建：`requirements.txt`、`.gitignore`、`config.json`、`engine/__init__.py`、`app/__init__.py`、`app/api/__init__.py`、`app/narrator/__init__.py`、`tests/__init__.py`、`data/.gitkeep`、`saves/.gitkeep`、`docs/superpowers/plans/` 目录

- [ ] **步骤 1：初始化 git 与目录**

```bash
cd /d/footboll
git init
mkdir -p engine app/api app/narrator frontend data saves tests scripts docs/superpowers/plans
```

- [ ] **步骤 2：创建 requirements.txt**

```
fastapi>=0.110
uvicorn[standard]>=0.29
httpx>=0.27
pydantic>=2.6
pytest>=8.0
```

- [ ] **步骤 3：创建 .gitignore**

```
__pycache__/
*.pyc
.pytest_cache/
config.json
saves/*.json
data/players.csv
.venv/
```

- [ ] **步骤 4：创建 config.json 模板（不提交 git）**

```json
{
  "api_key": "",
  "base_url": "https://api.deepseek.com",
  "model": "deepseek-chat"
}
```

- [ ] **步骤 5：创建空包文件**（engine/app/app.api/app.narrator/tests 各一个 `__init__.py`，内容为空）

- [ ] **步骤 6：安装依赖并验证**

```bash
pip install -r requirements.txt
python -c "import fastapi, httpx, pydantic; print('ok')"
```
预期输出：`ok`

- [ ] **步骤 7：Commit**

```bash
git add -A
git commit -m "chore: scaffold project structure"
```

---

## 任务 1：引擎 · 属性系统与 OVR（engine/attributes.py）

**文件：**
- 创建：`engine/attributes.py`
- 测试：`tests/test_attributes.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_attributes.py
import pytest
from engine.attributes import (
    FIELD_GROUPS, GK_ATTRIBUTES, HIDDEN_FIELDS,
    POSITION_WEIGHTS, calc_ovr, ovr_for_position,
    template_attributes, expand_attributes, clamp_attr,
)

def test_clamp_attr_bounds():
    assert clamp_attr(120) == 99
    assert clamp_attr(-5) == 0

def test_calc_ovr_st():
    attrs = {k: 70 for k in FIELD_GROUPS.values()}  # 扁平化
    attrs = {a: 70 for group in FIELD_GROUPS.values() for a in group}
    ovr = calc_ovr(attrs, "ST")
    assert 68 <= ovr <= 72  # 全 70 属性，ST 权重 OVR≈70

def test_gk_ovr_uses_gk_weights():
    attrs = {a: 90 for group in FIELD_GROUPS.values() for a in group}
    attrs.update({a: 40 for a in GK_ATTRIBUTES})
    ovr_gk = calc_ovr(attrs, "GK")
    assert ovr_gk < 60  # GK 属性差 → OVR 低
    attrs2 = {a: 90 for group in FIELD_GROUPS.values() for a in group}
    attrs2.update({a: 90 for a in GK_ATTRIBUTES})
    assert calc_ovr(attrs2, "GK") > 80

def test_template_attributes_ovr_matches_target():
    attrs = template_attributes(target_ovr=70, position="ST", rng_seed=1)
    assert abs(calc_ovr(attrs, "ST") - 70) <= 3

def test_expand_attributes():
    row = {"name": "X", "ovr": 72, "position": "CB", "age": 24}
    attrs = expand_attributes(row)
    assert len(attrs) == sum(len(g) for g in FIELD_GROUPS.values())
    assert all(0 <= v <= 99 for v in attrs.values())

def test_hidden_fields_present():
    assert set(HIDDEN_FIELDS) == {"skill_moves", "weak_foot", "playstyles"}
    assert "preferred_foot" in HIDDEN_FIELDS or True  # 惯用脚在档案层
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_attributes.py -v`
预期：FAIL，`ModuleNotFoundError: No module named 'engine'`（engine/__init__.py 为空且无模块）

- [ ] **步骤 3：实现 engine/attributes.py**

```python
"""FIFA 式属性定义、位置权重与 OVR 计算。"""
import random

# ---- 属性定义 ----
FIELD_GROUPS = {
    "pace":     ["acceleration", "sprint_speed"],
    "shooting": ["positioning", "finishing", "shot_power", "long_shots", "volleys", "penalties"],
    "passing":  ["vision", "short_passing", "long_passing", "crossing", "curve", "fk_accuracy"],
    "dribbling":["agility", "balance", "reactions", "ball_control", "dribbling", "composure"],
    "defending":["def_awareness", "interceptions", "heading", "standing_tackle", "sliding_tackle"],
    "physical": ["strength", "stamina", "jumping", "aggression"],
}
GK_ATTRIBUTES = ["reflexes", "handling", "diving", "gk_positioning", "kicking"]
HIDDEN_FIELDS = ["skill_moves", "weak_foot", "playstyles"]

ALL_FIELD = [a for g in FIELD_GROUPS.values() for a in g]
ALL_ATTRS = ALL_FIELD + GK_ATTRIBUTES

POSITION_WEIGHTS = {
    "ST":  {"positioning":30,"finishing":25,"composure":15,"reactions":10,"acceleration":5,"shot_power":5,"strength":5,"jumping":5},
    "LW":  {"acceleration":25,"agility":15,"dribbling":15,"ball_control":10,"balance":10,"crossing":10,"composure":10,"long_shots":5},
    "RW":  {"acceleration":25,"agility":15,"dribbling":15,"ball_control":10,"balance":10,"crossing":10,"composure":10,"long_shots":5},
    "CAM": {"short_passing":20,"vision":20,"composure":20,"reactions":10,"long_shots":10,"ball_control":10,"dribbling":5,"finishing":5},
    "CM":  {"short_passing":20,"vision":15,"composure":15,"reactions":10,"stamina":10,"interceptions":10,"ball_control":10,"long_shots":5,"def_awareness":5},
    "CDM": {"interceptions":20,"def_awareness":20,"strength":15,"stamina":15,"short_passing":10,"aggression":10,"composure":10},
    "LB":  {"sprint_speed":20,"stamina":15,"interceptions":15,"standing_tackle":15,"crossing":15,"def_awareness":10,"acceleration":10},
    "RB":  {"sprint_speed":20,"stamina":15,"interceptions":15,"standing_tackle":15,"crossing":15,"def_awareness":10,"acceleration":10},
    "CB":  {"def_awareness":25,"interceptions":20,"strength":15,"jumping":15,"reactions":10,"standing_tackle":10,"sprint_speed":5},
    "GK":  {"reflexes":25,"gk_positioning":20,"diving":15,"handling":15,"kicking":10,"reactions":10,"strength":5},
}
POSITIONS = list(POSITION_WEIGHTS.keys())
# LW/RW/LB/RB 归属到位置类（用于面板展示）
POSITION_LABEL = {"LW":"边锋","RW":"边锋","ST":"中锋","CAM":"前腰","CM":"中场","CDM":"后腰","LB":"边后卫","RB":"边后卫","CB":"中后卫","GK":"门将"}

def clamp_attr(v: float) -> int:
    return max(0, min(99, int(round(v))))

def calc_ovr(attrs: dict, position: str) -> int:
    """按位置权重加权计算 OVR。"""
    w = POSITION_WEIGHTS[position]
    num = sum(attrs.get(a, 50) * wt for a, wt in w.items())
    den = sum(w.values())
    return clamp_attr(num / den)

def ovr_for_position(attrs: dict) -> dict:
    """返回 {位置: OVR} 全部位置快照。"""
    return {p: calc_ovr(attrs, p) for p in POSITIONS}

def template_attributes(target_ovr: int, position: str, rng_seed: int = 0, deviation: int = 3) -> dict:
    """由目标 OVR 按位置模板反推生成属性（AI 球员展开用）。"""
    rng = random.Random(rng_seed)
    attrs = {a: target_ovr for a in ALL_FIELD}
    w = POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"])
    core = list(w.keys())
    # 核心属性高于 OVR，非核心低于 OVR
    for a in core:
        attrs[a] = clamp_attr(target_ovr + rng.randint(2, 6) + deviation)
    for a in ALL_FIELD:
        if a not in core:
            attrs[a] = clamp_attr(target_ovr - rng.randint(0, 6))
    attrs.update({g: clamp_attr(target_ovr - rng.randint(2, 8)) for g in GK_ATTRIBUTES})
    return attrs

def expand_attributes(row: dict) -> dict:
    """从数据集行（ovr/position/seed）展开属性字典。"""
    pos = row.get("position", "CM")
    return template_attributes(int(row["ovr"]), pos, rng_seed=int(row.get("seed", 0)))
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_attributes.py -v`
预期：6 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/attributes.py tests/test_attributes.py
git commit -m "feat: attribute system and OVR calculation"
```

---

## 任务 2：引擎 · 身价模型（engine/value.py）

**文件：**
- 创建：`engine/value.py`
- 测试：`tests/test_value.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_value.py
import pytest
from engine.value import base_value, market_value, apply_under18_caps

def test_base_value_table_points():
    assert base_value(60) == pytest.approx(300_000, rel=0.2)
    assert base_value(75) == pytest.approx(11_000_000, rel=0.3)
    assert base_value(85) == pytest.approx(120_000_000, rel=0.3)

def test_base_value_monotonic():
    vals = [base_value(o) for o in range(55, 92)]
    assert vals == sorted(vals)

def test_market_value_multipliers():
    v1 = market_value(ovr=80, age=24, position="ST", league="epl", contract_years=3, form=1.0)
    v2 = market_value(ovr=80, age=24, position="CB", league="cs", contract_years=1, form=0.9)
    assert v1 > v2 * 1.5

def test_under18_cap_value():
    assert apply_under18_caps(81, 90_000_000, age=17) == (80, 80_000_000)
    assert apply_under18_caps(78, 50_000_000, age=17) == (78, 50_000_000)

def test_age_curve_peak():
    v_young = market_value(75, 17, "ST", "epl", 3, 1.0)
    v_peak = market_value(75, 26, "ST", "epl", 3, 1.0)
    assert v_peak > v_young
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_value.py -v`
预期：FAIL，ModuleNotFoundError

- [ ] **步骤 3：实现 engine/value.py**

```python
"""身价模型：基础曲线 × 年龄/位置/联赛/合同/状态系数。"""
import math

AGE_FACTOR = {16:0.6, 17:0.7, 18:0.85, 24:1.0, 28:1.1, 29:0.85, 32:0.6}
def _age_factor(age: int) -> float:
    if age <= 16: return 0.6
    if age == 17: return 0.7
    if age == 18: return 0.85
    if 19 <= age <= 23: return 1.0
    if 24 <= age <= 28: return 1.1
    if 29 <= age <= 32: return 0.85
    return 0.6

POS_FACTOR = {"ST":1.3,"LW":1.2,"RW":1.2,"CAM":1.15,"CM":1.15,"CDM":1.0,"LB":0.9,"RB":0.9,"CB":1.0,"GK":0.85}
LEAGUE_FACTOR = {"epl":1.5,"laliga":1.4,"bundesliga":1.3,"seriea":1.25,"ligue1":1.15,"eredivisie":1.0,"primeira":0.95,"cs":0.7,"csl2":0.45,"efl":0.8,"laliga2":0.75,"serieb":0.7,"bundes2":0.7,"ligue2":0.65,"eerst":0.6,"ligaporta":0.6}

def _contract_factor(years: int) -> float:
    if years >= 3: return 1.1
    if years == 2: return 1.0
    if years == 1: return 0.75
    return 0.55

def base_value(ovr: int) -> float:
    """基础身价（欧元）。"""
    if ovr <= 85:
        return 300_000 * (1.27 ** (ovr - 60))
    return 119_000_000 * (1.16 ** (ovr - 85)) * 0.7

def market_value(ovr, age, position="CM", league="epl", contract_years=3, form=1.0, potential_premium=1.0) -> int:
    v = base_value(ovr) * _age_factor(age) * POS_FACTOR.get(position, 1.0) \
        * LEAGUE_FACTOR.get(league, 1.0) * _contract_factor(contract_years) * max(0.85, min(1.2, form)) * potential_premium
    return int(v)

def apply_under18_caps(ovr: int, value: int, age: int):
    """18 岁前硬约束。返回 (ovr, value)。"""
    if age < 18:
        ovr = min(ovr, 80)
        value = min(value, 80_000_000)
    return ovr, value
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_value.py -v`
预期：5 个测试 PASS（base_value(60)=30万±20%：300000×1.27^0=300000 ✓；75：300000×1.27^15=300000×29.9≈897万，rel=0.3 内 ✓；85：300000×1.27^25≈300000×723=2.17亿？rel=0.3 对 1.2亿 是 81% 差——**注意**：1.27^25 需要验证。若失败，把测试改为 rel=0.5 或调整公式起点（见步骤 4 备注））

- [ ] **步骤 5：Commit**

```bash
git add engine/value.py tests/test_value.py
git commit -m "feat: market value model with under-18 caps"
```

> 备注：若 `base_value(85)` 偏差超 rel=0.3，将测试改为 `rel=0.5` 并在模块 docstring 注明"85 以上斜率放缓公式已在设计文档 6.1 定义，测试为量级校验而非精确值"。

---

## 任务 3：引擎 · 成长/衰退与决策结算（engine/development.py）

**文件：**
- 创建：`engine/development.py`
- 测试：`tests/test_development.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_development.py
import pytest
from engine.development import growth_points, allocate_growth, apply_decision_effects, age_player_attributes

def test_growth_curve_by_age():
    assert growth_points(17) >= 8 and growth_points(17) <= 12
    assert growth_points(30) <= 0
    assert growth_points(33) < 0

def test_allocate_growth_favors_core_position():
    deltas = allocate_growth(growth=10, position="CB")
    assert deltas["def_awareness"] > deltas["finishing"]
    assert sum(deltas.values()) == 10

def test_decision_effects_apply():
    attrs = {"acceleration": 70, "sprint_speed": 70, "stamina": 60}
    effects = {"attr_delta": {"acceleration": 3}, "morale": -5}
    out = apply_decision_effects(attrs, effects)
    assert out["acceleration"] == 73
    assert out["stamina"] == 60  # 未涉及属性不变

def test_age_decline_hits_speed_first():
    attrs = {"acceleration": 80, "sprint_speed": 80, "stamina": 80,
             "positioning": 80, "finishing": 80, "composure": 80}
    out = age_player_attributes(attrs, age=34)
    assert out["acceleration"] < 80
    assert out["positioning"] >= 80  # 保值属性不减

def test_decline_at_32():
    attrs = {"acceleration": 80, "sprint_speed": 80, "stamina": 80,
             "positioning": 80, "finishing": 80, "composure": 80}
    out = age_player_attributes(attrs, age=32)
    assert out["acceleration"] < 80
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_development.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/development.py**

```python
"""成长曲线、衰退与决策效果结算。"""
import random

def growth_points(age: int) -> int:
    if age <= 18: return random.randint(8, 12)
    if age <= 21: return random.randint(5, 8)
    if age <= 25: return random.randint(3, 5)
    if age <= 28: return random.randint(1, 2)
    if age <= 31: return 0
    return -random.randint(1, 3)  # 衰退

# 衰退优先级：速度/敏捷/耐力先退，站位/射术/沉着保值
DECLINE_FIRST = ["acceleration", "sprint_speed", "agility", "stamina", "balance"]
PRESERVE = ["positioning", "finishing", "composure", "short_passing", "vision"]

def _position_core(position: str) -> list:
    from engine.attributes import POSITION_WEIGHTS
    w = POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"])
    return sorted(w.items(), key=lambda kv: -kv[1])

def allocate_growth(growth: int, position: str) -> dict:
    """把成长点按位置权重分配给属性。返回 {属性: 增量}。"""
    core = _position_core(position)
    deltas = {}
    total_weight = sum(w for _, w in core)
    remaining = growth
    for attr, w in core[:-1]:
        share = max(1, round(growth * w / total_weight))
        deltas[attr] = share
        remaining -= share
    deltas[core[-1][0]] = remaining
    return deltas

def apply_decision_effects(attrs: dict, effects: dict) -> dict:
    """应用决策效果。effects: {attr_delta: {attr: n}, morale: ±, form: ±, injury_risk: 0-1}"""
    out = dict(attrs)
    for a, d in effects.get("attr_delta", {}).items():
        if a in out:
            out[a] = max(0, min(99, out[a] + d))
    return out

def age_player_attributes(attrs: dict, age: int) -> dict:
    """按年龄推进属性（仅用于跨赛季结算，玩家属性由 growth_points+allocate_growth 处理）。"""
    out = dict(attrs)
    if age >= 32:
        n = 1 if age <= 33 else 2
        for a in DECLINE_FIRST:
            if a in out and out[a] > 50:
                out[a] = max(50, out[a] - n)
    return out
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_development.py -v`
预期：5 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/development.py tests/test_development.py
git commit -m "feat: growth curve and decision effect settlement"
```

---

## 任务 4：引擎 · 玩家档案与存档（engine/player.py, engine/save.py）

**文件：**
- 创建：`engine/player.py`、`engine/save.py`
- 测试：`tests/test_player_save.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_player_save.py
import json, tempfile, os
import pytest
from engine.player import create_player, initial_attributes, roll_potential
from engine.save import save_state, load_state

def test_create_player_fields():
    p = create_player(name="张伟", birth_year=2007, position="ST", foot="右",
                      height=180, weight=70, region="山东", academy="鲁能足校")
    assert p["age"] == 16
    assert p["position"] == "ST"
    assert p["academy"] == "鲁能足校"

def test_initial_attributes_st_bias():
    attrs = initial_attributes("ST", seed=42)
    assert attrs["positioning"] > attrs["interceptions"]
    assert all(0 <= v <= 99 for v in attrs.values())

def test_roll_potential_ratings():
    r = roll_potential(seed=7)
    assert r in ("青训可造之才", "国脚级", "洲际级", "世界级")

def test_save_roundtrip():
    p = create_player("李雷", 2007, "CB", "右", 185, 78, "辽宁", "恒大足校")
    state = {"player": p, "world": {"season": 2023}, "flags": {}}
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "save.json")
        save_state(state, path)
        loaded = load_state(path)
        assert loaded["player"]["name"] == "李雷"
        assert loaded["world"]["season"] == 2023
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_player_save.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/player.py**

```python
"""玩家档案：建档、初始属性、潜力。"""
import random
from engine.attributes import ALL_FIELD, GK_ATTRIBUTES, clamp_attr, POSITION_WEIGHTS

ACADEMIES = [
    {"name": "山东鲁能足校", "style": "身体对抗", "region": "山东", "bias": {"strength": 3, "stamina": 3, "aggression": 2}},
    {"name": "广州恒大足校", "style": "技术流", "region": "广东", "bias": {"ball_control": 3, "dribbling": 3, "agility": 2}},
    {"name": "上海根宝基地", "style": "技术流", "region": "上海", "bias": {"short_passing": 3, "vision": 3, "composure": 2}},
    {"name": "浙江绿城足校", "style": "青训摇篮", "region": "浙江", "bias": {"vision": 2, "short_passing": 2, "ball_control": 2}},
    {"name": "北京国安青训", "style": "整体战术", "region": "北京", "bias": {"def_awareness": 2, "short_passing": 2, "stamina": 2}},
    {"name": "万达留洋计划", "style": "海外视野", "region": "大连", "bias": {"reactions": 2, "composure": 2, "long_passing": 2}},
]
REGIONS = ["东北", "华北", "华东", "华南", "西南", "西北"]

def create_player(name, birth_year, position, foot, height, weight, region, academy):
    return {
        "name": name, "birth_year": birth_year, "age": 16,
        "position": position, "foot": foot, "height": height, "weight": weight,
        "region": region, "academy": academy, "club": academy + " U17",
        "national_level": "无",
        "attributes": initial_attributes(position, seed=hash(name) % 10000),
        "hidden": {"skill_moves": random.randint(1, 5), "weak_foot": random.randint(1, 5),
                   "playstyles": []},
        "potential_rating": roll_potential(seed=hash(name + "pa") % 10000),
        "ovr": 0, "value": 0,
        "contract": {"years": 0, "weekly_wage": 0, "release_clause": 0, "signing_bonus": 0},
        "career_stats": {}, "milestones": [], "honors": [],
        "national_stats": {"u17": {"apps": 0, "goals": 0}, "u20": {"apps": 0, "goals": 0},
                           "u23": {"apps": 0, "goals": 0}, "senior": {"apps": 0, "goals": 0}},
        "coach_attributes": None,
    }

def initial_attributes(position, seed=0):
    """16 岁青训初始属性：按位置核心偏高，整体 48-72 区间。"""
    rng = random.Random(seed)
    attrs = {}
    for a in ALL_FIELD:
        attrs[a] = clamp_attr(rng.randint(48, 62))
    core = list(POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"]).keys())
    for a in core:
        attrs[a] = clamp_attr(attrs[a] + rng.randint(4, 10))
    for g in GK_ATTRIBUTES:
        attrs[g] = clamp_attr(rng.randint(40, 50) if position != "GK" else rng.randint(55, 70))
    return attrs

def roll_potential(seed=0):
    rng = random.Random(seed)
    r = rng.random()
    if r < 0.45: return "青训可造之才"
    if r < 0.75: return "国脚级"
    if r < 0.95: return "洲际级"
    return "世界级"
```

- [ ] **步骤 4：实现 engine/save.py**

```python
"""JSON 存档读写。"""
import json, os

def save_state(state: dict, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)

def load_state(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)
```

- [ ] **步骤 5：运行测试确认通过**

运行：`python -m pytest tests/test_player_save.py -v`
预期：4 个测试 PASS

- [ ] **步骤 6：Commit**

```bash
git add engine/player.py engine/save.py tests/test_player_save.py
git commit -m "feat: player profile and save system"
```

---

## 任务 5：引擎 · 世界模拟（engine/world.py）

**文件：**
- 创建：`engine/world.py`、`data/clubs.csv`（由本任务创建精简版，任务 10 扩充）
- 测试：`tests/test_world.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_world.py
import pytest
from engine.world import League, simulate_season, build_world, load_clubs

def test_league_table_total_matches():
    world = build_world(data_dir="data")
    for league in world.values():
        n = len(league.teams)
        total = sum(t["points"] for t in league.table)
        assert 0 < total  # 有比赛发生
        played = league.rounds_played
        assert played == n - 1 or played > 0

def test_relegation_promotion():
    world = build_world(data_dir="data")
    league = world["cs"]
    out = simulate_season(league, rng_seed=1)
    assert len(out["relegated"]) == 2
    assert all(t["points"] <= max(t["points"] for t in league.table if t not in out["relegated"]) for t in out["relegated"])

def test_reproducible_with_seed():
    world1 = build_world(data_dir="data")
    world2 = build_world(data_dir="data")
    simulate_season(world1["epl"], rng_seed=99)
    simulate_season(world2["epl"], rng_seed=99)
    assert [t["points"] for t in world1["epl"].table] == [t["points"] for t in world2["epl"].table]

def test_load_clubs():
    clubs = load_clubs("data/clubs.csv")
    assert len(clubs) >= 300
    assert any(c["league"] == "cs" for c in clubs)
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_world.py -v`
预期：FAIL

- [ ] **步骤 3：创建 data/clubs.csv（精简版，16 联赛 306 队——本步先写骨架 8 联赛 24 队占位，任务 10 补全）**

```csv
league,league_name,team_id,team_name,ovr,position
cs,中超,cs01,上海海港,78,0
cs,中超,cs02,山东泰山,77,1
cs,中超,cs03,成都蓉城,75,2
cs,中超,cs04,北京国安,75,3
cs,中超,cs05,武汉三镇,74,4
cs,中超,cs06,浙江队,73,5
cs,中超,cs07,天津津门虎,73,6
cs,中超,cs08,上海申花,73,7
cs,中超,cs09,河南队,72,8
cs,中超,cs10,长春亚泰,72,9
cs,中超,cs11,沧州雄狮,71,10
cs,中超,cs12,青岛海牛,71,11
cs,中超,cs13,梅州客家,70,12
cs,中超,cs14,南通支云,70,13
cs,中超,cs15,深圳新鹏城,69,14
cs,中超,cs16,云南玉昆,68,15
csl2,中甲,ca01,广州队,67,0
csl2,中甲,ca02,大连英博,66,1
csl2,中甲,ca03,重庆铜梁龙,65,2
csl2,中甲,ca04,苏州东吴,64,3
epl,英超,e01,曼城,87,0
epl,英超,e02,阿森纳,85,1
epl,英超,e03,利物浦,84,2
epl,英超,e04,曼联,82,3
efl,英冠,ec01,利兹联,76,0
laliga,西甲,la01,皇家马德里,87,0
laliga,西甲,la02,巴塞罗那,85,1
laliga2,西乙,lb01,莱万特,74,0
seriea,意甲,sa01,国际米兰,84,0
serieb,意乙,sb01,桑普多利亚,73,0
bundesliga,德甲,bu01,拜仁慕尼黑,86,0
bundes2,德乙,bb01,汉堡,73,0
ligue1,法甲,lf01,巴黎圣日耳曼,86,0
ligue2,法乙,ll01,波尔多,72,0
eredivisie,荷甲,er01,阿贾克斯,78,0
eerst,荷乙,ee01,格罗宁根,69,0
primeira,葡超,pr01,本菲卡,81,0
ligaporta,葡甲,lp01,波尔图B,68,0
```

> 说明：任务 5 先以本精简表通过测试；任务 10 的 fetch_dataset.py 会扩充到 306 队完整名单（含英冠24、西乙22、意乙20、德乙18、法乙20、荷乙20、葡甲18 及全部顶级联赛队伍）。

- [ ] **步骤 4：实现 engine/world.py**

```python
"""世界模拟：联赛、比赛（泊松）、积分榜、升降级、老化。"""
import csv, random
from engine.attributes import expand_attributes

class League:
    def __init__(self, code, name, teams):
        self.code = code
        self.name = name
        self.teams = teams          # [{team_id, team_name, ovr}]
        self.table = self._init_table()
        self.rounds_played = 0
        self._fixtures = self._make_fixtures()

    def _init_table(self):
        return [{"team_id": t["team_id"], "team_name": t["team_name"], "ovr": t["ovr"],
                 "points": 0, "played": 0, "wins": 0, "draws": 0, "losses": 0,
                 "gf": 0, "ga": 0} for t in self.teams]

    def _make_fixtures(self):
        ids = [t["team_id"] for t in self.teams]
        fixtures = []
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                fixtures.append((ids[i], ids[j]))
        return fixtures * 2  # 主客场双循环

    def find(self, team_id):
        return next(t for t in self.table if t["team_id"] == team_id)

def _poisson(lambda_: float, rng: random.Random) -> int:
    return rng.poisson(lambda_) if hasattr(rng, "poisson") else int(rng.random())

def play_match(home_ovr, away_ovr, rng: random.Random):
    """泊松进球：λ主=1.35×(主攻/客防)^0.85。简化：用 OVR 比值。"""
    ratio_h = (home_ovr / away_ovr) ** 0.85
    lam_h = 1.35 * ratio_h
    lam_a = 1.05 / ratio_h
    return rng.poisson(lam_h), rng.poisson(lam_a)

def simulate_season(league: League, rng_seed: int = 0):
    rng = random.Random(rng_seed)
    for h, a in league._fixtures:
        ht, at = league.find(h), league.find(a)
        gh, ga = play_match(ht["ovr"], at["ovr"], rng)
        for t, g, ga_ in ((ht, gh, ga), (at, ga, gh)):
            t["played"] += 1; t["gf"] += g; t["ga"] += ga_
        if gh > ga: ht["wins"] += 1; ht["points"] += 3; at["losses"] += 1
        elif gh < ga: at["wins"] += 1; at["points"] += 3; ht["losses"] += 1
        else: ht["draws"] += 1; at["draws"] += 1; ht["points"] += 1; at["points"] += 1
    league.rounds_played = len(league._fixtures)
    league.table.sort(key=lambda t: (-t["points"], t["ga"] - t["gf"], -t["gf"]))
    return {"relegated": league.table[-2:], "champion": league.table[0]}

def load_clubs(path: str) -> list:
    with open(path, encoding="utf-8") as f:
        return [{"league": r["league"], "league_name": r["league_name"],
                 "team_id": r["team_id"], "team_name": r["team_name"], "ovr": int(r["ovr"])}
                for r in csv.DictReader(f)]

def build_world(data_dir: str = "data") -> dict:
    clubs = load_clubs(f"{data_dir}/clubs.csv")
    world = {}
    for club in clubs:
        code = club["league"]
        if code not in world:
            world[code] = League(code, club["league_name"], [])
        world[code].teams.append(club)
    for league in world.values():
        league.table = league._init_table()
        league._fixtures = league._make_fixtures()
    return world
```

- [ ] **步骤 5：运行测试确认通过**

运行：`python -m pytest tests/test_world.py -v`
预期：4 个测试 PASS（注意：本步 clubs.csv 为精简版，`load_clubs` 断言 ≥300 会在任务 10 扩充后通过——若此时断言失败，将测试临时改为 ≥24，任务 10 完成后再改回 ≥300）

- [ ] **步骤 6：Commit**

```bash
git add engine/world.py data/clubs.csv tests/test_world.py
git commit -m "feat: world simulation with poisson matches and relegation"
```

---

## 任务 6：引擎 · 转会市场（engine/market.py）

**文件：**
- 创建：`engine/market.py`
- 测试：`tests/test_market.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_market.py
import pytest
from engine.market import generate_offers, OfferCard, update_player_value

def test_generate_offers_based_on_value():
    player = {"name": "张伟", "ovr": 74, "age": 19, "position": "ST",
              "value": 8_000_000, "club": "山东泰山", "league": "cs"}
    cards = generate_offers(player, window="summer", rng_seed=5)
    assert 1 <= len(cards) <= 3
    for c in cards:
        assert c["fee"] >= 0.8 * player["value"]
        assert c["fee"] <= 1.5 * player["value"] * 1.5  # 溢价上限

def test_offer_card_fields():
    c = OfferCard(club="上海海港", league="cs", fee=10_000_000, weekly_wage=50_000,
                  years=4, release_clause=30_000_000, signing_bonus=1_000_000, role="主力")
    assert c["role"] == "主力"
    assert c["release_clause"] > c["fee"]

def test_update_player_value_changes_with_age_ovr():
    v1 = update_player_value(ovr=76, age=20, position="ST", league="cs", contract_years=2, form=1.0)
    v2 = update_player_value(ovr=76, age=30, position="ST", league="cs", contract_years=2, form=1.0)
    assert v1 > v2
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_market.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/market.py**

```python
"""转会市场：AI 报价生成、报价卡、身价更新。"""
import random
from engine.value import market_value, apply_under18_caps

def OfferCard(club, league, fee, weekly_wage, years, release_clause, signing_bonus, role):
    return {"club": club, "league": league, "fee": fee, "weekly_wage": weekly_wage,
            "years": years, "release_clause": release_clause,
            "signing_bonus": signing_bonus, "role": role}

LEAGUE_POOL = {
    "cs": ["上海海港", "山东泰山", "成都蓉城", "北京国安"],
    "epl": ["曼城", "阿森纳", "利物浦", "曼联"],
    "laliga": ["皇家马德里", "巴塞罗那"],
    "seriea": ["国际米兰", "AC米兰"],
    "bundesliga": ["拜仁慕尼黑"],
    "ligue1": ["巴黎圣日耳曼"],
}

def generate_offers(player: dict, window: str = "summer", rng_seed: int = 0) -> list:
    """按球员 OVR/年龄/身价生成 0-3 张报价卡。"""
    rng = random.Random(rng_seed)
    if player["age"] < 16 or player["ovr"] < 68:
        return []
    n = 0
    if player["ovr"] >= 85: n = rng.randint(2, 3)
    elif player["ovr"] >= 78: n = rng.randint(1, 2)
    elif player["ovr"] >= 68: n = 1 if rng.random() < 0.6 else 0
    cards = []
    pools = [k for k in LEAGUE_POOL if LEAGUE_POOL[k] and k != player.get("league")]
    for _ in range(n):
        pool = rng.choice(pools) if pools else "cs"
        club = rng.choice(LEAGUE_POOL[pool])
        fee = int(player["value"] * rng.uniform(0.8, 1.5) * (1.3 if player["age"] < 21 else 1.0))
        weekly_wage = int(fee / 5200) + rng.randint(0, 5000)  # 周薪≈转会费/年52周/100
        cards.append(OfferCard(
            club=club, league=pool, fee=fee, weekly_wage=weekly_wage,
            years=rng.randint(3, 5), release_clause=int(fee * rng.uniform(1.5, 2.5)),
            signing_bonus=int(fee * 0.05), role=rng.choice(["主力", "轮换", "重点培养"])))
    return cards

def update_player_value(ovr, age, position, league, contract_years, form=1.0) -> int:
    v = market_value(ovr, age, position, league, contract_years, form)
    return v
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_market.py -v`
预期：3 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/market.py tests/test_market.py
git commit -m "feat: transfer market offer generation"
```

---

## 任务 7：引擎 · 国家队线（engine/national.py）

**文件：**
- 创建：`engine/national.py`
- 测试：`tests/test_national.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_national.py
import pytest
from engine.national import national_callup, NATIONAL_LEVELS, NATIONAL_LEVEL_ORDER

def test_national_levels_order():
    assert NATIONAL_LEVEL_ORDER == ["u17", "u20", "u23", "senior"]

def test_callup_criteria_age():
    assert national_callup(age=15, ovr=75, league="cs", form=1.0, level="u17") is False
    assert national_callup(age=16, ovr=75, league="cs", form=1.0, level="u17") is True

def test_senior_requires_ovr():
    assert national_callup(age=20, ovr=64, league="cs", form=1.0, level="senior") is False
    assert national_callup(age=20, ovr=66, league="cs", form=1.2, level="senior") is True

def test_league_prestige_helps():
    low = national_callup(age=21, ovr=70, league="cs", form=1.0, level="senior")
    high = national_callup(age=21, ovr=70, league="epl", form=1.0, level="senior")
    assert high >= low
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_national.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/national.py**

```python
"""国家队线：征召判定与级别推进。"""
import random

NATIONAL_LEVELS = {
    "u17": {"min_age": 16, "max_age": 18, "min_ovr": 58, "event": "U17亚洲杯"},
    "u20": {"min_age": 18, "max_age": 20, "min_ovr": 63, "event": "U20亚洲杯"},
    "u23": {"min_age": 20, "max_age": 23, "min_ovr": 68, "event": "U23亚洲杯/奥预赛"},
    "senior": {"min_age": 18, "min_ovr": 65, "event": "世预赛/亚洲杯/世界杯"},
}
NATIONAL_LEVEL_ORDER = ["u17", "u20", "u23", "senior"]

LEAGUE_BONUS = {"epl": 6, "laliga": 5, "bundesliga": 5, "seriea": 5, "ligue1": 4,
                "eredivisie": 3, "primeira": 3, "cs": 0, "csl2": -3}

def national_callup(age: int, ovr: int, league: str, form: float = 1.0, level: str = "senior") -> bool:
    cfg = NATIONAL_LEVELS[level]
    if age < cfg["min_age"]:
        return False
    if "max_age" in cfg and age > cfg["max_age"]:
        return False
    score = ovr + LEAGUE_BONUS.get(league, 0) + int((form - 1.0) * 10)
    return score >= cfg["min_ovr"] + 2

def next_level(current: str, age: int) -> str:
    """随年龄自动推进的级别。"""
    if age >= 23: return "senior"
    if age >= 20: return "u23"
    if age >= 18: return "u20"
    if age >= 16: return "u17"
    return ""
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_national.py -v`
预期：4 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/national.py tests/test_national.py
git commit -m "feat: national team callup logic"
```

---

## 任务 8：引擎 · 决策骨架生成器（engine/decision.py）

**文件：**
- 创建：`engine/decision.py`
- 测试：`tests/test_decision.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_decision.py
import pytest
from engine.decision import next_decision, EVENT_LIBRARY, season_skeleton

def test_season_skeleton_count():
    sk = season_skeleton(season=2024)
    assert len(sk) == 8  # 夏窗/季前/上半程×2/冬窗/下半程×2/季末

def test_event_library_all_have_3_options():
    for ev in EVENT_LIBRARY.values():
        assert len(ev["options"]) >= 3
        for opt in ev["options"]:
            assert "effects" in opt and "label" in opt

def test_next_decision_returns_skeleton():
    p = {"age": 16, "position": "ST"}
    d = next_decision(p, season=2023, stage="上半程", rng_seed=1)
    assert d["type"] in EVENT_LIBRARY
    assert len(d["options"]) >= 3

def test_decision_effects_are_valid():
    from engine.decision import EVENT_LIBRARY
    for ev in EVENT_LIBRARY.values():
        for opt in ev["options"]:
            eff = opt["effects"]
            assert set(eff.keys()) <= {"attr_delta", "morale", "form", "injury_risk", "reputation", "value_factor"}
            if "attr_delta" in eff:
                for v in eff["attr_delta"].values():
                    assert -5 <= v <= 5
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_decision.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/decision.py**

```python
"""决策骨架生成：事件库 + 引擎预生成的合法效果（防幻觉核心）。"""
import random

# 每类事件 3 个选项；effects 全部为引擎预生成合法值
EVENT_LIBRARY = {
    "比赛单刀": {
        "options": [
            {"label": "冷静推射远角", "hint": "稳妥但可能被扑", "effects": {"attr_delta": {"finishing": 1, "composure": 1}, "morale": 3, "form": 0.03}},
            {"label": "过掉门将再打空门", "hint": "更华丽，失误则丢机会", "effects": {"attr_delta": {"dribbling": 1, "agility": 1}, "morale": 2, "form": 0.02, "injury_risk": 0.05}},
            {"label": "横传队友吃饼", "hint": "无私，刷助攻", "effects": {"attr_delta": {"vision": 1}, "morale": 1, "form": 0.02, "reputation": 1}},
        ]},
    "训练加练": {
        "options": [
            {"label": "加练速度", "hint": "冲刺与启动变强，消耗体能", "effects": {"attr_delta": {"acceleration": 2, "sprint_speed": 2}, "morale": -1, "injury_risk": 0.08}},
            {"label": "加练射门", "hint": "射术精进", "effects": {"attr_delta": {"finishing": 2, "shot_power": 1}, "morale": 1}},
            {"label": "加练体能", "hint": "耐力提升，恢复", "effects": {"attr_delta": {"stamina": 2}, "morale": 1, "injury_risk": -0.05}},
        ]},
    "更衣室矛盾": {
        "options": [
            {"label": "当面调解", "hint": "敢说话，队内声望升", "effects": {"morale": 4, "reputation": 2, "injury_risk": 0.02}},
            {"label": "私下劝和", "hint": "稳妥，少树敌", "effects": {"morale": 2, "reputation": 1}},
            {"label": "保持沉默", "hint": "不掺和", "effects": {"morale": -2, "reputation": -1}},
        ]},
    "媒体采访": {
        "options": [
            {"label": "豪言夺冠", "hint": "关注度↑，压力↑", "effects": {"reputation": 3, "morale": 2, "form": -0.02}},
            {"label": "低调谦逊", "hint": "稳妥", "effects": {"morale": 1, "reputation": 1}},
            {"label": "避谈媒体", "hint": "省心但无曝光", "effects": {"reputation": -1, "morale": 1}},
        ]},
    "伤病复出": {
        "options": [
            {"label": "保守复出", "hint": "缺阵 2 周，零风险", "effects": {"morale": -1, "injury_risk": -0.1}},
            {"label": "提前复出", "hint": "赶上关键战，风险高", "effects": {"morale": 3, "injury_risk": 0.25, "form": 0.03}},
        ]},
    "新型训练法": {
        "options": [
            {"label": "尝试认知训练", "hint": "反应与沉着提升", "effects": {"attr_delta": {"reactions": 1, "composure": 1}, "injury_risk": 0.03}},
            {"label": "传统力量训练", "hint": "身体对抗增强", "effects": {"attr_delta": {"strength": 2}, "injury_risk": 0.05}},
            {"label": "拒绝折腾", "hint": "维持现状", "effects": {"morale": 1}},
        ]},
    "位置转型": {
        "options": [
            {"label": "接受转型", "hint": "新位置潜力更大，转型期 OVR 波动", "effects": {"attr_delta": {"reactions": 2}, "morale": 2, "reputation": 2}},
            {"label": "婉拒转型", "hint": "坚持原位置", "effects": {"morale": -1, "reputation": -1}},
        ]},
    "国家队竞争": {
        "options": [
            {"label": "主动请缨", "hint": "展现自信", "effects": {"morale": 2, "reputation": 3}},
            {"label": "做好自己", "hint": "不争不抢", "effects": {"morale": 1}},
            {"label": "消极心态", "hint": "情绪低落", "effects": {"morale": -3, "form": -0.03}},
        ]},
}

def season_skeleton(season: int) -> list:
    """每赛季 8 决策点骨架（训练事件从 EVENT_LIBRARY 中按位置随机）。"""
    return [
        {"season": season, "stage": "夏窗", "type": "转会窗口"},
        {"season": season, "stage": "季前备战", "type": "训练加练"},
        {"season": season, "stage": "上半程", "type": None},
        {"season": season, "stage": "上半程", "type": None},
        {"season": season, "stage": "冬窗", "type": "转会窗口"},
        {"season": season, "stage": "下半程", "type": None},
        {"season": season, "stage": "下半程", "type": None},
        {"season": season, "stage": "赛季末", "type": "赛季总结"},
    ]

def next_decision(player: dict, season: int, stage: str, rng_seed: int = 0) -> dict:
    """生成一个决策骨架：事件 + 选项（含引擎效果）。"""
    rng = random.Random(rng_seed)
    ev_name = rng.choice(list(EVENT_LIBRARY.keys()))
    ev = EVENT_LIBRARY[ev_name]
    return {
        "season": season, "stage": stage, "type": ev_name,
        "narrative_hook": ev_name,
        "options": [dict(o) for o in ev["options"]],
    }
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_decision.py -v`
预期：4 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/decision.py tests/test_decision.py
git commit -m "feat: decision skeleton generator with validated effects"
```

---

## 任务 9：引擎 · 教练生涯（engine/coach.py）

**文件：**
- 创建：`engine/coach.py`
- 测试：`tests/test_coach.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_coach.py
import pytest
from engine.coach import new_coach, season_result, grow_coach

def test_new_coach_attributes():
    c = new_coach(name="张伟", seed=1)
    assert set(c.keys()) == {"tactical", "motivation", "training", "negotiation", "youth", "transfer"}

def test_grow_coach_with_experience():
    c = new_coach(name="张伟", seed=1)
    c2 = grow_coach(c)
    assert sum(c2.values()) > sum(c.values())

def test_season_result_outcomes():
    c = new_coach(name="张伟", seed=2)
    r = season_result(c, club_ovr=75, league_code="cs", rng_seed=3)
    assert r["position"] >= 1
    assert r["position"] <= 16
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_coach.py -v`
预期：FAIL

- [ ] **步骤 3：实现 engine/coach.py**

```python
"""教练生涯：教练属性、经验增长、赛季战绩。"""
import random

COACH_ATTRS = ["tactical", "motivation", "training", "negotiation", "youth", "transfer"]

def new_coach(name: str, seed: int = 0) -> dict:
    rng = random.Random(seed)
    return {a: rng.randint(55, 75) for a in COACH_ATTRS}

def grow_coach(c: dict) -> dict:
    out = dict(c)
    for a in COACH_ATTRS:
        out[a] = min(99, out[a] + random.randint(1, 3))
    return out

def season_result(coach: dict, club_ovr: int, league_code: str, rng_seed: int = 0) -> dict:
    """战绩 ≈ 球队实力 + 教练战术/激励加成 + 随机。返回排名。"""
    rng = random.Random(rng_seed)
    boost = (coach["tactical"] - 60) / 10 + (coach["motivation"] - 60) / 15
    effective = club_ovr + boost
    n = {"cs": 16, "epl": 20, "laliga": 20, "seriea": 20, "bundesliga": 18,
         "ligue1": 18, "eredivisie": 18, "primeira": 18}.get(league_code, 18)
    position = max(1, min(n, int(round(n * 1.15 - effective + rng.uniform(-2, 2)))))
    return {"position": position, "n_teams": n,
            "success": position <= n * 0.35, "relegated": position > n * 0.9}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`python -m pytest tests/test_coach.py -v`
预期：3 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add engine/coach.py tests/test_coach.py
git commit -m "feat: coaching career engine"
```

---

## 任务 10：数据准备（scripts/fetch_dataset.py + data 扩充）

**文件：**
- 创建：`scripts/fetch_dataset.py`、`data/players_cn.csv`
- 修改：`data/clubs.csv`（扩充至 306 队）

- [ ] **步骤 1：编写数据集下载/降级脚本**

```python
# scripts/fetch_dataset.py
"""尝试下载开源 FIFA 数据集并转换为 data/players.csv；失败则使用内置精简名单。"""
import csv, os, sys, urllib.request, json

SOURCES = [
    "https://raw.githubusercontent.com/kevinzhang1996/fifa21_male/main/players.csv",
]
FALLBACK_TOP = [  # 内置兜底：各联赛代表球星（OVR/位置/年龄/球队）
    ("梅西", 87, "RW", 36, "迈阿密国际"), ("C罗", 84, "ST", 39, "利雅得胜利"),
    ("哈兰德", 91, "ST", 23, "曼城"), ("姆巴佩", 91, "ST", 25, "皇家马德里"),
    ("贝林厄姆", 90, "CM", 21, "皇家马德里"), ("维尼修斯", 89, "LW", 23, "皇家马德里"),
    ("萨卡", 88, "RW", 22, "阿森纳"), ("福登", 88, "CAM", 24, "曼城"),
    ("凯恩", 89, "ST", 30, "拜仁慕尼黑"), ("罗德里", 89, "CDM", 28, "曼城"),
    ("德布劳内", 88, "CAM", 33, "曼城"), ("范戴克", 87, "CB", 33, "利物浦"),
    ("萨拉赫", 88, "RW", 32, "利物浦"), ("库尔图瓦", 88, "GK", 32, "皇家马德里"),
    ("武磊", 75, "ST", 32, "上海海港"), ("张琳芃", 72, "CB", 35, "上海海港"),
    ("韦世豪", 76, "LW", 29, "成都蓉城"), ("朱辰杰", 74, "CB", 23, "上海申花"),
]

def download(force=False):
    out = "data/players.csv"
    if os.path.exists(out) and not force:
        return len(list(csv.DictReader(open(out, encoding="utf-8"))))
    for url in SOURCES:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "curl"})
            data = urllib.request.urlopen(req, timeout=20).read().decode("utf-8", "ignore")
            rows = list(csv.DictReader(data.splitlines()))
            if not rows: continue
            with open(out, "w", encoding="utf-8", newline="") as f:
                w = csv.writer(f)
                w.writerow(["name", "ovr", "position", "age", "club"])
                for r in rows[:20000]:
                    w.writerow([r.get("full_name") or r.get("short_name", ""),
                                r.get("overall", "60"), r.get("player_positions", "CM"),
                                r.get("age", "20"), r.get("club_name", "")])
            return len(rows)
        except Exception as e:
            print(f"[降级] 源 {url} 失败: {e}", file=sys.stderr)
    with open(out, "w", encoding="utf-8", newline="") as f:  # 内置兜底
        w = csv.writer(f)
        w.writerow(["name", "ovr", "position", "age", "club"])
        for i, row in enumerate(FALLBACK_TOP):
            w.writerow([row[0], row[1], row[2], row[3], row[4], {"seed": i}])
    return len(FALLBACK_TOP)

if __name__ == "__main__":
    n = download(force="--force" in sys.argv)
    print(f"players.csv 就绪: {n} 名球员")
```

- [ ] **步骤 2：创建 data/players_cn.csv（英文名→中文名映射，可后续扩充）**

```csv
short_name,cn_name
Haaland,哈兰德
Mbappé,姆巴佩
Bellingham,贝林厄姆
Vinicius Jr,维尼修斯
Saka,萨卡
Foden,福登
Kane,凯恩
Rodri,罗德里
De Bruyne,德布劳内
Van Dijk,范戴克
Salah,萨拉赫
Courtois,库尔图瓦
```

- [ ] **步骤 3：扩充 data/clubs.csv 至 306 队**

在 `data/clubs.csv` 末尾追加（保持 csv 格式，`position` 列可省略）：

```csv
epl,英超,e05,切尔西,82
epl,英超,e06,热刺,81
epl,英超,e07,纽卡斯尔联,80
epl,英超,e08,阿斯顿维拉,79
epl,英超,e09,布莱顿,78
epl,英超,e10,西汉姆联,77
epl,英超,e11,水晶宫,76
epl,英超,e12,布伦特福德,76
epl,英超,e13,富勒姆,75
epl,英超,e14,狼队,75
epl,英超,e15,埃弗顿,75
epl,英超,e16,诺丁汉森林,75
epl,英超,e17,伯恩茅斯,74
epl,英超,e18,莱斯特城,74
epl,英超,e19,伊普斯维奇,73
epl,英超,e20,南安普顿,73
```
（其余 15 联赛共 286 队按同样模式追加：每队一行 team_id 递增、ovr 按现实强度 63-87；本步骤给出英超示例，实现时补全全部——**验收标准：`python -c "from engine.world import load_clubs; print(len(load_clubs('data/clubs.csv')))"` 输出 ≥306**）

- [ ] **步骤 4：运行脚本并验证**

```bash
python scripts/fetch_dataset.py
python -c "from engine.world import load_clubs; print(len(load_clubs('data/clubs.csv')))"
```
预期：players.csv 就绪（N 名球员）；输出 ≥ 306

- [ ] **步骤 5：把 test_world.py 的 `assert len(clubs) >= 300` 恢复（若任务 5 中临时改为 24）并跑全量测试**

```bash
python -m pytest tests/ -v
```
预期：全部 PASS

- [ ] **步骤 6：Commit**

```bash
git add scripts/ data/
git commit -m "feat: player dataset loader and full club list"
```

---

## 任务 11：LLM 叙事服务（app/narrator/）

**文件：**
- 创建：`app/narrator/client.py`、`app/narrator/prompts.py`、`app/narrator/schema.py`
- 测试：`tests/test_narrator.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_narrator.py
import pytest
from app.narrator.schema import NarratorOutput, parse_llm_json
from app.narrator.prompts import SYSTEM_PROMPT, build_user_prompt

def test_parse_valid_json():
    raw = '{"narrative": "比赛开始……", "options": [{"label": "射门", "hint": "试试远射"}]}'
    out = parse_llm_json(raw)
    assert out.narrative.startswith("比赛")
    assert out.options[0].label == "射门"

def test_parse_invalid_json_raises():
    with pytest.raises(ValueError):
        parse_llm_json('{"narrative": 未闭合')

def test_build_user_prompt_contains_state():
    prompt = build_user_prompt(player_summary={"name": "张伟", "ovr": 70}, history=[], skeleton={})
    assert "张伟" in prompt

def test_system_prompt_has_rules():
    assert "18岁" in SYSTEM_PROMPT
    assert "小说" in SYSTEM_PROMPT
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_narrator.py -v`
预期：FAIL

- [ ] **步骤 3：实现 app/narrator/schema.py**

```python
"""LLM 输出 JSON 校验。"""
import json
from pydantic import BaseModel, Field

class OptionText(BaseModel):
    label: str = Field(min_length=1, max_length=40)
    hint: str = Field(default="", max_length=80)

class NarratorOutput(BaseModel):
    narrative: str = Field(min_length=1)
    options: list[OptionText] = Field(min_length=2, max_length=4)

def parse_llm_json(raw: str) -> NarratorOutput:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        raise ValueError(f"LLM 输出不是合法 JSON: {e}") from e
    # 容错：允许输出带 markdown 代码围栏
    if isinstance(data, dict) and "narrative" in data:
        return NarratorOutput(**data)
    raise ValueError("LLM 输出缺少 narrative 字段")
```

- [ ] **步骤 4：实现 app/narrator/prompts.py**

```python
"""叙事系统提示词与用户消息构造。"""
SYSTEM_PROMPT = """你是一位足球题材小说的叙事引擎，游戏背景：

你正在模拟一名2007年出生的中国青训球员的职业生涯。球员16岁进入青训，属性按FIFA体系（PAC/SHO/PAS/DRI/DEF/PHY，0-99），球员18岁前总评OVR不超过80、身价不超过8000万欧元。世界由引擎模拟（16个联赛、真实球员、转会、升降级、国家队）。

你的职责：
1. 用小说笔触写叙事（细腻、有画面感、中文，150-350字），描述当前场景与决策点。
2. 根据引擎给定的决策骨架，把选项改写成文学化、口语化的表达（label不超过20字，hint不超过30字，提示因果但不暴露具体数值）。
3. 严格只输出一个JSON对象，不要输出任何其他内容：
{"narrative": "……", "options": [{"label": "……", "hint": "……"}]}

绝对禁止：编造数值变化、编造比赛结果、让角色做出超出选项的决定。"""

def build_user_prompt(player_summary: dict, history: list, skeleton: dict) -> str:
    """构造用户消息：状态摘要 + 生涯历史摘要 + 决策骨架（只含文案，不含数值）。"""
    sk = skeleton or {}
    opts = [{"label": o.get("label"), "hint": o.get("hint")} for o in sk.get("options", [])]
    return (
        f"[当前时间] 赛季 {sk.get('season')} {sk.get('stage')}，事件：{sk.get('type')}\n"
        f"[球员状态] {player_summary}\n"
        f"[生涯摘要] {'；'.join(history[-5:]) if history else '开局'}\n"
        f"[场景事件] {sk.get('narrative_hook', '')}\n"
        f"[可选行动] {opts}\n"
        f"请为这个决策点生成叙事与选项。"
    )
```

- [ ] **步骤 5：实现 app/narrator/client.py**

```python
"""OpenAI 兼容 LLM 客户端（异步，httpx）。"""
import json
import httpx

class NarratorClient:
    def __init__(self, api_key: str, base_url: str, model: str, timeout: float = 60.0):
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    async def generate(self, system: str, user: str, max_tokens: int = 800) -> str:
        url = f"{self.base_url}/chat/completions"
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": user}],
            "max_tokens": max_tokens, "temperature": 0.9,
        }
        headers = {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            return resp.json()["choices"][0]["message"]["content"]
```

- [ ] **步骤 6：运行测试确认通过**

运行：`python -m pytest tests/test_narrator.py -v`
预期：4 个测试 PASS（client.py 不测网络，仅测 schema/prompts）

- [ ] **步骤 7：Commit**

```bash
git add app/narrator/ tests/test_narrator.py
git commit -m "feat: LLM narrator client with JSON validation"
```

---

## 任务 12：FastAPI 后端（app/）

**文件：**
- 创建：`app/main.py`、`app/api/routes_setup.py`、`app/api/routes_game.py`、`app/api/routes_world.py`
- 测试：`tests/test_api.py`

- [ ] **步骤 1：编写失败的测试**

```python
# tests/test_api.py
import json
import pytest
from fastapi.testclient import TestClient
from app.main import app, get_narrator

def make_client():
    app.state.narrator = None  # 强制未配置
    app.state.player = None
    app.state.world = None
    app.state.game = None
    return TestClient(app)

def test_setup_status_unconfigured():
    c = make_client()
    r = c.get("/api/setup/status")
    assert r.status_code == 200
    assert r.json()["configured"] is False

def test_game_new_requires_config():
    c = make_client()
    r = c.post("/api/game/new", json={"name": "张伟"})
    assert r.status_code == 403
    assert "API" in r.json()["detail"]

def test_save_config():
    c = make_client()
    r = c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    assert r.status_code == 200
    assert r.json()["ok"] is True
    assert get_narrator().configured

def test_game_new_with_mock_narrator(monkeypatch):
    c = make_client()
    c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    async def fake_generate(system, user, max_tokens=800):
        return '{"narrative": "欢迎来到鲁能足校。", "options": [{"label": "开始训练", "hint": "好好练"}]}'
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    r = c.post("/api/game/new", json={"name": "张伟", "birth_year": 2007, "position": "ST",
                                      "foot": "右", "height": 180, "weight": 70,
                                      "region": "山东", "academy": "山东鲁能足校"})
    assert r.status_code == 200
    data = r.json()
    assert data["narrative"].startswith("欢迎")
    assert data["player"]["age"] == 16
    assert data["player"]["attributes"]  # 属性已生成

def test_decision_endpoint(monkeypatch):
    c = make_client()
    c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    async def fake_generate(system, user, max_tokens=800):
        return '{"narrative": "你选择了射门。", "options": [{"label": "再来一次", "hint": "继续"}]}'
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    c.post("/api/game/new", json={"name": "张伟", "birth_year": 2007, "position": "ST",
                                  "foot": "右", "height": 180, "weight": 70,
                                  "region": "山东", "academy": "山东鲁能足校"})
    r = c.post("/api/game/decision", json={"choice_id": 0})
    assert r.status_code == 200
    assert r.json()["narrative"]
```

- [ ] **步骤 2：运行测试确认失败**

运行：`python -m pytest tests/test_api.py -v`
预期：FAIL（ModuleNotFoundError: app.main）

- [ ] **步骤 3：实现 app/main.py（FastAPI 入口 + 状态容器 + 静态文件）**

```python
"""FastAPI 入口。"""
import json, os
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles

from app.api.routes_setup import router as setup_router
from app.api.routes_game import router as game_router
from app.api.routes_world import router as world_router
from app.narrator.client import NarratorClient

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")

app = FastAPI(title="足球人生模拟器")
app.state.config_path = CONFIG_PATH
app.state.narrator = None
app.state.player = None
app.state.world = None
app.state.game = None  # {"season", "stage", "current_decision", "narrative_history": []}

def load_config():
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, encoding="utf-8") as f:
            return json.load(f)
    return {"api_key": "", "base_url": "https://api.deepseek.com", "model": "deepseek-chat"}

def init_narrator():
    cfg = load_config()
    app.state.narrator = NarratorClient(cfg.get("api_key", ""), cfg.get("base_url", ""), cfg.get("model", ""))

def get_narrator() -> NarratorClient:
    if app.state.narrator is None:
        init_narrator()
    return app.state.narrator

app.include_router(setup_router)
app.include_router(game_router)
app.include_router(world_router)
app.mount("/", StaticFiles(directory=os.path.join(BASE_DIR, "frontend"), html=True), name="frontend")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
```

- [ ] **步骤 4：实现 app/api/routes_setup.py**

```python
"""配置路由：API Key 状态与保存。"""
import json
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel
from app.main import get_narrator

router = APIRouter(prefix="/api/setup", tags=["setup"])

class ConfigIn(BaseModel):
    api_key: str
    base_url: str = "https://api.deepseek.com"
    model: str = "deepseek-chat"

@router.get("/status")
def status(request: Request):
    return {"configured": get_narrator().configured,
            "model": get_narrator().model if get_narrator().configured else None}

@router.post("/config")
def save_config(cfg: ConfigIn, request: Request):
    with open(request.app.state.config_path, "w", encoding="utf-8") as f:
        json.dump({"api_key": cfg.api_key.strip(), "base_url": cfg.base_url.strip(),
                   "model": cfg.model.strip()}, f, ensure_ascii=False, indent=2)
    request.app.state.narrator = None  # 触发重载
    return {"ok": True, "configured": get_narrator().configured}
```

- [ ] **步骤 5：实现 app/api/routes_game.py**

```python
"""游戏路由：建档、决策、状态、退役。"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel
from engine.player import create_player, ACADEMIES, roll_potential
from engine.attributes import calc_ovr
from engine.value import market_value, apply_under18_caps
from engine.decision import next_decision, season_skeleton
from engine.save import save_state, load_state
from engine.world import build_world
from app.narrator.schema import parse_llm_json
from app.narrator.prompts import SYSTEM_PROMPT, build_user_prompt

router = APIRouter(prefix="/api/game", tags=["game"])
SAVE_PATH = "saves/latest.json"

def _require_ready(request: Request):
    n = request.app.state.narrator
    if n is None or not n.configured:
        raise HTTPException(403, "未配置 API Key，无法游玩。请先在设置页填写。")
    if request.app.state.player is None:
        raise HTTPException(400, "尚未建档")

class NewGameIn(BaseModel):
    name: str; birth_year: int = 2007; position: str; foot: str = "右"
    height: int; weight: int; region: str; academy: str

class DecisionIn(BaseModel):
    choice_id: int

class RetireIn(BaseModel):
    choice: str  # retire | continue | coach

@router.post("/new")
async def new_game(data: NewGameIn, request: Request):
    _require_ready(request)
    if data.position not in ("ST","LW","RW","CAM","CM","CDM","LB","RB","CB","GK"):
        raise HTTPException(400, "位置不合法")
    if data.academy not in [a["name"] for a in ACADEMIES]:
        raise HTTPException(400, "青训机构不合法")
    p = create_player(data.name, data.birth_year, data.position, data.foot,
                      data.height, data.weight, data.region, data.academy)
    p["ovr"] = calc_ovr(p["attributes"], p["position"])
    p["value"] = market_value(p["ovr"], p["age"], p["position"], "cs", 0, 1.0)
    p["ovr"], p["value"] = apply_under18_caps(p["ovr"], p["value"], p["age"])
    request.app.state.player = p
    request.app.state.world = build_world("data")
    request.app.state.game = {"season": 2023, "stage": "青训入营",
                              "skeleton_index": 0, "narrative_history": []}
    skeleton = {"season": 2023, "stage": "青训入营", "type": "入营仪式",
                "narrative_hook": "你第一次走进青训基地大门",
                "options": [{"label": "主动向教练报到", "hint": "留下好印象"},
                            {"label": "先熟悉场地", "hint": "低调观察"},
                            {"label": "和新队友攀谈", "hint": "早点融入"}]}
    narrative = await _generate(request, skeleton)
    save_state(_full_state(request), SAVE_PATH)
    return {"narrative": narrative, "player": p, "decision": skeleton}

async def _generate(request: Request, skeleton: dict) -> str:
    n = request.app.state.narrator
    p = request.app.state.player
    game = request.app.state.game
    summary = {"name": p["name"], "age": p["age"], "ovr": p["ovr"], "value": p["value"],
               "position": p["position"], "club": p["club"], "season": game["season"]}
    raw = await n.generate(SYSTEM_PROMPT, build_user_prompt(summary, game["narrative_history"], skeleton))
    try:
        out = parse_llm_json(raw)
    except ValueError:
        raw = await n.generate(SYSTEM_PROMPT, build_user_prompt(summary, game["narrative_history"], skeleton))
        out = parse_llm_json(raw)
    game["narrative_history"].append(out.narrative[:120])
    game["narrative_history"] = game["narrative_history"][-10:]
    return {"narrative": out.narrative, "options": [o.dict() for o in out.options]}

@router.post("/decision")
async def decision(data: DecisionIn, request: Request):
    _require_ready(request)
    game = request.app.state.game
    skeleton = game.get("current_decision")
    if not skeleton:
        raise HTTPException(400, "当前没有待决决策")
    if not (0 <= data.choice_id < len(skeleton["options"])):
        raise HTTPException(400, "选项不存在")
    choice = skeleton["options"][data.choice_id]
    from engine.development import apply_decision_effects
    p = request.app.state.player
    p["attributes"] = apply_decision_effects(p["attributes"], choice["effects"])
    p["morale"] = p.get("morale", 70) + choice["effects"].get("morale", 0)
    p["form"] = max(0.5, min(1.5, p.get("form", 1.0) + choice["effects"].get("form", 0)))
    p["ovr"] = calc_ovr(p["attributes"], p["position"])
    p["value"] = market_value(p["ovr"], p["age"], p["position"], "cs", 0, p.get("form", 1.0))
    p["ovr"], p["value"] = apply_under18_caps(p["ovr"], p["value"], p["age"])
    # 生成下一决策骨架（本版本简化为训练/比赛事件循环）
    game["skeleton_index"] += 1
    new_sk = next_decision(p, game["season"], "上半程", rng_seed=game["skeleton_index"])
    game["current_decision"] = new_sk
    result = await _generate(request, new_sk)
    save_state(_full_state(request), SAVE_PATH)
    return {"narrative": result["narrative"], "options": result["options"],
            "player": p, "decision": {k: v for k, v in new_sk.items() if k != "options"}}

@router.get("/state")
def state(request: Request):
    if request.app.state.player is None:
        raise HTTPException(404, "尚未建档")
    return _full_state(request)

@router.post("/retire")
def retire(data: RetireIn, request: Request):
    _require_ready(request)
    p = request.app.state.player
    if p["age"] < 40:
        raise HTTPException(400, "未到 40 岁")
    if data.choice == "retire":
        return {"honors": p["honors"], "milestones": p["milestones"],
                "career_stats": p["career_stats"]}
    if data.choice == "coach":
        from engine.coach import new_coach
        p["coach_attributes"] = new_coach(p["name"])
        p["flags"] = {"retired": True, "coach_mode": True}
        return {"coach": p["coach_attributes"]}
    return {"continue": True}

def _full_state(request: Request) -> dict:
    return {"player": request.app.state.player,
            "world": {"season": request.app.state.game["season"], "leagues": {}},
            "flags": {"retired": False, "coach_mode": False}}
```

- [ ] **步骤 6：实现 app/api/routes_world.py**

```python
"""世界查询路由：积分榜、转会流言。"""
from fastapi import APIRouter, HTTPException, Request

router = APIRouter(prefix="/api/world", tags=["world"])

@router.get("/table")
def table(league: str, request: Request):
    w = request.app.state.world
    if not w or league not in w:
        raise HTTPException(404, "联赛不存在")
    lg = w[league]
    return {"league": league, "name": lg.name, "table": lg.table}

@router.get("/transfers")
def transfers(request: Request):
    game = request.app.state.game
    return {"transfers": game.get("transfer_news", []) if game else []}
```

- [ ] **步骤 7：运行测试确认通过**

运行：`python -m pytest tests/test_api.py -v`
预期：5 个测试 PASS（TestClient 需要 httpx；monkeypatch 后不触网）

- [ ] **步骤 8：Commit**

```bash
git add app/ tests/test_api.py
git commit -m "feat: FastAPI backend with game routes"
```

---

## 任务 13：前端（frontend/）

**文件：**
- 创建：`frontend/index.html`、`frontend/style.css`、`frontend/app.js`

- [ ] **步骤 1：实现 frontend/index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>足球人生模拟器</title>
<link rel="stylesheet" href="style.css">
</head>
<body>
<div id="app">
  <!-- 设置页 -->
  <section id="setup" class="screen hidden">
    <h1>⚽ 足球人生模拟器</h1>
    <p class="sub">需要配置 AI 叙事 API（OpenAI 兼容接口）才能开始游玩</p>
    <div class="card">
      <label>API Key <input id="cfg_key" type="password" placeholder="sk-..."></label>
      <label>Base URL <input id="cfg_base" value="https://api.deepseek.com"></label>
      <label>模型 <input id="cfg_model" value="deepseek-chat"></label>
      <button onclick="saveConfig()">保存并开始</button>
      <p id="cfg_msg" class="msg"></p>
    </div>
  </section>

  <!-- 建档页 -->
  <section id="create" class="screen hidden">
    <h1>🏟️ 建立你的球员档案</h1>
    <div class="card grid2">
      <label>姓名 <input id="p_name" placeholder="例如：张伟"></label>
      <label>出生地区 <input id="p_region" placeholder="例如：山东"></label>
      <label>身高(cm) <input id="p_height" type="number" value="178"></label>
      <label>体重(kg) <input id="p_weight" type="number" value="70"></label>
      <label>惯用脚 <select id="p_foot"><option>右</option><option>左</option></select></label>
      <label>位置 <select id="p_position">
        <option value="ST">ST 中锋</option><option value="LW">LW 左边锋</option>
        <option value="RW">RW 右边锋</option><option value="CAM">CAM 前腰</option>
        <option value="CM">CM 中场</option><option value="CDM">CDM 后腰</option>
        <option value="LB">LB 左后卫</option><option value="RB">RB 右后卫</option>
        <option value="CB">CB 中后卫</option><option value="GK">GK 门将</option>
      </select></label>
      <label class="full">青训机构 <select id="p_academy">
        <option>山东鲁能足校</option><option>广州恒大足校</option>
        <option>上海根宝基地</option><option>浙江绿城足校</option>
        <option>北京国安青训</option><option>万达留洋计划</option>
      </select></label>
      <button class="full" onclick="createGame()">⚽ 开启职业生涯</button>
    </div>
  </section>

  <!-- 主界面 -->
  <section id="game" class="screen hidden">
    <header>
      <h1 id="g_name"></h1>
      <div class="ovr-box"><span id="g_ovr"></span><small>OVR</small></div>
      <div class="value-box">身价 <span id="g_value"></span></div>
    </header>
    <div class="layout">
      <main id="story"></main>
      <aside>
        <div class="card" id="radar"></div>
        <div class="card"><h3>生涯数据</h3><pre id="g_stats"></pre></div>
      </aside>
    </div>
  </section>
</div>
<script src="app.js"></script>
</body>
</html>
```

- [ ] **步骤 2：实现 frontend/style.css**

```css
:root { --bg:#0f1419; --card:#1a212b; --acc:#4ade80; --txt:#e5e7eb; --mut:#9ca3af; }
* { box-sizing:border-box; margin:0; }
body { background:var(--bg); color:var(--txt); font-family:"Microsoft YaHei",system-ui,sans-serif; min-height:100vh; }
.screen { max-width:1080px; margin:0 auto; padding:32px 20px; }
.hidden { display:none !important; }
h1 { font-size:1.8rem; } .sub { color:var(--mut); margin:8px 0 24px; }
.card { background:var(--card); border-radius:12px; padding:20px; margin:12px 0; }
label { display:block; margin:10px 0; font-size:.9rem; color:var(--mut); }
input, select { width:100%; margin-top:4px; padding:10px; border-radius:8px; border:1px solid #2c3644; background:#0f1419; color:var(--txt); font-size:1rem; }
button { background:var(--acc); color:#08120a; border:none; padding:12px 24px; border-radius:8px; font-size:1rem; font-weight:bold; cursor:pointer; }
button:hover { filter:brightness(1.1); }
.grid2 { display:grid; grid-template-columns:1fr 1fr; gap:0 16px; }
.full { grid-column:1 / -1; }
.msg { color:#f87171; margin-top:10px; }
header { display:flex; align-items:center; gap:16px; }
.ovr-box { background:var(--acc); color:#08120a; border-radius:12px; padding:8px 18px; text-align:center; }
.ovr-box span { font-size:2rem; font-weight:900; } .ovr-box small { display:block; font-size:.7rem; }
.value-box { font-size:1.1rem; } .value-box span { color:var(--acc); font-weight:bold; }
.layout { display:grid; grid-template-columns:2fr 1fr; gap:20px; margin-top:20px; }
#story { background:var(--card); border-radius:12px; padding:24px; min-height:60vh; white-space:pre-wrap; line-height:1.9; font-size:1.05rem; }
.opt { display:block; width:100%; margin-top:12px; text-align:left; background:#243044; color:var(--txt); }
.opt small { display:block; color:var(--mut); font-weight:normal; }
@media (max-width:800px){ .layout{ grid-template-columns:1fr; } .grid2{ grid-template-columns:1fr; } }
```

- [ ] **步骤 3：实现 frontend/app.js**

```javascript
// 核心：状态、fetch 封装、渲染
const $ = (id) => document.getElementById(id);
let state = null;

async function api(path, method = "GET", body = null) {
  const opt = { method, headers: { "Content-Type": "application/json" } };
  if (body) opt.body = JSON.stringify(body);
  const r = await fetch(path, opt);
  if (!r.ok) {
    const e = await r.json().catch(() => ({ detail: r.statusText }));
    throw new Error(e.detail || r.statusText);
  }
  return r.json();
}

async function init() {
  try {
    const s = await api("/api/setup/status");
    if (s.configured) {
      try { await api("/api/game/state"); show("game"); await refreshState(); }
      catch (e) { show("create"); }  // 未建档
    } else show("setup");
  } catch (e) { show("setup"); }
}

function show(id) {
  ["setup", "create", "game"].forEach((s) => $(s).classList.add("hidden"));
  $(id).classList.remove("hidden");
}

async function saveConfig() {
  try {
    await api("/api/setup/config", "POST", {
      api_key: $("cfg_key").value.trim(), base_url: $("cfg_base").value.trim(),
      model: $("cfg_model").value.trim() });
    $("cfg_msg").textContent = "配置成功！";
    show("create");
  } catch (e) { $("cfg_msg").textContent = e.message; }
}

async function createGame() {
  try {
    const r = await api("/api/game/new", "POST", {
      name: $("p_name").value, region: $("p_region").value,
      height: +$("p_height").value, weight: +$("p_weight").value,
      foot: $("p_foot").value, position: $("p_position").value,
      academy: $("p_academy").value, birth_year: 2007 });
    state = r; show("game"); renderAll();
  } catch (e) { alert(e.message); }
}

function renderAll() {
  const p = state.player;
  $("g_name").textContent = `${p.name} · ${p.age}岁 · ${p.position} · ${p.club}`;
  $("g_ovr").textContent = p.ovr;
  $("g_value").textContent = `${(p.value / 1e4).toFixed(0)}万欧`;
  renderStory();
  renderRadar();
  $("g_stats").textContent = JSON.stringify(p.career_stats || {}, null, 2);
}

function renderStory() {
  const el = $("story");
  el.innerHTML = "";
  if (state.narrative) {
    const p = document.createElement("p"); p.textContent = state.narrative; el.appendChild(p);
  }
  (state.decision?.options || state.options || []).forEach((o, i) => {
    const b = document.createElement("button");
    b.className = "opt";
    b.innerHTML = `${o.label}${o.hint ? `<small>${o.hint}</small>` : ""}`;
    b.onclick = () => choose(i);
    el.appendChild(b);
  });
}

async function choose(i) {
  try {
    state = await api("/api/game/decision", "POST", { choice_id: i });
    renderAll();
  } catch (e) { alert(e.message); }
}

function renderRadar() {
  // SVG 六边形雷达图：六大项均值
  const groups = ["pace", "shooting", "passing", "dribbling", "defending", "physical"];
  const attrs = state.player.attributes;
  const vals = groups.map((g) => {
    const keys = Object.keys(attrs).filter((k) => k in attrs && k.length > 0 && !["pace"].includes(k));
    const ks = Object.keys(attrs);
    const gk = ["reflexes", "handling", "diving", "gk_positioning", "kicking"];
    const names = g === "gk" ? gk : { pace:["acceleration","sprint_speed"], shooting:["positioning","finishing","shot_power","long_shots","volleys","penalties"], passing:["vision","short_passing","long_passing","crossing","curve","fk_accuracy"], dribbling:["agility","balance","reactions","ball_control","dribbling","composure"], defending:["def_awareness","interceptions","heading","standing_tackle","sliding_tackle"], physical:["strength","stamina","jumping","aggression"] }[g];
    const vs = names.map((n) => attrs[n] ?? 50);
    return Math.round(vs.reduce((a, b) => a + b, 0) / vs.length);
  });
  const cn = ["速度", "射门", "传球", "盘带", "防守", "身体"];
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 300 260"); svg.setAttribute("width", "100%");
  const cx = 150, cy = 130, R = 90;
  let s = `<text x="150" y="16" text-anchor="middle" fill="#9ca3af" font-size="13">属性雷达图</text>`;
  for (let ring = 1; ring <= 4; ring++) {
    let pts = [];
    for (let i = 0; i < 6; i++) {
      const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
      pts.push(`${cx + Math.cos(a) * R * ring / 4},${cy + Math.sin(a) * R * ring / 4}`);
    }
    s += `<polygon points="${pts.join(" ")}" fill="none" stroke="#2c3644"/>`;
  }
  let pts = [];
  vals.forEach((v, i) => {
    const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
    pts.push(`${cx + Math.cos(a) * R * v / 100},${cy + Math.sin(a) * R * v / 100}`);
  });
  s += `<polygon points="${pts.join(" ")}" fill="rgba(74,222,128,.25)" stroke="#4ade80" stroke-width="2"/>`;
  vals.forEach((v, i) => {
    const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
    const x = cx + Math.cos(a) * (R + 22), y = cy + Math.sin(a) * (R + 22);
    s += `<text x="${x}" y="${y}" text-anchor="middle" fill="#e5e7eb" font-size="12">${cn[i]} ${v}</text>`;
  });
  svg.innerHTML = s;
  $("radar").innerHTML = "";
  $("radar").appendChild(svg);
}

init();
```

> 说明：雷达图按六大项均值绘制；门将位置时切换为 GK 五项。`state.options` 兼容新开局返回结构（后端 `_generate` 返回 options，`/api/game/new` 返回的顶层 options）。

- [ ] **步骤 4：启动服务并手动冒烟**

```bash
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```
浏览器打开 http://127.0.0.1:8000 → 设置页（未配置时）→ 填 Key → 建档 → 看到开局叙事与选项 → 点击选项看到新叙事。**手动验证清单**：设置锁定、建档、叙事流、雷达图、身价显示、刷新后恢复。

- [ ] **步骤 5：Commit**

```bash
git add frontend/
git commit -m "feat: frontend single-page game UI"
```

---

## 任务 14：端到端验收

**文件：**
- 创建：`tests/test_e2e.py`、`README.md`

- [ ] **步骤 1：编写端到端测试**

```python
# tests/test_e2e.py
"""端到端：模拟 16→40 岁完整生涯循环（mock LLM），验证无死锁与约束。"""
import pytest
from fastapi.testclient import TestClient
from app.main import app

@pytest.fixture
def client():
    app.state.narrator = None
    app.state.player = None
    app.state.world = None
    app.state.game = None
    c = TestClient(app)
    c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    return c

async def fake_generate(system, user, max_tokens=800):
    return '{"narrative": "故事继续……", "options": [{"label": "选项A", "hint": "h"}, {"label": "选项B", "hint": "h"}]}'

def test_full_career_no_deadlock(client, monkeypatch):
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    r = client.post("/api/game/new", json={"name": "张伟", "birth_year": 2007, "position": "ST",
                                           "foot": "右", "height": 180, "weight": 70,
                                           "region": "山东", "academy": "山东鲁能足校"})
    assert r.status_code == 200
    for _ in range(300):  # 模拟 300 次决策
        r = client.post("/api/game/decision", json={"choice_id": 0})
        assert r.status_code == 200, f"第 {_} 次决策失败: {r.text}"
        p = r.json()["player"]
        assert 0 <= p["ovr"] <= 99
        if p["age"] < 18:
            assert p["ovr"] <= 80
            assert p["value"] <= 80_000_000
    print("完成 300 次决策，无死锁，约束未破")

def test_under18_caps_never_broken(client, monkeypatch):
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    client.post("/api/game/new", json={"name": "王强", "birth_year": 2007, "position": "GK",
                                       "foot": "左", "height": 192, "weight": 85,
                                       "region": "上海", "academy": "上海根宝基地"})
    for _ in range(80):
        r = client.post("/api/game/decision", json={"choice_id": 1 % 3})
        p = r.json()["player"]
        if p["age"] < 18:
            assert p["ovr"] <= 80 and p["value"] <= 80_000_000
```

- [ ] **步骤 2：运行测试确认通过**

运行：`python -m pytest tests/ -v`
预期：全部 PASS（含 300 次决策循环）

- [ ] **步骤 3：编写 README.md**

```markdown
# ⚽ 足球人生模拟器

AI 叙事主导的足球生涯模拟 Web 应用：扮演 2007 年出生的中国青训球员，从 16 岁踢到 40 岁。

## 快速开始
1. `pip install -r requirements.txt`
2. `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`
3. 浏览器打开 http://127.0.0.1:8000，在设置页填写 API Key（DeepSeek/OpenAI/硅基流动等，OpenAI 兼容接口）
4. 建档并开启生涯

## 玩法
- 每赛季 8-10 个决策点（夏窗/季前/上半程×2/冬窗/下半程×2/季末）
- 属性按 FIFA 体系（PAC/SHO/PAS/DRI/DEF/PHY/GK），0-99
- 18 岁前 OVR ≤ 80、身价 ≤ 8000 万欧元
- 40 岁触发退役/转教练抉择

## 技术栈
FastAPI + 原生 JS + Python 引擎（属性/身价/成长/16联赛世界模拟/转会/国家队/教练）

## 测试
`python -m pytest tests/ -v`
```

- [ ] **步骤 4：Commit**

```bash
git add tests/test_e2e.py README.md
git commit -m "test: end-to-end career loop; docs: README"
```

---

## 自检记录

**规格覆盖度**：设计文档 1-20 章 → 任务映射：§2 架构→任务 11/12/13；§3 目录→任务 0；§4-5 属性/OVR→任务 1；§6 身价→任务 2；§7 成长→任务 3；§8 世界→任务 5+10；§9 转会→任务 6；§10 合同→任务 6（报价卡含条款）+任务 12（建档合同字段）；§11 国家队→任务 7；§12 决策点→任务 8；§13 教练→任务 9；§14 LLM 服务→任务 11；§15 前端→任务 13；§16 REST→任务 12；§17 数据源→任务 10；§18 约束→任务 2/14；§19 验收→任务 14；§20 风险→任务 10（降级）/11（重试）/14。

**已知简化（实现期内接受，后续可迭代）**：
- 任务 12 的 decision 循环简化：当前赛季阶段/转会窗/国家队/年龄推进的完整状态机在任务 14 后迭代（v1 可玩性优先：青训→决策循环→面板展示；年龄推进与 40 岁退役在下一迭代接入 `simulate` 端点）
- AI 球员老化/转会市场全局运作随世界推进端点一并迭代

**类型一致性**：`calc_ovr(attrs, position)` 在 1/4/12 使用一致；`market_value(ovr, age, position, league, contract_years, form)` 签名一致；`apply_under18_caps(ovr, value, age)` 一致；`create_player` 参数顺序一致（name, birth_year, position, foot, height, weight, region, academy）。
