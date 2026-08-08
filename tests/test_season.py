"""赛季状态机测试。"""
import pytest

from engine.season import (SEASON_STAGES, advance_stage, next_skeleton,
                           settle_season)
from engine.player import create_player
from engine.world import build_world


@pytest.fixture
def player():
    p = create_player("张伟", 2007, "ST", "右", 180, 70, "山东", "山东鲁能足校")
    p["league"] = "cs"
    return p


@pytest.fixture
def game():
    return {"season": 2023, "stage": "上半程", "stage_index": 2,
            "skeleton_index": 0, "narrative_history": [], "retirement_offered": False}


@pytest.fixture
def world():
    return build_world("data")


def test_season_stages_eight_points():
    assert SEASON_STAGES == ["夏窗", "季前备战", "上半程", "上半程",
                             "冬窗", "下半程", "下半程", "赛季末"]


def test_advance_stage_cycles_to_new_season():
    game = {"season": 2023, "stage_index": len(SEASON_STAGES) - 1}
    advance_stage(game)
    assert game["season"] == 2024
    assert game["stage_index"] == 0
    assert game["stage"] == "夏窗"


def test_settle_season_ages_up(player, game, world):
    summary = settle_season(player, game, world, rng_seed=1)
    assert player["age"] == 17
    assert "2023" in player["career_stats"]
    assert summary["stats"]["apps"] > 0
    assert summary["text"]


def test_settle_growth_young_player(player, game, world):
    ovr_before = player["ovr"]
    settle_season(player, game, world, rng_seed=2)
    assert player["ovr"] >= ovr_before  # 16→17 成长


def test_settle_world_simulated(player, game, world):
    settle_season(player, game, world, rng_seed=3)
    epl = world["epl"]
    assert epl.rounds_played > 0
    assert any(t["played"] > 0 for t in epl.table)


def test_next_skeleton_transfer_window(player, game, world):
    game["stage"] = "夏窗"
    sk = next_skeleton(player, game, world, rng_seed=4)
    assert sk["type"] == "转会窗口"
    assert sk["transfer_window"] is True
    assert len(sk["options"]) >= 1
    # 最后一个选项永远是拒绝留队
    assert "拒绝报价" in sk["options"][-1]["label"]


def test_next_skeleton_training(player, game, world):
    game["stage"] = "季前备战"
    sk = next_skeleton(player, game, world, rng_seed=5)
    assert sk["type"] == "训练加练"
    assert len(sk["options"]) >= 3
    assert all("effects" in o for o in sk["options"])


def test_retirement_trigger_at_40(player, game, world):
    player["age"] = 39
    game["stage"] = "赛季末"
    sk = next_skeleton(player, game, world, rng_seed=6)
    assert player["age"] == 40  # 赛季结算已推进年龄
    assert sk["retirement_decision"] is True
    assert sk["type"] == "生涯抉择"
    labels = [o["label"] for o in sk["options"]]
    assert any("退役" in l for l in labels)
    assert any("教练" in l for l in labels)
    assert any("继续" in l for l in labels)
    assert game["retirement_offered"] is True


def test_no_retirement_before_40(player, game, world):
    game["stage"] = "赛季末"
    sk = next_skeleton(player, game, world, rng_seed=7)
    assert sk["type"] == "赛季总结"
    assert not sk.get("retirement_decision")
