"""引擎 · 身价模型测试。"""
import pytest
from engine.value import base_value, market_value, apply_under18_caps


def test_base_value_table_points():
    """基础身价曲线关键点（量级校验）。"""
    assert base_value(60) == pytest.approx(300_000, rel=0.2)
    assert base_value(75) == pytest.approx(11_000_000, rel=0.3)
    assert base_value(85) == pytest.approx(120_000_000, rel=0.3)


def test_base_value_monotonic():
    """两段曲线各自单调不减。

    设计文档 6.1 分段公式：OVR≤85 用 1.27 指数，>85 切换 1.16 指数×0.7
    （斜率放缓），分段点处允许回落——故只断言段内单调。
    """
    low = [base_value(o) for o in range(55, 86)]
    high = [base_value(o) for o in range(86, 100)]
    assert low == sorted(low)
    assert high == sorted(high)


def test_market_value_multipliers():
    """位置/联赛/合同/状态系数放大差距。"""
    v1 = market_value(ovr=80, age=24, position="ST", league="epl", contract_years=3, form=1.0)
    v2 = market_value(ovr=80, age=24, position="CB", league="cs", contract_years=1, form=0.9)
    assert v1 > v2 * 1.5


def test_under18_cap_value():
    """18 岁前 OVR≤80、身价≤8000 万；18 岁不再封顶。"""
    assert apply_under18_caps(81, 90_000_000, age=17) == (80, 80_000_000)
    assert apply_under18_caps(78, 50_000_000, age=17) == (78, 50_000_000)
    assert apply_under18_caps(82, 85_000_000, age=18) == (82, 85_000_000)


def test_age_curve_peak():
    """年龄曲线巅峰期（24-28）身价高于青年期。"""
    v_young = market_value(75, 17, "ST", "epl", 3, 1.0)
    v_peak = market_value(75, 26, "ST", "epl", 3, 1.0)
    assert v_peak > v_young
