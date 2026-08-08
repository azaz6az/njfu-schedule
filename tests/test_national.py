"""国家队线测试：征召判定、联赛加成、状态加成、级别推进。"""
import pytest
from engine.national import (
    NATIONAL_LEVELS, NATIONAL_LEVEL_ORDER,
    national_callup, next_level,
)


def test_national_levels_order():
    assert NATIONAL_LEVEL_ORDER == ["u17", "u20", "u23", "senior"]
    assert NATIONAL_LEVELS["u17"]["min_age"] == 16
    assert NATIONAL_LEVELS["senior"]["min_ovr"] == 65


def test_callup_criteria_age():
    assert national_callup(age=15, ovr=75, league="cs", form=1.0, level="u17") is False
    assert national_callup(age=16, ovr=75, league="cs", form=1.0, level="u17") is True


def test_u17_max_age_18():
    assert national_callup(age=18, ovr=75, league="cs", form=1.0, level="u17") is True
    assert national_callup(age=19, ovr=75, league="cs", form=1.0, level="u17") is False


def test_u20_u23_age_windows():
    assert national_callup(age=17, ovr=80, league="cs", form=1.0, level="u20") is False
    assert national_callup(age=19, ovr=80, league="cs", form=1.0, level="u20") is True
    assert national_callup(age=19, ovr=80, league="cs", form=1.0, level="u23") is False
    assert national_callup(age=24, ovr=80, league="cs", form=1.0, level="u23") is False


def test_senior_requires_ovr():
    assert national_callup(age=20, ovr=64, league="cs", form=1.0, level="senior") is False
    assert national_callup(age=20, ovr=66, league="cs", form=1.2, level="senior") is True


def test_form_boost_can_flip_callup():
    assert national_callup(age=20, ovr=65, league="cs", form=1.0, level="senior") is False
    assert national_callup(age=20, ovr=65, league="cs", form=1.2, level="senior") is True


def test_league_prestige_helps():
    low = national_callup(age=21, ovr=70, league="cs", form=1.0, level="senior")
    high = national_callup(age=21, ovr=70, league="epl", form=1.0, level="senior")
    assert high >= low
    # 联赛加成足以翻转结果
    assert national_callup(age=21, ovr=65, league="epl", form=1.0, level="senior") is True
    assert national_callup(age=21, ovr=65, league="csl2", form=1.0, level="senior") is False


def test_next_level_advances_by_age():
    assert next_level("u17", 15) == ""
    assert next_level("u17", 16) == "u17"
    assert next_level("u17", 18) == "u20"
    assert next_level("u20", 20) == "u23"
    assert next_level("u23", 23) == "senior"
    assert next_level("u23", 24) == "senior"


def test_next_level_never_downgrades():
    assert next_level("senior", 22) == "senior"
    assert next_level("u23", 21) == "u23"


def test_next_level_empty_current():
    assert next_level("", 19) == "u20"
