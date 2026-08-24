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

/// <reference path="../../types/index.d.ts" />
import { toolImpl } from './tools';
import { getRepository, searchRepositories } from './github/repos';
import { listIssues, listIssueComments, createIssue, commentIssue } from './github/issues';
import { listPullRequests, getPullRequest, createPullRequest, mergePullRequest } from './github/pulls';
import { getFileContent, createOrUpdateFile, deleteFile } from './github/contents';
import { createBranch } from './github/branches';
import { patchFileInRepo } from './github/patch';
import { getBaseUrl, getToken } from './github/api';

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

async function main(
  params?: {
    owner?: string;
    repo?: string;
    query?: string;
    path?: string;
    issue_number?: number;
    pull_number?: number;
    enable_write?: boolean;
    pr_head?: string;
    pr_base?: string;
  }
) {
  try {
    const owner = String(params?.owner || 'octocat');
    const repo = String(params?.repo || 'Hello-World');
    const query = String(params?.query || 'operit');
    const path = String(params?.path || 'README.md');
    const enableWrite = params?.enable_write === true;

    const baseUrl = getBaseUrl();
    const token = getToken();

    type TestResult =
      | { ok: true; data: any }
      | { ok: false; error: string; error_stack: string }
      | { ok: false; skipped: true; reason: string };

    const results: Record<string, TestResult> = {};

    const toErrMsg = (e: any) => String(e && e.message ? e.message : e);
    const toErrStack = (e: any) => String(e && e.stack ? e.stack : '');

    const run = async (name: string, fn: () => Promise<any>): Promise<TestResult> => {
      try {
        const data = await fn();
        return { ok: true, data };
      } catch (e: any) {
        return { ok: false, error: toErrMsg(e), error_stack: toErrStack(e) };
      }
    };

    const summarizeRepo = (r: any) =>
      r
        ? {
          id: r.id,
          full_name: r.full_name,
          private: r.private,
          default_branch: r.default_branch
        }
        : r;

    const summarizeList = (items: any[]) => ({
      count: Array.isArray(items) ? items.length : 0,
      first: Array.isArray(items) && items.length > 0 ? items[0] : null
    });

    results.get_repository = await run('get_repository', async () => summarizeRepo(await getRepository({ owner, repo })));

    results.search_repositories = await run('search_repositories', async () => {
      const r = await searchRepositories({ query, per_page: 5 });
      const items = Array.isArray(r?.items) ? r.items : [];
      return {
        total_count: r?.total_count,
        count: items.length,
        first: items[0] ? { id: items[0].id, full_name: items[0].full_name, stargazers_count: items[0].stargazers_count } : null
      };
    });

    results.list_issues = await run('list_issues', async () => summarizeList(await listIssues({ owner, repo, per_page: 5 })));

    let issueNumber: number | undefined = typeof params?.issue_number === 'number' ? params.issue_number : undefined;
    if (!issueNumber && results.list_issues.ok) {
      const first = results.list_issues.data?.first;
      if (first && typeof first.number === 'number') issueNumber = first.number;
    }

    if (issueNumber) {
      results.list_issue_comments = await run('list_issue_comments', async () =>
        summarizeList(await listIssueComments({ owner, repo, issue_number: issueNumber, per_page: 5 }))
      );
    } else {
      results.list_issue_comments = { ok: false, skipped: true, reason: 'No issue_number provided and cannot infer from list_issues.' };
    }

    results.list_pull_requests = await run('list_pull_requests', async () => summarizeList(await listPullRequests({ owner, repo, per_page: 5 })));

    let pullNumber: number | undefined = typeof params?.pull_number === 'number' ? params.pull_number : undefined;
    if (!pullNumber && results.list_pull_requests.ok) {
      const first = results.list_pull_requests.data?.first;
      if (first && typeof first.number === 'number') pullNumber = first.number;
    }

    if (pullNumber) {
      results.get_pull_request = await run('get_pull_request', async () => {
        const pr = await getPullRequest({ owner, repo, pull_number: pullNumber });
        return pr
          ? {
            number: pr.number,
            title: pr.title,
            state: pr.state,
            merged: pr.merged,
            head: pr.head?.ref,
            base: pr.base?.ref
          }
          : pr;
      });
    } else {
      results.get_pull_request = { ok: false, skipped: true, reason: 'No pull_number provided and cannot infer from list_pull_requests.' };
    }

    const inferReadableRepoFilePath = async (): Promise<string | undefined> => {
      if (params?.path) {
        return path;
      }

      try {
        const root = await getFileContent({ owner, repo, path: '' });
        if (!Array.isArray(root)) {
          return undefined;
        }

        const prefer = ['README.md', 'README', 'readme.md', 'Readme.md', 'README.MD'];
        for (const p of prefer) {
          const hit = root.find((it: any) => it && it.type === 'file' && String(it.path || it.name || '') === p);
          if (hit) return String(hit.path || hit.name);
        }

        const firstFile = root.find((it: any) => it && it.type === 'file' && (typeof it.path === 'string' || typeof it.name === 'string'));
        if (firstFile) return String(firstFile.path || firstFile.name);

        return undefined;
      } catch (e) {
        return undefined;
      }
    };

    const resolvedPath = await inferReadableRepoFilePath();
    if (!resolvedPath) {
      results.get_file_content = { ok: false, skipped: true, reason: 'No readable file found in repo root for get_file_content test.' };
    } else {
      results.get_file_content = await run('get_file_content', async () => {
        const f = await getFileContent({ owner, repo, path: resolvedPath });
        return f
          ? {
            type: f.type,
            path: f.path,
            sha: f.sha,
            size: f.size,
            has_decoded_text: typeof f.decoded_text === 'string' && f.decoded_text.length > 0
          }
          : f;
      });
    }

    const writeSkipped = (name: string, reason: string) => {
      results[name] = { ok: false, skipped: true, reason };
    };

    if (!enableWrite) {
      writeSkipped('create_issue', 'Skipped: enable_write=false (write operation).');
      writeSkipped('comment_issue', 'Skipped: enable_write=false (write operation).');
      writeSkipped('create_branch', 'Skipped: enable_write=false (write operation).');
      writeSkipped('create_or_update_file', 'Skipped: enable_write=false (write operation).');
      writeSkipped('patch_file_in_repo', 'Skipped: enable_write=false (write operation).');
      writeSkipped('delete_file', 'Skipped: enable_write=false (write operation).');
      writeSkipped('create_pull_request', 'Skipped: enable_write=false (write operation).');
      writeSkipped('merge_pull_request', 'Skipped: enable_write=false (write operation).');
    } else {
      const ts = Date.now();
      const testBranch = `operit-test-${ts}`;
      const testPath = `operit_test_${ts}.txt`;
      const baseBranch = (results.get_repository.ok ? results.get_repository.data?.default_branch : undefined) || 'main';

      results.create_branch = await run('create_branch', async () =>
        createBranch({ owner, repo, new_branch: testBranch, from_branch: baseBranch })
      );

      results.create_or_update_file = await run('create_or_update_file', async () =>
        createOrUpdateFile({
          owner,
          repo,
          path: testPath,
          message: `operit test create file ${ts}`,
          content: `operit github tools self-test ${ts}`,
          content_encoding: 'utf-8',
          branch: testBranch
        })
      );

      results.patch_file_in_repo = await run('patch_file_in_repo', async () =>
        patchFileInRepo({
          owner,
          repo,
          path: testPath,
          message: `operit test patch file ${ts}`,
          patch: `[START-REPLACE]\n[OLD]\noperit github tools self-test ${ts}\n[/OLD]\n[NEW]\noperit github tools self-test ${ts} (patched)\n[/NEW]\n[END-REPLACE]`,
          branch: testBranch
        })
      );

      results.delete_file = await run('delete_file', async () => {
        const sha =
          results.create_or_update_file.ok && results.create_or_update_file.data && results.create_or_update_file.data.content
            ? results.create_or_update_file.data.content.sha
            : undefined;
        if (!sha) {
          throw new Error('Cannot infer sha from create_or_update_file response; pass sha explicitly if needed.');
        }
        return deleteFile({ owner, repo, path: testPath, message: `operit test delete file ${ts}`, branch: testBranch, sha });
      });

      const canIssue = Boolean(token) && issueNumber !== undefined;
      if (!token) {
        writeSkipped('create_issue', 'Skipped: GITHUB_TOKEN missing (required for write operation).');
      } else {
        results.create_issue = await run('create_issue', async () =>
          createIssue({ owner, repo, title: `operit self-test issue ${ts}`, body: `created by operit github tools self-test ${ts}` })
        );
      }

      if (!canIssue) {
        writeSkipped('comment_issue', 'Skipped: need GITHUB_TOKEN and issue_number (or at least one issue from list_issues).');
      } else {
        results.comment_issue = await run('comment_issue', async () =>
          commentIssue({ owner, repo, issue_number: issueNumber!, body: `operit self-test comment ${ts}` })
        );
      }

      const prHead = params?.pr_head ? String(params.pr_head) : '';
      const prBase = params?.pr_base ? String(params.pr_base) : '';
      if (!token) {
        writeSkipped('create_pull_request', 'Skipped: GITHUB_TOKEN missing (required for write operation).');
      } else if (!prHead || !prBase) {
        writeSkipped('create_pull_request', 'Skipped: pr_head/pr_base not provided (required to create PR).');
      } else {
        results.create_pull_request = await run('create_pull_request', async () =>
          createPullRequest({ owner, repo, title: `operit self-test PR ${ts}`, head: prHead, base: prBase, body: `created by operit self-test ${ts}` })
        );
      }

      if (!token) {
        writeSkipped('merge_pull_request', 'Skipped: GITHUB_TOKEN missing (required for write operation).');
      } else if (!pullNumber) {
        writeSkipped('merge_pull_request', 'Skipped: pull_number missing (required to merge PR).');
      } else {
        results.merge_pull_request = await run('merge_pull_request', async () =>
          mergePullRequest({ owner, repo, pull_number: pullNumber!, merge_method: 'merge' })
        );
      }
    }

    const okCount = Object.values(results).filter((r) => (r as any).ok === true).length;
    const failCount = Object.values(results).filter((r) => (r as any).ok === false && !(r as any).skipped).length;
    const skippedCount = Object.values(results).filter((r) => (r as any).skipped).length;

    complete({
      success: failCount === 0,
      message:
        failCount === 0
          ? 'GitHub tools main test finished.'
          : `GitHub tools main test finished with failures: failed=${failCount}, ok=${okCount}, skipped=${skippedCount}.`,
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
  } catch (error: any) {
    complete({
      success: false,
      message: `GitHub tools main test failed: ${String(error && error.message ? error.message : error)}`,
      error_stack: String(error && error.stack ? error.stack : '')
    });
  }
}

exports.main = main;
