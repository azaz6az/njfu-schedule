"""决策骨架测试：事件库合法性、赛季骨架、防篡改深拷贝、可复现、上下文过滤（v2）。"""
import pytest
from engine.decision import (EVENT_LIBRARY, POSITION_TRANSFORMS, season_skeleton,
                             next_decision, _eligible_events,
                             _injury_skeleton, _injury_recovery_skeleton,
                             _position_change_skeleton)

EFFECT_KEYS = {"attr_delta", "morale", "form", "injury_risk", "reputation", "value_factor",
               "transfer", "retire", "position_change", "get_injured", "recover"}
DYNAMIC_TYPES = {"位置转型", "伤病来袭", "伤病复出"}  # 动态生成，不在静态事件库


def test_season_skeleton_8_nodes():
    sk = season_skeleton(season=2024)
    assert len(sk) == 8
    assert [s["stage"] for s in sk] == [
        "夏窗", "季前备战", "上半程", "上半程", "冬窗", "下半程", "下半程", "赛季末"]
    assert all(s["season"] == 2024 for s in sk)
    assert sk[0]["type"] == "转会窗口"
    assert sk[4]["type"] == "转会窗口"
    assert sk[7]["type"] == "赛季总结"


def test_event_library_events_and_min_options():
    # 契约：常规事件池 6 类（训练/伤病/转型由专门生成器处理），每类 ≥3 个选项
    assert set(EVENT_LIBRARY.keys()) == {
        "比赛单刀", "更衣室矛盾", "媒体采访", "新型训练法", "国家队竞争", "门将出击"}
    for ev in EVENT_LIBRARY.values():
        assert len(ev["options"]) >= 3
        for opt in ev["options"]:
            assert {"label", "hint", "effects"} <= set(opt.keys())


def test_decision_effects_are_valid():
    for ev in EVENT_LIBRARY.values():
        for opt in ev["options"]:
            eff = opt["effects"]
            assert set(eff.keys()) <= EFFECT_KEYS
            assert eff, "效果不得为空"
            if "attr_delta" in eff:
                for v in eff["attr_delta"].values():
                    assert -5 <= v <= 5


def test_next_decision_returns_skeleton():
    p = {"age": 16, "position": "ST", "ovr": 65, "injured": False, "injury_risk": 0.0}
    d = next_decision(p, season=2023, stage="上半程", rng_seed=1)
    assert set(d.keys()) == {"season", "stage", "type", "narrative_hook", "options"}
    assert d["season"] == 2023
    assert d["stage"] == "上半程"
    assert d["type"] in EVENT_LIBRARY or d["type"] in DYNAMIC_TYPES
    assert d["narrative_hook"]
    assert len(d["options"]) >= 3
    for o in d["options"]:
        assert set(o["effects"].keys()) <= EFFECT_KEYS


def test_next_decision_reproducible_with_seed():
    p = {"age": 16, "position": "ST", "ovr": 65, "injured": False, "injury_risk": 0.0}
    a = next_decision(p, season=2023, stage="上半程", rng_seed=42)
    b = next_decision(p, season=2023, stage="上半程", rng_seed=42)
    assert a == b


def test_next_decision_deep_copies_options():
    # 防幻觉核心：调用方篡改返回选项不得污染事件库
    p = {"age": 16, "position": "ST", "ovr": 65, "injured": False, "injury_risk": 0.0}
    d = next_decision(p, season=2023, stage="上半程", rng_seed=2)
    if d["type"] in EVENT_LIBRARY:
        d["options"][0]["label"] = "被篡改"
        d["options"][0]["effects"]["morale"] = 999
        d["options"][0]["effects"]["attr_delta"] = {"finishing": 99}
        orig = EVENT_LIBRARY[d["type"]]["options"][0]
        assert orig["label"] != "被篡改"
        assert orig["effects"]["morale"] != 999
        assert orig["effects"]["attr_delta"] != {"finishing": 99}


# ---- v2 上下文过滤 ----

def test_gk_never_gets_striker_events():
    """门将不会遇到进攻位事件（比赛单刀），且能遇到门将专属事件。"""
    gk = {"age": 17, "position": "GK", "ovr": 66, "injured": False, "injury_risk": 0.0}
    pool = _eligible_events(gk)
    assert "比赛单刀" not in pool
    assert "门将出击" in pool
    st = {"age": 17, "position": "ST", "ovr": 66, "injured": False, "injury_risk": 0.0}
    pool_st = _eligible_events(st)
    assert "比赛单刀" in pool_st
    assert "门将出击" not in pool_st


def test_no_consecutive_duplicates():
    """传入 last_type 后，下一事件不会与上一事件相同。"""
    p = {"age": 17, "position": "ST", "ovr": 66, "injured": False, "injury_risk": 0.0}
    for seed in range(20):
        d = next_decision(p, 2023, "上半程", rng_seed=seed, last_type="媒体采访")
        assert d["type"] != "媒体采访"


def test_injured_forces_recovery_decision():
    """受伤状态下强制生成复出决策。"""
    p = {"age": 18, "position": "ST", "ovr": 70, "injured": True, "injury_risk": 0.4}
    d = next_decision(p, 2024, "上半程", rng_seed=5)
    assert d["type"] == "伤病复出"
    assert any(o["effects"].get("recover") for o in d["options"])
    assert all("get_injured" not in o["effects"] for o in d["options"])


def test_high_risk_may_trigger_injury():
    """injury_risk 高时可能触发受伤事件。"""
    p = {"age": 18, "position": "ST", "ovr": 70, "injured": False, "injury_risk": 0.5}
    found_injury = False
    for seed in range(30):
        d = next_decision(p, 2024, "上半程", rng_seed=seed)
        if d["type"] == "伤病来袭":
            found_injury = True
            assert any(o["effects"].get("get_injured") for o in d["options"])
            break
    assert found_injury, "高伤病风险下 30 次内应至少出现一次受伤事件"


def test_low_risk_never_injured():
    """injury_risk 低时不会触发受伤事件。"""
    p = {"age": 18, "position": "ST", "ovr": 70, "injured": False, "injury_risk": 0.0}
    for seed in range(30):
        d = next_decision(p, 2024, "上半程", rng_seed=seed)
        assert d["type"] != "伤病来袭"


def test_position_change_target_is_legal():
    """位置转型目标必须在引擎预定义映射内。"""
    import random
    for pos, targets in POSITION_TRANSFORMS.items():
        rng = random.Random(1)
        sk = _position_change_skeleton({"position": pos}, 2024, "上半程", rng)
        if sk is None:
            continue  # GK 无可转型目标
        for o in sk["options"]:
            pc = o["effects"].get("position_change")
            if pc:
                assert pc in targets, f"{pos} 转型目标 {pc} 不在 {targets}"


def test_gk_no_position_change():
    """门将不会收到位置转型事件。"""
    p = {"age": 18, "position": "GK", "ovr": 70, "injured": False, "injury_risk": 0.0}
    for seed in range(30):
        d = next_decision(p, 2024, "上半程", rng_seed=seed)
        assert d["type"] != "位置转型"


def test_training_options_filtered_by_position():
    """门将的季前训练选项不含射门/盘带训练。"""
    from engine.season import _training_skeleton
    gk = {"position": "GK"}
    sk = _training_skeleton(gk, {"season": 2024, "stage": "季前备战"})
    assert len(sk["options"]) == 3
    labels = " ".join(o["label"] for o in sk["options"])
    assert "射门" not in labels and "盘带" not in labels
    assert "扑救" in labels
    st = {"position": "ST"}
    sk_st = _training_skeleton(st, {"season": 2024, "stage": "季前备战"})
    assert any("射门" in o["label"] for o in sk_st["options"])
