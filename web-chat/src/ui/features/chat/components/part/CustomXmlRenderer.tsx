import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { DetailsTagRenderer } from './DetailsTagRenderer';
import { ContentDetailDialog } from './DialogComponents';
import { MarkdownRenderer } from './MarkdownRenderer';
import { AnimatedExpandBody, StructuredExpandRow } from './StructuredExpand';
import { ToolDisplayComponent } from './ToolDisplayComponents';
import { ToolResultDisplay } from './ToolResultDisplay';
import type { WebMessageContentBlock } from '../../util/chatTypes';

const BUILT_IN_TAGS = new Set([
  'think',
  'thinking',
  'search',
  'tool',
  'status',
  'tool_result',
  'html',
  'mood',
  'font',
  'details',
  'detail',
  'meta'
]);

const EXPAND_THINKING_PROCESS_STORAGE_KEY = 'expand_thinking_process_default';
const READ_ONLY_GROUPABLE_TOOL_NAMES = new Set([
  'list_files',
  'grep_code',
  'grep_context',
  'read_file',
  'read_file_part',
  'read_file_full',
  'read_file_binary',
  'use_package',
  'find_files',
  'visit_web'
]);

const RESPONSES_WEB_SEARCH_PROVIDER = 'openai:responses_web_search';

interface ResponsesWebSearchAction {
  type?: string;
  query?: string;
  // DeepSeek 批量搜索返回 queries；未解析该字段会隐藏合法 metadata。
  queries?: string[];
}

interface ResponsesWebSearchPayload {
  type?: string;
  id?: string;
  status?: string;
  action?: ResponsesWebSearchAction | null;
}

interface ServerToolRecord {
  toolName: string;
  id: string;
  rawJson: string;
  status: string;
  query: string | null;
}

type ToolCollapseMode = 'read_only' | 'all' | 'full' | string;

function readThinkingExpandedPreference() {
  if (typeof window === 'undefined') {
    return false;
  }
  return window.localStorage.getItem(EXPAND_THINKING_PROCESS_STORAGE_KEY) === 'true';
}

function writeThinkingExpandedPreference(expanded: boolean) {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(EXPAND_THINKING_PROCESS_STORAGE_KEY, expanded ? 'true' : 'false');
}

function shouldHideGeminiThoughtSignatureMeta(block: WebMessageContentBlock) {
  return (
    block.tag_name === 'meta' &&
    (block.attrs?.provider?.trim()?.toLowerCase() ?? '') === 'gemini:thought_signature'
  );
}

function isResponsesWebSearchMeta(block: WebMessageContentBlock) {
  return (
    block.tag_name === 'meta' &&
    (block.attrs?.provider?.trim() ?? '') === RESPONSES_WEB_SEARCH_PROVIDER
  );
}

function decodeBase64Utf8(payload: string) {
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(payload) || payload.length % 4 !== 0) {
    throw new Error('invalid base64 payload');
  }

  const binary = atob(payload);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}

function decodeWebSearchActionQuery(action: ResponsesWebSearchAction) {
  const hasType = Object.prototype.hasOwnProperty.call(action, 'type');
  const hasQuery = Object.prototype.hasOwnProperty.call(action, 'query');
  const hasQueries = Object.prototype.hasOwnProperty.call(action, 'queries');
  if (hasType && (typeof action.type !== 'string' || !action.type.trim())) {
    throw new Error('invalid web search action type');
  }

  const singleQuery = action.query;
  if (hasQuery && (typeof singleQuery !== 'string' || !singleQuery.trim())) {
    throw new Error('invalid web search query');
  }

  const batchQueries = action.queries;
  if (
    hasQueries &&
    (!Array.isArray(batchQueries) ||
      batchQueries.length === 0 ||
      batchQueries.some((batchQuery) => typeof batchQuery !== 'string' || !batchQuery.trim()))
  ) {
    throw new Error('invalid web search queries');
  }

  if (action.type !== 'search') {
    return null;
  }

  const normalizedQueries = [
    ...(typeof singleQuery === 'string' ? [singleQuery.trim()] : []),
    ...(Array.isArray(batchQueries) ? batchQueries.map((batchQuery) => batchQuery.trim()) : [])
  ];
  if (normalizedQueries.length === 0) {
    throw new Error('search action is missing a query');
  }
  return normalizedQueries.join(' · ');
}

function decodeResponsesWebSearchRecord(block: WebMessageContentBlock): ServerToolRecord | null {
  if (!isResponsesWebSearchMeta(block) || block.closed === false) {
    return null;
  }

  try {
    const encodedPayload = block.content?.trim() ?? '';
    if (!encodedPayload) {
      throw new Error('empty payload');
    }

    const decodedPayload = decodeBase64Utf8(encodedPayload);
    const payload = JSON.parse(decodedPayload) as ResponsesWebSearchPayload;
    if (
      payload === null ||
      typeof payload !== 'object' ||
      Array.isArray(payload) ||
      payload.type !== 'web_search_call' ||
      typeof payload.id !== 'string' ||
      !payload.id.trim() ||
      typeof payload.status !== 'string' ||
      !payload.status.trim()
    ) {
      throw new Error('invalid web_search_call payload');
    }

    let query: string | null = null;
    if (payload.action !== undefined) {
      if (
        payload.action === null ||
        typeof payload.action !== 'object' ||
        Array.isArray(payload.action)
      ) {
        throw new Error('invalid web search action');
      }

      query = decodeWebSearchActionQuery(payload.action);
    }

    return {
      toolName: 'web_search',
      id: payload.id.trim(),
      rawJson: JSON.stringify(payload, null, 2),
      status: payload.status.trim(),
      query
    };
  } catch (error) {
    console.error('[CustomXmlRenderer] Invalid Responses web search metadata', error);
    return null;
  }
}

function shouldHideStatusBlock(block: WebMessageContentBlock, showStatusTags: boolean) {
  if (block.tag_name !== 'status' || showStatusTags) {
    return false;
  }
  const statusType = block.attrs?.type?.trim()?.toLowerCase();
  return statusType === 'completion' || statusType === 'complete' || statusType === 'wait_for_user_need';
}

function toolCountInGroup(children: WebMessageContentBlock[]) {
  return children.filter((child) => child.kind === 'xml' && child.tag_name === 'tool').length;
}

function shouldGroupToolByName(toolName: string | null | undefined, toolCollapseMode: ToolCollapseMode) {
  if (toolCollapseMode === 'all' || toolCollapseMode === 'full') {
    return true;
  }

  const normalized = toolName?.trim().toLowerCase();
  if (!normalized) {
    return false;
  }

  if (normalized.includes('search')) {
    return true;
  }

  return READ_ONLY_GROUPABLE_TOOL_NAMES.has(normalized);
}

function isConformingTailBlock(
  block: WebMessageContentBlock,
  toolCollapseMode: ToolCollapseMode
) {
  if (block.kind === 'text') {
    return !(block.content ?? '').trim();
  }

  if (block.kind === 'group') {
    return block.group_type === 'think_tools' || block.group_type === 'tools_only';
  }

  const tagName = block.tag_name;
  if (tagName === 'think' || tagName === 'thinking' || tagName === 'meta') {
    return true;
  }

  if (tagName === 'tool' || tagName === 'tool_result') {
    if (block.closed === false && !block.attrs?.name?.trim()) {
      return true;
    }
    return shouldGroupToolByName(block.attrs?.name, toolCollapseMode);
  }

  return !tagName && block.closed === false;
}

function GroupBlock({
  block,
  blockIndex,
  siblings,
  streaming,
  toolCollapseMode,
  renderBlock
}: {
  block: WebMessageContentBlock;
  blockIndex: number;
  siblings: WebMessageContentBlock[];
  streaming: boolean;
  toolCollapseMode: ToolCollapseMode;
  renderBlock: (
    child: WebMessageContentBlock,
    key: string,
    context?: { siblings: WebMessageContentBlock[]; index: number }
  ) => ReactNode;
}) {
  const children = block.children ?? [];
  const count = toolCountInGroup(children);
  const hasNonConformingAfterGroup = useMemo(() => {
    return siblings.slice(blockIndex + 1).some((candidate) => !isConformingTailBlock(candidate, toolCollapseMode));
  }, [blockIndex, siblings, toolCollapseMode]);
  const shouldAutoExpand = streaming && !hasNonConformingAfterGroup;
  const [expanded, setExpanded] = useState(shouldAutoExpand);
  const [userOverride, setUserOverride] = useState<boolean | null>(null);
  const title = block.group_type === 'tools_only' ? `工具调用 (${count})` : `思考与工具 (${count})`;

  useEffect(() => {
    if (userOverride === null) {
      setExpanded(shouldAutoExpand);
    }
  }, [shouldAutoExpand, userOverride]);

  return (
    <section className="structured-group">
      <StructuredExpandRow
        onClick={() => {
          setExpanded((value) => {
            const nextValue = !value;
            setUserOverride(nextValue);
            return nextValue;
          });
        }}
        expanded={expanded}
        title={title}
      />
      <AnimatedExpandBody className="structured-group-body" durationMs={200} visible={expanded}>
        <div className="structured-group-body-content">
          {children.map((child, index) =>
            renderBlock(child, `${block.group_type ?? 'group'}-${index}`, {
              siblings: children,
              index
            })
          )}
        </div>
      </AnimatedExpandBody>
    </section>
  );
}

function ThinkBlock({
  block,
  streaming
}: {
  block: WebMessageContentBlock;
  streaming: boolean;
}) {
  const isThinkingInProgress = streaming && block.closed === false;
  const [expanded, setExpanded] = useState(() => {
    return isThinkingInProgress ? readThinkingExpandedPreference() : false;
  });
  const [skipCollapseAnimationOnce, setSkipCollapseAnimationOnce] = useState(false);
  const thinkContent = block.content ?? '';

  useEffect(() => {
    if (isThinkingInProgress) {
      setExpanded(readThinkingExpandedPreference());
      return;
    }

    setSkipCollapseAnimationOnce(true);
    setExpanded(false);
  }, [isThinkingInProgress]);

  useEffect(() => {
    if (expanded) {
      setSkipCollapseAnimationOnce(false);
    }
  }, [expanded]);

  return (
    <section className={`structured-think-block ${isThinkingInProgress ? 'is-live' : ''}`}>
      <StructuredExpandRow
        expanded={expanded}
        live={isThinkingInProgress}
        onClick={() => {
          setSkipCollapseAnimationOnce(false);
          setExpanded((value) => {
            const nextValue = !value;
            if (isThinkingInProgress) {
              writeThinkingExpandedPreference(nextValue);
            }
            return nextValue;
          });
        }}
        title="思考过程"
      />
      {thinkContent ? (
        <AnimatedExpandBody
          className="structured-think-body-shell"
          durationMs={220}
          skipExitAnimation={skipCollapseAnimationOnce && !expanded}
          variant="think"
          visible={expanded}
        >
        <div className="structured-think-frame">
          <div className="structured-think-guide" />
          <div className="structured-think-scroll">
            <MarkdownRenderer className="markdown-block is-think" content={thinkContent} />
          </div>
        </div>
        </AnimatedExpandBody>
      ) : null}
    </section>
  );
}

function SearchBlock({ block }: { block: WebMessageContentBlock }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <section className="structured-search-block">
      <StructuredExpandRow
        expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
        title="搜索来源"
      />
      <AnimatedExpandBody className="structured-search-body" durationMs={200} visible={expanded}>
        <div className="structured-search-body-content">
          <MarkdownRenderer className="markdown-block is-structured" content={block.content ?? ''} />
        </div>
      </AnimatedExpandBody>
    </section>
  );
}

function StatusBlock({ block }: { block: WebMessageContentBlock }) {
  const statusType = block.attrs?.type?.trim()?.toLowerCase() ?? 'info';
  const statusContent = block.content?.trim() ?? '';
  const [detailOpen, setDetailOpen] = useState(false);

  if (statusType === 'warning') {
    return (
      <>
        <button
          className="structured-warning-row"
          onClick={() => {
            if (statusContent) {
              setDetailOpen(true);
            }
          }}
          type="button"
        >
          <span className="structured-warning-bar" />
          <span className="structured-warning-text">AI犯了一个错误</span>
        </button>
        {detailOpen ? (
          <div className="structured-modal-backdrop" onClick={() => setDetailOpen(false)} role="presentation">
            <section
              className="structured-modal-card"
              onClick={(event) => event.stopPropagation()}
              role="dialog"
            >
              <header className="structured-modal-header">
                <div className="structured-modal-title-row">
                  <strong>AI错误原因</strong>
                </div>
              </header>
              <div className="structured-modal-divider" />
              <div className="structured-modal-surface is-result is-error">
                <pre className="structured-modal-pre">{statusContent}</pre>
              </div>
              <footer className="structured-modal-footer">
                <button className="structured-modal-primary" onClick={() => setDetailOpen(false)} type="button">
                  关闭
                </button>
              </footer>
            </section>
          </div>
        ) : null}
      </>
    );
  }

  const statusText =
    statusType === 'completion' || statusType === 'complete'
      ? '✓ Task completed'
      : statusType === 'wait_for_user_need'
        ? '✓ Ready for further assistance'
        : statusContent;

  return <div className={`structured-status-card is-${statusType}`}>{statusText}</div>;
}

function ServerToolSourceIcon() {
  return (
    <svg
      aria-hidden="true"
      className="structured-server-tool-source-icon"
      fill="none"
      height="16"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
      width="16"
    >
      <path d="M19.35 10.04A7.49 7.49 0 0 0 12 4c-2.89 0-5.4 1.64-6.65 4.04A5.994 5.994 0 0 0 6 20h13a5 5 0 0 0 .35-9.96Z" />
    </svg>
  );
}

function ServerToolRecordBlock({ record }: { record: ServerToolRecord }) {
  const queryLabel = record.query ? `，查询：${record.query}` : '';
  const semanticDescription = `Server tool · ${record.toolName}，状态：${record.status}${queryLabel}`;
  const [detailOpen, setDetailOpen] = useState(false);

  return (
    <>
      <button
        aria-label={`${semanticDescription}，查看详情`}
        className="structured-server-tool-row"
        data-tool-call-id={record.id}
        onClick={() => setDetailOpen(true)}
        type="button"
      >
        <ServerToolSourceIcon />
        <span className="structured-server-tool-copy">
          <strong className="structured-server-tool-title">{record.toolName}</strong>
          <span className="structured-server-tool-details">
            <span className="structured-server-tool-status">{record.status}</span>
            {record.query ? (
              <span className="structured-server-tool-query">{record.query}</span>
            ) : null}
          </span>
        </span>
      </button>
      {detailOpen ? (
        <ContentDetailDialog
          content={record.rawJson}
          iconName="search"
          onDismiss={() => setDetailOpen(false)}
          title={`${record.toolName} 服务端调用详情`}
        />
      ) : null}
    </>
  );
}

function GenericXmlBlock({ block }: { block: WebMessageContentBlock }) {
  return (
    <section className="structured-card generic-card">
      <header className="structured-card-header">
        <strong>{block.tag_name ?? block.raw_tag_name ?? 'xml'}</strong>
      </header>
      <MarkdownRenderer className="markdown-block is-structured" content={block.content ?? block.xml ?? ''} />
    </section>
  );
}

function renderXmlBlock(
  block: WebMessageContentBlock,
  showThinking: boolean,
  showStatusTags: boolean,
  streaming: boolean
) {
  const tagName = block.tag_name;

  const serverToolRecord = decodeResponsesWebSearchRecord(block);
  if (serverToolRecord) {
    return <ServerToolRecordBlock record={serverToolRecord} />;
  }

  if (shouldHideGeminiThoughtSignatureMeta(block)) {
    return null;
  }

  if ((tagName === 'think' || tagName === 'thinking') && !showThinking) {
    return null;
  }

  if (shouldHideStatusBlock(block, showStatusTags)) {
    return null;
  }

  if (!block.closed) {
    if (tagName && BUILT_IN_TAGS.has(tagName) && tagName !== 'tool' && tagName !== 'think' && tagName !== 'thinking' && tagName !== 'search') {
      return null;
    }
    if (tagName && !BUILT_IN_TAGS.has(tagName)) {
      return <MarkdownRenderer content={block.xml ?? ''} />;
    }
  }

  if (tagName === 'meta' || tagName === 'mood') {
    return null;
  }

  if (tagName === 'tool') {
    return <ToolDisplayComponent block={block} />;
  }

  if (tagName === 'tool_result') {
    return <ToolResultDisplay block={block} />;
  }

  if (tagName === 'think' || tagName === 'thinking') {
    return <ThinkBlock block={block} streaming={streaming} />;
  }

  if (tagName === 'search') {
    return <SearchBlock block={block} />;
  }

  if (tagName === 'status') {
    return <StatusBlock block={block} />;
  }

  if (tagName === 'html') {
    return <div className="structured-html-card" dangerouslySetInnerHTML={{ __html: block.content ?? '' }} />;
  }

  if (tagName === 'font') {
    return <MarkdownRenderer content={block.content ?? ''} />;
  }

  if (tagName === 'details' || tagName === 'detail') {
    return (
      <DetailsTagRenderer
        block={block}
        renderMarkdown={(detailsContent) => (
          <MarkdownRenderer className="markdown-block is-structured" content={detailsContent} />
        )}
      />
    );
  }

  return <GenericXmlBlock block={block} />;
}

export function CustomXmlRenderer({
  content,
  blocks,
  showThinking,
  showStatusTags,
  streaming = false,
  toolCollapseMode = 'all'
}: {
  content: string;
  blocks?: WebMessageContentBlock[] | null;
  showThinking: boolean;
  showStatusTags: boolean;
  streaming?: boolean;
  toolCollapseMode?: ToolCollapseMode;
}) {
  const resolvedBlocks = useMemo(() => {
    if (blocks?.length) {
      return blocks;
    }
    if (!content) {
      return [];
    }
    return [
      {
        kind: 'text',
        content
      } satisfies WebMessageContentBlock
    ];
  }, [blocks, content]);

  const renderBlock = (
    block: WebMessageContentBlock,
    key: string,
    context?: { siblings: WebMessageContentBlock[]; index: number }
  ) => {
    if (block.kind === 'text') {
      return <MarkdownRenderer content={block.content ?? ''} key={key} />;
    }

    if (block.kind === 'group') {
      return (
        <GroupBlock
          block={block}
          blockIndex={context?.index ?? 0}
          key={key}
          renderBlock={renderBlock}
          siblings={context?.siblings ?? resolvedBlocks}
          streaming={streaming}
          toolCollapseMode={toolCollapseMode}
        />
      );
    }

    return <div key={key}>{renderXmlBlock(block, showThinking, showStatusTags, streaming)}</div>;
  };

  return (
    <div className="structured-content">
      {resolvedBlocks.map((block, index) =>
        renderBlock(block, `block-${index}`, {
          siblings: resolvedBlocks,
          index
        })
      )}
    </div>
  );
}
