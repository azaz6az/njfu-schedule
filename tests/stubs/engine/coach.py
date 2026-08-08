"""engine 最小 stub：教练生涯（契约与 engine/coach.py 一致）。"""
import random

COACH_ATTRS = ["tactical", "motivation", "training", "negotiation", "youth", "transfer"]


def new_coach(name: str, seed: int = 0) -> dict:
    rng = random.Random(seed)
    return {a: rng.randint(55, 75) for a in COACH_ATTRS}
