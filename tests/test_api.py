"""API 层测试：TestClient + monkeypatch narrator.generate（不触网）。

engine 模块未合并时由 conftest 安装 tests/stubs/engine 提供契约实现。
"""
import pytest
from fastapi.testclient import TestClient

from app.main import app, get_narrator

VALID_JSON = '{"narrative": "欢迎来到鲁能足校。", "options": [{"label": "开始训练", "hint": "好好练"}]}'

NEW_GAME_BODY = {"name": "张伟", "birth_year": 2007, "position": "ST",
                 "foot": "右", "height": 180, "weight": 70,
                 "region": "山东", "academy": "山东鲁能足校"}


def make_client():
    app.state.narrator = None
    app.state.player = None
    app.state.world = None
    app.state.game = None
    return TestClient(app)


def _configure(c) -> None:
    r = c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    assert r.status_code == 200


def _mock_narrator(monkeypatch, narrative: str = None):
    text = VALID_JSON if narrative is None else narrative
    async def fake_generate(system, user, max_tokens=800):
        return text
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)


def test_setup_status_unconfigured():
    c = make_client()
    r = c.get("/api/setup/status")
    assert r.status_code == 200
    assert r.json()["configured"] is False


def test_game_new_requires_config():
    c = make_client()
    r = c.post("/api/game/new", json=NEW_GAME_BODY)
    assert r.status_code == 403
    assert "API" in r.json()["detail"]


def test_decision_requires_config():
    c = make_client()
    r = c.post("/api/game/decision", json={"choice_id": 0})
    assert r.status_code == 403


def test_save_config():
    c = make_client()
    _configure(c)
    assert get_narrator().configured
    r = c.get("/api/setup/status")
    assert r.json()["configured"] is True
    assert r.json()["model"] == "m"


def test_game_new_with_mock_narrator(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    r = c.post("/api/game/new", json=NEW_GAME_BODY)
    assert r.status_code == 200
    data = r.json()
    assert data["narrative"].startswith("欢迎")  # 契约：narrative 为字符串
    assert data["player"]["age"] == 16
    assert data["player"]["attributes"]  # 属性已生成
    assert data["options"]  # 契约：顶层 options
    assert data["player"]["ovr"] <= 80  # 18 岁前约束


def test_game_new_rejects_bad_academy(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    body = dict(NEW_GAME_BODY, academy="野鸡足校")
    r = c.post("/api/game/new", json=body)
    assert r.status_code == 400


def test_decision_loop(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    assert c.post("/api/game/new", json=NEW_GAME_BODY).status_code == 200
    for i in range(10):  # 决策循环：结算 + 下一骨架 + 叙事
        r = c.post("/api/game/decision", json={"choice_id": 0})
        assert r.status_code == 200, f"第 {i} 次决策失败: {r.text}"
        data = r.json()
        assert data["narrative"]
        assert data["options"]
        assert data["player"]["ovr"] <= 99
        if data["player"]["age"] < 18:  # 18 岁前硬约束不被打破
            assert data["player"]["ovr"] <= 80
            assert data["player"]["value"] <= 80_000_000


def test_decision_out_of_range(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    c.post("/api/game/new", json=NEW_GAME_BODY)
    r = c.post("/api/game/decision", json={"choice_id": 99})
    assert r.status_code == 400


def test_game_state(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    c.post("/api/game/new", json=NEW_GAME_BODY)
    r = c.get("/api/game/state")
    assert r.status_code == 200
    assert r.json()["player"]["name"] == "张伟"


def test_retire_before_40(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch)
    c.post("/api/game/new", json=NEW_GAME_BODY)
    r = c.post("/api/game/retire", json={"choice": "retire"})
    assert r.status_code == 400  # 16 岁未到退役年龄


def test_world_transfers_empty():
    c = make_client()
    r = c.get("/api/world/transfers")
    assert r.status_code == 200
    assert r.json()["transfers"] == []


def test_world_table_unknown_league():
    c = make_client()
    r = c.get("/api/world/table?league=cs")
    assert r.status_code == 404
