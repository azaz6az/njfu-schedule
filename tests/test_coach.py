"""教练生涯测试：属性生成、经验成长、赛季战绩语义。"""
import pytest
from engine.coach import COACH_ATTRS, new_coach, grow_coach, season_result


def test_new_coach_attributes():
    c = new_coach(name="张伟", seed=1)
    assert set(c.keys()) == set(COACH_ATTRS)
    assert all(55 <= v <= 75 for v in c.values())


def test_new_coach_deterministic_with_seed():
    assert new_coach("张伟", seed=1) == new_coach("张伟", seed=1)


def test_grow_coach_increases_each_attr():
    c = new_coach(name="张伟", seed=1)
    c2 = grow_coach(c)
    assert sum(c2.values()) > sum(c.values())
    for a in COACH_ATTRS:
        assert c2[a] - c[a] in (1, 2, 3)  # 每项每年 +1~3


def test_grow_coach_caps_at_99():
    capped = {a: 99 for a in COACH_ATTRS}
    assert grow_coach(capped) == capped


def test_season_result_bounds_and_flags():
    c = new_coach(name="张伟", seed=2)
    r = season_result(c, club_ovr=75, league_code="cs", rng_seed=3)
    assert 1 <= r["position"] <= 16
    assert r["n_teams"] == 16
    assert r["success"] == (r["position"] <= round(16 * 0.35))
    assert r["relegated"] == (r["position"] > round(16 * 0.9))
    assert r["success"] != r["relegated"]  # 不可能既成功又降级


def test_season_result_league_sizes():
    assert season_result(new_coach("x"), 70, "epl", 1)["n_teams"] == 20
    assert season_result(new_coach("x"), 70, "bundesliga", 1)["n_teams"] == 18
    assert season_result(new_coach("x"), 70, "efl", 1)["n_teams"] == 24
    assert season_result(new_coach("x"), 70, "csl2", 1)["n_teams"] == 16


def test_season_result_deterministic():
    c = new_coach(name="张伟", seed=1)
    assert season_result(c, 75, "cs", rng_seed=9) == season_result(c, 75, "cs", rng_seed=9)


def test_stronger_club_finishes_higher():
    c = new_coach(name="张伟", seed=1)
    strong = season_result(c, club_ovr=80, league_code="cs", rng_seed=5)
    weak = season_result(c, club_ovr=62, league_code="cs", rng_seed=5)
    assert strong["position"] < weak["position"]


def test_better_coach_finishes_higher():
    good = {a: 90 for a in COACH_ATTRS}
    bad = {a: 40 for a in COACH_ATTRS}
    r1 = season_result(good, 70, "cs", rng_seed=7)
    r2 = season_result(bad, 70, "cs", rng_seed=7)
    assert r1["position"] < r2["position"]


def test_strong_club_success_and_weak_club_relegated():
    avg = {a: 60 for a in COACH_ATTRS}
    top = season_result(avg, 78, "cs", rng_seed=4)
    assert top["success"] is True
    bottom = season_result(avg, 62, "cs", rng_seed=4)
    assert bottom["relegated"] is True


def test_randomness_across_seeds():
    c = new_coach(name="张伟", seed=1)
    positions = {season_result(c, 70, "cs", rng_seed=s)["position"] for s in range(10)}
    assert len(positions) >= 3  # 随机噪声确实影响排名
