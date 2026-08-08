"""教练生涯：教练属性、经验增长、赛季战绩。

战绩公式（契约语义：战绩≈球队实力+战术/激励加成+随机）：
    实力分 = (球队 OVR - 联赛均值) + 战术加成 + 激励加成
    排名 = 中游位置 - 实力分 × 联赛规模系数 + 随机噪声，钳制在 [1, n]
注：计划草稿的 n*1.15 - OVR 量纲退化（60+ OVR 球队恒夺冠、噪声永不生效），
本实现按契约语义重新标定；均值和队数按 16 联赛表配置。
"""
import random

COACH_ATTRS = ["tactical", "motivation", "training", "negotiation", "youth", "transfer"]

LEAGUE_N_TEAMS = {"cs": 16, "csl2": 16, "epl": 20, "efl": 24, "laliga": 20, "laliga2": 22,
                  "seriea": 20, "serieb": 20, "bundesliga": 18, "bundes2": 18,
                  "ligue1": 18, "ligue2": 20, "eredivisie": 18, "eerst": 20,
                  "primeira": 18, "ligaporta": 18}

LEAGUE_BASE_OVR = {"cs": 72, "csl2": 64, "epl": 78, "efl": 71, "laliga": 78, "laliga2": 71,
                   "seriea": 76, "serieb": 70, "bundesliga": 77, "bundes2": 70,
                   "ligue1": 75, "ligue2": 69, "eredivisie": 72, "eerst": 66,
                   "primeira": 73, "ligaporta": 66}


def new_coach(name: str, seed: int = 0) -> dict:
    """新教练：六项属性 55-75 随机（固定种子可复现）。"""
    rng = random.Random(seed)
    return {a: rng.randint(55, 75) for a in COACH_ATTRS}


def grow_coach(c: dict) -> dict:
    """每年经验增长：每项 +1~3，上限 99。"""
    out = dict(c)
    for a in COACH_ATTRS:
        out[a] = min(99, out[a] + random.randint(1, 3))
    return out


def season_result(coach: dict, club_ovr: int, league_code: str, rng_seed: int = 0) -> dict:
    """结算一赛季战绩。返回 {position, n_teams, success, relegated}。"""
    rng = random.Random(rng_seed)
    n = LEAGUE_N_TEAMS.get(league_code, 18)
    base = LEAGUE_BASE_OVR.get(league_code, 72)
    boost = (coach["tactical"] - 60) / 10 + (coach["motivation"] - 60) / 15
    score = (club_ovr - base) + boost
    pos = int(round(n / 2 + 1 - score * (n / 10) + rng.uniform(-1.5, 1.5)))
    pos = max(1, min(n, pos))
    return {"position": pos, "n_teams": n,
            "success": pos <= round(n * 0.35), "relegated": pos > round(n * 0.9)}
