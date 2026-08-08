"""配置路由：API Key 状态与保存。"""
import json
from fastapi import APIRouter, Request
from pydantic import BaseModel

router = APIRouter(prefix="/api/setup", tags=["setup"])


class ConfigIn(BaseModel):
    api_key: str
    base_url: str = "https://api.deepseek.com"
    model: str = "deepseek-chat"


@router.get("/status")
def status(request: Request):
    from app.main import get_narrator  # 延迟导入避免 app.main 循环
    n = get_narrator()
    return {"configured": n.configured,
            "model": n.model if n.configured else None}


@router.post("/config")
def save_config(cfg: ConfigIn, request: Request):
    from app.main import get_narrator  # 延迟导入避免 app.main 循环
    payload = {"api_key": cfg.api_key.strip(), "base_url": cfg.base_url.strip(),
               "model": cfg.model.strip()}
    with open(request.app.state.config_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    request.app.state.narrator = None  # 触发重载
    return {"ok": True, "configured": get_narrator().configured}
