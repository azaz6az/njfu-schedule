"""世界模拟：联赛、比赛（泊松）、积分榜、升降级。

设计文档 §8：16 联赛 306 队；λ主=1.35×(主/客)^0.85，λ客=1.05×反向；
固定随机种子保证可复现；单赛季全联赛模拟 <1 秒。
"""
import csv
import math
import random


class League:
    """一个联赛：球队、积分榜、赛程。"""

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
        """主客场双循环赛程。"""
        ids = [t["team_id"] for t in self.teams]
        fixtures = []
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                fixtures.append((ids[i], ids[j]))
        return fixtures * 2

    def find(self, team_id):
        return next(t for t in self.table if t["team_id"] == team_id)


def _poisson(lambda_: float, rng: random.Random) -> int:
    """Knuth 泊松抽样（random.Random 无内置泊松）。"""
    threshold = math.exp(-lambda_)
    k, p = 0, 1.0
    while True:
        k += 1
        p *= rng.random()
        if p <= threshold:
            return k - 1


def play_match(home_ovr, away_ovr, rng):
    """模拟一场比赛，返回 (主队进球, 客队进球)。

    λ主 = 1.35 × (主/客)^0.85，λ客 = 1.05 × (客/主)^0.85。
    """
    ratio_h = (home_ovr / away_ovr) ** 0.85
    lam_h = 1.35 * ratio_h
    lam_a = 1.05 / ratio_h
    return _poisson(lam_h, rng), _poisson(lam_a, rng)


def simulate_season(league: League, rng_seed: int = 0) -> dict:
    """模拟整个赛季，返回 {"relegated": [...], "champion": {...}}，末 2 名降级。"""
    rng = random.Random(rng_seed)
    for home_id, away_id in league._fixtures:
        ht, at = league.find(home_id), league.find(away_id)
        gh, ga = play_match(ht["ovr"], at["ovr"], rng)
        for t, scored, conceded in ((ht, gh, ga), (at, ga, gh)):
            t["played"] += 1
            t["gf"] += scored
            t["ga"] += conceded
        if gh > ga:
            ht["wins"] += 1
            ht["points"] += 3
            at["losses"] += 1
        elif gh < ga:
            at["wins"] += 1
            at["points"] += 3
            ht["losses"] += 1
        else:
            ht["draws"] += 1
            at["draws"] += 1
            ht["points"] += 1
            at["points"] += 1
    league.rounds_played = len(league._fixtures)
    league.table.sort(key=lambda t: (-t["points"], t["ga"] - t["gf"], -t["gf"]))
    return {"relegated": league.table[-2:], "champion": league.table[0]}


def load_clubs(path: str) -> list:
    """读取 data/clubs.csv → [{league, league_name, team_id, team_name, ovr}]。"""
    with open(path, encoding="utf-8") as f:
        return [{"league": r["league"], "league_name": r["league_name"],
                 "team_id": r["team_id"], "team_name": r["team_name"], "ovr": int(r["ovr"])}
                for r in csv.DictReader(f)]


def build_world(data_dir: str = "data") -> dict:
    """按 clubs.csv 构建 {联赛code: League}，16 联赛 306 队。"""
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
