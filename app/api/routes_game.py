"""游戏路由：建档、决策、状态、退役。"""
import os

import httpx
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from engine.attributes import calc_ovr
from engine.decision import next_decision
from engine.development import apply_decision_effects
from engine.player import ACADEMIES, create_player
from engine.save import save_state
from engine.value import apply_under18_caps, market_value
from engine.world import build_world
from app.narrator.prompts import SYSTEM_PROMPT, build_user_prompt
from app.narrator.schema import NarratorOutput, parse_llm_json

router = APIRouter(prefix="/api/game", tags=["game"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SAVE_PATH = os.path.join(BASE_DIR, "saves", "latest.json")

POSITIONS = ("ST", "LW", "RW", "CAM", "CM", "CDM", "LB", "RB", "CB", "GK")


def _require_narrator(request: Request):
    n = request.app.state.narrator
    if n is None or not n.configured:
        raise HTTPException(403, "未配置 API Key，无法游玩。请先在设置页填写。")


def _require_game(request: Request):
    _require_narrator(request)
    if request.app.state.player is None or request.app.state.game is None:
        raise HTTPException(400, "尚未建档")


class NewGameIn(BaseModel):
    name: str
    birth_year: int = 2007
    position: str
    foot: str = "右"
    height: int
    weight: int
    region: str
    academy: str


class DecisionIn(BaseModel):
    choice_id: int


class RetireIn(BaseModel):
    choice: str  # retire | continue | coach


async def _ask_llm(request: Request, skeleton: dict) -> NarratorOutput:
    """调用 LLM 并校验 JSON；失败重试 1 次，仍失败返回 502（绝不伪造叙事）。"""
    n = request.app.state.narrator
    p = request.app.state.player
    game = request.app.state.game
    summary = {"name": p["name"], "age": p["age"], "ovr": p["ovr"], "value": p["value"],
               "position": p["position"], "club": p["club"], "season": game["season"]}
    last_error = None
    for _ in range(2):
        try:
            raw = await n.generate(SYSTEM_PROMPT,
                                   build_user_prompt(summary, game["narrative_history"], skeleton))
            return parse_llm_json(raw)
        except (ValueError, httpx.HTTPError) as e:
            last_error = e
    raise HTTPException(502, f"叙事生成失败，请重试（{last_error}）")


async def _generate(request: Request, skeleton: dict) -> dict:
    out = await _ask_llm(request, skeleton)
    game = request.app.state.game
    game["narrative_history"].append(out.narrative[:120])
    game["narrative_history"] = game["narrative_history"][-10:]
    return {"narrative": out.narrative,
            "options": [o.model_dump() for o in out.options]}


@router.post("/new")
async def new_game(data: NewGameIn, request: Request):
    _require_narrator(request)
    if data.position not in POSITIONS:
        raise HTTPException(400, "位置不合法")
    if data.academy not in [a["name"] for a in ACADEMIES]:
        raise HTTPException(400, "青训机构不合法")
    p = create_player(data.name, data.birth_year, data.position, data.foot,
                      data.height, data.weight, data.region, data.academy)
    p["ovr"] = calc_ovr(p["attributes"], p["position"])
    p["value"] = market_value(p["ovr"], p["age"], p["position"], "cs", 0, 1.0)
    p["ovr"], p["value"] = apply_under18_caps(p["ovr"], p["value"], p["age"])
    request.app.state.player = p
    request.app.state.world = build_world(os.path.join(BASE_DIR, "data"))
    skeleton = {"season": 2023, "stage": "青训入营", "type": "入营仪式",
                "narrative_hook": "你第一次走进青训基地大门",
                "options": [{"label": "主动向教练报到", "hint": "留下好印象"},
                            {"label": "先熟悉场地", "hint": "低调观察"},
                            {"label": "和新队友攀谈", "hint": "早点融入"}]}
    request.app.state.game = {"season": 2023, "stage": "青训入营",
                              "skeleton_index": 0, "narrative_history": [],
                              "current_decision": skeleton}
    result = await _generate(request, skeleton)
    save_state(_full_state(request), SAVE_PATH)
    return {"narrative": result["narrative"], "options": result["options"],
            "player": p, "decision": skeleton}


@router.post("/decision")
async def decision(data: DecisionIn, request: Request):
    _require_game(request)
    game = request.app.state.game
    skeleton = game.get("current_decision")
    if not skeleton:
        raise HTTPException(400, "当前没有待决决策")
    if not (0 <= data.choice_id < len(skeleton["options"])):
        raise HTTPException(400, "选项不存在")
    choice = skeleton["options"][data.choice_id]
    effects = choice.get("effects", {})  # 入营等文案骨架无引擎效果
    p = request.app.state.player
    # 引擎结算（防幻觉：数值效果全部来自引擎预生成的 effects）
    p["attributes"] = apply_decision_effects(p["attributes"], effects)
    p["morale"] = max(0, min(99, p.get("morale", 70) + effects.get("morale", 0)))
    p["form"] = max(0.5, min(1.5, p.get("form", 1.0) + effects.get("form", 0)))
    p["ovr"] = calc_ovr(p["attributes"], p["position"])
    p["value"] = market_value(p["ovr"], p["age"], p["position"], "cs", 0, p.get("form", 1.0))
    p["ovr"], p["value"] = apply_under18_caps(p["ovr"], p["value"], p["age"])
    # 生成下一决策骨架
    game["skeleton_index"] += 1
    new_sk = next_decision(p, game["season"], "上半程", rng_seed=game["skeleton_index"])
    game["current_decision"] = new_sk
    result = await _generate(request, new_sk)
    save_state(_full_state(request), SAVE_PATH)
    return {"narrative": result["narrative"], "options": result["options"],
            "player": p,
            "decision": {k: v for k, v in new_sk.items() if k != "options"}}


@router.get("/state")
def state(request: Request):
    if request.app.state.player is None:
        raise HTTPException(404, "尚未建档")
    return _full_state(request)


@router.post("/retire")
def retire(data: RetireIn, request: Request):
    _require_game(request)
    p = request.app.state.player
    if p["age"] < 40:
        raise HTTPException(400, "未到 40 岁")
    if data.choice not in ("retire", "continue", "coach"):
        raise HTTPException(400, "choice 必须为 retire|continue|coach")
    if data.choice == "retire":
        return {"honors": p["honors"], "milestones": p["milestones"],
                "career_stats": p["career_stats"]}
    if data.choice == "coach":
        from engine.coach import new_coach
        p["coach_attributes"] = new_coach(p["name"])
        p["flags"] = {"retired": True, "coach_mode": True}
        return {"coach": p["coach_attributes"]}
    return {"continue": True}


def _full_state(request: Request) -> dict:
    return {"player": request.app.state.player,
            "world": {"season": request.app.state.game["season"], "leagues": {}},
            "flags": {"retired": False, "coach_mode": False}}
