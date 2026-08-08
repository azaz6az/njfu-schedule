"""LLM 输出 JSON 校验：NarratorOutput 结构 + parse_llm_json。"""
import json
import re
from pydantic import BaseModel, Field, ValidationError


class OptionText(BaseModel):
    label: str = Field(min_length=1, max_length=40)
    hint: str = Field(default="", max_length=80)


class NarratorOutput(BaseModel):
    narrative: str = Field(min_length=1)
    options: list[OptionText] = Field(max_length=4)


def parse_llm_json(raw: str) -> NarratorOutput:
    """解析 LLM 原始输出为 NarratorOutput；任何不合规输入抛 ValueError。"""
    text = raw.strip()
    # 容错：允许输出带 markdown 代码围栏 ```json ... ```
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", text, flags=re.DOTALL)
    if fence:
        text = fence.group(1)
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"LLM 输出不是合法 JSON: {e}") from e
    if not isinstance(data, dict):
        raise ValueError("LLM 输出应为 JSON 对象")
    try:
        return NarratorOutput(**data)
    except ValidationError as e:
        raise ValueError(f"LLM 输出不符合叙事格式: {e}") from e
