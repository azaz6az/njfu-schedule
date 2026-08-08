"""引擎 · 成长/衰退与决策结算测试。"""
import pytest
from engine.development import (
    growth_points, allocate_growth, apply_decision_effects,
    age_player_attributes,
)


def test_growth_curve_by_age():
    """成长曲线分段：年轻增长多，横盘，老将衰退。"""
    assert 8 <= growth_points(17) <= 12
    assert 5 <= growth_points(20) <= 8
    assert 3 <= growth_points(23) <= 5
    assert 1 <= growth_points(27) <= 2
    assert growth_points(30) == 0
    assert growth_points(33) < 0


def test_allocate_growth_favors_core_position():
    """成长点按位置权重分配：核心属性多于非核心，总和=growth。"""
    deltas = allocate_growth(growth=10, position="CB")
    assert deltas["def_awareness"] > deltas["finishing"]
    assert sum(deltas.values()) == 10


def test_allocate_growth_total_matches():
    """各位置成长分配总和精确等于 growth 且无负增量。"""
    for pos in ("ST", "CM", "GK", "CB"):
        deltas = allocate_growth(growth=7, position=pos)
        assert sum(deltas.values()) == 7
        assert all(v >= 0 for v in deltas.values())


def test_decision_effects_apply():
    """决策效果：只改动涉及的属性，未涉及属性不变。"""
    attrs = {"acceleration": 70, "sprint_speed": 70, "stamina": 60}
    effects = {"attr_delta": {"acceleration": 3}, "morale": -5}
    out = apply_decision_effects(attrs, effects)
    assert out["acceleration"] == 73
    assert out["stamina"] == 60


def test_decision_effects_clamped():
    """决策结算不越界：0-99 钳制。"""
    attrs = {"finishing": 98, "strength": 2}
    effects = {"attr_delta": {"finishing": 5, "strength": -5}}
    out = apply_decision_effects(attrs, effects)
    assert out["finishing"] == 99
    assert out["strength"] == 0


def test_age_decline_hits_speed_first():
    """34 岁衰退：速度先退，站位/射术/沉着保值。"""
    attrs = {"acceleration": 80, "sprint_speed": 80, "stamina": 80,
             "positioning": 80, "finishing": 80, "composure": 80}
    out = age_player_attributes(attrs, age=34)
    assert out["acceleration"] < 80
    assert out["positioning"] >= 80
    assert out["finishing"] >= 80


def test_decline_at_32():
    """32 岁开始衰退。"""
    attrs = {"acceleration": 80, "sprint_speed": 80, "stamina": 80,
             "positioning": 80, "finishing": 80, "composure": 80}
    out = age_player_attributes(attrs, age=32)
    assert out["acceleration"] < 80
