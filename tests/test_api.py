"""API 层测试：TestClient + monkeypatch narrator（不触网）。

engine 模块未合并时由 conftest 安装 tests/stubs/engine 提供契约实现。
decision 端点为 SSE 流式（meta → narrative* → done | error），测试解析事件提取数据。
"""
import json

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


def _mock_narrator(monkeypatch, narrative: str = None, chunked: bool = True):
    text = VALID_JSON if narrative is None else narrative
    async def fake_generate(system, user, max_tokens=800):
        return text
    async def fake_generate_stream(system, user, max_tokens=800):
        if chunked:  # 分块流式，验证前端拼接
            for i in range(0, len(text), 20):
                yield text[i:i + 20]
        else:
            yield text
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    monkeypatch.setattr(app.state.narrator, "generate_stream", fake_generate_stream)


def parse_sse(text: str) -> list:
    """解析 SSE 文本 → [(event, payload), ...]。"""
    events = []
    for block in text.split("\n\n"):
        block = block.strip()
        if not block:
            continue
        ev = "message"
        data = ""
        for line in block.split("\n"):
            if line.startswith("event:"):
                ev = line[6:].strip()
            elif line.startswith("data:"):
                data += line[5:].strip()
        if data:
            events.append((ev, json.loads(data)))
    return events


def done_payload(text: str) -> dict:
    """取 SSE 流的 done 事件 payload。"""
    for ev, payload in parse_sse(text):
        if ev == "done":
            return payload
    raise AssertionError(f"SSE 流缺少 done 事件: {text[:200]}")


def meta_payload(text: str) -> dict:
    for ev, payload in parse_sse(text):
        if ev == "meta":
            return payload
    raise AssertionError(f"SSE 流缺少 meta 事件: {text[:200]}")


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
    for i in range(10):  # 决策循环：结算 + 下一骨架 + 流式叙事
        r = c.post("/api/game/decision", json={"choice_id": 0})
        assert r.status_code == 200, f"第 {i} 次决策失败: {r.text}"
        # SSE 流：meta + narrative 分块 + done
        events = parse_sse(r.text)
        assert events[0][0] == "meta"
        assert any(ev == "narrative" for ev, _ in events), "缺少 narrative 分块"
        done = done_payload(r.text)
        assert done["narrative"]
        assert done["options"], "done 必须携带与骨架对齐的选项"
        p = done["player"]
        assert p["ovr"] <= 99
        if p["age"] < 18:  # 18 岁前硬约束不被打破
            assert p["ovr"] <= 80
            assert p["value"] <= 80_000_000
        assert done["decision"]["stage"] in ("夏窗", "季前备战", "上半程", "冬窗", "下半程", "赛季末")


def test_decision_options_aligned_with_skeleton(monkeypatch):
    """修复回归：LLM 输出选项数 ≠ 骨架时，前端按钮数量以骨架为准。"""
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch, narrative='{"narrative": "只有一个选项。", '
                                          '"options": [{"label": "甲", "hint": "h1"}]}')
    c.post("/api/game/new", json=NEW_GAME_BODY)
    r = c.post("/api/game/decision", json={"choice_id": 0})
    assert r.status_code == 200
    done = done_payload(r.text)
    assert len(done["options"]) >= 3  # 与骨架数量一致，而非 LLM 的 1 个


def test_merge_options_contract():
    """选项合并：LLM 文案按索引合并，数量以引擎骨架为准，缺失回退骨架原文。"""
    from app.api.routes_game import _merge_options
    from app.narrator.schema import NarratorOutput, OptionText
    skeleton = {"options": [{"label": "A", "hint": ""}, {"label": "B", "hint": ""},
                            {"label": "C", "hint": ""}]}
    # LLM 只给 1 个
    out = NarratorOutput(narrative="x", options=[OptionText(label="甲", hint="h1")])
    merged = _merge_options(skeleton, out)
    assert [m["label"] for m in merged] == ["甲", "B", "C"]
    # LLM 给 4 个（超骨架）
    out4 = NarratorOutput(narrative="x", options=[OptionText(label=f"L{i}", hint="") for i in range(4)])
    merged4 = _merge_options(skeleton, out4)
    assert [m["label"] for m in merged4] == ["L0", "L1", "L2"]
    # LLM 为 None（极端兜底）
    assert [m["label"] for m in _merge_options(skeleton, None)] == ["A", "B", "C"]


def test_decision_invalid_llm_json_returns_error_event(monkeypatch):
    c = make_client()
    _configure(c)
    _mock_narrator(monkeypatch, narrative="这不是 JSON")
    c.post("/api/game/new", json=NEW_GAME_BODY)
    r = c.post("/api/game/decision", json={"choice_id": 0})
    assert r.status_code == 200  # SSE 层仍 200
    events = parse_sse(r.text)
    assert any(ev == "error" for ev, _ in events)


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
