"""由 JVM 测试任务运行资源分发回归，输出失败详情并传播退出状态。"""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[3]
if __name__ == "__main__":
    suite = unittest.defaultTestLoader.discover(str(ROOT / "ci/test"), pattern="test_offline_hub_assets.py")
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    sys.exit(0 if result.wasSuccessful() else 1)
