export type ChatSender = 'user' | 'assistant' | 'summary' | 'system';
export type ChatStyle = 'bubble' | 'cursor';
export type InputStyle = 'classic' | 'agent';
export type InputProcessingStage = 'idle' | 'connecting' | 'uploading' | 'streaming';

export interface WebCapabilities {
  attachments: boolean;
  per_chat_theme: boolean;
  structured_render: boolean;
  streaming: boolean;
  rename_chat: boolean;
  delete_chat: boolean;
}

export interface WebBootstrapResponse {
  version_name: string;
  current_chat_id: string | null;
  default_chat_style: string;
  default_input_style: string;
  show_thinking_process: boolean;
  show_status_tags: boolean;
  show_input_processing_status: boolean;
  capabilities: WebCapabilities;
}

export interface WebChatSummary {
  id: string;
  title: string;
  updated_at: number;
  group: string | null;
  character_card_name?: string | null;
  character_group_id?: string | null;
  character_group_name?: string | null;
  binding_avatar_url?: string | null;
  parent_chat_id?: string | null;
  active_streaming: boolean;
  locked: boolean;
}

export interface WebMessageAttachment {
  id: string;
  file_name: string;
  mime_type: string;
  file_size?: number | null;
  content?: string | null;
  asset_url?: string | null;
}

export interface WebMessageContentBlock {
  kind: 'text' | 'xml' | 'group';
  content?: string | null;
  xml?: string | null;
  tag_name?: string | null;
  raw_tag_name?: string | null;
  attrs?: Record<string, string>;
  closed?: boolean;
  group_type?: 'think_tools' | 'tools_only' | string | null;
  children?: WebMessageContentBlock[] | null;
}

export interface WebReplyPreview {
  sender: string;
  timestamp: number;
  content: string;
}

export interface WebMessageImageLink {
  id: string;
  asset_url?: string | null;
  expired: boolean;
}

export interface WebChatMessage {
  id: string;
  sender: ChatSender;
  content_raw: string;
  timestamp: number;
  streaming?: boolean;
  role_name?: string | null;
  provider?: string | null;
  model_name?: string | null;
  display_content?: string | null;
  display_name?: string | null;
  display_name_is_proxy?: boolean;
  avatar_url?: string | null;
  reply_preview?: WebReplyPreview | null;
  image_links?: WebMessageImageLink[];
  content_blocks?: WebMessageContentBlock[] | null;
  attachments: WebMessageAttachment[];
}

export interface WebChatMessagesPage {
  messages: WebChatMessage[];
  has_more_before: boolean;
  has_more_after: boolean;
  next_before_timestamp?: number | null;
  next_after_timestamp?: number | null;
}

export interface WebChatMessageLocatorPreview {
  message_index: number | null;
  timestamp: number;
  sender: ChatSender | 'think' | string;
  preview_content: string;
  content_length: number;
  display_mode: string;
  is_favorite: boolean;
}

export type HistoryDisplayMode = 'BY_CHARACTER_CARD' | 'BY_FOLDER' | 'CURRENT_CHARACTER_ONLY';

export interface WebThemeBackground {
  type: string;
  asset_url?: string | null;
  opacity: number;
}

export interface WebThemePalette {
  background_color: string;
  surface_color: string;
  surface_variant_color: string;
  surface_container_color: string;
  surface_container_high_color: string;
  primary_color: string;
  secondary_color: string;
  primary_container_color: string;
  on_primary_container_color: string;
  on_surface_color: string;
  on_surface_variant_color: string;
  outline_color: string;
  outline_variant_color: string;
}

export interface WebHeaderTheme {
  transparent: boolean;
  overlay: boolean;
}

export interface WebInputTheme {
  style: string;
  transparent: boolean;
  floating: boolean;
  liquid_glass: boolean;
  water_glass: boolean;
}

export interface WebFontTheme {
  type: string;
  system_font_name?: string | null;
  custom_font_asset_url?: string | null;
  scale: number;
}

export interface WebBubbleImageTheme {
  enabled: boolean;
  asset_url?: string | null;
  render_mode?: string | null;
}

export interface WebBubbleTheme {
  show_avatar: boolean;
  wide_layout: boolean;
  cursor_user_follow_theme: boolean;
  cursor_user_color?: string | null;
  user_bubble_color?: string | null;
  assistant_bubble_color?: string | null;
  user_text_color?: string | null;
  assistant_text_color?: string | null;
  cursor_user_liquid_glass: boolean;
  cursor_user_water_glass: boolean;
  user_liquid_glass: boolean;
  user_water_glass: boolean;
  assistant_liquid_glass: boolean;
  assistant_water_glass: boolean;
  user_rounded: boolean;
  assistant_rounded: boolean;
  user_padding_left: number;
  user_padding_right: number;
  assistant_padding_left: number;
  assistant_padding_right: number;
  user_image: WebBubbleImageTheme;
  assistant_image: WebBubbleImageTheme;
}

export interface WebAvatarTheme {
  shape: string;
  corner_radius?: number | null;
  user_avatar_url?: string | null;
  assistant_avatar_url?: string | null;
}

export interface WebDisplayPreferences {
  show_user_name: boolean;
  show_role_name: boolean;
  show_model_name: boolean;
  show_model_provider: boolean;
  show_message_token_stats: boolean;
  show_message_timing_stats: boolean;
  show_message_timestamp: boolean;
  tool_collapse_mode: string;
  global_user_name?: string | null;
}

export interface WebThemeSnapshot {
  source: string;
  source_id?: string | null;
  theme_mode: string;
  use_system_theme: boolean;
  use_custom_colors: boolean;
  primary_color?: string | null;
  secondary_color?: string | null;
  palette: WebThemePalette;
  background: WebThemeBackground;
  header: WebHeaderTheme;
  input: WebInputTheme;
  font: WebFontTheme;
  chat_style: string;
  show_thinking_process: boolean;
  show_status_tags: boolean;
  show_input_processing_status: boolean;
  display: WebDisplayPreferences;
  bubble: WebBubbleTheme;
  avatars: WebAvatarTheme;
}

export interface WebUploadedAttachment {
  attachment_id: string;
  file_name: string;
  mime_type: string;
  file_size: number;
}

export type WebActivePromptType = 'character_card' | 'character_group';

export interface WebActivePromptTarget {
  type: WebActivePromptType;
  id: string;
}

export interface WebActivePromptSnapshot extends WebActivePromptTarget {
  name: string;
  avatar_url?: string | null;
}

export interface WebCharacterCardSelectorItem {
  id: string;
  name: string;
  description: string;
  avatar_url?: string | null;
  created_at: number;
  updated_at: number;
}

export interface WebCharacterGroupSelectorItem {
  id: string;
  name: string;
  description: string;
  member_count: number;
  avatar_url?: string | null;
  created_at: number;
  updated_at: number;
}

export interface WebCharacterSelectorResponse {
  active_prompt: WebActivePromptSnapshot;
  cards: WebCharacterCardSelectorItem[];
  groups: WebCharacterGroupSelectorItem[];
}

export interface WebModelSelectorConfig {
  id: string;
  name: string;
  model_name: string;
  models: string[];
  selected: boolean;
  selected_model_index?: number | null;
}

export interface WebModelSelectorState {
  current_config_id: string;
  current_config_name?: string | null;
  current_model_index: number;
  current_model_name: string;
  current_provider_type: string;
  locked_by_character_card: boolean;
  locked_character_card_id?: string | null;
  locked_character_card_name?: string | null;
  configs: WebModelSelectorConfig[];
}

export interface WebSelectModelResponse {
  success: boolean;
  requires_character_card_switch_confirmation: boolean;
  selector: WebModelSelectorState;
}

export interface WebMemoryProfileItem {
  id: string;
  name: string;
}

export interface WebMemorySelectorState {
  current_profile_id: string;
  profiles: WebMemoryProfileItem[];
}

export interface WebChatStreamEvent {
  event: 'start' | 'user_message' | 'assistant_delta' | 'assistant_done' | 'error';
  chat_id: string;
  message?: WebChatMessage | null;
  delta?: string | null;
  error?: string | null;
}

export interface PendingQueueMessageItem {
  id: number;
  text: string;
}

export interface ContextStatsSnapshot {
  currentValue: number;
  maxValue: number;
  percent: number;
}

export interface WebInputSettingsState {
  enable_thinking_mode: boolean;
  thinking_quality_level: number;
  enable_memory_auto_update: boolean;
  enable_auto_read: boolean;
  enable_max_context_mode: boolean;
  enable_tools: boolean;
  disable_stream_output: boolean;
  disable_user_preference_description: boolean;
  permission_level: 'ALLOW' | 'ASK' | 'FORBID' | string;
  current_window_tokens: number;
  base_context_length_k: number;
  max_context_length_k: number;
  active_context_length_k: number;
  max_window_tokens: number;
}

export interface WebChatReorderItem {
  chat_id: string;
  display_order: number;
  group?: string | null;
}
