"""引擎 · 玩家档案与存档测试。"""
import json
import os
import tempfile

import pytest
from engine.player import create_player, initial_attributes, roll_potential, ACADEMIES
from engine.save import save_state, load_state


def test_create_player_fields():
    """建档：16 岁青训球员，关键字段齐全。"""
    p = create_player(name="张伟", birth_year=2007, position="ST", foot="右",
                      height=180, weight=70, region="山东", academy="鲁能足校")
    assert p["age"] == 16
    assert p["position"] == "ST"
    assert p["academy"] == "鲁能足校"
    assert p["foot"] == "右"
    assert p["potential_rating"] in ("青训可造之才", "国脚级", "洲际级", "世界级")


def test_create_player_reproducible():
    """同名玩家建档结果可复现（存档一致性）。"""
    p1 = create_player("王磊", 2007, "CM", "左", 178, 68, "上海", "根宝基地")
    p2 = create_player("王磊", 2007, "CM", "左", 178, 68, "上海", "根宝基地")
    assert p1["attributes"] == p2["attributes"]
    assert p1["potential_rating"] == p2["potential_rating"]


def test_initial_attributes_st_bias():
    """16 岁初始属性：核心属性偏高，全部在 0-99。"""
    attrs = initial_attributes("ST", seed=42)
    assert attrs["positioning"] > attrs["interceptions"]
    assert all(0 <= v <= 99 for v in attrs.values())


def test_initial_attributes_reproducible():
    """同 seed 初始属性可复现。"""
    assert initial_attributes("CM", seed=7) == initial_attributes("CM", seed=7)


def test_roll_potential_ratings():
    """潜力评级四档。"""
    assert roll_potential(seed=7) in ("青训可造之才", "国脚级", "洲际级", "世界级")


def test_academies_defined():
    """六大青训机构。"""
    assert len(ACADEMIES) == 6
    assert {a["name"] for a in ACADEMIES} == {
        "山东鲁能足校", "广州恒大足校", "上海根宝基地", "浙江绿城足校",
        "北京国安青训", "万达留洋计划",
    }


def test_save_roundtrip():
    """存档写入/读取往返一致。"""
    p = create_player("李雷", 2007, "CB", "右", 185, 78, "辽宁", "恒大足校")
    state = {"player": p, "world": {"season": 2023}, "flags": {}}
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "save.json")
        save_state(state, path)
        loaded = load_state(path)
        assert loaded["player"]["name"] == "李雷"
        assert loaded["world"]["season"] == 2023


def test_save_unicode_preserved():
    """中文以 UTF-8 明文写入，读取结果与原始状态一致。"""
    state = {"player": {"name": "张伟⚽", "note": "国脚级"}}
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "s.json")
        save_state(state, path)
        with open(path, encoding="utf-8") as f:
            raw = f.read()
        assert "张伟" in raw  # ensure_ascii=False
        assert load_state(path) == state


def test_load_missing_file_raises():
    """读取不存在的存档报错。"""
    with tempfile.TemporaryDirectory() as d:
        with pytest.raises(FileNotFoundError):
            load_state(os.path.join(d, "nope.json"))
