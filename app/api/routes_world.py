"""世界查询路由：积分榜、转会流言。"""
from fastapi import APIRouter, HTTPException, Request

router = APIRouter(prefix="/api/world", tags=["world"])


@router.get("/table")
def table(league: str, request: Request):
    w = request.app.state.world
    if not w or league not in w:
        raise HTTPException(404, "联赛不存在")
    lg = w[league]
    return {"league": league, "name": lg.name, "table": lg.table}


@router.get("/transfers")
def transfers(request: Request):
    game = request.app.state.game
    return {"transfers": game.get("transfer_news", []) if game else []}
