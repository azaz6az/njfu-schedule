"""engine 最小 stub：决策效果结算（契约与 engine/development.py 一致）。"""


def apply_decision_effects(attrs: dict, effects: dict) -> dict:
    out = dict(attrs)
    for a, d in effects.get("attr_delta", {}).items():
        if a in out:
            out[a] = max(0, min(99, out[a] + d))
    return out
