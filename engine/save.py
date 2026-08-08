"""JSON 存档读写。"""
import json
import os


def save_state(state: dict, path: str):
    """存档写入（UTF-8 明文中文）。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def load_state(path: str) -> dict:
    """读取存档。"""
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)
