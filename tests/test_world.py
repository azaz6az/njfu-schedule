import random

import pytest
from engine.world import League, play_match, simulate_season, build_world, load_clubs


def test_league_table_total_matches():
    world = build_world(data_dir="data")
    for league in world.values():
        simulate_season(league, rng_seed=0)
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
    assert all(
        t["points"] <= max(t["points"] for t in league.table if t not in out["relegated"])
        for t in out["relegated"]
    )


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


def test_play_match_poisson():
    total_home = sum(play_match(80, 75, random.Random(i))[0] for i in range(200))
    total_away = sum(play_match(80, 75, random.Random(i))[1] for i in range(200))
    # 主强客弱：主队平均进球应明显多于客队
    assert total_home > total_away
