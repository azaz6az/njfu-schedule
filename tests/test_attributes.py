"""引擎 · 属性系统与 OVR 计算测试。"""
import pytest
from engine.attributes import (
    FIELD_GROUPS, GK_ATTRIBUTES, HIDDEN_FIELDS, ALL_ATTRS,
    POSITION_WEIGHTS, POSITIONS, POSITION_LABEL,
    calc_ovr, ovr_for_position, template_attributes, expand_attributes,
    clamp_attr,
)


def test_clamp_attr_bounds():
    """属性钳制到 0-99 整数。"""
    assert clamp_attr(120) == 99
    assert clamp_attr(-5) == 0
    assert clamp_attr(99.4) == 99
    assert clamp_attr(99.6) == 99   # 四舍五入 100 → 钳制回 99
    assert clamp_attr(70.4) == 70   # 四舍五入
    assert clamp_attr(70.6) == 71


def test_calc_ovr_st():
    """全 70 属性，ST 权重 OVR≈70。"""
    attrs = {a: 70 for group in FIELD_GROUPS.values() for a in group}
    ovr = calc_ovr(attrs, "ST")
    assert 68 <= ovr <= 72


def test_gk_ovr_uses_gk_weights():
    """门将 OVR 只吃 GK 权重：GK 属性差则 OVR 低，GK 属性满则 OVR 高。"""
    attrs = {a: 90 for group in FIELD_GROUPS.values() for a in group}
    attrs.update({a: 40 for a in GK_ATTRIBUTES})
    assert calc_ovr(attrs, "GK") < 60
    attrs2 = {a: 90 for group in FIELD_GROUPS.values() for a in group}
    attrs2.update({a: 90 for a in GK_ATTRIBUTES})
    assert calc_ovr(attrs2, "GK") > 80


def test_ovr_for_position_covers_all():
    """ovr_for_position 返回全部 10 个位置。"""
    attrs = {a: 75 for a in ALL_ATTRS}
    result = ovr_for_position(attrs)
    assert set(result.keys()) == set(POSITIONS)
    assert all(v == 75 for v in result.values())


def test_template_attributes_ovr_matches_target():
    """模板展开的属性按目标 OVR 反推，OVR 偏差 ≤3。"""
    attrs = template_attributes(target_ovr=70, position="ST", rng_seed=1)
    assert abs(calc_ovr(attrs, "ST") - 70) <= 3


def test_expand_attributes():
    """数据集行展开：包含全部属性，值在 0-99。"""
    row = {"name": "X", "ovr": 72, "position": "CB", "age": 24}
    attrs = expand_attributes(row)
    assert set(attrs.keys()) == set(ALL_ATTRS)
    assert all(0 <= v <= 99 for v in attrs.values())


def test_position_weights_and_labels():
    """位置权重表与中文标签覆盖全部位置。"""
    assert set(POSITION_WEIGHTS) == {"ST", "LW", "RW", "CAM", "CM", "CDM", "LB", "RB", "CB", "GK"}
    assert set(POSITION_LABEL) == set(POSITIONS)
    assert POSITION_LABEL["GK"] == "门将"


def test_hidden_fields():
    """隐藏属性三件套。"""
    assert set(HIDDEN_FIELDS) == {"skill_moves", "weak_foot", "playstyles"}
