"""FastAPI 入口：状态容器 + 静态挂载。"""
import json
import os

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.api.routes_game import router as game_router
from app.api.routes_setup import router as setup_router
from app.api.routes_world import router as world_router
from app.narrator.client import NarratorClient

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")

app = FastAPI(title="足球人生模拟器")
app.state.config_path = CONFIG_PATH
app.state.narrator = None
app.state.player = None
app.state.world = None
app.state.game = None  # {"season", "stage", "skeleton_index", "narrative_history", "current_decision"}


def load_config() -> dict:
    if os.path.exists(app.state.config_path):
        with open(app.state.config_path, encoding="utf-8") as f:
            return json.load(f)
    return {"api_key": "", "base_url": "https://api.deepseek.com", "model": "deepseek-chat"}


def init_narrator():
    cfg = load_config()
    app.state.narrator = NarratorClient(cfg.get("api_key", ""), cfg.get("base_url", ""),
                                        cfg.get("model", ""))


def get_narrator() -> NarratorClient:
    if app.state.narrator is None:
        init_narrator()
    return app.state.narrator


app.include_router(setup_router)
app.include_router(game_router)
app.include_router(world_router)
app.mount("/", StaticFiles(directory=os.path.join(BASE_DIR, "frontend"), html=True),
          name="frontend")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
