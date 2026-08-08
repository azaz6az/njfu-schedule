"""叙事系统提示词与用户消息构造（防幻觉：只给文案，不给数值）。"""
SYSTEM_PROMPT = """你是一位足球题材小说的叙事引擎，游戏背景：

你正在模拟一名2007年出生的中国青训球员的职业生涯。球员16岁进入青训，属性按FIFA体系（PAC/SHO/PAS/DRI/DEF/PHY，0-99），球员18岁前总评OVR不超过80、身价不超过8000万欧元。世界由引擎模拟（16个联赛、真实球员、转会、升降级、国家队）。

你的职责：
1. 用小说笔触写叙事（细腻、有画面感、中文，150-350字），描述当前场景与决策点。
2. 根据引擎给定的决策骨架，把选项改写成文学化、口语化的表达（label不超过40字，hint不超过80字，提示因果但不暴露具体数值）。
3. 严格只输出一个JSON对象，不要输出任何其他内容：
{"narrative": "……", "options": [{"label": "……", "hint": "……"}]}

绝对禁止：编造数值变化、编造比赛结果、让角色做出超出选项的决定。"""


def build_user_prompt(player_summary: dict, history: list, skeleton: dict) -> str:
    """构造用户消息：当前时间 + 球员状态 + 生涯摘要（最近5条）+ 场景事件 + 可选行动（仅 label/hint）。"""
    sk = skeleton or {}
    opts = [{"label": o.get("label"), "hint": o.get("hint")} for o in sk.get("options", [])]
    return (
        f"[当前时间] 赛季 {sk.get('season')} {sk.get('stage')}，事件：{sk.get('type')}\n"
        f"[球员状态] {player_summary}\n"
        f"[生涯摘要] {'；'.join(history[-5:]) if history else '开局'}\n"
        f"[场景事件] {sk.get('narrative_hook', '')}\n"
        f"[可选行动] {opts}\n"
        f"请为这个决策点生成叙事与选项。"
    )
