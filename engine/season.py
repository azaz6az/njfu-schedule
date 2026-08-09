"""赛季状态机：8 决策点流转、赛季结算（年龄/成长/世界/数据）、40 岁退役触发。

v1 简化说明（后续迭代扩展）：
- 转会窗口：报价卡 → 接受（改 club/league）/拒绝
- 赛季表现数据：按 OVR/状态/年龄随机生成（无逐场模拟）
- 升降级：仅记录顶级联赛升降级标签（不实际换队）
"""
import random

from engine.attributes import calc_ovr
from engine.decision import EVENT_LIBRARY, TRAINING_OPTIONS, next_decision
from engine.development import age_player_attributes, allocate_growth, growth_points
from engine.market import generate_offers
from engine.national import national_callup, next_level
from engine.value import apply_under18_caps, market_value
from engine.world import simulate_season

# 每赛季 8 个决策点（设计文档 §12.1）
SEASON_STAGES = ["夏窗", "季前备战", "上半程", "上半程", "冬窗", "下半程", "下半程", "赛季末"]
STAGE_LABEL = {"夏窗": "夏季转会窗", "季前备战": "季前备战", "上半程": "赛季上半程",
               "冬窗": "冬季转会窗", "下半程": "赛季下半程", "赛季末": "赛季总结"}

RETIREMENT_THRESHOLD = 40


def _transfer_skeleton(player: dict, game: dict, rng_seed: int) -> dict:
    """转会窗骨架：引擎生成报价卡 → 接受/拒绝选项（防幻觉：报价全部引擎生成）。"""
    offers = generate_offers(player, rng_seed=rng_seed)
    options = []
    for o in offers:
        options.append({
            "label": f"接受 {o['club']} 的报价",
            "hint": f"{o['fee'] // 10000}万欧 / 周薪{o['weekly_wage']}欧 / {o['years']}年",
            "effects": {"transfer": o, "morale": 3, "reputation": 2},
        })
    options.append({
        "label": "拒绝报价，留队继续",
        "hint": "等待更好的机会",
        "effects": {"morale": 1, "reputation": -1},
    })
    return {"season": game["season"], "stage": game["stage"], "type": "转会窗口",
            "narrative_hook": f"{STAGE_LABEL[game['stage']]}开启，你的表现引起了其他俱乐部的关注。"
                              f"{'你收到了以下报价。' if offers else '目前没有合适的报价。'}",
            "options": options, "transfer_window": True}


def _training_skeleton(player: dict, game: dict) -> dict:
    """季前备战：训练侧重（按位置过滤选项，门将使用专属训练项）。"""
    if player["position"] == "GK":
        options = [
            {"label": "加练反应与扑救", "hint": "近距离扑救更稳",
             "effects": {"attr_delta": {"reflexes": 2, "reactions": 1}, "morale": 1}},
            {"label": "加练开球与脚下", "hint": "大脚与手抛球更准",
             "effects": {"attr_delta": {"kicking": 2, "ball_control": 1}, "morale": 1}},
            {"label": "加练体能", "hint": "耐力提升，恢复",
             "effects": {"attr_delta": {"stamina": 2}, "morale": 1, "injury_risk": -0.05}},
        ]
    else:
        options = [dict(o) for o in TRAINING_OPTIONS]
    return {"season": game["season"], "stage": game["stage"], "type": "训练加练",
            "narrative_hook": "新赛季前的备战期，教练组让你选择训练重点。",
            "options": options}


def _season_end_skeleton(player: dict, game: dict, world: dict, rng_seed: int) -> dict:
    """赛季末：先结算赛季，再生成总结骨架；40 岁触发退役抉择。"""
    summary = settle_season(player, game, world, rng_seed)
    game["last_season_summary"] = summary
    if player["age"] >= RETIREMENT_THRESHOLD and not game.get("retirement_offered"):
        game["retirement_offered"] = True
        return {
            "season": game["season"], "stage": "赛季末", "type": "生涯抉择",
            "narrative_hook": (f"你已经 40 岁了。{summary['text']} "
                               f"这一刻终于到来——是时候决定你的未来了。"),
            "options": [
                {"label": "正式退役，结束球员生涯", "hint": "总结生涯荣誉",
                 "effects": {"retire": "retire", "morale": 0}},
                {"label": "退役并转型教练", "hint": "开启执教生涯",
                 "effects": {"retire": "coach", "morale": 0}},
                {"label": "继续征战一年", "hint": "老将再战",
                 "effects": {"retire": "continue", "morale": 2}},
            ],
            "retirement_decision": True,
        }
    return {
        "season": game["season"], "stage": "赛季末", "type": "赛季总结",
        "narrative_hook": (f"{game['season']} 赛季结束。{summary['text']}"),
        "options": [
            {"label": "开启新赛季", "hint": "进入下一年",
             "effects": {"morale": 1}},
        ],
    }


def next_skeleton(player: dict, game: dict, world: dict, rng_seed: int) -> dict:
    """按当前 stage 生成下一决策骨架。"""
    stage = game["stage"]
    if stage in ("夏窗", "冬窗"):
        return _transfer_skeleton(player, game, rng_seed)
    if stage == "季前备战":
        return _training_skeleton(player, game)
    if stage == "赛季末":
        return _season_end_skeleton(player, game, world, rng_seed)
    # 上半程/下半程事件：传入上一事件类型，防止连续重复
    last_type = game.get("current_decision", {}).get("type") if game.get("current_decision") else None
    return next_decision(player, game["season"], stage, rng_seed, last_type=last_type)


def advance_stage(game: dict):
    """决策完成后推进到下一决策点；赛季末之后进入新赛季夏窗。"""
    idx = game.get("stage_index", 0) + 1
    if idx >= len(SEASON_STAGES):
        game["season"] += 1
        game["stage_index"] = 0
    else:
        game["stage_index"] = idx
    game["stage"] = SEASON_STAGES[game["stage_index"]]


def _season_stats(player: dict, season: int, rng: random.Random) -> dict:
    """按 OVR/位置/状态生成赛季表现数据（v1 简化：无逐场模拟）。"""
    pos = player["position"]
    ovr = player["ovr"]
    age = player["age"]
    base = max(10, ovr - 40)
    if age <= 17:
        apps = rng.randint(3, 12)          # 青训期比赛少
    elif age >= 33:
        apps = rng.randint(10, 24)         # 老将轮换
    else:
        apps = rng.randint(22, 38)
    if pos == "GK":
        goals, assists, cs = 0, 0, rng.randint(3, 15)
    elif pos in ("ST", "LW", "RW", "CAM"):
        goals = int(base * rng.uniform(0.18, 0.42) * (apps / 38))
        assists = int(base * rng.uniform(0.08, 0.2) * (apps / 38))
        cs = 0
    else:
        goals = int(base * rng.uniform(0.02, 0.08) * (apps / 38))
        assists = int(base * rng.uniform(0.06, 0.18) * (apps / 38))
        cs = 0
    return {"season": season, "age": age, "apps": apps, "goals": goals,
            "assists": assists, "clean_sheets": cs, "club": player["club"]}


def settle_season(player: dict, game: dict, world: dict, rng_seed: int = 0) -> dict:
    """赛季末结算：世界模拟 → 玩家成长/年龄 → OVR/身价 → 赛季数据 → 国家队征召。"""
    rng = random.Random(rng_seed)
    # 1. 世界模拟（全部联赛，固定种子可复现）
    world_summary = {}
    for code, league in world.items():
        result = simulate_season(league, rng_seed=rng_seed + hash(code) % 97)
        world_summary[code] = {
            "champion": result["champion"]["team_name"],
            "relegated": [t["team_name"] for t in result["relegated"]],
        }
    # 2. 玩家年龄与成长
    age = player["age"] + 1
    player["age"] = age
    growth = growth_points(age)
    if growth > 0:
        deltas = allocate_growth(growth, player["position"])
        for attr, d in deltas.items():
            player["attributes"][attr] = max(0, min(99, player["attributes"][attr] + d))
    elif growth < 0:
        player["attributes"] = age_player_attributes(player["attributes"], age)
    # 3. OVR 与身价
    player["ovr"] = calc_ovr(player["attributes"], player["position"])
    league = player.get("league", "cs")
    contract_years = player.get("contract", {}).get("years", 0)
    player["value"] = market_value(player["ovr"], age, player["position"], league,
                                   contract_years, player.get("form", 1.0))
    player["ovr"], player["value"] = apply_under18_caps(player["ovr"], player["value"], age)
    # 4. 赛季表现数据
    stats = _season_stats(player, game["season"], rng)
    player["career_stats"][str(game["season"])] = stats
    # 5. 国家队征召判定
    level = next_level(player.get("national_level", ""), age)
    called = False
    if level:
        called = national_callup(age, player["ovr"], league, player.get("form", 1.0), level)
        player["national_level"] = level
        if called:
            key = level if level in player["national_stats"] else "senior"
            ns = player["national_stats"][key]
            ns["apps"] += rng.randint(2, 6)
            ns["goals"] += rng.randint(0, 2) if level != "u17" else rng.randint(0, 1)
    # 6. 里程碑与荣誉
    if stats["goals"] >= 15 and player["age"] <= 21:
        player["milestones"].append(f"{game['season']}赛季：年轻射手打进{stats['goals']}球")
    if world_summary.get(league, {}).get("champion") == player["club"]:
        player["honors"].append(f"{game['season']}赛季联赛冠军")
        player["milestones"].append(f"{game['season']}赛季随队夺得联赛冠军")
    text = (f"你以 {stats['apps']} 次出场、{stats['goals']} 球、{stats['assists']} 次助攻"
            f"结束本赛季（身价 {player['value'] // 10000} 万欧）。"
            + (f"你入选了国字号名单（{level.upper()}），" if called else ""))
    return {"text": text, "stats": stats, "world": world_summary, "called_up": called}
