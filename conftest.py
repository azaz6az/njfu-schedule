"""测试公共设施：
1. engine 模块未合并时（本分支无 engine/*.py），把 tests/stubs/engine 安装为 engine 包子模块；
   合并后真实模块存在则跳过，stub 不生效。
2. 每个测试独立：config 写入临时目录、app 状态清零（避免污染真实 config.json / 跨测试串扰）。
"""
import importlib
import importlib.util
import sys
from pathlib import Path

import pytest

_STUB_ROOT = Path(__file__).parent / "tests" / "stubs" / "engine"


def _install_engine_stubs():
    """engine.player 等不可导入时，从 tests/stubs/engine 加载子模块挂到真实 engine 包上。"""
    if importlib.util.find_spec("engine.player") is not None:
        return
    import engine  # 真实空包（会话 A/B/C 未合并时仅 __init__.py）
    for py in sorted(_STUB_ROOT.glob("*.py")):
        if py.name == "__init__.py":
            continue
        mod_name = f"engine.{py.stem}"
        spec = importlib.util.spec_from_file_location(mod_name, py)
        mod = importlib.util.module_from_spec(spec)
        sys.modules[mod_name] = mod
        spec.loader.exec_module(mod)
        setattr(engine, py.stem, mod)


_install_engine_stubs()


@pytest.fixture(autouse=True)
def isolated_app_state(tmp_path):
    """每个测试独立配置与状态。"""
    from app.main import app
    app.state.config_path = str(tmp_path / "config.json")
    app.state.narrator = None
    app.state.player = None
    app.state.world = None
    app.state.game = None
    yield
