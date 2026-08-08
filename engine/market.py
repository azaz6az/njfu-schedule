"""转会市场：AI 报价生成、报价卡、身价更新。

设计文档 §9：报价 = 身价 × (0.8~1.5)，年轻/高潜溢价；玩家相关生成
"报价卡"（转会费/周薪/年限/解约金/签字费/定位承诺）。
"""
import random

try:
    from engine.value import market_value  # 会话 A（engine/value.py）合入后使用正式实现
except ImportError:  # pragma: no cover - 仅会话 A 未合入时生效
    # ⚠️ 临时降级：与计划任务 2 的公式一致，合并时以会话 A 版本为准
    def market_value(ovr, age, position="CM", league="epl", contract_years=3, form=1.0,
                     potential_premium=1.0) -> int:
        def _age_factor(a):
            if a <= 16: return 0.6
            if a == 17: return 0.7
            if a == 18: return 0.85
            if 19 <= a <= 23: return 1.0
            if 24 <= a <= 28: return 1.1
            if 29 <= a <= 32: return 0.85
            return 0.6

        pos_factor = {"ST": 1.3, "LW": 1.2, "RW": 1.2, "CAM": 1.15, "CM": 1.15,
                      "CDM": 1.0, "LB": 0.9, "RB": 0.9, "CB": 1.0, "GK": 0.85}
        league_factor = {"epl": 1.5, "laliga": 1.4, "bundesliga": 1.3, "seriea": 1.25,
                         "ligue1": 1.15, "eredivisie": 1.0, "primeira": 0.95, "cs": 0.7,
                         "csl2": 0.45, "efl": 0.8, "laliga2": 0.75, "serieb": 0.7,
                         "bundes2": 0.7, "ligue2": 0.65, "eerst": 0.6, "ligaporta": 0.6}
        contract_factor = 1.1 if contract_years >= 3 else 1.0 if contract_years == 2 \
            else 0.75 if contract_years == 1 else 0.55
        if ovr <= 85:
            base = 300_000 * (1.27 ** (ovr - 60))
        else:
            base = 119_000_000 * (1.16 ** (ovr - 85)) * 0.7
        return int(base * _age_factor(age) * pos_factor.get(position, 1.0)
                   * league_factor.get(league, 1.0) * contract_factor
                   * max(0.85, min(1.2, form)) * potential_premium)


def OfferCard(club, league, fee, weekly_wage, years, release_clause, signing_bonus, role):
    return {"club": club, "league": league, "fee": fee, "weekly_wage": weekly_wage,
            "years": years, "release_clause": release_clause,
            "signing_bonus": signing_bonus, "role": role}


# 买家池：五大联赛 + 中超豪门（计划任务 6）
LEAGUE_POOL = {
    "cs": ["上海海港", "山东泰山", "成都蓉城", "北京国安", "上海申花"],
    "epl": ["曼城", "阿森纳", "利物浦", "曼联", "切尔西"],
    "laliga": ["皇家马德里", "巴塞罗那", "马德里竞技"],
    "seriea": ["国际米兰", "AC米兰", "尤文图斯"],
    "bundesliga": ["拜仁慕尼黑", "勒沃库森", "多特蒙德"],
    "ligue1": ["巴黎圣日耳曼", "摩纳哥", "马赛"],
}


def generate_offers(player: dict, window: str = "summer", rng_seed: int = 0) -> list:
    """按球员 OVR/年龄/身价生成 0-3 张报价卡。

    - ovr < 68（或 <16 岁）无报价
    - OVR ≥ 85 → 2-3 张；78-84 → 1-2 张；68-77 → 1 张
    - fee = 身价 × (0.8~1.5) × (21 岁以下 ×1.3 年轻溢价)
    """
    rng = random.Random(rng_seed)
    if player["age"] < 16 or player["ovr"] < 68:
        return []
    if player["ovr"] >= 85:
        n = rng.randint(2, 3)
    elif player["ovr"] >= 78:
        n = rng.randint(1, 2)
    else:
        n = 1
    pools = [code for code in LEAGUE_POOL if code != player.get("league")]
    if not pools:
        pools = list(LEAGUE_POOL)
    cards = []
    for _ in range(n):
        code = rng.choice(pools)
        club = rng.choice(LEAGUE_POOL[code])
        young_premium = 1.3 if player["age"] < 21 else 1.0
        fee = int(player["value"] * rng.uniform(0.8, 1.5) * young_premium)
        weekly_wage = int(fee / 5200) + rng.randint(0, 5000)  # 周薪 ≈ 转会费/年52周/100
        cards.append(OfferCard(
            club=club, league=code, fee=fee, weekly_wage=weekly_wage,
            years=rng.randint(3, 5), release_clause=int(fee * rng.uniform(1.5, 2.5)),
            signing_bonus=int(fee * 0.05), role=rng.choice(["主力", "轮换", "重点培养"])))
    return cards


def update_player_value(ovr, age, position, league, contract_years, form=1.0) -> int:
    """委托 engine.value.market_value 结算身价。"""
    return int(market_value(ovr, age, position, league, contract_years, form))
