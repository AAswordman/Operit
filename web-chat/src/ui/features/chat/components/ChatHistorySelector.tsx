import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from 'react';
import {
  AddCircleIcon,
  BackIcon,
  BranchIcon,
  CheckIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  DragHandleIcon,
  FolderIcon,
  GroupIcon,
  LockIcon,
  PencilIcon,
  PersonIcon,
  PlusIcon,
  SearchIcon,
  SearchOffIcon,
  SwapIcon,
  TrashIcon,
  TuneIcon
} from '../util/chatIcons';
import type {
  HistoryDisplayMode,
  WebCharacterSelectorResponse,
  WebChatReorderItem,
  WebChatSummary
} from '../util/chatTypes';

const HISTORY_SHOW_SWIPE_HINT_KEY = 'web_chat_show_swipe_hint';
const LONG_PRESS_DURATION_MS = 420;
const SWIPE_LOCK_DISTANCE_PX = 10;
const SWIPE_ACTION_TRIGGER_PX = 100;
const SWIPE_ACTION_MAX_PX = 116;
const UNGROUPED_KEY = '__ungrouped__';

type GroupActionTarget = {
  groupName: string;
  characterCardName?: string | null;
};

function readStoredSwipeHint(): boolean {
  if (typeof window === 'undefined') {
    return true;
  }
  return window.localStorage.getItem(HISTORY_SHOW_SWIPE_HINT_KEY) !== 'false';
}

function writeStoredValue(key: string, value: string) {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(key, value);
  }
}

function normalizeGroupName(value: string | null | undefined) {
  return value?.trim() || null;
}

function NestedHistoryGutter({ kind }: { kind: 'group' | 'chat' }) {
  return (
    <span aria-hidden="true" className={`chat-history-selector-nested-gutter is-${kind}`}>
      <span className="chat-history-selector-nested-line" />
    </span>
  );
}

function buildBindingLabel(chat: WebChatSummary) {
  if (chat.character_group_id) {
    return {
      key: `group:${chat.character_group_id}`,
      label: `群组: ${chat.character_group_name ?? chat.character_group_id}`,
      kind: 'group' as const,
      avatarUrl: chat.binding_avatar_url ?? null,
      characterCardName: null
    };
  }
  if (chat.character_card_name) {
    return {
      key: `card:${chat.character_card_name}`,
      label: chat.character_card_name,
      kind: 'card' as const,
      avatarUrl: chat.binding_avatar_url ?? null,
      characterCardName: chat.character_card_name
    };
  }
  return {
    key: 'unbound',
    label: '未绑定',
    kind: 'unbound' as const,
    avatarUrl: null,
    characterCardName: null
  };
}

function HistoryBindingAvatar({
  avatarUrl,
  kind,
  label
}: {
  avatarUrl?: string | null;
  kind: 'group' | 'card' | 'unbound';
  label: string;
}) {
  if (avatarUrl) {
    return (
      <span className="chat-history-selector-character-avatar">
        <img alt={label} src={avatarUrl} />
      </span>
    );
  }

  return (
    <span className="chat-history-selector-character-avatar">
      {kind === 'group' ? <GroupIcon size={14} /> : <PersonIcon size={14} />}
    </span>
  );
}

function HistoryQuickScrollButton({
  ariaLabel,
  disabled,
  icon,
  onClick
}: {
  ariaLabel: string;
  disabled: boolean;
  icon: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      aria-label={ariaLabel}
      className="chat-history-selector-quick-scroll-button"
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {icon}
    </button>
  );
}

function HistoryQuickScroller({
  contentRef,
  scrollContainerRef
}: {
  contentRef: RefObject<HTMLDivElement | null>;
  scrollContainerRef: RefObject<HTMLDivElement | null>;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const pointerIdRef = useRef<number | null>(null);
  const lastYRef = useRef(0);
  const scrollStopTimerRef = useRef<number | null>(null);
  const [scrollMetrics, setScrollMetrics] = useState({
    scrollTop: 0,
    scrollHeight: 0,
    clientHeight: 0
  });
  const [trackHeight, setTrackHeight] = useState(0);
  const [isHandlingTouch, setIsHandlingTouch] = useState(false);
  const [isScrolling, setIsScrolling] = useState(false);

  useEffect(() => {
    const viewport = scrollContainerRef.current;
    const content = contentRef.current;
    if (!viewport || !content) {
      return;
    }

    let frame = 0;
    const update = () => {
      setScrollMetrics({
        scrollTop: viewport.scrollTop,
        scrollHeight: viewport.scrollHeight,
        clientHeight: viewport.clientHeight
      });
      setTrackHeight(trackRef.current?.clientHeight ?? 0);
      setIsScrolling(true);
      if (scrollStopTimerRef.current !== null) {
        window.clearTimeout(scrollStopTimerRef.current);
      }
      scrollStopTimerRef.current = window.setTimeout(() => {
        setIsScrolling(false);
        scrollStopTimerRef.current = null;
      }, 120);
      frame = 0;
    };
    const schedule = () => {
      if (frame !== 0) {
        window.cancelAnimationFrame(frame);
      }
      frame = window.requestAnimationFrame(update);
    };

    schedule();

    const resizeObserver = new ResizeObserver(schedule);
    resizeObserver.observe(viewport);
    resizeObserver.observe(content);
    if (trackRef.current) {
      resizeObserver.observe(trackRef.current);
    }

    viewport.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);

    return () => {
      if (frame !== 0) {
        window.cancelAnimationFrame(frame);
      }
      if (scrollStopTimerRef.current !== null) {
        window.clearTimeout(scrollStopTimerRef.current);
        scrollStopTimerRef.current = null;
      }
      resizeObserver.disconnect();
      viewport.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
    };
  }, [contentRef, scrollContainerRef]);

  const shouldShow = scrollMetrics.scrollHeight > scrollMetrics.clientHeight + 1;
  const visibleFraction =
    scrollMetrics.clientHeight > 0 && scrollMetrics.scrollHeight > 0
      ? Math.min(Math.max(scrollMetrics.clientHeight / scrollMetrics.scrollHeight, 0.12), 1)
      : 1;
  const thumbHeight =
    trackHeight <= 0
      ? 36
      : Math.min(
          Math.max(trackHeight * visibleFraction, 36),
          trackHeight
        );
  const maxThumbOffset = Math.max(trackHeight - thumbHeight, 1);
  const maxScrollOffset = Math.max(scrollMetrics.scrollHeight - scrollMetrics.clientHeight, 1);
  const scrollProgress =
    maxScrollOffset <= 0 ? 0 : Math.min(Math.max(scrollMetrics.scrollTop / maxScrollOffset, 0), 1);
  const thumbOffset = maxThumbOffset * scrollProgress;
  const canScrollBackward = scrollMetrics.scrollTop > 0;
  const canScrollForward =
    scrollMetrics.scrollTop + scrollMetrics.clientHeight < scrollMetrics.scrollHeight - 1;
  const quickScrollerAlpha = isHandlingTouch || isScrolling ? 0.9 : 0.5;

  function scrollToTop() {
    const viewport = scrollContainerRef.current;
    if (viewport) {
      viewport.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  function scrollToBottom() {
    const viewport = scrollContainerRef.current;
    if (viewport) {
      viewport.scrollTo({ top: viewport.scrollHeight, behavior: 'smooth' });
    }
  }

  function jumpToPointer(pointerY: number) {
    const viewport = scrollContainerRef.current;
    const track = trackRef.current;
    if (!viewport || !track || trackHeight <= 0 || !shouldShow) {
      return;
    }

    const rect = track.getBoundingClientRect();
    const trackableHeight = Math.max(rect.height - thumbHeight, 1);
    const normalizedOffset = Math.min(
      Math.max(pointerY - rect.top - thumbHeight / 2, 0),
      trackableHeight
    );
    const progress = normalizedOffset / trackableHeight;
    viewport.scrollTop = progress * maxScrollOffset;
  }

  function scrollByPointerDelta(pointerDeltaY: number) {
    const viewport = scrollContainerRef.current;
    const track = trackRef.current;
    if (!viewport || !track || trackHeight <= 0 || !shouldShow) {
      return;
    }

    const rect = track.getBoundingClientRect();
    const trackableHeight = Math.max(rect.height - thumbHeight, 1);
    const contentDelta = pointerDeltaY * (maxScrollOffset / trackableHeight);
    if (contentDelta !== 0) {
      viewport.scrollTop += contentDelta;
    }
  }

  if (!shouldShow) {
    return null;
  }

  return (
    <div
      className="chat-history-selector-quick-scroller"
      style={{ opacity: quickScrollerAlpha }}
    >
      <HistoryQuickScrollButton
        ariaLabel="history_scroll_to_top"
        disabled={!canScrollBackward}
        icon={<ChevronUpIcon size={18} />}
        onClick={scrollToTop}
      />
      <div
        aria-label="history_fast_scroll"
        className="chat-history-selector-quick-scroll-track"
        onPointerDown={(event) => {
          if (event.button !== 0) {
            return;
          }

          pointerIdRef.current = event.pointerId;
          lastYRef.current = event.clientY;
          setIsHandlingTouch(true);
          jumpToPointer(event.clientY);
          event.currentTarget.setPointerCapture(event.pointerId);
          event.preventDefault();
        }}
        onPointerMove={(event) => {
          if (pointerIdRef.current !== event.pointerId || !isHandlingTouch) {
            return;
          }

          scrollByPointerDelta(event.clientY - lastYRef.current);
          lastYRef.current = event.clientY;
          event.preventDefault();
        }}
        onPointerUp={(event) => {
          if (pointerIdRef.current === event.pointerId) {
            if (event.currentTarget.hasPointerCapture(event.pointerId)) {
              event.currentTarget.releasePointerCapture(event.pointerId);
            }
            pointerIdRef.current = null;
            setIsHandlingTouch(false);
          }
        }}
        onPointerCancel={() => {
          if (pointerIdRef.current !== null && trackRef.current?.hasPointerCapture(pointerIdRef.current)) {
            trackRef.current.releasePointerCapture(pointerIdRef.current);
          }
          pointerIdRef.current = null;
          setIsHandlingTouch(false);
        }}
        ref={trackRef}
      >
        <span className="chat-history-selector-quick-scroll-rail" />
        <span
          className="chat-history-selector-quick-scroll-thumb"
          style={{ height: `${thumbHeight}px`, transform: `translate(-50%, ${thumbOffset}px)` }}
        />
      </div>
      <HistoryQuickScrollButton
        ariaLabel="history_scroll_to_bottom"
        disabled={!canScrollForward}
        icon={<ChevronDownIcon size={18} />}
        onClick={scrollToBottom}
      />
    </div>
  );
}

function matchesCurrentBinding(chat: WebChatSummary, selectedChat: WebChatSummary | null) {
  if (!selectedChat) {
    return true;
  }
  if (selectedChat.character_group_id) {
    return chat.character_group_id === selectedChat.character_group_id;
  }
  if (selectedChat.character_card_name) {
    return !chat.character_group_id && chat.character_card_name === selectedChat.character_card_name;
  }
  return !chat.character_group_id && !chat.character_card_name;
}

function buildGroupBuckets(chats: WebChatSummary[]) {
  const buckets = new Map<
    string,
    {
      key: string;
      label: string;
      chats: WebChatSummary[];
    }
  >();

  chats.forEach((chat) => {
    const key = normalizeGroupName(chat.group) ?? UNGROUPED_KEY;
    const existing = buckets.get(key);
    if (existing) {
      existing.chats.push(chat);
      return;
    }

    buckets.set(key, {
      key,
      label: chat.group ?? '未分组',
      chats: [chat]
    });
  });

  return [...buckets.values()];
}

function clampSwipeOffset(offset: number) {
  return Math.max(-SWIPE_ACTION_MAX_PX, Math.min(SWIPE_ACTION_MAX_PX, offset));
}

function buildBindingDraftValue(
  chat: WebChatSummary,
  selector: WebCharacterSelectorResponse | null
) {
  if (chat.character_group_id) {
    return selector?.groups.some((group) => group.id === chat.character_group_id)
      ? `group:${chat.character_group_id}`
      : `group-legacy:${chat.character_group_id}`;
  }
  if (chat.character_card_name) {
    const matchedCard = selector?.cards.find((card) => card.name === chat.character_card_name);
    return matchedCard ? `card:${matchedCard.id}` : `card-legacy:${chat.character_card_name}`;
  }
  return 'unbound';
}

function buildReorderItems(chats: WebChatSummary[]): WebChatReorderItem[] {
  return chats.map((chat, index) => ({
    chat_id: chat.id,
    display_order: index,
    group: chat.group ?? null
  }));
}

function HistorySettingsSwitch({
  checked
}: {
  checked: boolean;
}) {
  return (
    <span className={`history-settings-switch ${checked ? 'is-checked' : ''}`}>
      <span className="history-settings-switch-thumb" />
    </span>
  );
}

function HistoryGroupHeader({
  canManageGroup,
  collapsed,
  label,
  nested = false,
  onManage,
  onToggle,
  showManageHint
}: {
  canManageGroup: boolean;
  collapsed: boolean;
  label: string;
  nested?: boolean;
  onManage: () => void;
  onToggle: () => void;
  showManageHint: boolean;
}) {
  const pointerIdRef = useRef<number | null>(null);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const ignoreClickRef = useRef(false);
  const longPressTriggeredRef = useRef(false);
  const longPressTimerRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (longPressTimerRef.current !== null) {
        window.clearTimeout(longPressTimerRef.current);
      }
    };
  }, []);

  function clearLongPress() {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }

  return (
    <button
      className={`chat-history-selector-group-surface ${nested ? 'is-nested' : ''}`}
      onClick={() => {
        if (ignoreClickRef.current) {
          ignoreClickRef.current = false;
          return;
        }
        onToggle();
      }}
      onContextMenu={(event) => {
        event.preventDefault();
      }}
      onPointerCancel={() => {
        clearLongPress();
        pointerIdRef.current = null;
        ignoreClickRef.current = true;
        longPressTriggeredRef.current = false;
      }}
      onPointerDown={(event) => {
        if (event.button !== 0) {
          return;
        }

        pointerIdRef.current = event.pointerId;
        startXRef.current = event.clientX;
        startYRef.current = event.clientY;
        ignoreClickRef.current = false;
        longPressTriggeredRef.current = false;
        clearLongPress();

        if (!canManageGroup) {
          return;
        }

        longPressTimerRef.current = window.setTimeout(() => {
          longPressTriggeredRef.current = true;
          ignoreClickRef.current = true;
          onManage();
        }, LONG_PRESS_DURATION_MS);
      }}
      onPointerLeave={() => {
        clearLongPress();
      }}
      onPointerMove={(event) => {
        if (pointerIdRef.current !== event.pointerId || longPressTriggeredRef.current) {
          return;
        }

        const deltaX = Math.abs(event.clientX - startXRef.current);
        const deltaY = Math.abs(event.clientY - startYRef.current);
        if (deltaX >= SWIPE_LOCK_DISTANCE_PX || deltaY >= SWIPE_LOCK_DISTANCE_PX) {
          clearLongPress();
        }
      }}
      onPointerUp={() => {
        clearLongPress();
        pointerIdRef.current = null;
        longPressTriggeredRef.current = false;
      }}
      type="button"
    >
      <span className="chat-history-selector-group-header-content">
        <span className="chat-history-selector-group-header-main">
          <FolderIcon size={24} />
          <span className="chat-history-selector-group-header-copy">
            <span className="chat-history-selector-group-header-title-row">
              <strong>{label}</strong>
              {canManageGroup && showManageHint ? <em>{` (长按管理)`}</em> : null}
            </span>
          </span>
        </span>
        <span className="chat-history-selector-group-header-arrow">
          {collapsed ? <ChevronDownIcon size={24} /> : <ChevronUpIcon size={24} />}
        </span>
      </span>
    </button>
  );
}

function SwipeableHistoryChatRow({
  chat,
  nested = false,
  active,
  parentChat,
  onDismissSwipeHint,
  onLongPress,
  onSelect,
  onRename,
  onDelete
}: {
  chat: WebChatSummary;
  nested?: boolean;
  active: boolean;
  parentChat: WebChatSummary | null;
  onDismissSwipeHint: () => void;
  onLongPress: (chat: WebChatSummary) => void;
  onSelect: (chatId: string) => void;
  onRename: (chat: WebChatSummary) => void;
  onDelete: (chat: WebChatSummary) => void;
}) {
  const [offsetX, setOffsetX] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const pointerIdRef = useRef<number | null>(null);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const gestureAxisRef = useRef<'horizontal' | 'vertical' | null>(null);
  const ignoreClickRef = useRef(false);
  const longPressTriggeredRef = useRef(false);
  const longPressTimerRef = useRef<number | null>(null);
  const currentOffsetRef = useRef(0);

  useEffect(() => {
    return () => {
      if (longPressTimerRef.current !== null) {
        window.clearTimeout(longPressTimerRef.current);
      }
    };
  }, []);

  function clearLongPress() {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }

  function resetSwipe() {
    currentOffsetRef.current = 0;
    setOffsetX(0);
    setIsDragging(false);
  }

  function finalizeGesture(target?: EventTarget | null) {
    clearLongPress();

    const gestureAxis = gestureAxisRef.current;
    const committedOffset = currentOffsetRef.current;
    const longPressTriggered = longPressTriggeredRef.current;

    if (
      target instanceof Element &&
      pointerIdRef.current !== null &&
      target.hasPointerCapture(pointerIdRef.current)
    ) {
      target.releasePointerCapture(pointerIdRef.current);
    }

    pointerIdRef.current = null;
    gestureAxisRef.current = null;

    if (longPressTriggered) {
      longPressTriggeredRef.current = false;
      resetSwipe();
      return;
    }

    if (gestureAxis === 'horizontal') {
      ignoreClickRef.current = true;
      resetSwipe();
      if (committedOffset >= SWIPE_ACTION_TRIGGER_PX) {
        onRename(chat);
      } else if (committedOffset <= -SWIPE_ACTION_TRIGGER_PX) {
        onDelete(chat);
      }
      return;
    }

    resetSwipe();
  }

  const swipeStartProgress = Math.max(0, offsetX) / SWIPE_ACTION_MAX_PX;
  const swipeEndProgress = Math.max(0, -offsetX) / SWIPE_ACTION_MAX_PX;

  return (
    <div
      className={[
        'chat-history-selector-chat-row',
        active ? 'is-active' : '',
        nested ? 'is-nested' : ''
      ]
        .filter(Boolean)
        .join(' ')}
      key={chat.id}
    >
      {nested ? <NestedHistoryGutter kind="chat" /> : null}
      <div className="chat-history-selector-swipe-shell">
        <div className="chat-history-selector-swipe-track">
          <div aria-hidden="true" className="chat-history-selector-swipe-actions">
            <div
              className="chat-history-selector-swipe-action is-start"
              style={{ opacity: swipeStartProgress, transform: `scale(${0.9 + swipeStartProgress * 0.1})` }}
            >
              <span className="chat-history-selector-swipe-action-icon">
                <PencilIcon size={20} />
              </span>
            </div>
            <div
              className="chat-history-selector-swipe-action is-end"
              style={{ opacity: swipeEndProgress, transform: `scale(${0.9 + swipeEndProgress * 0.1})` }}
            >
              <span className="chat-history-selector-swipe-action-icon">
                <TrashIcon size={20} />
              </span>
            </div>
          </div>

          <button
            className={`chat-history-selector-chat-surface ${isDragging ? 'is-dragging' : ''}`}
            onClick={() => {
              if (ignoreClickRef.current) {
                ignoreClickRef.current = false;
                return;
              }
              onSelect(chat.id);
            }}
            onContextMenu={(event) => {
              event.preventDefault();
            }}
            onPointerCancel={(event) => {
              ignoreClickRef.current = true;
              finalizeGesture(event.currentTarget);
            }}
            onPointerDown={(event) => {
              if (event.button !== 0) {
                return;
              }

              pointerIdRef.current = event.pointerId;
              startXRef.current = event.clientX;
              startYRef.current = event.clientY;
              gestureAxisRef.current = null;
              ignoreClickRef.current = false;
              longPressTriggeredRef.current = false;
              resetSwipe();
              clearLongPress();

              longPressTimerRef.current = window.setTimeout(() => {
                longPressTriggeredRef.current = true;
                ignoreClickRef.current = true;
                resetSwipe();
                onLongPress(chat);
              }, LONG_PRESS_DURATION_MS);
            }}
            onPointerLeave={() => {
              if (gestureAxisRef.current !== 'horizontal') {
                clearLongPress();
              }
            }}
            onPointerMove={(event) => {
              if (pointerIdRef.current !== event.pointerId || longPressTriggeredRef.current) {
                return;
              }

              const deltaX = event.clientX - startXRef.current;
              const deltaY = event.clientY - startYRef.current;

              if (gestureAxisRef.current === null) {
                if (
                  Math.abs(deltaX) < SWIPE_LOCK_DISTANCE_PX &&
                  Math.abs(deltaY) < SWIPE_LOCK_DISTANCE_PX
                ) {
                  return;
                }

                clearLongPress();

                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                  gestureAxisRef.current = 'horizontal';
                  ignoreClickRef.current = true;
                  event.currentTarget.setPointerCapture(event.pointerId);
                } else {
                  gestureAxisRef.current = 'vertical';
                  return;
                }
              }

              if (gestureAxisRef.current !== 'horizontal') {
                return;
              }

              event.preventDefault();
              onDismissSwipeHint();

              const nextOffset = clampSwipeOffset(deltaX);
              currentOffsetRef.current = nextOffset;
              setOffsetX(nextOffset);
              setIsDragging(true);
            }}
            onPointerUp={(event) => finalizeGesture(event.currentTarget)}
            style={{ transform: `translateX(${offsetX}px)` }}
            type="button"
          >
            <div className="chat-history-selector-chat-main">
              <span className="chat-history-selector-chat-drag">
                <DragHandleIcon size={18} />
              </span>
              <div className="chat-history-selector-chat-title-block">
                <strong>{chat.title}</strong>
                {parentChat ? (
                  <div className="chat-history-selector-parent-line">
                    <BranchIcon size={14} />
                    <span>{parentChat.title}</span>
                  </div>
                ) : null}
              </div>
              <span className="chat-history-selector-chat-tail">
                {chat.active_streaming ? <span className="chat-history-selector-streaming-dot" /> : null}
                {chat.locked ? <LockIcon size={16} /> : null}
              </span>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
}

export function ChatHistorySelector({
  open,
  chats,
  search,
  selectedChatId,
  busy,
  streaming,
  onClose,
  onCreateChat,
  onRenameChat,
  onDeleteChat,
  onSearchChange,
  onSelectChat,
  characterSelector,
  historyDisplayMode,
  autoSwitchCharacterCard,
  autoSwitchChatOnCharacterSelect,
  onHistoryDisplayModeChange,
  onAutoSwitchCharacterCardChange,
  onAutoSwitchChatOnCharacterSelectChange,
  onUpdateChat,
  onReorderChats,
  onRenameGroup,
  onDeleteGroup
}: {
  open: boolean;
  chats: WebChatSummary[];
  search: string;
  selectedChatId: string | null;
  busy: boolean;
  streaming: boolean;
  onClose: () => void;
  onCreateChat: (options?: { group?: string | null }) => Promise<void>;
  onRenameChat: (chat: WebChatSummary, title: string) => Promise<void>;
  onDeleteChat: (chat: WebChatSummary) => Promise<void>;
  onSearchChange: (value: string) => void;
  onSelectChat: (chatId: string) => void;
  characterSelector: WebCharacterSelectorResponse | null;
  historyDisplayMode: HistoryDisplayMode;
  autoSwitchCharacterCard: boolean;
  autoSwitchChatOnCharacterSelect: boolean;
  onHistoryDisplayModeChange: (value: HistoryDisplayMode) => void;
  onAutoSwitchCharacterCardChange: (value: boolean) => void;
  onAutoSwitchChatOnCharacterSelectChange: (value: boolean) => void;
  onUpdateChat: (
    chat: WebChatSummary,
    payload: {
      title?: string;
      group?: string | null;
      update_group?: boolean;
      locked?: boolean;
      update_locked?: boolean;
      character_card_name?: string | null;
      character_group_id?: string | null;
      update_binding?: boolean;
    }
  ) => Promise<WebChatSummary | null>;
  onReorderChats: (items: WebChatReorderItem[]) => Promise<void>;
  onRenameGroup: (oldName: string, newName: string, characterCardName?: string | null) => Promise<void>;
  onDeleteGroup: (
    groupName: string,
    deleteChats: boolean,
    characterCardName?: string | null
  ) => Promise<void>;
}) {
  const [editingChat, setEditingChat] = useState<WebChatSummary | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<WebChatSummary | null>(null);
  const [actionTarget, setActionTarget] = useState<WebChatSummary | null>(null);
  const [bindingTarget, setBindingTarget] = useState<WebChatSummary | null>(null);
  const [bindingDraftValue, setBindingDraftValue] = useState('unbound');
  const [moveTarget, setMoveTarget] = useState<WebChatSummary | null>(null);
  const [groupActionTarget, setGroupActionTarget] = useState<GroupActionTarget | null>(null);
  const [draftTitle, setDraftTitle] = useState('');
  const [newGroupName, setNewGroupName] = useState('');
  const [groupRenameDraft, setGroupRenameDraft] = useState('');
  const [moveGroupDraft, setMoveGroupDraft] = useState('');
  const [showSearchBox, setShowSearchBox] = useState(Boolean(search));
  const [showSettingsDialog, setShowSettingsDialog] = useState(false);
  const [showNewGroupDialog, setShowNewGroupDialog] = useState(false);
  const [showSwipeHint, setShowSwipeHint] = useState(readStoredSwipeHint);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const [collapsedCharacters, setCollapsedCharacters] = useState<Set<string>>(new Set());
  const [hasLongPressedGroup, setHasLongPressedGroup] = useState(false);
  const listScrollContainerRef = useRef<HTMLDivElement | null>(null);
  const listContentRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) {
      setEditingChat(null);
      setDeleteTarget(null);
      setActionTarget(null);
      setBindingTarget(null);
      setMoveTarget(null);
      setGroupActionTarget(null);
      setShowSettingsDialog(false);
      setShowNewGroupDialog(false);
      setHasLongPressedGroup(false);
    }
  }, [open]);

  useEffect(() => {
    if (search) {
      setShowSearchBox(true);
    }
  }, [search]);

  const selectedChat = useMemo(
    () => chats.find((chat) => chat.id === selectedChatId) ?? null,
    [chats, selectedChatId]
  );

  const filteredChats = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return chats;
    }
    return chats.filter((chat) =>
      [
        chat.title,
        chat.group ?? '',
        chat.character_card_name ?? '',
        chat.character_group_name ?? '',
        chat.character_group_id ?? ''
      ].some((value) => value.toLowerCase().includes(keyword))
    );
  }, [chats, search]);

  const visibleChats = useMemo(() => {
    if (historyDisplayMode === 'CURRENT_CHARACTER_ONLY') {
      return filteredChats.filter((chat) => matchesCurrentBinding(chat, selectedChat));
    }
    return filteredChats;
  }, [filteredChats, historyDisplayMode, selectedChat]);

  const chatsById = useMemo(() => new Map(chats.map((chat) => [chat.id, chat] as const)), [chats]);

  const characterBuckets = useMemo(() => {
    if (historyDisplayMode !== 'BY_CHARACTER_CARD') {
      return [];
    }

    const buckets = new Map<
      string,
      {
        key: string;
        label: string;
        kind: 'group' | 'card' | 'unbound';
        avatarUrl: string | null;
        characterCardName?: string | null;
        groups: ReturnType<typeof buildGroupBuckets>;
      }
    >();

    visibleChats.forEach((chat) => {
      const binding = buildBindingLabel(chat);
      const bucket = buckets.get(binding.key);
      if (bucket) {
        const groupKey = normalizeGroupName(chat.group) ?? UNGROUPED_KEY;
        const groupBucket = bucket.groups.find((item) => item.key === groupKey);
        if (groupBucket) {
          groupBucket.chats.push(chat);
        } else {
          bucket.groups.push({
            key: groupKey,
            label: chat.group ?? '未分组',
            chats: [chat]
          });
        }
        return;
      }

      buckets.set(binding.key, {
        key: binding.key,
        label: binding.label,
        kind: binding.kind,
        avatarUrl: binding.avatarUrl,
        characterCardName: binding.characterCardName,
        groups: buildGroupBuckets([chat])
      });
    });

    return [...buckets.values()];
  }, [historyDisplayMode, visibleChats]);

  const groupBuckets = useMemo(() => {
    if (historyDisplayMode === 'BY_CHARACTER_CARD') {
      return [];
    }
    return buildGroupBuckets(visibleChats);
  }, [historyDisplayMode, visibleChats]);

  function toggleCollapsedGroup(key: string) {
    setCollapsedGroups((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function toggleCollapsedCharacter(key: string) {
    setCollapsedCharacters((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function dismissSwipeHint() {
    if (!showSwipeHint) {
      return;
    }
    setShowSwipeHint(false);
    writeStoredValue(HISTORY_SHOW_SWIPE_HINT_KEY, 'false');
  }

  async function moveChat(chat: WebChatSummary, delta: number) {
    const currentIndex = chats.findIndex((item) => item.id === chat.id);
    if (currentIndex < 0) {
      return;
    }
    const nextIndex = currentIndex + delta;
    if (nextIndex < 0 || nextIndex >= chats.length) {
      return;
    }
    const reordered = [...chats];
    const [movedChat] = reordered.splice(currentIndex, 1);
    reordered.splice(nextIndex, 0, movedChat);
    await onReorderChats(buildReorderItems(reordered));
  }

  function renderChatItem(chat: WebChatSummary, nested = false) {
    const parentChat = chat.parent_chat_id ? chatsById.get(chat.parent_chat_id) ?? null : null;
    return (
      <SwipeableHistoryChatRow
        active={selectedChatId === chat.id}
        chat={chat}
        key={chat.id}
        nested={nested}
        onDelete={(target) => setDeleteTarget(target)}
        onDismissSwipeHint={dismissSwipeHint}
        onLongPress={(target) => setActionTarget(target)}
        onRename={(target) => {
          setEditingChat(target);
          setDraftTitle(target.title);
        }}
        onSelect={onSelectChat}
        parentChat={parentChat}
      />
    );
  }

  function renderGroupBucket(
    bucket: { key: string; label: string; chats: WebChatSummary[] },
    nested = false,
    scopeKey = 'root',
    characterCardName?: string | null
  ) {
    const collapseKey = `${scopeKey}:${bucket.key}`;
    const collapsed = collapsedGroups.has(collapseKey);
    const canManageGroup = bucket.key !== UNGROUPED_KEY;

    return (
      <section
        className="chat-history-selector-group-block"
        key={collapseKey}
      >
        <div className={['chat-history-selector-group-header-row', nested ? 'is-nested' : ''].filter(Boolean).join(' ')}>
          {nested ? <NestedHistoryGutter kind="group" /> : null}
          <HistoryGroupHeader
            canManageGroup={canManageGroup}
            collapsed={collapsed}
            label={bucket.label}
            nested={nested}
            onManage={() => {
              setGroupActionTarget({
                groupName: bucket.label,
                characterCardName: characterCardName ?? null
              });
              setGroupRenameDraft(bucket.label);
              setHasLongPressedGroup(true);
            }}
            onToggle={() => toggleCollapsedGroup(collapseKey)}
            showManageHint={!hasLongPressedGroup}
          />
        </div>

        {collapsed ? null : (
          <div className="chat-history-selector-group-list">
            {bucket.chats.map((chat) => renderChatItem(chat, nested))}
          </div>
        )}
      </section>
    );
  }

  return (
    <>
      <div
        aria-hidden={!open}
        className={`chat-history-selector-scrim ${open ? 'is-visible' : ''}`}
        onClick={onClose}
      />
      <aside className={`chat-history-selector ${open ? 'is-open' : ''}`}>
        <div className="chat-history-selector-top">
          <header className="chat-history-selector-title-row">
            <strong>对话历史</strong>
            <div className="chat-history-selector-title-actions">
              <button
                onClick={() => {
                  setShowSearchBox((value) => !value);
                  if (showSearchBox) {
                    onSearchChange('');
                  }
                }}
                type="button"
              >
                {showSearchBox ? <SearchOffIcon size={18} /> : <SearchIcon size={18} />}
              </button>
              <button onClick={() => setShowSettingsDialog(true)} type="button">
                <TuneIcon size={18} />
              </button>
              <button onClick={onClose} type="button">
                <BackIcon size={18} />
              </button>
            </div>
          </header>

          <div className="chat-history-selector-create-row">
            <button
              className="chat-history-selector-create-button"
              disabled={busy || streaming}
              onClick={() => {
                void onCreateChat();
              }}
              type="button"
            >
              <PlusIcon size={20} />
              <span>新建对话</span>
            </button>
            <button
              className="chat-history-selector-create-icon"
              disabled={busy || streaming}
              onClick={() => setShowNewGroupDialog(true)}
              type="button"
            >
              <AddCircleIcon size={24} />
            </button>
          </div>

          {showSearchBox ? (
            <label className="chat-history-selector-search-field">
              <SearchIcon size={16} />
              <input
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="搜索"
                value={search}
              />
            </label>
          ) : null}

          {showSwipeHint ? (
            <button className="chat-history-selector-swipe-hint" onClick={dismissSwipeHint} type="button">
              <SwapIcon size={16} />
              <span>左右滑动可编辑或删除(点击不再显示)</span>
            </button>
          ) : null}
        </div>

        <div className="chat-history-selector-divider" />

        <div className="chat-history-selector-list-region">
          <div className="chat-history-selector-list" ref={listScrollContainerRef}>
            <div className="chat-history-selector-list-shell" ref={listContentRef}>
              {historyDisplayMode === 'BY_CHARACTER_CARD' ? (
                characterBuckets.length ? (
                  characterBuckets.map((bucket) => {
                    const collapsed = collapsedCharacters.has(bucket.key);
                    return (
                      <section className="chat-history-selector-character-block" key={bucket.key}>
                        <button
                          className="chat-history-selector-character-header"
                          onClick={() => toggleCollapsedCharacter(bucket.key)}
                          type="button"
                        >
                          <span className="chat-history-selector-character-chip">
                            <HistoryBindingAvatar
                              avatarUrl={bucket.avatarUrl}
                              kind={bucket.kind}
                              label={bucket.label}
                            />
                            <strong>{bucket.label}</strong>
                          </span>
                          <span className="chat-history-selector-character-line" />
                          <span className="chat-history-selector-character-arrow">
                            {collapsed ? <ChevronDownIcon size={24} /> : <ChevronUpIcon size={24} />}
                          </span>
                        </button>

                        {collapsed ? null : (
                          <div className="chat-history-selector-character-content">
                            {bucket.groups.map((group) =>
                              renderGroupBucket(group, true, bucket.key, bucket.characterCardName)
                            )}
                          </div>
                        )}
                      </section>
                    );
                  })
                ) : (
                  <div className="chat-history-selector-empty">
                    <strong>{busy ? '正在同步会话' : '没有匹配的会话'}</strong>
                    <span>{busy ? '历史列表会在打开侧栏时按需加载。' : '换个关键词，或者直接新建对话。'}</span>
                  </div>
                )
              ) : groupBuckets.length ? (
                groupBuckets.map((group) => renderGroupBucket(group))
              ) : (
                <div className="chat-history-selector-empty">
                  <strong>{busy ? '正在同步会话' : '没有匹配的会话'}</strong>
                  <span>{busy ? '历史列表会在打开侧栏时按需加载。' : '换个关键词，或者直接新建对话。'}</span>
                </div>
              )}
            </div>
          </div>
          <HistoryQuickScroller
            contentRef={listContentRef}
            scrollContainerRef={listScrollContainerRef}
          />
        </div>
      </aside>

      {actionTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog history-action-dialog" role="dialog">
            <header>
              <h3>对话历史</h3>
              <p>{actionTarget.title}</p>
            </header>
            <div className="history-action-list">
              <button
                className="history-action-item"
                onClick={() => {
                  setEditingChat(actionTarget);
                  setDraftTitle(actionTarget.title);
                  setActionTarget(null);
                }}
                type="button"
              >
                <PencilIcon size={18} />
                <span>编辑标题</span>
              </button>
              <button
                className="history-action-item"
                onClick={() => {
                  setBindingTarget(actionTarget);
                  setBindingDraftValue(buildBindingDraftValue(actionTarget, characterSelector));
                  setActionTarget(null);
                }}
                type="button"
              >
                <PersonIcon size={18} />
                <span>编辑绑定</span>
              </button>
              <button
                className="history-action-item"
                onClick={() => {
                  setMoveTarget(actionTarget);
                  setMoveGroupDraft(actionTarget.group ?? '');
                  setActionTarget(null);
                }}
                type="button"
              >
                <FolderIcon size={18} />
                <span>移动分组</span>
              </button>
              <button
                className="history-action-item"
                onClick={() => {
                  void moveChat(actionTarget, -1);
                  setActionTarget(null);
                }}
                type="button"
              >
                <ChevronUpIcon size={18} />
                <span>上移</span>
              </button>
              <button
                className="history-action-item"
                onClick={() => {
                  void moveChat(actionTarget, 1);
                  setActionTarget(null);
                }}
                type="button"
              >
                <ChevronDownIcon size={18} />
                <span>下移</span>
              </button>
              <button
                className="history-action-item"
                onClick={() => {
                  void onUpdateChat(actionTarget, {
                    locked: !actionTarget.locked,
                    update_locked: true
                  });
                  setActionTarget(null);
                }}
                type="button"
              >
                <LockIcon size={18} />
                <span>{actionTarget.locked ? '解锁' : '锁定'}</span>
              </button>
              <button
                className="history-action-item is-danger"
                onClick={() => {
                  setDeleteTarget(actionTarget);
                  setActionTarget(null);
                }}
                type="button"
              >
                <TrashIcon size={18} />
                <span>删除</span>
              </button>
            </div>
            <footer>
              <button onClick={() => setActionTarget(null)} type="button">
                取消
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {showSettingsDialog ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog history-settings-dialog" role="dialog">
            <header>
              <h3>聊天记录设置</h3>
            </header>
            <div className="history-settings-section">
              <span>显示模式</span>
              {[
                {
                  mode: 'BY_CHARACTER_CARD' as const,
                  title: '按角色卡分类',
                  description: '角色卡 - 文件夹 - 对话'
                },
                {
                  mode: 'BY_FOLDER' as const,
                  title: '按文件夹分类',
                  description: '显示所有角色卡的文件夹'
                },
                {
                  mode: 'CURRENT_CHARACTER_ONLY' as const,
                  title: '仅当前角色',
                  description: '只显示当前绑定下的聊天'
                }
              ].map((item) => {
                const selected = historyDisplayMode === item.mode;
                return (
                  <button
                    className={`history-settings-option ${selected ? 'is-selected' : ''}`}
                    key={item.mode}
                    onClick={() => onHistoryDisplayModeChange(item.mode)}
                    type="button"
                  >
                    <span className="history-settings-option-copy">
                      <strong>{item.title}</strong>
                      <small>{item.description}</small>
                    </span>
                    {selected ? <CheckIcon size={18} /> : null}
                  </button>
                );
              })}
            </div>
            <div className="history-settings-section">
              <span>自动切换</span>
              <button
                className={`history-settings-option is-toggle ${autoSwitchCharacterCard ? 'is-selected' : ''}`}
                onClick={() => onAutoSwitchCharacterCardChange(!autoSwitchCharacterCard)}
                type="button"
              >
                <span className="history-settings-option-copy">
                  <strong>切换聊天时同步角色卡</strong>
                  <small>选中聊天后，自动切到聊天绑定的角色</small>
                </span>
                <HistorySettingsSwitch checked={autoSwitchCharacterCard} />
              </button>
              <button
                className={`history-settings-option is-toggle ${autoSwitchChatOnCharacterSelect ? 'is-selected' : ''}`}
                onClick={() =>
                  onAutoSwitchChatOnCharacterSelectChange(!autoSwitchChatOnCharacterSelect)
                }
                type="button"
              >
                <span className="history-settings-option-copy">
                  <strong>切换角色时同步聊天</strong>
                  <small>切角色后，自动跳到匹配的聊天</small>
                </span>
                <HistorySettingsSwitch checked={autoSwitchChatOnCharacterSelect} />
              </button>
            </div>
            <footer>
              <button onClick={() => setShowSettingsDialog(false)} type="button">
                取消
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {showNewGroupDialog ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>新建分组</h3>
            </header>
            <input
              autoFocus
              onChange={(event) => setNewGroupName(event.target.value)}
              placeholder="新分组名称"
              value={newGroupName}
            />
            <footer>
              <button
                onClick={() => {
                  setShowNewGroupDialog(false);
                  setNewGroupName('');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  const trimmed = newGroupName.trim();
                  if (!trimmed) {
                    return;
                  }
                  void (async () => {
                    await onCreateChat({ group: trimmed });
                    setShowNewGroupDialog(false);
                    setNewGroupName('');
                  })();
                }}
                type="button"
              >
                创建
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {editingChat ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>重命名会话</h3>
              <p>会同步修改手机当前历史列表中的标题。</p>
            </header>
            <input autoFocus onChange={(event) => setDraftTitle(event.target.value)} value={draftTitle} />
            <footer>
              <button
                onClick={() => {
                  setEditingChat(null);
                  setDraftTitle('');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    await onRenameChat(editingChat, draftTitle);
                    setEditingChat(null);
                    setDraftTitle('');
                  })();
                }}
                type="button"
              >
                保存
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {bindingTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>编辑绑定</h3>
              <p>{bindingTarget.title}</p>
            </header>
            <select
              onChange={(event) => setBindingDraftValue(event.target.value)}
              value={bindingDraftValue}
            >
              <option value="unbound">未绑定</option>
              {characterSelector?.cards.map((card) => (
                <option key={`card:${card.id}`} value={`card:${card.id}`}>
                  {`角色卡: ${card.name}`}
                </option>
              ))}
              {characterSelector?.groups.map((group) => (
                <option key={`group:${group.id}`} value={`group:${group.id}`}>
                  {`角色组: ${group.name}`}
                </option>
              ))}
              {bindingDraftValue.startsWith('card-legacy:') ? (
                <option value={bindingDraftValue}>{`角色卡: ${bindingDraftValue.slice('card-legacy:'.length)}`}</option>
              ) : null}
              {bindingDraftValue.startsWith('group-legacy:') ? (
                <option value={bindingDraftValue}>{`角色组: ${bindingDraftValue.slice('group-legacy:'.length)}`}</option>
              ) : null}
            </select>
            <footer>
              <button
                onClick={() => {
                  setBindingTarget(null);
                  setBindingDraftValue('unbound');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    let payload: {
                      character_card_name?: string | null;
                      character_group_id?: string | null;
                      update_binding: boolean;
                    } = {
                      character_card_name: null,
                      character_group_id: null,
                      update_binding: true
                    };

                    if (bindingDraftValue.startsWith('card:')) {
                      const cardId = bindingDraftValue.slice('card:'.length);
                      const card = characterSelector?.cards.find((item) => item.id === cardId);
                      payload = {
                        character_card_name: card?.name ?? null,
                        character_group_id: null,
                        update_binding: true
                      };
                    } else if (bindingDraftValue.startsWith('group:')) {
                      payload = {
                        character_card_name: null,
                        character_group_id: bindingDraftValue.slice('group:'.length),
                        update_binding: true
                      };
                    } else if (bindingDraftValue.startsWith('card-legacy:')) {
                      payload = {
                        character_card_name: bindingDraftValue.slice('card-legacy:'.length),
                        character_group_id: null,
                        update_binding: true
                      };
                    } else if (bindingDraftValue.startsWith('group-legacy:')) {
                      payload = {
                        character_card_name: null,
                        character_group_id: bindingDraftValue.slice('group-legacy:'.length),
                        update_binding: true
                      };
                    }

                    await onUpdateChat(bindingTarget, payload);
                    setBindingTarget(null);
                    setBindingDraftValue('unbound');
                  })();
                }}
                type="button"
              >
                保存
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {moveTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>移动分组</h3>
              <p>{moveTarget.title}</p>
            </header>
            <input
              autoFocus
              onChange={(event) => setMoveGroupDraft(event.target.value)}
              placeholder="留空表示未分组"
              value={moveGroupDraft}
            />
            <footer>
              <button
                onClick={() => {
                  setMoveTarget(null);
                  setMoveGroupDraft('');
                }}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    await onUpdateChat(moveTarget, {
                      group: normalizeGroupName(moveGroupDraft),
                      update_group: true
                    });
                    setMoveTarget(null);
                    setMoveGroupDraft('');
                  })();
                }}
                type="button"
              >
                保存
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {groupActionTarget ? (
        <div className="dialog-scrim" role="presentation">
        <div className="history-dialog" role="dialog">
          <header>
            <h3>管理分组</h3>
            <p>{groupActionTarget.groupName}</p>
          </header>
            <input
              autoFocus
              onChange={(event) => setGroupRenameDraft(event.target.value)}
              placeholder="新的分组名称"
              value={groupRenameDraft}
            />
            <footer>
              <button
                onClick={() => setGroupActionTarget(null)}
                type="button"
              >
                取消
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    await onRenameGroup(
                      groupActionTarget.groupName,
                      groupRenameDraft,
                      groupActionTarget.characterCardName
                    );
                    setGroupActionTarget(null);
                  })();
                }}
                type="button"
              >
                重命名
              </button>
              <button
                onClick={() => {
                  void (async () => {
                    await onDeleteGroup(
                      groupActionTarget.groupName,
                      false,
                      groupActionTarget.characterCardName
                    );
                    setGroupActionTarget(null);
                  })();
                }}
                type="button"
              >
                清空分组
              </button>
              <button
                className="is-danger"
                onClick={() => {
                  void (async () => {
                    await onDeleteGroup(
                      groupActionTarget.groupName,
                      true,
                      groupActionTarget.characterCardName
                    );
                    setGroupActionTarget(null);
                  })();
                }}
                type="button"
              >
                删除分组和聊天
              </button>
            </footer>
          </div>
        </div>
      ) : null}

      {deleteTarget ? (
        <div className="dialog-scrim" role="presentation">
          <div className="history-dialog" role="dialog">
            <header>
              <h3>确认删除聊天</h3>
              <p>确定要删除聊天“{deleteTarget.title}”吗？此操作不可撤销。</p>
            </header>
            <div className="history-dialog-delete-target">{deleteTarget.title}</div>
            <footer>
              <button onClick={() => setDeleteTarget(null)} type="button">
                取消
              </button>
              <button
                className="is-danger"
                onClick={() => {
                  void (async () => {
                    await onDeleteChat(deleteTarget);
                    setDeleteTarget(null);
                  })();
                }}
                type="button"
              >
                确定删除
              </button>
            </footer>
          </div>
        </div>
      ) : null}
    </>
  );
}
