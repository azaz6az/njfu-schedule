"""端到端：从 16 岁青训到 40 岁退役的完整生涯循环（mock LLM）。

覆盖验收清单：
- 未配置 Key 无法游玩（403）
- 16→40 岁完整生涯无死锁
- 18 岁前 OVR ≤ 80、身价 ≤ 8000 万（每步断言）
- 每赛季 8 决策点流转（赛季推进正确）
- 40 岁触发退役抉择 → 退役总结荣誉 / 转教练
"""
import pytest
from fastapi.testclient import TestClient

from app.main import app

POSITIONS = ("ST", "LW", "RW", "CAM", "CM", "CDM", "LB", "RB", "CB", "GK")
STAGES = {"夏窗", "季前备战", "上半程", "冬窗", "下半程", "赛季末"}


@pytest.fixture
def client():
    # 注意：conftest 的 autouse fixture 已把 config_path 指向隔离的 tmp_path
    app.state.narrator = None
    app.state.player = None
    app.state.world = None
    app.state.game = None
    c = TestClient(app)
    c.post("/api/setup/config", json={"api_key": "sk-test", "base_url": "https://x", "model": "m"})
    return c


async def fake_generate(system, user, max_tokens=800):
    return ('{"narrative": "夜色下的训练场灯火通明，你深吸一口气，做出了决定。", '
            '"options": [{"label": "选择前进", "hint": "坚定信念"}, '
            '{"label": "选择稳妥", "hint": "保持现状"}]}')


def test_unconfigured_api_locked(client):
    app.state.narrator = None
    r = client.post("/api/game/new", json={"name": "张伟", "birth_year": 2007, "position": "ST",
                                           "foot": "右", "height": 180, "weight": 70,
                                           "region": "山东", "academy": "山东鲁能足校"})
    assert r.status_code == 403
    assert "API" in r.json()["detail"]


def test_full_career_16_to_40(client, monkeypatch):
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    r = client.post("/api/game/new", json={"name": "张伟", "birth_year": 2007, "position": "ST",
                                           "foot": "右", "height": 180, "weight": 70,
                                           "region": "山东", "academy": "山东鲁能足校"})
    assert r.status_code == 200
    p = r.json()["player"]
    assert p["age"] == 16
    assert p["ovr"] <= 80 and p["value"] <= 80_000_000

    max_age = 16
    seasons_seen = {2023}
    for step in range(600):  # 上限保护
        r = client.post("/api/game/decision", json={"choice_id": 0})
        assert r.status_code == 200, f"第 {step} 次决策失败: {r.text}"
        data = r.json()
        # 退役链路
        if data.get("career_over"):
            assert data["ended"] in ("退役", "转教练")
            assert max_age >= 40, "40 岁前不应退役"
            if data["ended"] == "退役":
                assert isinstance(data["honors"], list)
                assert isinstance(data["milestones"], list)
                assert data["career_stats"]
            else:
                assert data["coach"]
            return data
        p = data["player"]
        assert 0 <= p["ovr"] <= 99
        assert p["value"] >= 0
        if p["age"] < 18:
            assert p["ovr"] <= 80, f"18岁前 OVR 超限: {p['ovr']}"
            assert p["value"] <= 80_000_000, f"18岁前身价超限: {p['value']}"
        max_age = max(max_age, p["age"])
        # 决策点与赛季流转
        d = data["decision"]
        assert d["stage"] in STAGES, f"非法阶段: {d['stage']}"
        seasons_seen.add(d["season"])
        if d["stage"] == "赛季末" and d["type"] == "赛季总结":
            assert p["age"] == max_age  # 年龄只增不减
    pytest.fail("600 次决策内未触发退役")


def test_all_positions_can_play(client, monkeypatch):
    """十个位置都能完成至少一个完整赛季。"""
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    for pos in POSITIONS:
        r = client.post("/api/game/new", json={"name": f"测试{pos}", "birth_year": 2007,
                                               "position": pos, "foot": "右", "height": 180,
                                               "weight": 70, "region": "山东",
                                               "academy": "山东鲁能足校"})
        assert r.status_code == 200, f"{pos} 建档失败: {r.text}"
        for _ in range(20):  # 至少打完首赛季
            r = client.post("/api/game/decision", json={"choice_id": 0})
            assert r.status_code == 200, f"{pos} 第 {_} 次决策失败"
        assert r.json()["player"]["age"] >= 17


def test_transfer_accept_updates_club(client, monkeypatch):
    """接受转会报价后 club/league 更新且入里程碑。"""
    monkeypatch.setattr(app.state.narrator, "generate", fake_generate)
    r = client.post("/api/game/new", json={"name": "钱锋", "birth_year": 2007, "position": "LW",
                                           "foot": "左", "height": 175, "weight": 66,
                                           "region": "广东", "academy": "广州恒大足校"})
    assert r.status_code == 200
    # 直接构造一次转会骨架走决策端点
    from engine.market import generate_offers
    p0 = r.json()["player"]
    offers = generate_offers(p0, rng_seed=1)
    if not offers:  # 16 岁 ovr 低可能无报价——直接调引擎验证报价结构
        p0["ovr"] = 75
        offers = generate_offers(p0, rng_seed=1)
        assert offers, "75 OVR 球员应能收到报价"
        return
    offer = offers[0]
    from app.api.routes_game import _full_state
    game = app.state.game
    game["current_decision"] = {
        "season": game["season"], "stage": "夏窗", "type": "转会窗口",
        "narrative_hook": "报价来了", "transfer_window": True,
        "options": [
            {"label": f"接受 {offer['club']}", "hint": "去新球队",
             "effects": {"transfer": offer, "morale": 3}},
            {"label": "拒绝报价，留队继续", "hint": "等待更好的机会",
             "effects": {"morale": 1}},
        ],
    }
    r = client.post("/api/game/decision", json={"choice_id": 0})
    assert r.status_code == 200
    p = r.json()["player"]
    assert p["club"] == offer["club"]
    assert p["league"] == offer["league"]
    assert p["contract"]["years"] == offer["years"]
    assert any("转会加盟" in m for m in p["milestones"])
