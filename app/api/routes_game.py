"""游戏路由：建档、决策（SSE 流式）、状态、退役。"""
import asyncio
import json
import os

import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from engine.attributes import calc_ovr
from engine.season import advance_stage, next_skeleton
from engine.development import apply_decision_effects
from engine.player import ACADEMIES, create_player
from engine.save import load_state, save_state
from engine.value import apply_under18_caps, market_value
from engine.world import build_world
from app.narrator.prompts import SYSTEM_PROMPT, build_user_prompt
from app.narrator.schema import NarratorOutput, parse_llm_json

router = APIRouter(prefix="/api/game", tags=["game"])

BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SAVE_PATH = os.path.join(BASE_DIR, "saves", "latest.json")

POSITIONS = ("ST", "LW", "RW", "CAM", "CM", "CDM", "LB", "RB", "CB", "GK")
POSITION_CHANGEABLE = ("ST", "LW", "RW", "CAM", "CM", "CDM", "LB", "RB", "CB")


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


def _merge_options(skeleton: dict, llm_out: NarratorOutput | None) -> list:
    """LLM 文案按索引合并到骨架选项；数量以引擎骨架为准（修复选项错位报错）。

    骨架 3 个选项时，LLM 即使只输出 2 个/输出 4 个，前端按钮始终与骨架对齐。
    """
    sk_opts = skeleton["options"]
    llm_opts = list(llm_out.options) if llm_out else []
    merged = []
    for i, sk in enumerate(sk_opts):
        if i < len(llm_opts):
            merged.append({"label": llm_opts[i].label, "hint": llm_opts[i].hint})
        else:
            merged.append({"label": sk["label"], "hint": sk["hint"]})
    return merged


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _player_summary(request: Request) -> dict:
    p = request.app.state.player
    game = request.app.state.game
    return {"name": p["name"], "age": p["age"], "ovr": p["ovr"], "value": p["value"],
            "position": p["position"], "club": p["club"], "season": game["season"]}


async def _stream_narrative(request: Request, skeleton: dict):
    """流式叙事生成（3 次重试，仅对连接阶段失败重试）。

    产出叙事增量文本；结束时更新历史摘要。LLM 输出不合规时抛 HTTPException(502)。
    调用方自行收集增量并 parse_llm_json（async generator 无法带值 return）。
    """
    n = request.app.state.narrator
    p = request.app.state.player
    game = request.app.state.game
    prompt = build_user_prompt(_player_summary(request), game["narrative_history"], skeleton)
    last_err = None
    for attempt in range(3):
        parts = []
        try:
            async for delta in n.generate_stream(SYSTEM_PROMPT, prompt):
                parts.append(delta)
                yield delta
            raw = "".join(parts)
            out = parse_llm_json(raw)
            game["narrative_history"].append(out.narrative[:120])
            game["narrative_history"] = game["narrative_history"][-10:]
            return
        except httpx.HTTPError as e:
            last_err = e
            if attempt < 2:
                await asyncio.sleep(1.0 * (attempt + 1))
                continue
        except ValueError as e:
            last_err = e
            break  # JSON 不合规：流已发完，无法重试
    raise HTTPException(502, f"叙事生成失败，请重试（{last_err}）")


@router.post("/new")
async def new_game(data: NewGameIn, request: Request):
    _require_narrator(request)
    if data.position not in POSITIONS:
        raise HTTPException(400, "位置不合法")
    if data.academy not in [a["name"] for a in ACADEMIES]:
        raise HTTPException(400, "青训机构不合法")
    p = create_player(data.name, data.birth_year, data.position, data.foot,
                      data.height, data.weight, data.region, data.academy)
    p["league"] = "cs"  # 青训起点：中超体系
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
    # 16 岁青训首年：从"上半程"开始（青训没有转会窗），赛季末结算后进入 2024 夏窗
    request.app.state.game = {"season": 2023, "stage": "上半程", "stage_index": 2,
                              "skeleton_index": 0, "narrative_history": [],
                              "current_decision": skeleton, "retirement_offered": False}
    # 开局叙事（非流式，仅一次；带重试）
    out = None
    last_err = None
    for attempt in range(3):
        try:
            raw = await request.app.state.narrator.generate(
                SYSTEM_PROMPT, build_user_prompt(_player_summary(request), [], skeleton))
            out = parse_llm_json(raw)
            break
        except (ValueError, httpx.HTTPError) as e:
            last_err = e
            if attempt < 2:
                await asyncio.sleep(1.0 * (attempt + 1))
    if out is None:
        raise HTTPException(502, f"开局叙事生成失败，请重试（{last_err}）")
    options = _merge_options(skeleton, out)
    save_state(_full_state(request), SAVE_PATH)
    return {"narrative": out.narrative, "options": options, "player": p,
            "decision": {k: v for k, v in skeleton.items() if k != "options"}}


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

    # ---- 引擎结算（防幻觉：数值效果全部来自引擎预生成的 effects）----
    p["attributes"] = apply_decision_effects(p["attributes"], effects)
    p["morale"] = max(0, min(99, p.get("morale", 70) + effects.get("morale", 0)))
    p["form"] = max(0.5, min(1.5, p.get("form", 1.0) + effects.get("form", 0)))
    # 伤病状态机：风险累积 → 受伤 → 复出恢复
    p["injury_risk"] = max(0.0, min(1.0, p.get("injury_risk", 0.0) + effects.get("injury_risk", 0)))
    if effects.get("get_injured"):
        p["injured"] = True
        p["milestones"].append(f"{game['season']}年：遭遇伤病（{game['stage']}）")
    if effects.get("recover"):
        p["injured"] = False
        p["milestones"].append(f"{game['season']}年：伤愈复出")
    # 位置转型：引擎预定义目标，真正改变位置并重算 OVR
    pos_change = effects.get("position_change")
    if pos_change and pos_change in POSITION_CHANGEABLE:
        p["position"] = pos_change
        p["milestones"].append(f"{game['season']}年：位置转型为{pos_change}")
    # 转会结算（引擎生成的报价卡）
    transfer = effects.get("transfer")
    if transfer:
        p["club"] = transfer["club"]
        p["league"] = transfer["league"]
        p["contract"] = {"years": transfer["years"], "weekly_wage": transfer["weekly_wage"],
                         "release_clause": transfer["release_clause"],
                         "signing_bonus": transfer["signing_bonus"]}
        p["milestones"].append(f"{game['season']}年：转会加盟{transfer['club']}（{transfer['fee'] // 10000}万欧）")
    # 数值更新
    p["ovr"] = calc_ovr(p["attributes"], p["position"])
    p["value"] = market_value(p["ovr"], p["age"], p["position"], p.get("league", "cs"),
                              p.get("contract", {}).get("years", 0), p.get("form", 1.0))
    p["ovr"], p["value"] = apply_under18_caps(p["ovr"], p["value"], p["age"])

    # 退役抉择：不走 LLM，直接返回结局
    retire_choice = effects.get("retire")
    if retire_choice:
        if retire_choice == "coach":
            from engine.coach import new_coach
            p["coach_attributes"] = new_coach(p["name"], seed=game["season"])
            p["flags"] = {"retired": True, "coach_mode": True}
            outcome = {"career_over": True, "ended": "转教练", "coach": p["coach_attributes"],
                       "honors": p["honors"], "milestones": p["milestones"]}
        else:  # retire（退役）
            outcome = {"career_over": True, "ended": "退役",
                       "honors": p["honors"], "milestones": p["milestones"],
                       "career_stats": p["career_stats"]}

        async def retire_stream():
            yield _sse("meta", {"player": p, **outcome})
            yield _sse("done", {})
        save_state(_full_state(request), SAVE_PATH)
        return StreamingResponse(retire_stream(), media_type="text/event-stream")

    # ---- 推进到下一决策点并生成骨架 ----
    game["skeleton_index"] += 1
    advance_stage(game)
    new_sk = next_skeleton(p, game, request.app.state.world, game["skeleton_index"])
    game["current_decision"] = new_sk

    async def event_stream():
        yield _sse("meta", {"player": p, "decision_type": new_sk["type"],
                            "stage": game["stage"], "season": game["season"]})
        try:
            full_text = []
            async for delta in _stream_narrative(request, new_sk):
                full_text.append(delta)
                yield _sse("narrative", {"delta": delta})
            raw = "".join(full_text)
            out = parse_llm_json(raw)
        except HTTPException as e:
            yield _sse("error", {"detail": e.detail})
            return
        # 选项合并：数量以引擎骨架为准
        options = _merge_options(new_sk, out)
        save_state(_full_state(request), SAVE_PATH)
        yield _sse("done", {"options": options, "narrative": out.narrative,
                            "decision": {k: v for k, v in new_sk.items() if k != "options"},
                            "player": p})

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.get("/state")
def state(request: Request):
    if request.app.state.player is None:
        if not _try_restore(request):
            raise HTTPException(404, "尚未建档")
    return _full_state(request)


def _try_restore(request: Request) -> bool:
    """服务重启/刷新后从存档恢复内存状态（player/world/game）。"""
    if not os.path.exists(SAVE_PATH):
        return False
    try:
        s = load_state(SAVE_PATH)
    except Exception:
        return False
    if not s.get("player"):
        return False
    request.app.state.player = s["player"]
    request.app.state.world = build_world(os.path.join(BASE_DIR, "data"))
    g = s.get("game") or {}
    game = {
        "season": g.get("season", 2023),
        "stage": g.get("stage", "上半程"),
        "stage_index": g.get("stage_index", 2),
        "skeleton_index": g.get("skeleton_index", 0),
        "narrative_history": g.get("narrative_history", []),
        "current_decision": g.get("current_decision"),
        "retirement_offered": g.get("retirement_offered", False),
    }
    request.app.state.game = game
    # 旧版存档无待决策状态：按当前赛季/阶段重新生成骨架（保证可继续游玩）
    if game["current_decision"] is None:
        game["current_decision"] = next_skeleton(
            request.app.state.player, game, request.app.state.world, game["skeleton_index"])
    return True


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
    game = request.app.state.game or {"season": 2023, "stage": "", "retirement_offered": False}
    p = request.app.state.player or {}
    w = request.app.state.world or {}
    flags = p.get("flags", {})
    return {"player": p,
            "world": {"season": game["season"], "stage": game["stage"],
                      "leagues": {code: {"name": lg.name, "table": lg.table}
                                  for code, lg in w.items()}},
            "flags": {"retired": flags.get("retired", False),
                      "coach_mode": flags.get("coach_mode", False),
                      "retirement_offered": game.get("retirement_offered", False)},
            "game": {"season": game["season"], "stage": game["stage"],
                     "stage_index": game.get("stage_index", 0),
                     "skeleton_index": game.get("skeleton_index", 0),
                     "narrative_history": game.get("narrative_history", []),
                     "current_decision": game.get("current_decision"),
                     "retirement_offered": game.get("retirement_offered", False)}}
