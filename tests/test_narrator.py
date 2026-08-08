"""narrator 层测试：schema 校验与 prompt 构造（不触网）。"""
import pytest
from app.narrator.schema import NarratorOutput, parse_llm_json
from app.narrator.prompts import SYSTEM_PROMPT, build_user_prompt


def test_parse_valid_json():
    raw = '{"narrative": "比赛开始……", "options": [{"label": "射门", "hint": "试试远射"}]}'
    out = parse_llm_json(raw)
    assert isinstance(out, NarratorOutput)
    assert out.narrative.startswith("比赛")
    assert out.options[0].label == "射门"
    assert out.options[0].hint == "试试远射"


def test_parse_invalid_json_raises():
    with pytest.raises(ValueError):
        parse_llm_json('{"narrative": 未闭合')


def test_parse_missing_narrative_raises():
    with pytest.raises(ValueError):
        parse_llm_json('{"options": [{"label": "a", "hint": "b"}]}')


def test_parse_invalid_option_length_raises():
    with pytest.raises(ValueError):
        parse_llm_json('{"narrative": "x", "options": [{"label": "超" * 30, "hint": "h"}]}')


def test_parse_code_fence_tolerated():
    raw = '```json\n{"narrative": "x", "options": [{"label": "a", "hint": "b"}]}\n```'
    out = parse_llm_json(raw)
    assert out.narrative == "x"


def test_build_user_prompt_contains_state():
    prompt = build_user_prompt(player_summary={"name": "张伟", "ovr": 70}, history=[], skeleton={})
    assert "张伟" in prompt


def test_build_user_prompt_slices_history_to_last_five():
    history = [f"事件{i}" for i in range(8)]
    prompt = build_user_prompt(
        {"name": "张伟"}, history,
        {"season": 2023, "stage": "上半程", "type": "比赛单刀", "narrative_hook": "获得单刀机会",
         "options": [{"label": "射门", "hint": "h", "effects": {"attr_delta": {"finishing": 5}}}]},
    )
    assert "事件3" in prompt and "事件7" in prompt  # 最近 5 条
    assert "事件0" not in prompt
    assert "effects" not in prompt  # 仅 label/hint，不含数值与内部字段
    assert "attr_delta" not in prompt


def test_system_prompt_has_rules():
    assert "18岁" in SYSTEM_PROMPT
    assert "小说" in SYSTEM_PROMPT
    assert "JSON" in SYSTEM_PROMPT
