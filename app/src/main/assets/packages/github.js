/* METADATA
{
  "name": "github",

  "display_name": {
      "zh": "GitHub API",
      "en": "GitHub API"
  },
  "description": { "zh": "基于 GitHub REST API 的工具集合（不依赖 GitHub MCP）。包含 GitHub 侧（仓库/Issues/PR/文件提交/分支/差异提交）与本地侧（apply_file 差异更新、terminal 终端）能力。", "en": "A toolkit built on the GitHub REST API (does not depend on GitHub MCP). Includes GitHub-side operations (repos/issues/PRs/commits/branches/diffs) and local-side utilities (apply_file patch updates, terminal)." },
  "category": "Development",
  "env": [
    {
      "name": "GITHUB_TOKEN",
      "description": { "zh": "GitHub API 认证令牌", "en": "GitHub API authentication token" },
      "required": true
    },
    {
      "name": "GITHUB_API_BASE_URL",
      "description": { "zh": "GitHub API 基础 URL", "en": "GitHub API base URL" },
      "required": false,
      "defaultValue": "https://api.github.com"
    }
  ],
  "enabledByDefault": false,
  "tools": [
    {
      "name": "search_repositories",
      "description": { "zh": "搜索 GitHub 仓库（/search/repositories）。", "en": "Search GitHub repositories (/search/repositories)." },
      "parameters": [
        { "name": "query", "description": { "zh": "搜索关键词（GitHub search query）", "en": "Search keywords (GitHub search query)." }, "type": "string", "required": true },
        { "name": "sort", "description": { "zh": "排序字段：stars/forks/help-wanted-issues/updated", "en": "Sort field: stars/forks/help-wanted-issues/updated." }, "type": "string", "required": false },
        { "name": "order", "description": { "zh": "排序方向：desc/asc", "en": "Sort order: desc/asc." }, "type": "string", "required": false },
        { "name": "page", "description": { "zh": "页码（默认 1）", "en": "Page number (default: 1)." }, "type": "number", "required": false },
        { "name": "per_page", "description": { "zh": "每页数量（默认 30，最大 100）", "en": "Items per page (default: 30, max: 100)." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "get_repository",
      "description": { "zh": "获取仓库信息（/repos/{owner}/{repo}）。", "en": "Get repository information (/repos/{owner}/{repo})." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true }
      ]
    },
    {
      "name": "list_issues",
      "description": { "zh": "列出仓库 Issues（/repos/{owner}/{repo}/issues）。注意：GitHub 的 issues API 默认也会包含 PR，需要时可用 include_pull_requests 控制。", "en": "List repository issues (/repos/{owner}/{repo}/issues). Note: GitHub issues API also includes PRs by default; use include_pull_requests when needed." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "state", "description": { "zh": "open/closed/all（默认 open）", "en": "open/closed/all (default: open)." }, "type": "string", "required": false },
        { "name": "labels", "description": { "zh": "labels 逗号分隔", "en": "Labels, comma-separated." }, "type": "string", "required": false },
        { "name": "creator", "description": { "zh": "创建者 login", "en": "Creator login." }, "type": "string", "required": false },
        { "name": "page", "description": { "zh": "页码（默认 1）", "en": "Page number (default: 1)." }, "type": "number", "required": false },
        { "name": "per_page", "description": { "zh": "每页数量（默认 30，最大 100）", "en": "Items per page (default: 30, max: 100)." }, "type": "number", "required": false },
        { "name": "include_pull_requests", "description": { "zh": "是否保留 PR（默认 false，仅返回 issue）", "en": "Whether to include PRs (default: false; issues only)." }, "type": "boolean", "required": false }
      ]
    },
    {
      "name": "create_issue",
      "description": { "zh": "创建 Issue（/repos/{owner}/{repo}/issues）。", "en": "Create an issue (/repos/{owner}/{repo}/issues)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "title", "description": { "zh": "Issue 标题", "en": "Issue title." }, "type": "string", "required": true },
        { "name": "body", "description": { "zh": "Issue 内容", "en": "Issue body." }, "type": "string", "required": false },
        { "name": "labels", "description": { "zh": "labels 数组（字符串数组）", "en": "Labels array (string array)." }, "type": "array", "required": false },
        { "name": "assignees", "description": { "zh": "assignees 数组（字符串数组）", "en": "Assignees array (string array)." }, "type": "array", "required": false }
      ]
    },
    {
      "name": "comment_issue",
      "description": { "zh": "给 Issue/PR 评论（/repos/{owner}/{repo}/issues/{issue_number}/comments）。", "en": "Comment on an issue/PR (/repos/{owner}/{repo}/issues/{issue_number}/comments)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "issue_number", "description": { "zh": "Issue 编号", "en": "Issue number." }, "type": "number", "required": true },
        { "name": "body", "description": { "zh": "评论内容", "en": "Comment body." }, "type": "string", "required": true }
      ]
    },
    {
      "name": "list_issue_comments",
      "description": { "zh": "列出 Issue/PR 的评论（/repos/{owner}/{repo}/issues/{issue_number}/comments）。", "en": "List comments on an issue/PR (/repos/{owner}/{repo}/issues/{issue_number}/comments)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "issue_number", "description": { "zh": "Issue 编号", "en": "Issue number." }, "type": "number", "required": true },
        { "name": "page", "description": { "zh": "页码（默认 1）", "en": "Page number (default: 1)." }, "type": "number", "required": false },
        { "name": "per_page", "description": { "zh": "每页数量（默认 30，最大 100）", "en": "Items per page (default: 30, max: 100)." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "list_pull_requests",
      "description": { "zh": "列出 PR（/repos/{owner}/{repo}/pulls）。", "en": "List pull requests (/repos/{owner}/{repo}/pulls)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "state", "description": { "zh": "open/closed/all（默认 open）", "en": "open/closed/all (default: open)." }, "type": "string", "required": false },
        { "name": "head", "description": { "zh": "head 过滤（可选）", "en": "Head filter (optional)." }, "type": "string", "required": false },
        { "name": "base", "description": { "zh": "base 过滤（可选）", "en": "Base filter (optional)." }, "type": "string", "required": false },
        { "name": "page", "description": { "zh": "页码（默认 1）", "en": "Page number (default: 1)." }, "type": "number", "required": false },
        { "name": "per_page", "description": { "zh": "每页数量（默认 30，最大 100）", "en": "Items per page (default: 30, max: 100)." }, "type": "number", "required": false }
      ]
    },
    {
      "name": "create_pull_request",
      "description": { "zh": "创建 PR（/repos/{owner}/{repo}/pulls）。", "en": "Create a pull request (/repos/{owner}/{repo}/pulls)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "title", "description": { "zh": "PR 标题", "en": "PR title." }, "type": "string", "required": true },
        { "name": "head", "description": { "zh": "源分支，例如 feature-branch 或 owner:branch", "en": "Head branch, e.g. feature-branch or owner:branch." }, "type": "string", "required": true },
        { "name": "base", "description": { "zh": "目标分支，例如 main", "en": "Base branch, e.g. main." }, "type": "string", "required": true },
        { "name": "body", "description": { "zh": "PR 内容", "en": "PR body." }, "type": "string", "required": false },
        { "name": "draft", "description": { "zh": "是否为 Draft", "en": "Whether this is a draft PR." }, "type": "boolean", "required": false }
      ]
    },
    {
      "name": "get_pull_request",
      "description": { "zh": "获取 PR 详情（/repos/{owner}/{repo}/pulls/{pull_number}）。", "en": "Get pull request details (/repos/{owner}/{repo}/pulls/{pull_number})." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "pull_number", "description": { "zh": "PR 编号", "en": "PR number." }, "type": "number", "required": true }
      ]
    },
    {
      "name": "merge_pull_request",
      "description": { "zh": "合并 PR（/repos/{owner}/{repo}/pulls/{pull_number}/merge）。", "en": "Merge a pull request (/repos/{owner}/{repo}/pulls/{pull_number}/merge)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "pull_number", "description": { "zh": "PR 编号", "en": "PR number." }, "type": "number", "required": true },
        { "name": "commit_title", "description": { "zh": "可选，merge commit 标题", "en": "Optional: merge commit title." }, "type": "string", "required": false },
        { "name": "commit_message", "description": { "zh": "可选，merge commit 内容", "en": "Optional: merge commit message." }, "type": "string", "required": false },
        { "name": "merge_method", "description": { "zh": "merge/squash/rebase", "en": "merge/squash/rebase." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "get_file_content",
      "description": { "zh": "读取仓库文件内容（/repos/{owner}/{repo}/contents/{path}）。若文件是 base64 返回，将自动解码为文本并返回。", "en": "Read repository file content (/repos/{owner}/{repo}/contents/{path}). If the API returns base64, it will be decoded and returned as text." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "path", "description": { "zh": "文件路径（如 README.md）", "en": "File path (e.g. README.md)." }, "type": "string", "required": true },
        { "name": "ref", "description": { "zh": "分支/Tag/SHA（可选）", "en": "Branch/Tag/SHA (optional)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "create_or_update_file",
      "description": { "zh": "创建或更新仓库文件并提交（/repos/{owner}/{repo}/contents/{path}，PUT）。", "en": "Create or update a repository file and commit it (/repos/{owner}/{repo}/contents/{path}, PUT)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "path", "description": { "zh": "文件路径", "en": "File path." }, "type": "string", "required": true },
        { "name": "message", "description": { "zh": "提交信息", "en": "Commit message." }, "type": "string", "required": true },
        { "name": "content", "description": { "zh": "文件内容（默认按 utf-8 文本编码为 base64）", "en": "File content (base64-encoded as utf-8 text by default)." }, "type": "string", "required": true },
        { "name": "content_encoding", "description": { "zh": "utf-8/base64（默认 utf-8）", "en": "utf-8/base64 (default: utf-8)." }, "type": "string", "required": false },
        { "name": "branch", "description": { "zh": "目标分支（可选）", "en": "Target branch (optional)." }, "type": "string", "required": false },
        { "name": "sha", "description": { "zh": "可选：已知 sha（更新文件时）", "en": "Optional: known sha (when updating a file)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "patch_file_in_repo",
      "description": { "zh": "对仓库文件做差异更新（传入 [START-REPLACE]/[START-DELETE] 块），并提交。", "en": "Patch a repository file by applying diff blocks ([START-REPLACE]/[START-DELETE]) and commit." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "path", "description": { "zh": "文件路径", "en": "File path." }, "type": "string", "required": true },
        { "name": "message", "description": { "zh": "提交信息", "en": "Commit message." }, "type": "string", "required": true },
        { "name": "patch", "description": { "zh": "差异块字符串（可多块）", "en": "Diff blocks string (can include multiple blocks)." }, "type": "string", "required": true },
        { "name": "branch", "description": { "zh": "目标分支（可选）", "en": "Target branch (optional)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "delete_file",
      "description": { "zh": "删除仓库文件并提交（/repos/{owner}/{repo}/contents/{path}，DELETE）。", "en": "Delete a repository file and commit (/repos/{owner}/{repo}/contents/{path}, DELETE)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "path", "description": { "zh": "文件路径", "en": "File path." }, "type": "string", "required": true },
        { "name": "message", "description": { "zh": "提交信息", "en": "Commit message." }, "type": "string", "required": true },
        { "name": "branch", "description": { "zh": "目标分支（可选）", "en": "Target branch (optional)." }, "type": "string", "required": false },
        { "name": "sha", "description": { "zh": "可选：已知 sha（删除文件时）", "en": "Optional: known sha (when deleting a file)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "create_branch",
      "description": { "zh": "基于已有分支创建新分支（/git/refs）。", "en": "Create a new branch from an existing branch (/git/refs)." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner", "en": "Repository owner." }, "type": "string", "required": true },
        { "name": "repo", "description": { "zh": "仓库名", "en": "Repository name." }, "type": "string", "required": true },
        { "name": "new_branch", "description": { "zh": "新分支名", "en": "New branch name." }, "type": "string", "required": true },
        { "name": "from_branch", "description": { "zh": "源分支（可选，默认使用 default_branch）", "en": "Source branch (optional; defaults to default_branch)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "apply_local_replace",
      "description": { "zh": "使用 Tools.Files.apply 对本地文件做 REPLACE（结构化块）。", "en": "Run Tools.Files.apply REPLACE on a local file (structured blocks)." },
      "parameters": [
        { "name": "path", "description": { "zh": "本地文件路径", "en": "Local file path." }, "type": "string", "required": true },
        { "name": "old", "description": { "zh": "要替换的旧内容片段", "en": "Old content snippet to replace." }, "type": "string", "required": true },
        { "name": "new", "description": { "zh": "替换后的新内容片段", "en": "New content snippet." }, "type": "string", "required": true },
        { "name": "environment", "description": { "zh": "android/linux（可选）", "en": "android/linux (optional)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "apply_local_delete",
      "description": { "zh": "使用 Tools.Files.apply 对本地文件做 DELETE（结构化块）。", "en": "Run Tools.Files.apply DELETE on a local file (structured blocks)." },
      "parameters": [
        { "name": "path", "description": { "zh": "本地文件路径", "en": "Local file path." }, "type": "string", "required": true },
        { "name": "old", "description": { "zh": "要删除的旧内容片段", "en": "Old content snippet to delete." }, "type": "string", "required": true },
        { "name": "environment", "description": { "zh": "android/linux（可选）", "en": "android/linux (optional)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "overwrite_local_file",
      "description": { "zh": "覆盖写入本地文件（如果存在会先删除再写入）。", "en": "Overwrite a local file (if it exists, it will be deleted before writing)." },
      "parameters": [
        { "name": "path", "description": { "zh": "本地文件路径", "en": "Local file path." }, "type": "string", "required": true },
        { "name": "content", "description": { "zh": "完整文件内容", "en": "Full file content." }, "type": "string", "required": true },
        { "name": "environment", "description": { "zh": "android/linux（可选）", "en": "android/linux (optional)." }, "type": "string", "required": false }
      ]
    },
    {
      "name": "terminal_exec",
      "description": { "zh": "在终端会话中执行命令（Tools.System.terminal）。", "en": "Execute a command in a terminal session (Tools.System.terminal)." },
      "parameters": [
        { "name": "command", "description": { "zh": "要执行的命令", "en": "Command to execute." }, "type": "string", "required": true },
        { "name": "session_name", "description": { "zh": "会话名（可选，默认 github_tools_session）", "en": "Session name (optional; default: github_tools_session)." }, "type": "string", "required": false },
        { "name": "timeout_ms", "description": { "zh": "超时时间（毫秒，默认120000）", "en": "Timeout in milliseconds (default 120000)." }, "type": "number", "required": false },
        { "name": "close", "description": { "zh": "是否执行后关闭会话", "en": "Whether to close the session after execution." }, "type": "boolean", "required": false }
      ]
    },
    {
      "name": "main",
      "description": { "zh": "用于快速连通性自测：拉取一个仓库信息并做一次仓库搜索，然后返回结果。", "en": "Quick connectivity self-test: fetch a repository and run a repository search, then return results." },
      "parameters": [
        { "name": "owner", "description": { "zh": "仓库 owner（默认 octocat）", "en": "Repository owner (default: octocat)." }, "type": "string", "required": false },
        { "name": "repo", "description": { "zh": "仓库名（默认 Hello-World）", "en": "Repository name (default: Hello-World)." }, "type": "string", "required": false },
        { "name": "query", "description": { "zh": "搜索关键词（默认 operit）", "en": "Search keyword (default: operit)." }, "type": "string", "required": false }
      ]
    }
  ]
}
*/

var __defProp = Object.defineProperty;
var __defProps = Object.defineProperties;
var __getOwnPropDescs = Object.getOwnPropertyDescriptors;
var __getOwnPropSymbols = Object.getOwnPropertySymbols;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __propIsEnum = Object.prototype.propertyIsEnumerable;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __spreadValues = (a, b) => {
  for (var prop in b || (b = {}))
    if (__hasOwnProp.call(b, prop))
      __defNormalProp(a, prop, b[prop]);
  if (__getOwnPropSymbols)
    for (var prop of __getOwnPropSymbols(b)) {
      if (__propIsEnum.call(b, prop))
        __defNormalProp(a, prop, b[prop]);
    }
  return a;
};
var __spreadProps = (a, b) => __defProps(a, __getOwnPropDescs(b));

// src/utils/wrap.ts
async function wrap(func, params, successMessage, failMessage) {
  try {
    const data = await func(params);
    const result = { success: true, message: successMessage, data };
    complete(result);
  } catch (error) {
    const result = {
      success: false,
      message: `${failMessage}: ${String(error && error.message ? error.message : error)}`,
      error_stack: String(error && error.stack ? error.stack : "")
    };
    complete(result);
  }
}

// src/github/api.ts
function getBaseUrl() {
  const fromEnv = (typeof getEnv === "function" ? getEnv("GITHUB_API_BASE_URL") : void 0) || "";
  return fromEnv && String(fromEnv).trim() || "https://api.github.com";
}
function getToken() {
  const token = (typeof getEnv === "function" ? getEnv("GITHUB_TOKEN") : void 0) || "";
  const trimmed = String(token || "").trim();
  return trimmed ? trimmed : void 0;
}
function buildUrl(pathname, query) {
  const base = getBaseUrl().replace(/\/+$/, "");
  const path = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const qs = [];
  if (query) {
    Object.keys(query).forEach((k) => {
      const v = query[k];
      if (v === void 0 || v === null) return;
      qs.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
    });
  }
  return qs.length > 0 ? `${base}${path}?${qs.join("&")}` : `${base}${path}`;
}
function defaultHeaders(extra) {
  const headers = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "Operit-Examples-GitHub"
  };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (extra) {
    Object.keys(extra).forEach((k) => {
      headers[k] = extra[k];
    });
  }
  return headers;
}
function createHttpClient(timeoutMs = 3e4) {
  return OkHttp.newBuilder().connectTimeout(timeoutMs).readTimeout(timeoutMs).writeTimeout(timeoutMs).build();
}
async function requestJson(options) {
  var _a;
  const client = createHttpClient((_a = options.timeoutMs) != null ? _a : 3e4);
  const req = client.newRequest().url(options.url).method(options.method);
  const headers = defaultHeaders(options.headers);
  req.headers(headers);
  if (options.body !== void 0 && options.body !== null && options.method !== "GET") {
    req.body(JSON.stringify(options.body), "json");
  }
  const resp = await req.build().execute();
  if (!resp.isSuccessful()) {
    throw new Error(`GitHub API Error: ${resp.statusCode} ${resp.statusMessage}
${resp.content}`);
  }
  return resp.json();
}

// src/github/repos.ts
async function searchRepositories(params) {
  var _a, _b;
  const page = (_a = params.page) != null ? _a : 1;
  const perPage = (_b = params.per_page) != null ? _b : 30;
  const url = buildUrl("/search/repositories", {
    q: params.query,
    sort: params.sort,
    order: params.order,
    page,
    per_page: perPage
  });
  return requestJson({ method: "GET", url });
}
async function getRepository(params) {
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}`);
  return requestJson({ method: "GET", url });
}

// src/github/issues.ts
async function listIssues(params) {
  var _a, _b, _c;
  const page = (_a = params.page) != null ? _a : 1;
  const perPage = (_b = params.per_page) != null ? _b : 30;
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`, {
    state: (_c = params.state) != null ? _c : "open",
    labels: params.labels,
    creator: params.creator,
    page,
    per_page: perPage
  });
  const items = await requestJson({ method: "GET", url });
  const includePRs = params.include_pull_requests === true;
  if (includePRs) return items;
  return items.filter((it) => !it || !it.pull_request);
}
async function createIssue(params) {
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for create_issue.");
  }
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues`);
  return requestJson({
    method: "POST",
    url,
    body: {
      title: params.title,
      body: params.body,
      labels: params.labels,
      assignees: params.assignees
    }
  });
}
async function commentIssue(params) {
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for comment_issue.");
  }
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`
  );
  return requestJson({ method: "POST", url, body: { body: params.body } });
}
async function listIssueComments(params) {
  var _a, _b;
  const page = (_a = params.page) != null ? _a : 1;
  const perPage = (_b = params.per_page) != null ? _b : 30;
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/issues/${params.issue_number}/comments`,
    { page, per_page: perPage }
  );
  return requestJson({ method: "GET", url });
}

// src/github/pulls.ts
async function listPullRequests(params) {
  var _a, _b, _c;
  const page = (_a = params.page) != null ? _a : 1;
  const perPage = (_b = params.per_page) != null ? _b : 30;
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`, {
    state: (_c = params.state) != null ? _c : "open",
    head: params.head,
    base: params.base,
    page,
    per_page: perPage
  });
  return requestJson({ method: "GET", url });
}
async function createPullRequest(params) {
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for create_pull_request.");
  }
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls`);
  return requestJson({
    method: "POST",
    url,
    body: {
      title: params.title,
      head: params.head,
      base: params.base,
      body: params.body,
      draft: params.draft
    }
  });
}
async function getPullRequest(params) {
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}`);
  return requestJson({ method: "GET", url });
}
async function mergePullRequest(params) {
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for merge_pull_request.");
  }
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/pulls/${params.pull_number}/merge`
  );
  return requestJson({
    method: "PUT",
    url,
    body: {
      commit_title: params.commit_title,
      commit_message: params.commit_message,
      merge_method: params.merge_method
    }
  });
}

// src/utils/base64.ts
var BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function normalizeBase64Input(value) {
  const cleaned = String(value || "").replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/");
  if (!cleaned) {
    return "";
  }
  const remainder = cleaned.length % 4;
  if (remainder === 1) {
    throw new Error("Invalid base64 string");
  }
  return remainder === 0 ? cleaned : cleaned + "=".repeat(4 - remainder);
}
function decodeBase64ToBytes(value) {
  const normalized = normalizeBase64Input(value);
  const bytes = [];
  for (let i = 0; i < normalized.length; i += 4) {
    const c1 = normalized[i];
    const c2 = normalized[i + 1];
    const c3 = normalized[i + 2];
    const c4 = normalized[i + 3];
    const v1 = BASE64_ALPHABET.indexOf(c1);
    const v2 = BASE64_ALPHABET.indexOf(c2);
    const v3 = c3 === "=" ? 0 : BASE64_ALPHABET.indexOf(c3);
    const v4 = c4 === "=" ? 0 : BASE64_ALPHABET.indexOf(c4);
    if (v1 < 0 || v2 < 0 || c3 !== "=" && v3 < 0 || c4 !== "=" && v4 < 0) {
      throw new Error("Invalid base64 string");
    }
    const chunk = v1 << 18 | v2 << 12 | v3 << 6 | v4;
    bytes.push(chunk >> 16 & 255);
    if (c3 !== "=") {
      bytes.push(chunk >> 8 & 255);
    }
    if (c4 !== "=") {
      bytes.push(chunk & 255);
    }
  }
  return bytes;
}
function encodeBytesToBase64(bytes) {
  let output = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b1 = bytes[i];
    const hasB2 = i + 1 < bytes.length;
    const hasB3 = i + 2 < bytes.length;
    const b2 = hasB2 ? bytes[i + 1] : 0;
    const b3 = hasB3 ? bytes[i + 2] : 0;
    const chunk = b1 << 16 | b2 << 8 | b3;
    output += BASE64_ALPHABET[chunk >> 18 & 63];
    output += BASE64_ALPHABET[chunk >> 12 & 63];
    output += hasB2 ? BASE64_ALPHABET[chunk >> 6 & 63] : "=";
    output += hasB3 ? BASE64_ALPHABET[chunk & 63] : "=";
  }
  return output;
}
function utf8Encode(text) {
  const bytes = [];
  const input = String(text != null ? text : "");
  for (let i = 0; i < input.length; i++) {
    const codePoint = input.codePointAt(i);
    if (codePoint == null) {
      continue;
    }
    if (codePoint > 65535) {
      i += 1;
    }
    if (codePoint <= 127) {
      bytes.push(codePoint);
    } else if (codePoint <= 2047) {
      bytes.push(
        192 | codePoint >> 6,
        128 | codePoint & 63
      );
    } else if (codePoint <= 65535) {
      bytes.push(
        224 | codePoint >> 12,
        128 | codePoint >> 6 & 63,
        128 | codePoint & 63
      );
    } else {
      bytes.push(
        240 | codePoint >> 18,
        128 | codePoint >> 12 & 63,
        128 | codePoint >> 6 & 63,
        128 | codePoint & 63
      );
    }
  }
  return bytes;
}
function readContinuationByte(bytes, index) {
  const value = bytes[index];
  if (value == null || (value & 192) !== 128) {
    throw new Error("Invalid UTF-8 sequence");
  }
  return value & 63;
}
function utf8Decode(bytes) {
  let output = "";
  for (let i = 0; i < bytes.length; i++) {
    const first = bytes[i];
    if (first == null) {
      break;
    }
    if (first <= 127) {
      output += String.fromCodePoint(first);
      continue;
    }
    if ((first & 224) === 192) {
      const codePoint = (first & 31) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    if ((first & 240) === 224) {
      const codePoint = (first & 15) << 12 | readContinuationByte(bytes, ++i) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    if ((first & 248) === 240) {
      const codePoint = (first & 7) << 18 | readContinuationByte(bytes, ++i) << 12 | readContinuationByte(bytes, ++i) << 6 | readContinuationByte(bytes, ++i);
      output += String.fromCodePoint(codePoint);
      continue;
    }
    throw new Error("Invalid UTF-8 sequence");
  }
  return output;
}
function safeAtobBase64(b64) {
  return utf8Decode(decodeBase64ToBytes(b64));
}
function safeBtoaBase64(text) {
  return encodeBytesToBase64(utf8Encode(String(text != null ? text : "")));
}

// src/github/contents.ts
async function getFileContent(params) {
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`,
    { ref: params.ref }
  );
  const data = await requestJson({ method: "GET", url });
  if (data && data.type === "file" && typeof data.content === "string" && data.encoding === "base64") {
    const decoded = safeAtobBase64(data.content);
    return __spreadProps(__spreadValues({}, data), {
      decoded_text: decoded
    });
  }
  return data;
}
async function resolveFileSha(params) {
  try {
    const existing = await getFileContent({
      owner: params.owner,
      repo: params.repo,
      path: params.path,
      ref: params.branch
    });
    const sha = existing && typeof existing.sha === "string" ? existing.sha : void 0;
    return sha;
  } catch (e) {
    return void 0;
  }
}
async function createOrUpdateFile(params) {
  var _a;
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for create_or_update_file.");
  }
  const encoding = (params.content_encoding || "utf-8").toLowerCase();
  const base64Content = encoding === "base64" ? params.content : safeBtoaBase64(params.content);
  const sha = (_a = params.sha) != null ? _a : await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch });
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`
  );
  return requestJson({
    method: "PUT",
    url,
    body: {
      message: params.message,
      content: base64Content,
      branch: params.branch,
      sha
    }
  });
}
async function deleteFile(params) {
  var _a;
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for delete_file.");
  }
  const sha = (_a = params.sha) != null ? _a : await resolveFileSha({ owner: params.owner, repo: params.repo, path: params.path, branch: params.branch });
  if (!sha) {
    throw new Error("File sha is required (unable to resolve automatically).");
  }
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/contents/${params.path.split("/").map((p) => encodeURIComponent(p)).join("/")}`
  );
  return requestJson({
    method: "DELETE",
    url,
    body: {
      message: params.message,
      branch: params.branch,
      sha
    }
  });
}

// src/github/branches.ts
async function getBranchHeadSha(params) {
  var _a;
  const url = buildUrl(
    `/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/ref/heads/${encodeURIComponent(
      params.branch
    )}`
  );
  const data = await requestJson({ method: "GET", url });
  const sha = (_a = data == null ? void 0 : data.object) == null ? void 0 : _a.sha;
  if (!sha) {
    throw new Error(`Cannot resolve branch sha for ${params.branch}`);
  }
  return sha;
}
async function createBranch(params) {
  var _a, _b;
  const token = getToken();
  if (!token) {
    throw new Error("GITHUB_TOKEN is required for create_branch.");
  }
  const fromBranch = (_b = params.from_branch) != null ? _b : String(((_a = await getRepository({ owner: params.owner, repo: params.repo })) == null ? void 0 : _a.default_branch) || "main");
  const sha = await getBranchHeadSha({ owner: params.owner, repo: params.repo, branch: fromBranch });
  const url = buildUrl(`/repos/${encodeURIComponent(params.owner)}/${encodeURIComponent(params.repo)}/git/refs`);
  return requestJson({
    method: "POST",
    url,
    body: {
      ref: `refs/heads/${params.new_branch}`,
      sha
    }
  });
}

// src/github/patch.ts
function normalizeNewlines(text) {
  return String(text != null ? text : "").replace(/\r\n/g, "\n");
}
function parseBlocks(patch) {
  const src = normalizeNewlines(patch);
  const ops = [];
  const blockRegex = /\[START-(REPLACE|DELETE)\]\s*\n([\s\S]*?)\[END-\1\]/g;
  let match;
  while ((match = blockRegex.exec(src)) !== null) {
    const action = match[1];
    const body = match[2] || "";
    const oldMatch = /\[OLD\]\s*\n([\s\S]*?)\n\[\/OLD\]/.exec(body);
    if (!oldMatch) {
      throw new Error(`Patch block missing [OLD] section for ${action}`);
    }
    const oldContent = oldMatch[1];
    let newContent = "";
    if (action === "REPLACE") {
      const newMatch = /\[NEW\]\s*\n([\s\S]*?)\n\[\/NEW\]/.exec(body);
      if (!newMatch) {
        throw new Error("REPLACE block missing [NEW] section");
      }
      newContent = newMatch[1];
    }
    ops.push({ action, oldContent, newContent });
  }
  if (ops.length === 0) {
    throw new Error("No patch blocks found. Use [START-REPLACE]/[START-DELETE] blocks.");
  }
  return ops;
}
function applyOps(original, ops) {
  let out = normalizeNewlines(original);
  for (const op of ops) {
    if (!out.includes(op.oldContent)) {
      throw new Error("OLD block content not found in target file.");
    }
    if (op.action === "DELETE") {
      out = out.replace(op.oldContent, "");
    } else {
      out = out.replace(op.oldContent, op.newContent);
    }
  }
  return out;
}
async function patchFileInRepo(params) {
  const file = await getFileContent({
    owner: params.owner,
    repo: params.repo,
    path: params.path,
    ref: params.branch
  });
  const originalText = typeof (file == null ? void 0 : file.decoded_text) === "string" ? file.decoded_text : "";
  const sha = typeof (file == null ? void 0 : file.sha) === "string" ? file.sha : void 0;
  if (!sha) {
    throw new Error("Cannot patch file: sha missing (is it a file path?)");
  }
  const ops = parseBlocks(params.patch);
  const patched = applyOps(originalText, ops);
  return createOrUpdateFile({
    owner: params.owner,
    repo: params.repo,
    path: params.path,
    message: params.message,
    content: patched,
    content_encoding: "utf-8",
    branch: params.branch,
    sha
  });
}

// src/local/fileApply.ts
async function applyLocalReplace(params) {
  return Tools.Files.apply(params.path, "replace", params.old, params.new, params.environment);
}
async function applyLocalDelete(params) {
  return Tools.Files.apply(params.path, "delete", params.old, void 0, params.environment);
}
async function overwriteLocalFile(params) {
  var _a;
  const exists = await Tools.Files.exists(params.path, params.environment);
  if (exists.exists) {
    await Tools.Files.deleteFile(params.path, true, params.environment);
  }
  return Tools.Files.write(params.path, String((_a = params.content) != null ? _a : ""), false, params.environment);
}

// src/local/terminal.ts
var DEFAULT_SESSION_NAME = "github_tools_session";
var DEFAULT_TIMEOUT_MS = 12e4;
var terminalSessionId = null;
var terminalSessionName = null;
var sessionCreationPromise = null;
function normalizeSessionName(sessionName) {
  const normalized = String(sessionName || "").trim();
  return normalized || DEFAULT_SESSION_NAME;
}
function invalidateSession(sessionId) {
  if (!sessionId || terminalSessionId === sessionId) {
    terminalSessionId = null;
    terminalSessionName = null;
  }
}
async function closeSessionQuietly(sessionId) {
  if (!sessionId) return;
  try {
    await Tools.System.terminal.close(sessionId);
  } catch (_error) {
  } finally {
    invalidateSession(sessionId);
  }
}
function isSessionLifecycleError(error) {
  const message = String(error && error.message ? error.message : error || "").toLowerCase();
  const mentionsSession = message.includes("session") || message.includes("\u4F1A\u8BDD") || message.includes("pty");
  const mentionsLifecycle = message.includes("not exist") || message.includes("does not exist") || message.includes("closed") || message.includes("invalid") || message.includes("unavailable") || message.includes("\u4E0D\u5B58\u5728") || message.includes("\u5173\u95ED") || message.includes("\u5931\u6548") || message.includes("\u4E0D\u53EF\u7528");
  return mentionsSession && mentionsLifecycle;
}
function isTimeoutError(error) {
  const message = String(error && error.message ? error.message : error || "").toLowerCase();
  return message.includes("timed out") || message.includes("timeout") || message.includes("\u8D85\u65F6");
}
function normalizeTimeout(timeoutMs) {
  const value = timeoutMs === void 0 || timeoutMs === null ? DEFAULT_TIMEOUT_MS : Number(timeoutMs);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error("timeout_ms must be a positive number");
  }
  return Math.floor(value);
}
async function getTerminalSession(sessionName) {
  const normalizedName = normalizeSessionName(sessionName);
  if (terminalSessionId && terminalSessionName === normalizedName) {
    return terminalSessionId;
  }
  if (sessionCreationPromise) {
    return sessionCreationPromise;
  }
  sessionCreationPromise = (async () => {
    if (terminalSessionId) {
      await closeSessionQuietly(terminalSessionId);
    }
    const session = await Tools.System.terminal.create(normalizedName);
    if (!session || !session.sessionId) {
      throw new Error(`Terminal session creation returned no sessionId for ${normalizedName}`);
    }
    terminalSessionId = String(session.sessionId);
    terminalSessionName = normalizedName;
    return terminalSessionId;
  })();
  try {
    return await sessionCreationPromise;
  } finally {
    sessionCreationPromise = null;
  }
}
async function terminalExec(params) {
  const sessionName = normalizeSessionName(params.session_name);
  const timeoutMs = normalizeTimeout(params.timeout_ms);
  let sessionId = await getTerminalSession(sessionName);
  try {
    let result;
    try {
      result = await Tools.System.terminal.exec(sessionId, params.command, timeoutMs);
    } catch (error) {
      if (isTimeoutError(error)) {
        await closeSessionQuietly(sessionId);
        throw error;
      }
      if (!isSessionLifecycleError(error)) {
        throw error;
      }
      invalidateSession(sessionId);
      sessionId = await getTerminalSession(sessionName);
      result = await Tools.System.terminal.exec(sessionId, params.command, timeoutMs);
    }
    if (result && result.timedOut === true) {
      await closeSessionQuietly(sessionId);
      throw new Error(`Terminal command timed out after ${timeoutMs}ms`);
    }
    return result;
  } finally {
    if (params.close) {
      await closeSessionQuietly(sessionId);
    }
  }
}

// src/tools.ts
var toolImpl = {
  search_repositories: (p) => wrap(searchRepositories, p, "\u641C\u7D22\u4ED3\u5E93\u6210\u529F", "\u641C\u7D22\u4ED3\u5E93\u5931\u8D25"),
  get_repository: (p) => wrap(getRepository, p, "\u83B7\u53D6\u4ED3\u5E93\u4FE1\u606F\u6210\u529F", "\u83B7\u53D6\u4ED3\u5E93\u4FE1\u606F\u5931\u8D25"),
  list_issues: (p) => wrap(listIssues, p, "\u83B7\u53D6 Issues \u6210\u529F", "\u83B7\u53D6 Issues \u5931\u8D25"),
  create_issue: (p) => wrap(createIssue, p, "\u521B\u5EFA Issue \u6210\u529F", "\u521B\u5EFA Issue \u5931\u8D25"),
  comment_issue: (p) => wrap(commentIssue, p, "\u53D1\u8868\u8BC4\u8BBA\u6210\u529F", "\u53D1\u8868\u8BC4\u8BBA\u5931\u8D25"),
  list_issue_comments: (p) => wrap(listIssueComments, p, "\u83B7\u53D6 Issue \u8BC4\u8BBA\u6210\u529F", "\u83B7\u53D6 Issue \u8BC4\u8BBA\u5931\u8D25"),
  list_pull_requests: (p) => wrap(listPullRequests, p, "\u83B7\u53D6 PR \u5217\u8868\u6210\u529F", "\u83B7\u53D6 PR \u5217\u8868\u5931\u8D25"),
  create_pull_request: (p) => wrap(createPullRequest, p, "\u521B\u5EFA PR \u6210\u529F", "\u521B\u5EFA PR \u5931\u8D25"),
  get_pull_request: (p) => wrap(getPullRequest, p, "\u83B7\u53D6 PR \u6210\u529F", "\u83B7\u53D6 PR \u5931\u8D25"),
  merge_pull_request: (p) => wrap(mergePullRequest, p, "\u5408\u5E76 PR \u6210\u529F", "\u5408\u5E76 PR \u5931\u8D25"),
  get_file_content: (p) => wrap(getFileContent, p, "\u8BFB\u53D6\u6587\u4EF6\u6210\u529F", "\u8BFB\u53D6\u6587\u4EF6\u5931\u8D25"),
  create_or_update_file: (p) => wrap(createOrUpdateFile, p, "\u63D0\u4EA4\u6587\u4EF6\u6210\u529F", "\u63D0\u4EA4\u6587\u4EF6\u5931\u8D25"),
  delete_file: (p) => wrap(deleteFile, p, "\u5220\u9664\u6587\u4EF6\u6210\u529F", "\u5220\u9664\u6587\u4EF6\u5931\u8D25"),
  create_branch: (p) => wrap(createBranch, p, "\u521B\u5EFA\u5206\u652F\u6210\u529F", "\u521B\u5EFA\u5206\u652F\u5931\u8D25"),
  patch_file_in_repo: (p) => wrap(patchFileInRepo, p, "\u4ED3\u5E93\u6587\u4EF6\u5DEE\u5F02\u66F4\u65B0\u6210\u529F", "\u4ED3\u5E93\u6587\u4EF6\u5DEE\u5F02\u66F4\u65B0\u5931\u8D25"),
  apply_local_replace: (p) => wrap(applyLocalReplace, p, "\u672C\u5730\u5DEE\u5F02\u66F4\u65B0\u6210\u529F", "\u672C\u5730\u5DEE\u5F02\u66F4\u65B0\u5931\u8D25"),
  apply_local_delete: (p) => wrap(applyLocalDelete, p, "\u672C\u5730\u5220\u9664\u7247\u6BB5\u6210\u529F", "\u672C\u5730\u5220\u9664\u7247\u6BB5\u5931\u8D25"),
  overwrite_local_file: (p) => wrap(overwriteLocalFile, p, "\u672C\u5730\u8986\u76D6\u5199\u5165\u6210\u529F", "\u672C\u5730\u8986\u76D6\u5199\u5165\u5931\u8D25"),
  terminal_exec: (p) => wrap(terminalExec, p, "\u7EC8\u7AEF\u6267\u884C\u6210\u529F", "\u7EC8\u7AEF\u6267\u884C\u5931\u8D25")
};

// src/index.ts
exports.search_repositories = toolImpl.search_repositories;
exports.get_repository = toolImpl.get_repository;
exports.list_issues = toolImpl.list_issues;
exports.create_issue = toolImpl.create_issue;
exports.comment_issue = toolImpl.comment_issue;
exports.list_issue_comments = toolImpl.list_issue_comments;
exports.list_pull_requests = toolImpl.list_pull_requests;
exports.create_pull_request = toolImpl.create_pull_request;
exports.get_pull_request = toolImpl.get_pull_request;
exports.merge_pull_request = toolImpl.merge_pull_request;
exports.get_file_content = toolImpl.get_file_content;
exports.create_or_update_file = toolImpl.create_or_update_file;
exports.patch_file_in_repo = toolImpl.patch_file_in_repo;
exports.delete_file = toolImpl.delete_file;
exports.create_branch = toolImpl.create_branch;
exports.apply_local_replace = toolImpl.apply_local_replace;
exports.apply_local_delete = toolImpl.apply_local_delete;
exports.overwrite_local_file = toolImpl.overwrite_local_file;
exports.terminal_exec = toolImpl.terminal_exec;
async function main(params) {
  var _a, _b, _c;
  try {
    const owner = String((params == null ? void 0 : params.owner) || "octocat");
    const repo = String((params == null ? void 0 : params.repo) || "Hello-World");
    const query = String((params == null ? void 0 : params.query) || "operit");
    const path = String((params == null ? void 0 : params.path) || "README.md");
    const enableWrite = (params == null ? void 0 : params.enable_write) === true;
    const baseUrl = getBaseUrl();
    const token = getToken();
    const results = {};
    const toErrMsg = (e) => String(e && e.message ? e.message : e);
    const toErrStack = (e) => String(e && e.stack ? e.stack : "");
    const run = async (name, fn) => {
      try {
        const data = await fn();
        return { ok: true, data };
      } catch (e) {
        return { ok: false, error: toErrMsg(e), error_stack: toErrStack(e) };
      }
    };
    const summarizeRepo = (r) => r ? {
      id: r.id,
      full_name: r.full_name,
      private: r.private,
      default_branch: r.default_branch
    } : r;
    const summarizeList = (items) => ({
      count: Array.isArray(items) ? items.length : 0,
      first: Array.isArray(items) && items.length > 0 ? items[0] : null
    });
    results.get_repository = await run("get_repository", async () => summarizeRepo(await getRepository({ owner, repo })));
    results.search_repositories = await run("search_repositories", async () => {
      const r = await searchRepositories({ query, per_page: 5 });
      const items = Array.isArray(r == null ? void 0 : r.items) ? r.items : [];
      return {
        total_count: r == null ? void 0 : r.total_count,
        count: items.length,
        first: items[0] ? { id: items[0].id, full_name: items[0].full_name, stargazers_count: items[0].stargazers_count } : null
      };
    });
    results.list_issues = await run("list_issues", async () => summarizeList(await listIssues({ owner, repo, per_page: 5 })));
    let issueNumber = typeof (params == null ? void 0 : params.issue_number) === "number" ? params.issue_number : void 0;
    if (!issueNumber && results.list_issues.ok) {
      const first = (_a = results.list_issues.data) == null ? void 0 : _a.first;
      if (first && typeof first.number === "number") issueNumber = first.number;
    }
    if (issueNumber) {
      results.list_issue_comments = await run(
        "list_issue_comments",
        async () => summarizeList(await listIssueComments({ owner, repo, issue_number: issueNumber, per_page: 5 }))
      );
    } else {
      results.list_issue_comments = { ok: false, skipped: true, reason: "No issue_number provided and cannot infer from list_issues." };
    }
    results.list_pull_requests = await run("list_pull_requests", async () => summarizeList(await listPullRequests({ owner, repo, per_page: 5 })));
    let pullNumber = typeof (params == null ? void 0 : params.pull_number) === "number" ? params.pull_number : void 0;
    if (!pullNumber && results.list_pull_requests.ok) {
      const first = (_b = results.list_pull_requests.data) == null ? void 0 : _b.first;
      if (first && typeof first.number === "number") pullNumber = first.number;
    }
    if (pullNumber) {
      results.get_pull_request = await run("get_pull_request", async () => {
        var _a2, _b2;
        const pr = await getPullRequest({ owner, repo, pull_number: pullNumber });
        return pr ? {
          number: pr.number,
          title: pr.title,
          state: pr.state,
          merged: pr.merged,
          head: (_a2 = pr.head) == null ? void 0 : _a2.ref,
          base: (_b2 = pr.base) == null ? void 0 : _b2.ref
        } : pr;
      });
    } else {
      results.get_pull_request = { ok: false, skipped: true, reason: "No pull_number provided and cannot infer from list_pull_requests." };
    }
    const inferReadableRepoFilePath = async () => {
      if (params == null ? void 0 : params.path) {
        return path;
      }
      try {
        const root = await getFileContent({ owner, repo, path: "" });
        if (!Array.isArray(root)) {
          return void 0;
        }
        const prefer = ["README.md", "README", "readme.md", "Readme.md", "README.MD"];
        for (const p of prefer) {
          const hit = root.find((it) => it && it.type === "file" && String(it.path || it.name || "") === p);
          if (hit) return String(hit.path || hit.name);
        }
        const firstFile = root.find((it) => it && it.type === "file" && (typeof it.path === "string" || typeof it.name === "string"));
        if (firstFile) return String(firstFile.path || firstFile.name);
        return void 0;
      } catch (e) {
        return void 0;
      }
    };
    const resolvedPath = await inferReadableRepoFilePath();
    if (!resolvedPath) {
      results.get_file_content = { ok: false, skipped: true, reason: "No readable file found in repo root for get_file_content test." };
    } else {
      results.get_file_content = await run("get_file_content", async () => {
        const f = await getFileContent({ owner, repo, path: resolvedPath });
        return f ? {
          type: f.type,
          path: f.path,
          sha: f.sha,
          size: f.size,
          has_decoded_text: typeof f.decoded_text === "string" && f.decoded_text.length > 0
        } : f;
      });
    }
    const writeSkipped = (name, reason) => {
      results[name] = { ok: false, skipped: true, reason };
    };
    if (!enableWrite) {
      writeSkipped("create_issue", "Skipped: enable_write=false (write operation).");
      writeSkipped("comment_issue", "Skipped: enable_write=false (write operation).");
      writeSkipped("create_branch", "Skipped: enable_write=false (write operation).");
      writeSkipped("create_or_update_file", "Skipped: enable_write=false (write operation).");
      writeSkipped("patch_file_in_repo", "Skipped: enable_write=false (write operation).");
      writeSkipped("delete_file", "Skipped: enable_write=false (write operation).");
      writeSkipped("create_pull_request", "Skipped: enable_write=false (write operation).");
      writeSkipped("merge_pull_request", "Skipped: enable_write=false (write operation).");
    } else {
      const ts = Date.now();
      const testBranch = `operit-test-${ts}`;
      const testPath = `operit_test_${ts}.txt`;
      const baseBranch = (results.get_repository.ok ? (_c = results.get_repository.data) == null ? void 0 : _c.default_branch : void 0) || "main";
      results.create_branch = await run(
        "create_branch",
        async () => createBranch({ owner, repo, new_branch: testBranch, from_branch: baseBranch })
      );
      results.create_or_update_file = await run(
        "create_or_update_file",
        async () => createOrUpdateFile({
          owner,
          repo,
          path: testPath,
          message: `operit test create file ${ts}`,
          content: `operit github tools self-test ${ts}`,
          content_encoding: "utf-8",
          branch: testBranch
        })
      );
      results.patch_file_in_repo = await run(
        "patch_file_in_repo",
        async () => patchFileInRepo({
          owner,
          repo,
          path: testPath,
          message: `operit test patch file ${ts}`,
          patch: `[START-REPLACE]
[OLD]
operit github tools self-test ${ts}
[/OLD]
[NEW]
operit github tools self-test ${ts} (patched)
[/NEW]
[END-REPLACE]`,
          branch: testBranch
        })
      );
      results.delete_file = await run("delete_file", async () => {
        const sha = results.create_or_update_file.ok && results.create_or_update_file.data && results.create_or_update_file.data.content ? results.create_or_update_file.data.content.sha : void 0;
        if (!sha) {
          throw new Error("Cannot infer sha from create_or_update_file response; pass sha explicitly if needed.");
        }
        return deleteFile({ owner, repo, path: testPath, message: `operit test delete file ${ts}`, branch: testBranch, sha });
      });
      const canIssue = Boolean(token) && issueNumber !== void 0;
      if (!token) {
        writeSkipped("create_issue", "Skipped: GITHUB_TOKEN missing (required for write operation).");
      } else {
        results.create_issue = await run(
          "create_issue",
          async () => createIssue({ owner, repo, title: `operit self-test issue ${ts}`, body: `created by operit github tools self-test ${ts}` })
        );
      }
      if (!canIssue) {
        writeSkipped("comment_issue", "Skipped: need GITHUB_TOKEN and issue_number (or at least one issue from list_issues).");
      } else {
        results.comment_issue = await run(
          "comment_issue",
          async () => commentIssue({ owner, repo, issue_number: issueNumber, body: `operit self-test comment ${ts}` })
        );
      }
      const prHead = (params == null ? void 0 : params.pr_head) ? String(params.pr_head) : "";
      const prBase = (params == null ? void 0 : params.pr_base) ? String(params.pr_base) : "";
      if (!token) {
        writeSkipped("create_pull_request", "Skipped: GITHUB_TOKEN missing (required for write operation).");
      } else if (!prHead || !prBase) {
        writeSkipped("create_pull_request", "Skipped: pr_head/pr_base not provided (required to create PR).");
      } else {
        results.create_pull_request = await run(
          "create_pull_request",
          async () => createPullRequest({ owner, repo, title: `operit self-test PR ${ts}`, head: prHead, base: prBase, body: `created by operit self-test ${ts}` })
        );
      }
      if (!token) {
        writeSkipped("merge_pull_request", "Skipped: GITHUB_TOKEN missing (required for write operation).");
      } else if (!pullNumber) {
        writeSkipped("merge_pull_request", "Skipped: pull_number missing (required to merge PR).");
      } else {
        results.merge_pull_request = await run(
          "merge_pull_request",
          async () => mergePullRequest({ owner, repo, pull_number: pullNumber, merge_method: "merge" })
        );
      }
    }
    const okCount = Object.values(results).filter((r) => r.ok === true).length;
    const failCount = Object.values(results).filter((r) => r.ok === false && !r.skipped).length;
    const skippedCount = Object.values(results).filter((r) => r.skipped).length;
    complete({
      success: failCount === 0,
      message: failCount === 0 ? "GitHub tools main test finished." : `GitHub tools main test finished with failures: failed=${failCount}, ok=${okCount}, skipped=${skippedCount}.`,
      data: {
        baseUrl,
        hasToken: Boolean(token),
        enable_write: enableWrite,
        owner,
        repo,
        summary: { ok: okCount, failed: failCount, skipped: skippedCount },
        results
      }
    });
  } catch (error) {
    complete({
      success: false,
      message: `GitHub tools main test failed: ${String(error && error.message ? error.message : error)}`,
      error_stack: String(error && error.stack ? error.stack : "")
    });
  }
}
exports.main = main;
