# ToolPkg WASM Demo

Minimal ToolPkg example for enterprise-style prime-number logic:

- `src/main.ts` is the ToolPkg authoring entry. Keep public export names here.
- `src/wasm/core.ts` is the typed TS facade used by `src/main.ts`.
- `src/wasm/core.as.ts` contains hot logic compiled by AssemblyScript.
- `manifest.json` declares `wasm_modules`.
- `main.js` and `modules/core.wasm` are generated locally and then packed into the ToolPkg.
- `src/wasm/assemblyscript-env.d.ts` is editor-only metadata for TypeScript language service diagnostics, not a runtime ABI definition.

Author code with normal TS imports:

```ts
import { nthPrime } from "./wasm/core";

export async function nth_prime(params: { index: number }) {
  return { prime: await nthPrime(params.index) };
}
```

Build the JS entry, WASM module, and package the demo:

```bash
npm ci
npm run pack:toolpkg
```

The command writes `dist/toolpkg_wasm_demo.toolpkg`. Generated `main.js`,
`modules/core.wasm`, and `dist/toolpkg_wasm_demo.toolpkg` are intentionally not checked in.
