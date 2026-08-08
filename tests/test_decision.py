"""决策骨架测试：事件库合法性、赛季骨架、防篡改深拷贝、可复现。"""
import pytest
from engine.decision import EVENT_LIBRARY, season_skeleton, next_decision

EFFECT_KEYS = {"attr_delta", "morale", "form", "injury_risk", "reputation", "value_factor"}


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
    # 契约：8 类事件，每类 ≥3 个选项，含 label/hint/effects
    assert set(EVENT_LIBRARY.keys()) == {
        "比赛单刀", "训练加练", "更衣室矛盾", "媒体采访",
        "伤病复出", "新型训练法", "位置转型", "国家队竞争"}
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
    p = {"age": 16, "position": "ST"}
    d = next_decision(p, season=2023, stage="上半程", rng_seed=1)
    assert set(d.keys()) == {"season", "stage", "type", "narrative_hook", "options"}
    assert d["season"] == 2023
    assert d["stage"] == "上半程"
    assert d["type"] in EVENT_LIBRARY
    assert d["narrative_hook"]
    assert len(d["options"]) >= 3
    for o in d["options"]:
        assert set(o["effects"].keys()) <= EFFECT_KEYS


def test_next_decision_reproducible_with_seed():
    p = {"age": 16, "position": "ST"}
    a = next_decision(p, season=2023, stage="上半程", rng_seed=42)
    b = next_decision(p, season=2023, stage="上半程", rng_seed=42)
    assert a == b


def test_next_decision_deep_copies_options():
    # 防幻觉核心：调用方篡改返回选项不得污染事件库
    p = {"age": 16, "position": "ST"}
    d = next_decision(p, season=2023, stage="上半程", rng_seed=2)
    d["options"][0]["label"] = "被篡改"
    d["options"][0]["effects"]["morale"] = 999
    d["options"][0]["effects"]["attr_delta"] = {"finishing": 99}
    orig = EVENT_LIBRARY[d["type"]]["options"][0]
    assert orig["label"] != "被篡改"
    assert orig["effects"]["morale"] != 999
    assert orig["effects"]["attr_delta"] != {"finishing": 99}
