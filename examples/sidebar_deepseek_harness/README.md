# Sidebar DeepSeek Harness

`sidebar_deepseek_harness` is a ToolPkg wrapper around the upstream DeepSeek Harness Web Runtime. It does not translate Cordis plugins into ToolPkg APIs. The Linux terminal runs the original Node runtime, while the ToolPkg sidebar displays its local Web UI.

## Runtime Contract

- Requires `node` and `pnpm` in the Linux terminal environment
- Installs the fixed upstream CLI `@deepseek-ai/dsh@0.1.0-rc.6` under `/root/sidebar_deepseek_harness/node_modules`
- Stores Harness profiles and sessions under `/root/sidebar_deepseek_harness/dsh-home`
- Listens only at `http://127.0.0.1:3080`
- Writes server output to `/root/sidebar_deepseek_harness/deepseek-harness-web.log`

The first sidebar visit creates the runtime package manifest and installs the fixed DSH version with pnpm. Later visits reuse the existing local process when its loopback server responds.

## Scope

This package provides the original DSH Web Runtime. DSH NPM bundles continue to be installed and managed by DSH itself. Native ToolPkg import, Android/Java bridge access, and offline tarball management are intentionally outside this first version.
