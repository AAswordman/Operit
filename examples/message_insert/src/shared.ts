const SETTINGS_PREFS_NAME = "toolpkg_message_insert_extra_info";
const SETTINGS_KEY = "message_insert_extra_info_settings";

const COMBINED_ATTACHMENT_FILE_NAME_PREFIX = "Time:";
const COMBINED_ATTACHMENT_ID_PREFIX = "message_insert_extra_bundle_";
const LEGACY_ATTACHMENT_FILE_NAMES = [
  "extra_info_time.txt",
  "extra_info_battery.txt",
  "extra_info_weather.txt",
  "extra_info_location.txt",
  "extra_info_notifications.txt",
] as const;
const LEGACY_ATTACHMENT_ID_PREFIXES = [
  "message_insert_extra_time_",
  "message_insert_extra_battery_",
  "message_insert_extra_weather_",
  "message_insert_extra_location_",
  "message_insert_extra_notifications_",
] as const;

const NOTIFICATION_FETCH_LIMIT = 5;
const APP_USAGE_FETCH_LIMIT = 3;
// Weather is a pre-send injection path, so the shared Hook deadline bounds its location and HTTP work.
const WEATHER_INJECTION_STEP_TIMEOUT_SECONDS = 5;

export type ExtraInfoInjectionSettings = {
  masterEnabled: boolean;
  persistInjectedContent: boolean;
  injectTime: boolean;
  injectBattery: boolean;
  injectWeather: boolean;
  injectLocation: boolean;
  usePreciseLocation: boolean;
  injectCurrentScreenApp: boolean;
  injectRecentAppUsage: boolean;
  injectScreenText: boolean;
  injectNotifications: boolean;
  injectMemory: boolean;
  allowRepeatedMemorySearch: boolean;
  memoryLimit: number;
};

export type ExtraInfoI18n = {
  menuTitle: string;
  menuDescription: string;
  toolboxTitle: string;
  toolboxSubtitle: string;
  toolboxBanner: string;
  masterSectionTitle: string;
  masterToggleTitle: string;
  masterToggleDescription: string;
  persistToggleTitle: string;
  persistToggleDescription: string;
  itemsSectionTitle: string;
  timeToggleTitle: string;
  timeToggleDescription: string;
  batteryToggleTitle: string;
  batteryToggleDescription: string;
  weatherToggleTitle: string;
  weatherToggleDescription: string;
  locationToggleTitle: string;
  locationToggleDescription: string;
  preciseLocationToggleTitle: string;
  preciseLocationToggleDescription: string;
  currentScreenAppToggleTitle: string;
  currentScreenAppToggleDescription: string;
  recentAppUsageToggleTitle: string;
  recentAppUsageToggleDescription: string;
  screenTextToggleTitle: string;
  screenTextToggleDescription: string;
  notificationsToggleTitle: string;
  notificationsToggleDescription: string;
  memoryToggleTitle: string;
  memoryToggleDescription: string;
  memoryConfigTitle: string;
  memoryConfigDescription: string;
  memoryRepeatToggleTitle: string;
  memoryRepeatToggleDescription: string;
  memoryLimitFieldLabel: string;
  memoryLimitFieldDescription: string;
  memoryLimitFieldPlaceholder: string;
  memoryConfigApplyButton: string;
  invalidMemoryLimitMessage: string;
  summarySectionTitle: string;
  summaryMasterEnabled: string;
  summaryMasterDisabled: string;
  summaryPersistEnabled: string;
  summaryPersistDisabled: string;
  summaryTimeEnabled: string;
  summaryTimeDisabled: string;
  summaryBatteryEnabled: string;
  summaryBatteryDisabled: string;
  summaryWeatherEnabled: string;
  summaryWeatherDisabled: string;
  summaryLocationEnabled: string;
  summaryLocationDisabled: string;
  summaryPreciseLocationEnabled: string;
  summaryPreciseLocationDisabled: string;
  summaryCurrentScreenAppEnabled: string;
  summaryCurrentScreenAppDisabled: string;
  summaryRecentAppUsageEnabled: string;
  summaryRecentAppUsageDisabled: string;
  summaryScreenTextEnabled: string;
  summaryScreenTextDisabled: string;
  summaryNotificationsEnabled: string;
  summaryNotificationsDisabled: string;
  summaryMemoryEnabled: string;
  summaryMemoryDisabled: string;
  summaryMemoryRepeatEnabled: string;
  summaryMemoryRepeatDisabled: string;
  summaryRulesHint: string;
  saveErrorPrefix: string;
  attachmentTimeTitle: string;
  attachmentBatteryTitle: string;
  attachmentWeatherTitle: string;
  attachmentLocationTitle: string;
  attachmentCurrentScreenAppTitle: string;
  attachmentRecentAppUsageTitle: string;
  attachmentScreenTextTitle: string;
  attachmentNotificationsTitle: string;
  attachmentMemoryTitle: string;
  timeZoneLabel: string;
  weekdayLabel: string;
  batteryLevelLabel: string;
  batteryStatusLabel: string;
  batteryCharging: string;
  batteryNotCharging: string;
  batteryFull: string;
  weatherLocationLabel: string;
  weatherConditionLabel: string;
  weatherTemperatureLabel: string;
  weatherFeelsLikeLabel: string;
  weatherHumidityLabel: string;
  weatherWindLabel: string;
  weatherSourceLabel: string;
  locationAddressLabel: string;
  locationCoordinatesLabel: string;
  locationAccuracyLabel: string;
  locationProviderLabel: string;
  locationTimestampLabel: string;
  currentScreenAppLabel: string;
  currentScreenPackageLabel: string;
  currentScreenActivityLabel: string;
  appUsageWindowLabel: string;
  appUsageDurationLabel: string;
  appUsageLastUsedLabel: string;
  appUsageEmpty: string;
  screenTextScreenshotPathLabel: string;
  screenTextLineCountLabel: string;
  screenTextEmpty: string;
  notificationCountLabel: string;
  notificationAppLabel: string;
  notificationTextLabel: string;
  notificationTimeLabel: string;
  notificationsEmpty: string;
  memoryQueryLabel: string;
  memorySnapshotLabel: string;
  memoryLimitLabel: string;
  memoryResultCountLabel: string;
  memoryTitleLabel: string;
  memoryContentLabel: string;
  memorySourceLabel: string;
  memoryCreatedAtLabel: string;
  memoryTagsLabel: string;
  memoryChunkInfoLabel: string;
  memoryEmpty: string;
  memorySnapshotUnavailable: string;
  errorLabel: string;
};

const ZH_CN_I18N: ExtraInfoI18n = {
  menuTitle: "额外信息注入",
  menuDescription: "发送消息时自动附加时间、电量、天气、位置、通知、记忆等额外信息，并与设置页开关同步",
  toolboxTitle: "额外信息注入",
  toolboxSubtitle: "把时间、电量、天气、位置、通知、记忆作为显性附件注入到用户消息里，并可单独控制是否随聊天记录一起保存。",
  toolboxBanner: "这里的开关和输入菜单里的“额外信息注入”是同一个状态；你可以分别控制注入项目、是否落盘保存，以及记忆检索是否允许重复命中。",
  masterSectionTitle: "注入开关",
  masterToggleTitle: "额外信息注入",
  masterToggleDescription: "和输入菜单里的“额外信息注入”开关完全同步，切一个地方，另一个地方会一起变化。",
  persistToggleTitle: "注入内容随消息保存",
  persistToggleDescription: "关闭后，额外信息只在发送给模型时注入，不写入聊天记录。",
  itemsSectionTitle: "注入项目",
  timeToggleTitle: "注入时间",
  timeToggleDescription: "每次发送消息时都插入当前时间附件。",
  batteryToggleTitle: "注入电量",
  batteryToggleDescription: "每次发送消息时都插入当前电量与充电状态。",
  weatherToggleTitle: "注入天气",
  weatherToggleDescription: "每次发送消息时都插入当前天气信息。",
  locationToggleTitle: "注入位置",
  locationToggleDescription: "每次发送消息时都插入当前定位信息与地址。",
  preciseLocationToggleTitle: "精确定位",
  preciseLocationToggleDescription: "仅在注入位置时生效；开启后使用高精度定位，可能更慢也更耗电。",
  currentScreenAppToggleTitle: "注入当前屏幕应用",
  currentScreenAppToggleDescription: "每次发送消息时都插入当前屏幕所属应用与 Activity。",
  recentAppUsageToggleTitle: "注入前几个应用使用时长",
  recentAppUsageToggleDescription: "每次发送消息时都插入最近 24 小时内前几个应用的前台使用时长。",
  screenTextToggleTitle: "注入屏幕文本",
  screenTextToggleDescription: "截图当前屏幕后调用 OCR 提取文本，并作为附件插入。",
  notificationsToggleTitle: "注入通知",
  notificationsToggleDescription: "每次发送消息时都插入最近通知摘要。",
  memoryToggleTitle: "注入记忆",
  memoryToggleDescription: "每次发送消息时根据当前输入自动分词检索记忆，并把命中的记忆摘要附加进去。",
  memoryConfigTitle: "记忆检索设置",
  memoryConfigDescription: "记忆搜索会直接使用软件内的记忆搜索设置；这里仅控制同会话去重和每次最多注入多少条。默认会复用当前会话 id 的前六位作为快照 id；开启“允许重复命中”后，将不再复用快照，同一条记忆后续还可以再次被检索到。",
  memoryRepeatToggleTitle: "允许重复命中同一记忆",
  memoryRepeatToggleDescription: "开启后，每次都会使用新的记忆查询快照，不再排除本会话里之前命中过的记忆。",
  memoryLimitFieldLabel: "记忆上限",
  memoryLimitFieldDescription: "默认 3。控制每次最多注入多少条记忆。",
  memoryLimitFieldPlaceholder: "例如 3",
  memoryConfigApplyButton: "保存记忆设置",
  invalidMemoryLimitMessage: "记忆上限必须是大于等于 1 的整数",
  summarySectionTitle: "当前规则",
  summaryMasterEnabled: "额外信息注入：已开启",
  summaryMasterDisabled: "额外信息注入：已关闭",
  summaryPersistEnabled: "保存策略：注入内容会随消息一起落盘",
  summaryPersistDisabled: "保存策略：注入内容只发送给模型，不写入聊天记录",
  summaryTimeEnabled: "时间：每次发送都注入",
  summaryTimeDisabled: "时间：已关闭",
  summaryBatteryEnabled: "电量：每次发送都注入",
  summaryBatteryDisabled: "电量：已关闭",
  summaryWeatherEnabled: "天气：每次发送都注入",
  summaryWeatherDisabled: "天气：已关闭",
  summaryLocationEnabled: "位置：每次发送都注入",
  summaryLocationDisabled: "位置：已关闭",
  summaryPreciseLocationEnabled: "精确定位：注入位置时使用高精度定位",
  summaryPreciseLocationDisabled: "精确定位：已关闭",
  summaryCurrentScreenAppEnabled: "当前屏幕应用：每次发送都注入",
  summaryCurrentScreenAppDisabled: "当前屏幕应用：已关闭",
  summaryRecentAppUsageEnabled: "应用使用时长：每次发送都注入前几个应用的使用时长",
  summaryRecentAppUsageDisabled: "应用使用时长：已关闭",
  summaryScreenTextEnabled: "屏幕文本：每次发送都会截图并执行 OCR",
  summaryScreenTextDisabled: "屏幕文本：已关闭",
  summaryNotificationsEnabled: "通知：每次发送都注入",
  summaryNotificationsDisabled: "通知：已关闭",
  summaryMemoryEnabled: "记忆：已开启，按当前输入自动分词检索",
  summaryMemoryDisabled: "记忆：已关闭",
  summaryMemoryRepeatEnabled: "记忆去重：允许重复命中",
  summaryMemoryRepeatDisabled: "记忆去重：默认排除本会话已命中的记忆",
  summaryRulesHint: "这些设置会直接影响用户消息中显性附件的生成规则。",
  saveErrorPrefix: "保存失败：",
  attachmentTimeTitle: "【当前时间】",
  attachmentBatteryTitle: "【当前电量】",
  attachmentWeatherTitle: "【当前天气】",
  attachmentLocationTitle: "【当前位置】",
  attachmentCurrentScreenAppTitle: "【当前屏幕应用】",
  attachmentRecentAppUsageTitle: "【应用使用时长】",
  attachmentScreenTextTitle: "【屏幕文本】",
  attachmentNotificationsTitle: "【最近通知】",
  attachmentMemoryTitle: "【相关记忆】",
  timeZoneLabel: "时区",
  weekdayLabel: "星期",
  batteryLevelLabel: "电量",
  batteryStatusLabel: "状态",
  batteryCharging: "充电中",
  batteryNotCharging: "未充电",
  batteryFull: "已充满",
  weatherLocationLabel: "地点",
  weatherConditionLabel: "天气",
  weatherTemperatureLabel: "温度",
  weatherFeelsLikeLabel: "体感",
  weatherHumidityLabel: "湿度",
  weatherWindLabel: "风速",
  weatherSourceLabel: "来源",
  locationAddressLabel: "地址",
  locationCoordinatesLabel: "坐标",
  locationAccuracyLabel: "精度",
  locationProviderLabel: "定位源",
  locationTimestampLabel: "时间",
  currentScreenAppLabel: "应用",
  currentScreenPackageLabel: "包名",
  currentScreenActivityLabel: "Activity",
  appUsageWindowLabel: "统计窗口",
  appUsageDurationLabel: "使用时长",
  appUsageLastUsedLabel: "最近使用",
  appUsageEmpty: "当前没有可注入的应用使用时长数据",
  screenTextScreenshotPathLabel: "截图路径",
  screenTextLineCountLabel: "文本行数",
  screenTextEmpty: "当前屏幕未识别到可用文本",
  notificationCountLabel: "通知数量",
  notificationAppLabel: "应用",
  notificationTextLabel: "内容",
  notificationTimeLabel: "时间",
  notificationsEmpty: "当前没有可注入的通知",
  memoryQueryLabel: "查询",
  memorySnapshotLabel: "快照",
  memoryLimitLabel: "上限",
  memoryResultCountLabel: "命中数量",
  memoryTitleLabel: "标题",
  memoryContentLabel: "内容",
  memorySourceLabel: "来源",
  memoryCreatedAtLabel: "时间",
  memoryTagsLabel: "标签",
  memoryChunkInfoLabel: "分块",
  memoryEmpty: "当前没有命中的记忆",
  memorySnapshotUnavailable: "当前会话 id 不可用，无法生成记忆快照",
  errorLabel: "错误",
};

const EN_US_I18N: ExtraInfoI18n = {
  menuTitle: "Extra Info Injection",
  menuDescription: "Automatically attach time, battery, weather, location, notifications, memories, and other extra info when sending messages, synced with the settings switch",
  toolboxTitle: "Extra Info Injection",
  toolboxSubtitle: "Attach time, battery, weather, location, notifications, and memories as visible attachments to the user message, with a separate option for whether they are persisted in chat history.",
  toolboxBanner: "This switch is the same state as the input-menu toggle. You can separately control the injected items, whether they are persisted, and whether memory hits may repeat in the same chat.",
  masterSectionTitle: "Injection Switch",
  masterToggleTitle: "Extra Info Injection",
  masterToggleDescription: "This is the exact same switch as the input-menu toggle. Changing either one keeps the other in sync.",
  persistToggleTitle: "Persist injected content",
  persistToggleDescription: "When disabled, extra info is injected only for the model request and is not written into chat history.",
  itemsSectionTitle: "Injection Items",
  timeToggleTitle: "Inject Time",
  timeToggleDescription: "Insert the current time attachment on every send.",
  batteryToggleTitle: "Inject Battery",
  batteryToggleDescription: "Insert the current battery level and charging state on every send.",
  weatherToggleTitle: "Inject Weather",
  weatherToggleDescription: "Insert current weather information on every send.",
  locationToggleTitle: "Inject Location",
  locationToggleDescription: "Insert current location and address on every send.",
  preciseLocationToggleTitle: "Precise Location",
  preciseLocationToggleDescription: "Only applies to location injection. When enabled, high accuracy mode is used and may be slower or use more battery.",
  currentScreenAppToggleTitle: "Inject Current Screen App",
  currentScreenAppToggleDescription: "Insert the current foreground app and activity shown on screen on every send.",
  recentAppUsageToggleTitle: "Inject Recent App Usage",
  recentAppUsageToggleDescription: "Insert the top few app foreground usage durations from the last 24 hours on every send.",
  screenTextToggleTitle: "Inject Screen Text",
  screenTextToggleDescription: "Capture the current screen, run OCR, and insert the recognized text as an attachment.",
  notificationsToggleTitle: "Inject Notifications",
  notificationsToggleDescription: "Insert a summary of recent notifications on every send.",
  memoryToggleTitle: "Inject Memory",
  memoryToggleDescription: "Tokenize the current input, query related memories, and attach the matched memory summaries on every send.",
  memoryConfigTitle: "Memory Search Settings",
  memoryConfigDescription: "Memory lookup directly uses the app's memory search settings. This panel only controls same-chat de-duplication and how many memories are injected each time. By default, the first 6 characters of the current chat id are reused as the snapshot id. When repeated hits are allowed, a fresh snapshot is used for each query so previously matched memories can appear again.",
  memoryRepeatToggleTitle: "Allow repeated memory hits",
  memoryRepeatToggleDescription: "When enabled, each query uses a fresh snapshot instead of excluding memories that were already matched earlier in this chat.",
  memoryLimitFieldLabel: "Memory limit",
  memoryLimitFieldDescription: "Default is 3. Controls how many memories can be injected each time.",
  memoryLimitFieldPlaceholder: "For example 3",
  memoryConfigApplyButton: "Save memory settings",
  invalidMemoryLimitMessage: "Memory limit must be an integer greater than or equal to 1",
  summarySectionTitle: "Current Rules",
  summaryMasterEnabled: "Extra info injection: enabled",
  summaryMasterDisabled: "Extra info injection: disabled",
  summaryPersistEnabled: "Persistence: injected content is saved with the message",
  summaryPersistDisabled: "Persistence: injected content is sent only to the model and not saved in chat history",
  summaryTimeEnabled: "Time: inject on every send",
  summaryTimeDisabled: "Time: disabled",
  summaryBatteryEnabled: "Battery: inject on every send",
  summaryBatteryDisabled: "Battery: disabled",
  summaryWeatherEnabled: "Weather: inject on every send",
  summaryWeatherDisabled: "Weather: disabled",
  summaryLocationEnabled: "Location: inject on every send",
  summaryLocationDisabled: "Location: disabled",
  summaryPreciseLocationEnabled: "Precise location: use high accuracy mode for location injection",
  summaryPreciseLocationDisabled: "Precise location: disabled",
  summaryCurrentScreenAppEnabled: "Current screen app: inject on every send",
  summaryCurrentScreenAppDisabled: "Current screen app: disabled",
  summaryRecentAppUsageEnabled: "App usage: inject recent top app usage on every send",
  summaryRecentAppUsageDisabled: "App usage: disabled",
  summaryScreenTextEnabled: "Screen text: capture and run OCR on every send",
  summaryScreenTextDisabled: "Screen text: disabled",
  summaryNotificationsEnabled: "Notifications: inject on every send",
  summaryNotificationsDisabled: "Notifications: disabled",
  summaryMemoryEnabled: "Memory: enabled with automatic tokenized lookup",
  summaryMemoryDisabled: "Memory: disabled",
  summaryMemoryRepeatEnabled: "Memory dedupe: repeated hits are allowed",
  summaryMemoryRepeatDisabled: "Memory dedupe: previously hit memories are excluded in this chat",
  summaryRulesHint: "These settings directly control how visible attachments are generated for user messages.",
  saveErrorPrefix: "Save failed: ",
  attachmentTimeTitle: "[Current Time]",
  attachmentBatteryTitle: "[Current Battery]",
  attachmentWeatherTitle: "[Current Weather]",
  attachmentLocationTitle: "[Current Location]",
  attachmentCurrentScreenAppTitle: "[Current Screen App]",
  attachmentRecentAppUsageTitle: "[Recent App Usage]",
  attachmentScreenTextTitle: "[Screen Text]",
  attachmentNotificationsTitle: "[Recent Notifications]",
  attachmentMemoryTitle: "[Related Memories]",
  timeZoneLabel: "Time zone",
  weekdayLabel: "Weekday",
  batteryLevelLabel: "Level",
  batteryStatusLabel: "Status",
  batteryCharging: "Charging",
  batteryNotCharging: "Not charging",
  batteryFull: "Fully charged",
  weatherLocationLabel: "Location",
  weatherConditionLabel: "Condition",
  weatherTemperatureLabel: "Temperature",
  weatherFeelsLikeLabel: "Feels like",
  weatherHumidityLabel: "Humidity",
  weatherWindLabel: "Wind",
  weatherSourceLabel: "Source",
  locationAddressLabel: "Address",
  locationCoordinatesLabel: "Coordinates",
  locationAccuracyLabel: "Accuracy",
  locationProviderLabel: "Provider",
  locationTimestampLabel: "Time",
  currentScreenAppLabel: "App",
  currentScreenPackageLabel: "Package",
  currentScreenActivityLabel: "Activity",
  appUsageWindowLabel: "Time window",
  appUsageDurationLabel: "Duration",
  appUsageLastUsedLabel: "Last used",
  appUsageEmpty: "There is no app usage data to inject right now",
  screenTextScreenshotPathLabel: "Screenshot path",
  screenTextLineCountLabel: "Line count",
  screenTextEmpty: "No usable text was recognized on the current screen",
  notificationCountLabel: "Notification count",
  notificationAppLabel: "App",
  notificationTextLabel: "Content",
  notificationTimeLabel: "Time",
  notificationsEmpty: "There are no notifications to inject right now",
  memoryQueryLabel: "Query",
  memorySnapshotLabel: "Snapshot",
  memoryLimitLabel: "Limit",
  memoryResultCountLabel: "Matched",
  memoryTitleLabel: "Title",
  memoryContentLabel: "Content",
  memorySourceLabel: "Source",
  memoryCreatedAtLabel: "Time",
  memoryTagsLabel: "Tags",
  memoryChunkInfoLabel: "Chunk",
  memoryEmpty: "There are no matched memories right now",
  memorySnapshotUnavailable: "Current chat id is unavailable, so the memory snapshot cannot be created",
  errorLabel: "Error",
};

const DEFAULT_SETTINGS: ExtraInfoInjectionSettings = {
  masterEnabled: false,
  persistInjectedContent: true,
  injectTime: true,
  injectBattery: false,
  injectWeather: false,
  injectLocation: false,
  usePreciseLocation: false,
  injectCurrentScreenApp: false,
  injectRecentAppUsage: false,
  injectScreenText: false,
  injectNotifications: false,
  injectMemory: false,
  allowRepeatedMemorySearch: false,
  memoryLimit: 3,
};

function normalizeLocale(locale?: string): string {
  const value = String(locale || "").trim().toLowerCase();
  if (!value) {
    return "zh-CN";
  }
  if (value.startsWith("en")) {
    return "en-US";
  }
  if (value.startsWith("zh")) {
    return "zh-CN";
  }
  return "zh-CN";
}

export function resolveExtraInfoI18n(locale?: string): ExtraInfoI18n {
  const rawLocale = locale ?? (typeof getLang === "function" ? getLang() : "");
  return normalizeLocale(rawLocale) === "en-US" ? EN_US_I18N : ZH_CN_I18N;
}

export function getAppContext() {
  if (typeof Java.getApplicationContext !== "function") {
    return null;
  }
  return Java.getApplicationContext();
}

function getPrefs() {
  const context = getAppContext();
  if (!context) {
    throw new Error("application context unavailable");
  }
  return context.getSharedPreferences(SETTINGS_PREFS_NAME, 0);
}

function sanitizeSettings(input: Partial<ExtraInfoInjectionSettings> | null | undefined): ExtraInfoInjectionSettings {
  const memoryLimit = Number(input?.memoryLimit);
  return {
    masterEnabled: Boolean(input?.masterEnabled ?? DEFAULT_SETTINGS.masterEnabled),
    persistInjectedContent: Boolean(
      input?.persistInjectedContent ?? DEFAULT_SETTINGS.persistInjectedContent
    ),
    injectTime: Boolean(input?.injectTime ?? DEFAULT_SETTINGS.injectTime),
    injectBattery: Boolean(input?.injectBattery ?? DEFAULT_SETTINGS.injectBattery),
    injectWeather: Boolean(input?.injectWeather ?? DEFAULT_SETTINGS.injectWeather),
    injectLocation: Boolean(input?.injectLocation ?? DEFAULT_SETTINGS.injectLocation),
    usePreciseLocation: Boolean(
      input?.usePreciseLocation ?? DEFAULT_SETTINGS.usePreciseLocation
    ),
    injectCurrentScreenApp: Boolean(
      input?.injectCurrentScreenApp ?? DEFAULT_SETTINGS.injectCurrentScreenApp
    ),
    injectRecentAppUsage: Boolean(
      input?.injectRecentAppUsage ?? DEFAULT_SETTINGS.injectRecentAppUsage
    ),
    injectScreenText: Boolean(input?.injectScreenText ?? DEFAULT_SETTINGS.injectScreenText),
    injectNotifications: Boolean(input?.injectNotifications ?? DEFAULT_SETTINGS.injectNotifications),
    injectMemory: Boolean(input?.injectMemory ?? DEFAULT_SETTINGS.injectMemory),
    allowRepeatedMemorySearch: Boolean(
      input?.allowRepeatedMemorySearch ?? DEFAULT_SETTINGS.allowRepeatedMemorySearch
    ),
    memoryLimit: Number.isFinite(memoryLimit) && memoryLimit >= 1
      ? Math.floor(memoryLimit)
      : DEFAULT_SETTINGS.memoryLimit,
  };
}

export function loadSettings(): ExtraInfoInjectionSettings {
  try {
    const raw = String(getPrefs().getString(SETTINGS_KEY, "") || "").trim();
    if (!raw) {
      return { ...DEFAULT_SETTINGS };
    }
    return sanitizeSettings(JSON.parse(raw) as Partial<ExtraInfoInjectionSettings>);
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(
  patch: Partial<ExtraInfoInjectionSettings>
): ExtraInfoInjectionSettings {
  const next = sanitizeSettings({ ...loadSettings(), ...patch });
  getPrefs().edit().putString(SETTINGS_KEY, JSON.stringify(next)).apply();
  return next;
}

export function getExtraInfoInjectionEnabled(): boolean {
  return loadSettings().masterEnabled;
}

export function setExtraInfoInjectionEnabled(enabled: boolean): ExtraInfoInjectionSettings {
  return saveSettings({ masterEnabled: !!enabled });
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

export function containsExtraInfoAttachment(input: string): boolean {
  const attachmentMarkers = [
    `filename="${COMBINED_ATTACHMENT_FILE_NAME_PREFIX}`,
    `id="${COMBINED_ATTACHMENT_ID_PREFIX}`,
    ...LEGACY_ATTACHMENT_FILE_NAMES.map(name => `filename="${name}"`),
    ...LEGACY_ATTACHMENT_ID_PREFIXES.map(prefix => `id="${prefix}`),
  ];
  return attachmentMarkers.some(marker => input.includes(marker));
}

function buildAttachmentTag(
  idPrefix: string,
  fileName: string,
  content: string
): string {
  const attachmentId = `${idPrefix}${Date.now()}`;
  const attributes = [
    `id="${escapeXml(attachmentId)}"`,
    `filename="${escapeXml(fileName)}"`,
    `type="text/plain"`,
    `size="${content.length}"`,
  ].join(" ");
  return `<attachment ${attributes}>${escapeXml(content)}</attachment>`;
}

function buildTimeContent(): string {
  const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
  const DateClass = Java.type("java.util.Date");
  const LocaleClass = Java.type("java.util.Locale");
  const TimeZoneClass = Java.type("java.util.TimeZone");

  const locale = LocaleClass.getDefault();
  const now = new DateClass();
  const text = resolveExtraInfoI18n();
  const localTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(now);
  const weekday = new SimpleDateFormat("EEEE", locale).format(now);
  const timeZone = TimeZoneClass.getDefault().getID();

  return [
    text.attachmentTimeTitle,
    localTime,
    `${text.timeZoneLabel}: ${timeZone}`,
    `${text.weekdayLabel}: ${weekday}`,
  ].join("\n");
}

function formatLocalTimestamp(timestampMs: number): string {
  const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
  const DateClass = Java.type("java.util.Date");
  const LocaleClass = Java.type("java.util.Locale");
  const locale = LocaleClass.getDefault();
  return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(new DateClass(timestampMs));
}

function formatCoordinates(latitude: number, longitude: number): string {
  return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
}

function buildLocationParts(location: any): string[] {
  return [
    location?.city,
    location?.province,
    location?.country,
  ].map((item: unknown) => String(item || "").trim()).filter(Boolean);
}

async function readLocationSnapshot(
  highAccuracy = false,
  timeoutSeconds = 8,
  includeAddress = true
): Promise<{
  location: any;
  latitude: number;
  longitude: number;
  addressParts: string[];
}> {
  const location = await Tools.System.getLocation(highAccuracy, timeoutSeconds, includeAddress);
  const latitude = Number(location?.latitude);
  const longitude = Number(location?.longitude);

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error("location coordinates unavailable");
  }

  return {
    location,
    latitude,
    longitude,
    addressParts: buildLocationParts(location),
  };
}

function resolveWeatherLanguage(locale?: string): string {
  return normalizeLocale(locale) === "en-US" ? "en" : "zh";
}

function buildCombinedAttachmentFileName(timestampMs: number): string {
  const SimpleDateFormat = Java.type("java.text.SimpleDateFormat");
  const DateClass = Java.type("java.util.Date");
  const LocaleClass = Java.type("java.util.Locale");
  const locale = LocaleClass.getDefault();
  const displayTime = new SimpleDateFormat("HH:mm dd/yyyy/M", locale).format(new DateClass(timestampMs));
  return `${COMBINED_ATTACHMENT_FILE_NAME_PREFIX}${displayTime}`;
}

function readBatterySnapshot() {
  const IntentClass = Java.type("android.content.Intent");
  const IntentFilterClass = Java.type("android.content.IntentFilter");
  const BatteryManagerClass = Java.type("android.os.BatteryManager");
  const context = getAppContext();
  if (!context) {
    throw new Error("application context unavailable");
  }

  const batteryIntent = context.registerReceiver(
    null,
    new IntentFilterClass(IntentClass.ACTION_BATTERY_CHANGED)
  );
  if (!batteryIntent) {
    throw new Error("battery intent unavailable");
  }

  const level = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_LEVEL, -1));
  const scale = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_SCALE, -1));
  const status = Number(batteryIntent.getIntExtra(BatteryManagerClass.EXTRA_STATUS, -1));
  const percentage = level >= 0 && scale > 0 ? Math.round((level * 100) / scale) : -1;

  return {
    percentage,
    status,
  };
}

function resolveBatteryStatus(status: number): string {
  const BatteryManagerClass = Java.type("android.os.BatteryManager");
  const text = resolveExtraInfoI18n();
  if (status === Number(BatteryManagerClass.BATTERY_STATUS_FULL)) {
    return text.batteryFull;
  }
  if (status === Number(BatteryManagerClass.BATTERY_STATUS_CHARGING)) {
    return text.batteryCharging;
  }
  return text.batteryNotCharging;
}

function buildBatteryContent(): string {
  const text = resolveExtraInfoI18n();
  const snapshot = readBatterySnapshot();
  if (snapshot.percentage < 0) {
    throw new Error("battery percentage unavailable");
  }
  return [
    text.attachmentBatteryTitle,
    `${text.batteryLevelLabel}: ${snapshot.percentage}%`,
    `${text.batteryStatusLabel}: ${resolveBatteryStatus(snapshot.status)}`,
  ].join("\n");
}

function buildErrorContent(title: string, error: unknown): string {
  const text = resolveExtraInfoI18n();
  const message = error instanceof Error ? error.message : String(error || "unknown");
  return [
    title,
    `${text.errorLabel}: ${message}`,
  ].join("\n");
}

async function fetchWeatherPayload(
  latitude: number,
  longitude: number,
  locale?: string
): Promise<any> {
  const queryLocation = `${latitude.toFixed(4)},${longitude.toFixed(4)}`;
  const weatherLanguage = resolveWeatherLanguage(locale);
  const response = await Tools.Net.http({
    url: `https://wttr.in/${queryLocation}?format=j1&lang=${weatherLanguage}`,
    method: "GET",
    headers: {
      Accept: "application/json",
    },
    connect_timeout: WEATHER_INJECTION_STEP_TIMEOUT_SECONDS,
    read_timeout: WEATHER_INJECTION_STEP_TIMEOUT_SECONDS,
    validateStatus: false,
  });

  if (Number(response.statusCode) < 200 || Number(response.statusCode) >= 300) {
    throw new Error(`weather request failed: HTTP ${response.statusCode}`);
  }

  const rawContent = String(response.content || "").trim();
  if (!rawContent) {
    throw new Error("weather response empty");
  }

  try {
    return JSON.parse(rawContent);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error || "unknown");
    throw new Error(`weather response parse failed: ${message}`);
  }
}

async function buildWeatherContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const locale = typeof getLang === "function" ? String(getLang() || "") : "";
  const locationSnapshot = await readLocationSnapshot(
    false,
    WEATHER_INJECTION_STEP_TIMEOUT_SECONDS,
    false
  );
  const payload = await fetchWeatherPayload(
    locationSnapshot.latitude,
    locationSnapshot.longitude,
    locale
  );
  const current = Array.isArray(payload?.current_condition) ? payload.current_condition[0] : null;
  const locationText = formatCoordinates(
    locationSnapshot.latitude,
    locationSnapshot.longitude
  );
  const weatherDesc = normalizeLocale(locale) === "en-US"
    ? String(current?.weatherDesc?.[0]?.value || "").trim()
    : String(current?.lang_zh?.[0]?.value || "").trim();
  const tempC = String(current?.temp_C || "").trim();
  const feelsLikeC = String(current?.FeelsLikeC || "").trim();
  const humidity = String(current?.humidity || "").trim();
  const windKmph = String(current?.windspeedKmph || "").trim();

  return [
    text.attachmentWeatherTitle,
    `${text.weatherLocationLabel}: ${locationText}`,
    `${text.weatherConditionLabel}: ${weatherDesc || "-"}`,
    `${text.weatherTemperatureLabel}: ${tempC ? `${tempC}°C` : "-"}${feelsLikeC ? ` (${text.weatherFeelsLikeLabel}: ${feelsLikeC}°C)` : ""}`,
    `${text.weatherHumidityLabel}: ${humidity ? `${humidity}%` : "-"}`,
    `${text.weatherWindLabel}: ${windKmph ? `${windKmph} km/h` : "-"}`,
    `${text.weatherSourceLabel}: wttr.in`,
  ].join("\n");
}

async function buildLocationContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const locationSnapshot = await readLocationSnapshot(loadSettings().usePreciseLocation);
  const { location, latitude, longitude, addressParts } = locationSnapshot;

  const coordinates = formatCoordinates(latitude, longitude);
  const accuracy =
    Number.isFinite(Number(location.accuracy)) && Number(location.accuracy) > 0
      ? `${Math.round(Number(location.accuracy))} m`
      : "-";
  const timestamp =
    Number.isFinite(Number(location.timestamp)) && Number(location.timestamp) > 0
      ? formatLocalTimestamp(Number(location.timestamp))
      : "-";

  return [
    text.attachmentLocationTitle,
    `${text.locationAddressLabel}: ${addressParts.join(" / ") || "-"}`,
    `${text.locationCoordinatesLabel}: ${coordinates}`,
    `${text.locationAccuracyLabel}: ${accuracy}`,
    `${text.locationProviderLabel}: ${String(location.provider || "-").trim() || "-"}`,
    `${text.locationTimestampLabel}: ${timestamp}`,
  ].join("\n");
}

function resolveAppLabel(packageName: string): string {
  const normalizedPackageName = String(packageName || "").trim();
  if (!normalizedPackageName) {
    return "-";
  }

  const context = getAppContext();
  if (!context) {
    return normalizedPackageName;
  }

  try {
    const applicationInfo = context.packageManager.getApplicationInfo(normalizedPackageName, 0);
    const label = String(applicationInfo.loadLabel(context.packageManager) || "").trim();
    return label || normalizedPackageName;
  } catch {
    return normalizedPackageName;
  }
}

async function readCurrentPageInfo(): Promise<any> {
  const result = await toolCall("get_page_info", {});
  if (!result || typeof result !== "object") {
    throw new Error("page info unavailable");
  }
  return result;
}

async function buildCurrentScreenAppContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const pageInfo = await readCurrentPageInfo();
  const packageName = String(pageInfo?.packageName || "").trim();
  const activityName = String(pageInfo?.activityName || "").trim();

  if (!packageName) {
    throw new Error("current screen package unavailable");
  }

  return [
    text.attachmentCurrentScreenAppTitle,
    `${text.currentScreenAppLabel}: ${resolveAppLabel(packageName)}`,
    `${text.currentScreenPackageLabel}: ${packageName}`,
    `${text.currentScreenActivityLabel}: ${activityName || "-"}`,
  ].join("\n");
}

function formatDurationMs(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const parts: string[] = [];

  if (hours > 0) {
    parts.push(`${hours}h`);
  }
  if (minutes > 0 || hours > 0) {
    parts.push(`${minutes}m`);
  }
  parts.push(`${seconds}s`);

  return parts.join(" ");
}

async function buildRecentAppUsageContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const result = await Tools.System.getAppUsageTime({
    sinceHours: 24,
    limit: APP_USAGE_FETCH_LIMIT,
    includeSystemApps: false,
  });
  const entries = Array.isArray(result?.entries) ? result.entries : [];
  const lines = [
    text.attachmentRecentAppUsageTitle,
    `${text.appUsageWindowLabel}: 24h`,
  ];

  if (!entries.length) {
    lines.push(text.appUsageEmpty);
    return lines.join("\n");
  }

  entries.forEach((entry: any, index: number) => {
    const packageName = String(entry?.packageName || "").trim() || "-";
    const appName = String(entry?.appName || "").trim() || resolveAppLabel(packageName);
    const durationMs = Number(entry?.totalForegroundTimeMs);
    const lastTimeUsed = Number(entry?.lastTimeUsed);

    lines.push(
      `#${index + 1}`,
      `${text.currentScreenAppLabel}: ${appName}`,
      `${text.currentScreenPackageLabel}: ${packageName}`,
      `${text.appUsageDurationLabel}: ${Number.isFinite(durationMs) ? formatDurationMs(durationMs) : "-"}`,
      `${text.appUsageLastUsedLabel}: ${
        Number.isFinite(lastTimeUsed) && lastTimeUsed > 0 ? formatLocalTimestamp(lastTimeUsed) : "-"
      }`
    );
  });

  return lines.join("\n");
}

function extractScreenshotPath(result: string): string {
  return result.trim();
}

async function readScreenTextFromScreenshot(screenshotPath: string): Promise<string> {
  const normalizedPath = String(screenshotPath || "").trim();
  if (!normalizedPath) {
    throw new Error("screenshot path unavailable");
  }

  const context = getAppContext();
  if (!context) {
    throw new Error("application context unavailable");
  }

  const FileClass = Java.java.io.File;
  const UriClass = Java.android.net.Uri;
  const OCRUtils = Java.com.ai.assistance.operit.util.OCRUtils;
  const screenshotUri = UriClass.fromFile(new FileClass(normalizedPath));

  return String(
    await OCRUtils.callSuspend("recognizeText", context, screenshotUri, OCRUtils.Quality.HIGH)
  ).trim();
}

function countTextLines(value: string): number {
  return String(value || "")
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean).length;
}

async function buildScreenTextContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const screenshotResult = await toolCall("capture_screenshot", {});
  const screenshotPath = extractScreenshotPath(screenshotResult);
  const recognizedText = await readScreenTextFromScreenshot(screenshotPath);

  return [
    text.attachmentScreenTextTitle,
    `${text.screenTextScreenshotPathLabel}: ${screenshotPath || "-"}`,
    `${text.screenTextLineCountLabel}: ${countTextLines(recognizedText)}`,
    recognizedText || text.screenTextEmpty,
  ].join("\n");
}

async function buildNotificationsContent(): Promise<string> {
  const text = resolveExtraInfoI18n();
  const result = await Tools.System.getNotifications(NOTIFICATION_FETCH_LIMIT, false);
  const notifications = Array.isArray(result?.notifications) ? result.notifications : [];

  const lines = [
    text.attachmentNotificationsTitle,
    `${text.notificationCountLabel}: ${notifications.length}`,
  ];

  if (!notifications.length) {
    lines.push(text.notificationsEmpty);
    return lines.join("\n");
  }

  notifications.forEach((notification, index) => {
    const packageName = String(notification?.packageName || "").trim() || "-";
    const notificationText = String(notification?.text || "").trim() || "-";
    const timestamp =
      Number.isFinite(Number(notification?.timestamp)) && Number(notification.timestamp) > 0
        ? formatLocalTimestamp(Number(notification.timestamp))
        : "-";

    lines.push(
      `#${index + 1}`,
      `${text.notificationAppLabel}: ${packageName}`,
      `${text.notificationTextLabel}: ${notificationText}`,
      `${text.notificationTimeLabel}: ${timestamp}`
    );
  });

  return lines.join("\n");
}

function formatDecimal(value: number): string {
  if (!Number.isFinite(value)) {
    return "0";
  }
  return value.toFixed(3).replace(/0+$/, "").replace(/\.$/, "");
}

function collapseInlineWhitespace(value: unknown, maxLength = 220): string {
  const normalized = String(value || "").replace(/\s+/g, " ").trim();
  if (!normalized) {
    return "-";
  }
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, maxLength)}...`;
}

function buildMemorySnapshotId(chatId?: string): string {
  const normalized = String(chatId || "").trim();
  if (!normalized) {
    return "";
  }
  return normalized.slice(0, 6);
}

function resolveMemoryCallerCardId(
  activePrompt?: ToolPkg.ActivePromptSnapshot | null
): string | undefined {
  if (!activePrompt || activePrompt.type !== "character_card") {
    return undefined;
  }
  const callerCardId = String(activePrompt.id || "").trim();
  return callerCardId || undefined;
}

function stripMessageForMemorySearch(messageText: string): string {
  return String(messageText || "")
    .replace(/<attachment\b[\s\S]*?<\/attachment>/gi, " ")
    .replace(/<workspace_attachment\b[\s\S]*?<\/workspace_attachment>/gi, " ")
    .replace(/<reply_to\b[\s\S]*?<\/reply_to>/gi, " ")
    .replace(/<proxy_sender\b[^>]*\/?>/gi, " ")
    .replace(/\[\s*From [^\]]+\]\s*/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/[\r\n\t]+/g, " ")
    .replace(/[|]+/g, " ")
    .trim();
}

function buildMemorySearchQuery(messageText: string): string {
  return stripMessageForMemorySearch(messageText);
}

async function buildMemoryContent(
  messageText: string,
  chatId?: string,
  activePrompt?: ToolPkg.ActivePromptSnapshot | null
): Promise<string> {
  const text = resolveExtraInfoI18n();
  const settings = loadSettings();
  const reuseSnapshot = !settings.allowRepeatedMemorySearch;
  const snapshotId = reuseSnapshot ? buildMemorySnapshotId(chatId) : "";
  const callerCardId = resolveMemoryCallerCardId(activePrompt);
  if (reuseSnapshot && !snapshotId) {
    throw new Error(text.memorySnapshotUnavailable);
  }

  const searchQuery = buildMemorySearchQuery(messageText);
  const lines = [
    text.attachmentMemoryTitle,
    `${text.memoryQueryLabel}: ${searchQuery || "-"}`,
    `${text.memorySnapshotLabel}: ${snapshotId || "-"}`,
    `${text.memoryLimitLabel}: ${settings.memoryLimit}`,
  ];

  if (!searchQuery) {
    lines.push(
      `${text.memoryResultCountLabel}: 0`,
      text.memoryEmpty
    );
    return lines.join("\n");
  }

  const result = await toolCall("query_memory", {
    query: searchQuery,
    limit: settings.memoryLimit,
    ...(reuseSnapshot ? { snapshot_id: snapshotId } : {}),
    ...(callerCardId ? { caller_card_id: callerCardId } : {}),
  });

  const memories = Array.isArray(result?.memories) ? result.memories : [];
  lines.push(`${text.memoryResultCountLabel}: ${memories.length}`);

  if (!memories.length) {
    lines.push(text.memoryEmpty);
    return lines.join("\n");
  }

  memories.forEach((memory: any, index: number) => {
    const tags = Array.isArray(memory?.tags)
      ? memory.tags.map((item: unknown) => collapseInlineWhitespace(item, 40)).filter(Boolean)
      : [];

    lines.push(
      `#${index + 1}`,
      `${text.memoryTitleLabel}: ${collapseInlineWhitespace(memory?.title, 80)}`,
      `${text.memoryContentLabel}: ${collapseInlineWhitespace(memory?.content, 220)}`,
      `${text.memorySourceLabel}: ${collapseInlineWhitespace(memory?.source, 60)}`,
      `${text.memoryCreatedAtLabel}: ${collapseInlineWhitespace(memory?.createdAt, 40)}`
    );

    if (tags.length) {
      lines.push(`${text.memoryTagsLabel}: ${tags.join(", ")}`);
    }

    if (String(memory?.chunkInfo || "").trim()) {
      lines.push(
        `${text.memoryChunkInfoLabel}: ${collapseInlineWhitespace(memory.chunkInfo, 80)}`
      );
    }
  });

  return lines.join("\n");
}

export async function appendExtraInfoToMessage(
  messageText: string,
  chatId?: string,
  activePrompt?: ToolPkg.ActivePromptSnapshot | null
): Promise<string | null> {
  if (!stripMessageForMemorySearch(messageText)) {
    return null;
  }

  const tags = await buildExtraInfoAttachmentTags(
    messageText,
    chatId,
    activePrompt
  );
  if (!tags.length) {
    return null;
  }

  return `${String(messageText || "").replace(/\s+$/, "")} ${tags.join(" ")}`.trim();
}

export async function buildExtraInfoAttachmentTags(
  messageText: string,
  chatId?: string,
  activePrompt?: ToolPkg.ActivePromptSnapshot | null
): Promise<string[]> {
  const settings = loadSettings();
  if (!settings.masterEnabled || containsExtraInfoAttachment(messageText)) {
    return [];
  }

  const attachmentTimestampMs = Date.now();
  const contentBlocks: string[] = [];

  if (settings.injectTime) {
    contentBlocks.push(buildTimeContent());
  }

  if (settings.injectBattery) {
    let content = "";
    try {
      content = buildBatteryContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentBatteryTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectWeather) {
    let content = "";
    try {
      content = await buildWeatherContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentWeatherTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectLocation) {
    let content = "";
    try {
      content = await buildLocationContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentLocationTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectCurrentScreenApp) {
    let content = "";
    try {
      content = await buildCurrentScreenAppContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentCurrentScreenAppTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectRecentAppUsage) {
    let content = "";
    try {
      content = await buildRecentAppUsageContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentRecentAppUsageTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectScreenText) {
    let content = "";
    try {
      content = await buildScreenTextContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentScreenTextTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectNotifications) {
    let content = "";
    try {
      content = await buildNotificationsContent();
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentNotificationsTitle, error);
    }
    contentBlocks.push(content);
  }

  if (settings.injectMemory) {
    let content = "";
    try {
      content = await buildMemoryContent(messageText, chatId, activePrompt);
    } catch (error) {
      content = buildErrorContent(resolveExtraInfoI18n().attachmentMemoryTitle, error);
    }
    contentBlocks.push(content);
  }

  if (!contentBlocks.length) {
    return [];
  }

  return [
    buildAttachmentTag(
      COMBINED_ATTACHMENT_ID_PREFIX,
      buildCombinedAttachmentFileName(attachmentTimestampMs),
      contentBlocks.join("\n\n")
    ),
  ];
}
