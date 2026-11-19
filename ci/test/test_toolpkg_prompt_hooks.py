from __future__ import annotations

import shutil
import subprocess
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
REGRESSION_SCRIPT = REPO_ROOT / "tools" / "toolpkg" / "prompt_hook_regression.js"


@unittest.skipUnless(shutil.which("node"), "node is required to run the ToolPkg prompt hook regression")
class ToolPkgPromptHookRegressionTest(unittest.TestCase):
    """Regression coverage for issue #1258.

    A minimal *external* ToolPkg is loaded through the real generated ToolPkg
    registration bridge and each supported prompt hook is dispatched through the
    real execution runtime, mirroring a normal chat request. This guards both the
    external-package-load half (hooks must be captured with a resolvable function
    reference) and the chat-dispatch half (each hook must fire and mutate) without
    needing an Android device.
    """

    def test_external_toolpkg_prompt_hooks_register_and_dispatch(self) -> None:
        completed = subprocess.run(
            ["node", str(REGRESSION_SCRIPT)],
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        output = completed.stdout + completed.stderr
        self.assertEqual(
            completed.returncode,
            0,
            msg=f"prompt hook regression failed:\n{output}",
        )
        self.assertIn("ALL PROMPT-HOOK CHECKS PASSED", output)


if __name__ == "__main__":
    unittest.main()
