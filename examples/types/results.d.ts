/**
 * Result interface definitions for Assistance Package Tools
 * 
 * This file provides type definitions for all result data structures
 * returned by the various tools.
 */

import { BaseResult } from './core';

// ============================================================================
// Calculation and Date Result Types
// ============================================================================

/**
 * Calculation result data
 */
export interface CalculationResultData {
    expression: string;
    result: number;
    formattedResult: string;
    variables: Record<string, number>;
    toString(): string;
}

/**
 * Date result data
 */
export interface DateResultData {
    /* Missing in original file but needed for DateResult interface */
    date: Date;
    formattedDate: string;
    timestamp: number;
    toString(): string;
}

// ============================================================================
// Connection Result Types
// ============================================================================

/**
 * Connection result data
 */
export interface ConnectionResultData {
    connectionId: string;
    isActive: boolean;
    timestamp: number;
    toString(): string;
}

// ============================================================================
// File Operation Types
// ============================================================================

/**
 * File entry in directory listing
 */
export interface FileEntry {
    name: string;
    isDirectory: boolean;
    size: number;
    permissions: string;
    lastModified: string;
    toString(): string;
}

export interface FileExistsData {
    env: 'android' | 'linux';
    path: string;
    exists: boolean;
    isDirectory?: boolean;
    size?: number;
}

/**
 * Detailed file information data
 */
export interface FileInfoData {
    env: 'android' | 'linux';
    path: string;
    exists: boolean;
    fileType: string;  // "file", "directory", or "other"
    size: number;
    permissions: string;
    owner: string;
    group: string;
    lastModified: string;
    rawStatOutput: string;
}

/**
 * Directory listing data
 */
export interface DirectoryListingData {
    env: 'android' | 'linux';
    path: string;
    entries: FileEntry[];
    toString(): string;
}

/**
 * File content data
 */
export interface FileContentData {
    env: 'android' | 'linux';
    path: string;
    content: string;
    size: number;
    toString(): string;
}

/**
 * Binary file content data (Base64 encoded)
 */
export interface BinaryFileContentData {
    env: 'android' | 'linux';
    path: string;
    /** Base64 encoded content of the file */
    contentBase64: string;
    /** File size in bytes */
    size: number;
    toString(): string;
}

/**
 * File part content data
 */
export interface FilePartContentData {
    env: 'android' | 'linux';
    path: string;
    content: string;
    partIndex: number;
    totalParts: number;
    startLine: number;
    endLine: number;
    totalLines: number;
    toString(): string;
}

/**
 * File operation data
 */
export interface FileOperationData {
    env: 'android' | 'linux';
    operation: string;
    path: string;
    successful: boolean;
    details: string;
    toString(): string;
}

/**
 * File apply result data
 */
export interface FileApplyResultData {
    operation: FileOperationData;
    aiDiffInstructions: string;
    toString(): string;
}

/**
 * Find files result data
 */
export interface FindFilesResultData {
    env: 'android' | 'linux';
    path: string;
    pattern: string;
    files: string[];
    toString(): string;
}

/**
 * Line match in grep result
 */
export interface GrepLineMatch {
    lineNumber: number;
    lineContent: string;
    matchContext?: string;
}

/**
 * File match in grep result
 */
export interface GrepFileMatch {
    filePath: string;
    lineMatches: GrepLineMatch[];
}

/**
 * Grep search result data
 */
export interface GrepResultData {
    env: 'android' | 'linux';
    searchPath: string;
    pattern: string;
    filePattern?: string;
    matches: GrepFileMatch[];
    totalMatches: number;
    filesSearched: number;
    toString(): string;
}


// ============================================================================
// HTTP and Network Types
// ============================================================================

/**
 * HTTP response data
 */
export interface HttpResponseData {
    url: string;
    statusCode: number;
    statusMessage: string;
    headers: Record<string, string>;
    contentType: string;
    content: string;
    size: number;
    toString(): string;
}

/**
 * Web page link
 */
export interface Link {
    text: string;
    url: string;
    toString(): string;
}


/**
 * Web page visit result data
 */
export interface VisitWebResultData {
    url: string;
    title: string;
    content: string;
    metadata?: Record<string, string>;
    links?: Link[];
    imageLinks?: string[];
    visitKey?: string;
    contentSavedTo?: string;
    contentTruncated?: boolean;
    originalContentLength?: number;
    toString(): string;
}

// ============================================================================
// Device Information Types
// ============================================================================

/**
 * Device information result data
 */
export interface DeviceInfoResultData {
    deviceId: string;
    model: string;
    manufacturer: string;
    androidVersion: string;
    sdkVersion: number;
    screenResolution: string;
    screenDensity: number;
    totalMemory: string;
    availableMemory: string;
    totalStorage: string;
    availableStorage: string;
    batteryLevel: number;
    batteryCharging: boolean;
    cpuInfo: string;
    networkType: string;
    additionalInfo: Record<string, string>;
    toString(): string;
}

// ============================================================================
// System Settings and App Types
// ============================================================================

/**
 * Sleep result data
 */
export interface SleepResultData {
    requestedMs: number;
    sleptMs: number;
    toString(): string;
}

/**
 * System setting data
 */
export interface SystemSettingData {
    namespace: string;
    setting: string;
    value: string;
    toString(): string;
}

/**
 * App operation data
 */
export interface AppOperationData {
    operationType: string;
    packageName: string;
    success: boolean;
    details: string;
    toString(): string;
}

/**
 * App list data
 */
export interface AppListData {
    includesSystemApps: boolean;
    packages: string[];
    toString(): string;
}

/**
 * Single app usage time entry
 */
export interface AppUsageTimeEntry {
    packageName: string;
    appName: string;
    totalForegroundTimeMs: number;
    lastTimeUsed: number;
    isSystemApp: boolean;
}

/**
 * App usage time result data
 */
export interface AppUsageTimeResultData {
    startTime: number;
    endTime: number;
    sinceHours: number;
    requestedPackageName?: string;
    includesSystemApps: boolean;
    totalEntries: number;
    entries: AppUsageTimeEntry[];
    toString(): string;
}

/**
 * Bluetooth adapter state
 */
export interface BluetoothStateData {
    supported: boolean;
    enabled: boolean;
    state: 'unsupported' | 'off' | 'turning_on' | 'on' | 'turning_off' | 'unknown';
    toString(): string;
}

/**
 * Bluetooth bonded device entry
 */
export interface BluetoothDeviceData {
    name?: string;
    address: string;
    type: 'classic' | 'le' | 'dual' | 'unknown';
    bondState: 'none' | 'bonding' | 'bonded' | 'unknown';
}

/**
 * Bluetooth bonded devices result
 */
export interface BluetoothBondedDevicesData {
    devices: BluetoothDeviceData[];
    toString(): string;
}

/**
 * Bluetooth scanned device entry
 */
export interface BluetoothScannedDeviceData extends BluetoothDeviceData {
    source: 'classic' | 'ble';
    rssi?: number;
}

/**
 * Bluetooth scan result
 */
export interface BluetoothScanResultData {
    devices: BluetoothScannedDeviceData[];
    durationMs: number;
    includesBle: boolean;
    toString(): string;
}

/**
 * Bluetooth session result
 */
export interface BluetoothSessionData {
    sessionId: string;
    address: string;
    mode: 'classic' | 'classic_listener' | 'ble';
    toString(): string;
}

/**
 * Bluetooth write result
 */
export interface BluetoothTransferData {
    sessionId: string;
    bytesWritten: number;
    toString(): string;
}

/**
 * Bluetooth read result
 */
export interface BluetoothReadData {
    sessionId: string;
    bytesRead: number;
    text?: string;
    dataBase64?: string;
    toString(): string;
}

/**
 * BLE services result
 */
export interface BluetoothBleServicesData {
    sessionId: string;
    services: BluetoothBleServiceData[];
    toString(): string;
}

export interface BluetoothBleServiceData {
    uuid: string;
    characteristics: BluetoothBleCharacteristicData[];
}

export interface BluetoothBleCharacteristicData {
    uuid: string;
    properties: Array<'read' | 'write' | 'write_no_response' | 'notify' | 'indicate'>;
}

/**
 * BLE notification list result
 */
export interface BluetoothBleNotificationData {
    sessionId: string;
    notifications: BluetoothBleNotificationEntry[];
    toString(): string;
}

export interface BluetoothBleNotificationEntry {
    characteristicUuid: string;
    bytesRead: number;
    text?: string;
    dataBase64?: string;
    timestamp: number;
}

/**
 * Notification data structure
 */
export interface NotificationData {
    /** List of notification objects */
    notifications: Array<{
        /** The package name of the application that posted the notification */
        packageName: string;
        /** The text content of the notification */
        text: string;
        /** Timestamp when the notification was captured */
        timestamp: number;
    }>;
    /** Timestamp when the notifications were retrieved */
    timestamp: number;
    /** Returns a formatted string representation of the notifications */
    toString(): string;
}

/**
 * Location data structure
 */
export interface LocationData {
    /** Latitude coordinate in decimal degrees */
    latitude: number;
    /** Longitude coordinate in decimal degrees */
    longitude: number;
    /** Accuracy of the location in meters */
    accuracy: number;
    /** Location provider (e.g., "gps", "network", etc.) */
    provider: string;
    /** Timestamp when the location was retrieved */
    timestamp: number;
    /** Raw location data from the system */
    rawData: string;
    /** Street address determined from coordinates */
    address?: string;
    /** City name determined from coordinates */
    city?: string;
    /** Province/state name determined from coordinates */
    province?: string;
    /** Country name determined from coordinates */
    country?: string;
    /** Returns a formatted string representation of the location */
    toString(): string;
}

// ============================================================================
// UI Types
// ============================================================================

/**
 * UI node structure for hierarchical display
 */
export interface SimplifiedUINode {
    className?: string;
    text?: string;
    contentDesc?: string;
    resourceId?: string;
    bounds?: string;
    isClickable: boolean;
    children: SimplifiedUINode[];
    toString(): string;
    toTreeString(indent?: string): string;
    shouldKeepNode?(): boolean;
}

/**
 * UI page result data
 */
export interface UIPageResultData {
    packageName: string;
    activityName: string;
    uiElements: SimplifiedUINode;
    toString(): string;
}

/**
 * UI action result data
 */
export interface UIActionResultData {
    actionType: string;
    actionDescription: string;
    coordinates?: [number, number];
    elementId?: string;
    toString(): string;
}

/**
 * Combined operation result data
 */
export interface CombinedOperationResultData {
    operationSummary: string;
    waitTime: number;
    pageInfo: UIPageResultData;
    toString(): string;
}


/**
 * Automation execution result data (for UI subagent runs)
 */
export interface AutomationExecutionResultData {
    /** Function name of the automation or subagent */
    functionName: string;
    /** Parameters provided to the automation */
    providedParameters: Record<string, string>;
    /** Optional agent id used for this run (can be reused to keep operating on the same virtual screen session) */
    agentId?: string | null;
    /** Optional virtual display id associated with the agent session */
    displayId?: number | null;
    /** Whether the execution succeeded */
    executionSuccess: boolean;
    /** Detailed execution message and action logs */
    executionMessage: string;
    /** Optional error message when execution fails */
    executionError?: string | null;
    /** Final UI state information, if available */
    finalState?: {
        nodeId: string;
        packageName: string;
        activityName: string;
    } | null;
    /** Number of steps executed */
    executionSteps: number;
    toString(): string;
}

/**
 * ADB command execution result data
 */
export interface ADBResultData {
    /** The ADB command that was executed */
    command: string;

    /** The output from the ADB command execution */
    output: string;

    /** Exit code from the ADB command (0 typically means success) */
    exitCode: number;

    /** Returns a formatted string representation of the ADB execution result */
    toString(): string;
}

/**
 * Intent execution result data
 */
export interface IntentResultData {
    action: string;
    uri: string;
    package_name: string;
    component: string;
    flags: number;
    extras_count: number;
    result: string;
    type: 'activity' | 'broadcast' | 'service';
    toString(): string;
}

/**
 * Terminal command execution result data
 */
export interface TerminalCommandResultData {
    /** The command that was executed */
    command: string;

    /** The output from the command execution */
    output: string;

    /** Exit code from the command (0 typically means success) */
    exitCode: number;

    /** ID of the terminal session used for execution */
    sessionId: string;

    /** Whether this execution ended due to timeout. On timeout, the current command is cancelled and the terminal session is kept. */
    timedOut?: boolean;

    /** Returns a formatted string representation of the terminal execution result */
    toString(): string;
}

/**
 * Terminal command streaming event data
 */
export interface TerminalStreamEventData {
    /** Event type, currently "start" or "chunk" */
    type: string;

    /** The command being executed */
    command: string;

    /** ID of the terminal session used for execution */
    sessionId: string;

    /** Incremental output chunk for "chunk" events */
    chunk?: string | null;

    /** Zero-based chunk index */
    chunkIndex?: number | null;

    /** Total received character count so far */
    receivedChars?: number | null;

    /** Returns a formatted string representation of the stream event */
    toString(): string;
}

/**
 * Hidden terminal command execution result data
 */
export interface HiddenTerminalCommandResultData {
    /** The command that was executed */
    command: string;

    /** The output from the command execution */
    output: string;

    /** Exit code from the command (0 typically means success) */
    exitCode: number;

    /** Hidden executor key used for execution */
    executorKey: string;

    /** Whether this execution ended due to timeout. On timeout, the current command is cancelled and the terminal session is kept. */
    timedOut?: boolean;

    /** Returns a formatted string representation of the hidden terminal execution result */
    toString(): string;
}

/**
 * Music playback result data
 */
export type MusicPlaybackState = 'idle' | 'preparing' | 'playing' | 'paused' | 'ended' | 'stopped' | 'error';

export interface MusicPlaybackResultData {
    /** Playback state */
    state: MusicPlaybackState;
    /** Current audio source */
    source?: string | null;
    /** Current audio source type */
    sourceType?: string | null;
    /** Display title */
    title?: string | null;
    /** Display artist */
    artist?: string | null;
    /** Duration in milliseconds, when known */
    durationMs?: number | null;
    /** Current playback position in milliseconds */
    positionMs: number;
    /** Buffered playback position in milliseconds */
    bufferedPositionMs: number;
    /** Playback volume from 0 to 1 */
    volume: number;
    /** Whether current track loops */
    loop: boolean;
    /** Zero-based queue index when playing a queue */
    queueIndex?: number | null;
    /** Queue size when playing a queue */
    queueSize?: number | null;
    /** Operation message */
    message: string;
    /** Returns a formatted string representation of the music playback result */
    toString(): string;
}

/**
 * Terminal session creation result data
 */
export interface TerminalSessionCreationResultData {
    /** ID of the created or retrieved session */
    sessionId: string;
    /** Name of the session */
    sessionName: string;
    /** Whether a new session was created */
    isNewSession: boolean;
}

/**
 * Terminal session close result data
 */
export interface TerminalSessionCloseResultData {
    /** ID of the closed session */
    sessionId: string;
    /** Whether the session was closed successfully */
    success: boolean;
    /** A message describing the result */
    message: string;
}

/**
 * Terminal session current screen snapshot result data (single screen, no scrollback/history)
 */
export interface TerminalSessionScreenResultData {
    /** ID of the session */
    sessionId: string;
    /** Screen row count */
    rows: number;
    /** Screen column count */
    cols: number;
    /** Current visible screen text content */
    content: string;
    /** Returns a formatted string representation */
    toString(): string;
}

// ============================================================================
// FFmpeg Types
// ============================================================================

import { FFmpegVideoCodec, FFmpegAudioCodec } from './ffmpeg';

/**
 * FFmpeg stream information
 * Represents detailed information about a video or audio stream in a media file
 */
export interface FFmpegStreamInfo {
    /** Stream index in the media file (0-based) */
    index: number;

    /** Stream type: "video" or "audio" */
    type: 'video' | 'audio';

    /** Codec name used for this stream */
    codec: FFmpegVideoCodec | FFmpegAudioCodec;

    /** Frame rate for video streams (e.g., "30/1", "29.97") */
    frameRate?: `${number}/${number}` | `${number}`;

    /** Sample rate for audio streams in Hz (e.g., "44100") */
    sampleRate?: `${number}`;

    /** Number of audio channels (e.g., 2 for stereo) */
    channels?: 1 | 2 | 4 | 6 | 8;

    /** Returns a formatted string representation of the stream info */
    toString(): string;
}

/**
 * FFmpeg result data
 * Contains comprehensive information about the FFmpeg operation execution
 */
export interface FFmpegResultData {
    /** The complete FFmpeg command that was executed */
    command: string;

    /** FFmpeg return code (0 indicates success) */
    returnCode: number;

    /** Complete output from the FFmpeg command execution */
    output: string;

    /** Execution duration in milliseconds */
    duration: number;

    /** Array of video stream information */
    videoStreams: FFmpegStreamInfo[];

    /** Array of audio stream information */
    audioStreams: FFmpegStreamInfo[];

    /** Returns a formatted string representation of the result */
    toString(): string;
}

// ============================================================================
// Result Type Wrappers
// ============================================================================

export interface CalculationResult extends BaseResult {
    data: CalculationResultData;
}

export interface DateResult extends BaseResult {
    data: DateResultData;
}

export interface ConnectionResult extends BaseResult {
    data: ConnectionResultData;
}

export interface DirectoryListingResult extends BaseResult {
    data: DirectoryListingData;
}

export interface FileContentResult extends BaseResult {
    data: FileContentData;
}

export interface BinaryFileContentResult extends BaseResult {
    data: BinaryFileContentData;
}

export interface FilePartContentResult extends BaseResult {
    data: FilePartContentData;
}

export interface FileOperationResult extends BaseResult {
    data: FileOperationData;
}

export interface FileApplyResult extends BaseResult {
    data: FileApplyResultData;
}

export interface HttpResponseResult extends BaseResult {
    data: HttpResponseData;
}

export interface VisitWebResult extends BaseResult {
    data: VisitWebResultData;
}

export interface SystemSettingResult extends BaseResult {
    data: SystemSettingData;
}

export interface AppOperationResult extends BaseResult {
    data: AppOperationData;
}

export interface AppListResult extends BaseResult {
    data: AppListData;
}

export interface UIPageResult extends BaseResult {
    data: UIPageResultData;
}

export interface UIActionResult extends BaseResult {
    data: UIActionResultData;
}

export interface AutomationExecutionResult extends BaseResult {
    data: AutomationExecutionResultData;
}

export interface ADBResult extends BaseResult {
    data: ADBResultData;
}

export interface IntentResult extends BaseResult {
    data: IntentResultData;
}

export interface TerminalCommandResult extends BaseResult {
    data: TerminalCommandResultData;
}

export interface HiddenTerminalCommandResult extends BaseResult {
    data: HiddenTerminalCommandResultData;
}

export interface TerminalSessionCreationResult extends BaseResult {
    data: TerminalSessionCreationResultData;
}

export interface TerminalSessionCloseResult extends BaseResult {
    data: TerminalSessionCloseResultData;
}

export interface TerminalSessionScreenResult extends BaseResult {
    data: TerminalSessionScreenResultData;
}

export interface DeviceInfoResult extends BaseResult {
    data: DeviceInfoResultData;
}

export interface CombinedOperationResult extends BaseResult {
    data: CombinedOperationResultData;
}

// ============================================================================
// Workflow Types
// ============================================================================

/**
 * 工作流基本信息结果数据
 */
export interface WorkflowResultData {
    /** 工作流 ID */
    id: string;
    /** 工作流名称 */
    name: string;
    /** 工作流描述 */
    description: string;
    /** 节点数量 */
    nodeCount: number;
    /** 连接数量 */
    connectionCount: number;
    /** 是否启用 */
    enabled: boolean;
    /** 创建时间戳 */
    createdAt: number;
    /** 更新时间戳 */
    updatedAt: number;
    /** 最后执行时间 */
    lastExecutionTime?: number | null;
    /** 最后执行状态 */
    lastExecutionStatus?: string | null;
    /** 总执行次数 */
    totalExecutions: number;
    /** 成功执行次数 */
    successfulExecutions: number;
    /** 失败执行次数 */
    failedExecutions: number;
    /** Returns a formatted string representation of the workflow */
    toString(): string;
}

/**
 * 工作流列表结果数据
 */
export interface WorkflowListResultData {
    /** 工作流列表 */
    workflows: WorkflowResultData[];
    /** 工作流总数 */
    totalCount: number;
    /** Returns a formatted string representation of the workflow list */
    toString(): string;
}

/**
 * 工作流节点位置
 */
export interface NodePosition {
    x: number;
    y: number;
}

export interface StaticValue {
    __type?: string;
    value: string;
}

export interface NodeReference {
    __type?: string;
    nodeId: string;
}

export type ParameterValue = string | StaticValue | NodeReference;

/**
 * 触发类型
 */
export type TriggerType =
    | 'manual'
    | 'schedule'
    | 'tasker'
    | 'intent'
    | 'speech'
    | (string & { __triggerTypeBrand?: never });

/**
 * 触发节点
 */
export interface TriggerNode {
    __type?: string;
    /** 节点 ID */
    id: string;
    /** 节点类型 */
    type: 'trigger';
    /** 节点名称 */
    name: string;
    /** 节点描述 */
    description: string;
    /** 节点位置 */
    position: NodePosition;
    /** 触发类型 */
    triggerType: TriggerType;
    /** 触发配置 */
    triggerConfig: Record<string, string>;
}

/**
 * 执行节点
 */
export interface ExecuteNode {
    __type?: string;
    /** 节点 ID */
    id: string;
    /** 节点类型 */
    type: 'execute';
    /** 节点名称 */
    name: string;
    /** 节点描述 */
    description: string;
    /** 节点位置 */
    position: NodePosition;
    /** 动作类型（工具名称） */
    actionType: string;
    /** 动作配置（工具参数） */
    actionConfig: Record<string, string | ParameterValue>;
    /** JavaScript 代码（可选） */
    jsCode?: string | null;
}

export type ConditionOperator =
    | 'EQ'
    | 'NE'
    | 'GT'
    | 'GTE'
    | 'LT'
    | 'LTE'
    | 'CONTAINS'
    | 'NOT_CONTAINS'
    | 'IN'
    | 'NOT_IN';

export interface ConditionNode {
    __type?: string;
    id: string;
    type: 'condition';
    name: string;
    description: string;
    position: NodePosition;
    left: ParameterValue;
    operator: ConditionOperator;
    right: ParameterValue;
}

export type LogicOperator = 'AND' | 'OR';

export interface LogicNode {
    __type?: string;
    id: string;
    type: 'logic';
    name: string;
    description: string;
    position: NodePosition;
    operator: LogicOperator;
}

export type ExtractMode = 'REGEX' | 'JSON' | 'SUB' | 'CONCAT' | 'RANDOM_INT' | 'RANDOM_STRING';

export interface ExtractNode {
    __type?: string;
    id: string;
    type: 'extract';
    name: string;
    description: string;
    position: NodePosition;
    source: ParameterValue;
    mode: ExtractMode;
    expression: string;
    group: number;
    defaultValue: string;
    others?: ParameterValue[];
    startIndex?: number;
    length?: number;
    randomMin?: number;
    randomMax?: number;
    randomStringLength?: number;
    randomStringCharset?: string;
    useFixed?: boolean;
    fixedValue?: string;
}

/**
 * 工作流节点（联合类型）
 */
export type WorkflowNode = TriggerNode | ExecuteNode | ConditionNode | LogicNode | ExtractNode;

/**
 * 工作流节点连接条件关键字
 */
export type WorkflowConnectionConditionKeyword =
    | 'true'
    | 'false'
    | 'on_success'
    | 'success'
    | 'ok'
    | 'on_error'
    | 'error'
    | 'failed';

/**
 * 工作流节点连接条件
 */
export type WorkflowConnectionCondition = WorkflowConnectionConditionKeyword | (string & { __regexConditionBrand?: never });

/**
 * 工作流节点连接
 */
export interface WorkflowNodeConnection {
    /** 连接 ID */
    id: string;
    /** 源节点 ID */
    sourceNodeId: string;
    /** 目标节点 ID */
    targetNodeId: string;
    /** 连接条件（可选） */
    condition?: WorkflowConnectionCondition | null;
}

/**
 * 工作流详细信息结果数据（包含完整的节点和连接信息）
 */
export interface WorkflowDetailResultData {
    /** 工作流 ID */
    id: string;
    /** 工作流名称 */
    name: string;
    /** 工作流描述 */
    description: string;
    /** 节点列表 */
    nodes: WorkflowNode[];
    /** 连接列表 */
    connections: WorkflowNodeConnection[];
    /** 是否启用 */
    enabled: boolean;
    /** 创建时间戳 */
    createdAt: number;
    /** 更新时间戳 */
    updatedAt: number;
    /** 最后执行时间 */
    lastExecutionTime?: number | null;
    /** 最后执行状态 */
    lastExecutionStatus?: string | null;
    /** 总执行次数 */
    totalExecutions: number;
    /** 成功执行次数 */
    successfulExecutions: number;
    /** 失败执行次数 */
    failedExecutions: number;
    /** Returns a formatted string representation of the workflow details */
    toString(): string;
}

/**
 * 工具运行时内部的字符串包装；JavaScript toolCall 会收到 primitive string。
 */
export interface StringResultData {
    /** 字符串值 */
    value: string;
    /** Returns the string value */
    toString(): string;
}

export interface SandboxScriptExecutionResultData {
    success: boolean;
    scriptPath: string;
    functionName: string;
    params?: unknown;
    envFilePath?: string | null;
    startedAtMs: number;
    finishedAtMs: number;
    durationMs: number;
    result?: unknown;
    error?: string | null;
    events: string[];
    executionMode?: string | null;
    scriptLabel?: string | null;
    requestedWaitMs?: number | null;
    toString(): string;
}

// ============================================================================
// Software Settings Types
// ============================================================================

export interface EnvironmentVariableReadResultData {
    key: string;
    value?: string | null;
    exists: boolean;
    toString(): string;
}

export interface EnvironmentVariableWriteResultData {
    key: string;
    requestedValue: string;
    value?: string | null;
    exists: boolean;
    cleared: boolean;
    toString(): string;
}

export interface SandboxPackageResultItem {
    packageName: string;
    displayName: string;
    description: string;
    isBuiltIn: boolean;
    enabledByDefault: boolean;
    enabled: boolean;
    imported: boolean;
    isDisabledByUser: boolean;
    toolCount: number;
    manageMode: string;
}

export interface SandboxPackagesResultData {
    externalPackagesPath: string;
    scriptDevGuide: string;
    totalCount: number;
    builtInCount: number;
    externalCount: number;
    enabledCount: number;
    disabledCount: number;
    packages: SandboxPackageResultItem[];
    packageLoadErrors: Record<string, string>;
    toString(): string;
}

export interface SandboxPackageUpdateResultData {
    packageName: string;
    requestedEnabled: boolean;
    previousEnabled: boolean;
    currentEnabled: boolean;
    message: string;
    toString(): string;
}

export interface McpRestartLogPluginResultItem {
    id: string;
    displayName: string;
    shortName: string;
    status: string;
    message: string;
    serviceName: string;
    log: string;
}

export interface McpRestartWithLogsResultData {
    timeoutMs: number;
    elapsedMs: number;
    timedOut: boolean;
    progress: number;
    message: string;
    pluginsTotal: number;
    pluginsStarted: number;
    successCount: number;
    failedCount: number;
    plugins: McpRestartLogPluginResultItem[];
    extraLogs: Record<string, string>;
    toString(): string;
}

export interface SpeechTtsHttpConfigResultItem {
    urlTemplate: string;
    apiKeySet: boolean;
    apiKeyPreview: string;
    headers: Record<string, string>;
    httpMethod: string;
    requestBody: string;
    contentType: string;
    localeTag: string;
    voiceId: string;
    modelName: string;
    responsePipeline: Array<{
        type: string;
        path: string;
        headers: Record<string, string>;
    }>;
}

export interface SpeechSttHttpConfigResultItem {
    endpointUrl: string;
    apiKeySet: boolean;
    apiKeyPreview: string;
    modelName: string;
}

export interface SpeechServicesConfigResultData {
    ttsServiceType: string;
    ttsHttpConfig: SpeechTtsHttpConfigResultItem;
    ttsCleanerRegexs: string[];
    ttsSpeechRate: number;
    ttsPitch: number;
    sttServiceType: string;
    sttHttpConfig: SpeechSttHttpConfigResultItem;
    toString(): string;
}

export interface SpeechServicesUpdateResultData {
    updated: boolean;
    changedFields: string[];
    ttsServiceType: string;
    sttServiceType: string;
    ttsApiKeySet: boolean;
    sttApiKeySet: boolean;
    toString(): string;
}

export interface SpeechServicesTtsPlaybackTestResultData {
    ttsServiceType: string;
    providerClass: string;
    initialized: boolean;
    playbackTriggered: boolean;
    interrupt: boolean;
    textLength: number;
    speechRate: number;
    pitch: number;
    errorType?: string | null;
    errorMessage?: string | null;
    httpStatusCode?: number | null;
    errorBody?: string | null;
    causeMessage?: string | null;
    toString(): string;
}

export interface ModelConfigResultItem {
    id: string;
    name: string;
    apiProviderType: string;
    apiEndpoint: string;
    modelName: string;
    modelList: string[];
    apiKeySet: boolean;
    apiKeyPreview: string;
    maxTokensEnabled: boolean;
    maxTokens: number;
    temperatureEnabled: boolean;
    temperature: number;
    topPEnabled: boolean;
    topP: number;
    topKEnabled: boolean;
    topK: number;
    presencePenaltyEnabled: boolean;
    presencePenalty: number;
    frequencyPenaltyEnabled: boolean;
    frequencyPenalty: number;
    repetitionPenaltyEnabled: boolean;
    repetitionPenalty: number;
    hasCustomParameters: boolean;
    customParameters: string;
    hasCustomHeaders: boolean;
    customHeaders: string;
    contextLength: number;
    maxContextLength: number;
    enableMaxContextMode: boolean;
    summaryTokenThreshold: number;
    enableSummary: boolean;
    enableSummaryByMessageCount: boolean;
    summaryMessageCountThreshold: number;
    mnnForwardType: number;
    mnnThreadCount: number;
    llamaThreadCount: number;
    llamaContextSize: number;
    llamaBatchSize: number;
    llamaUBatchSize: number;
    llamaGpuLayers: number;
    llamaUseMmap: boolean;
    llamaFlashAttention: boolean;
    llamaKvUnified: boolean;
    llamaOffloadKqv: boolean;
    enableDirectImageProcessing: boolean;
    enableDirectAudioProcessing: boolean;
    enableDirectVideoProcessing: boolean;
    enableGoogleSearch: boolean;
    enableClaude1hPromptCache: boolean;
    enableToolCall: boolean;
    requestLimitPerMinute: number;
    maxConcurrentRequests: number;
    useMultipleApiKeys: boolean;
    apiKeyPoolCount: number;
    toString(): string;
}

export interface FunctionModelMappingResultItem {
    functionType: string;
    configId: string;
    configName?: string | null;
    modelIndex: number;
    actualModelIndex?: number | null;
    selectedModel?: string | null;
}

export interface ModelConfigsResultData {
    totalConfigCount: number;
    defaultConfigId: string;
    configs: ModelConfigResultItem[];
    functionMappings: FunctionModelMappingResultItem[];
    toString(): string;
}

export interface ModelConfigCreateResultData {
    created: boolean;
    config: ModelConfigResultItem;
    changedFields: string[];
    toString(): string;
}

export interface ModelConfigUpdateResultData {
    updated: boolean;
    config: ModelConfigResultItem;
    changedFields: string[];
    affectedFunctions: string[];
    toString(): string;
}

export interface ModelConfigDeleteResultData {
    deleted: boolean;
    configId: string;
    affectedFunctions: string[];
    fallbackConfigId: string;
    toString(): string;
}

export interface FunctionModelConfigsResultData {
    defaultConfigId: string;
    mappings: FunctionModelMappingResultItem[];
    toString(): string;
}

export interface FunctionModelConfigResultData {
    defaultConfigId: string;
    functionType: string;
    configId: string;
    configName: string;
    modelIndex: number;
    actualModelIndex: number;
    selectedModel: string;
    config: ModelConfigResultItem;
    toString(): string;
}

export interface FunctionModelBindingResultData {
    functionType: string;
    configId: string;
    configName: string;
    requestedModelIndex: number;
    actualModelIndex: number;
    selectedModel: string;
    toString(): string;
}

export interface ModelConfigConnectionTestItemResultData {
    type: string;
    success: boolean;
    error?: string | null;
}

export interface ModelConfigConnectionTestResultData {
    configId: string;
    configName: string;
    providerType: string;
    requestedModelIndex: number;
    actualModelIndex: number;
    testedModelName: string;
    success: boolean;
    totalTests: number;
    passedTests: number;
    failedTests: number;
    tests: ModelConfigConnectionTestItemResultData[];
    toString(): string;
}

// ============================================================================
// Chat Manager Types
// ============================================================================

/**
 * Chat service start result data
 */
export interface ChatServiceStartResultData {
    /** Whether the service is connected */
    isConnected: boolean;
    /** Connection timestamp */
    connectionTime: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat creation result data
 */
export interface ChatCreationResultData {
    /** The ID of the newly created chat */
    chatId: string;
    /** Creation timestamp */
    createdAt: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat information
 */
export interface ChatInfo {
    /** Chat ID */
    id: string;
    /** Chat title */
    title: string;
    /** Number of messages in the chat */
    messageCount: number;
    /** Creation timestamp */
    createdAt: string;
    /** Last updated timestamp */
    updatedAt: string;
    /** Whether this is the current active chat */
    isCurrent: boolean;
    /** Total input tokens used */
    inputTokens: number;
    /** Total output tokens used */
    outputTokens: number;
    /** Bound character card name (if any) */
    characterCardName?: string | null;
}

/**
 * Chat list result data
 */
export interface ChatListResultData {
    /** Total number of chats */
    totalCount: number;
    /** The ID of the current active chat */
    currentChatId: string | null;
    /** List of chat information */
    chats: ChatInfo[];
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat switch result data
 */
export interface ChatSwitchResultData {
    /** The ID of the chat switched to */
    chatId: string;
    /** The title of the chat */
    chatTitle: string;
    /** Switch timestamp */
    switchedAt: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat title update result data
 */
export interface ChatTitleUpdateResultData {
    /** Target chat ID */
    chatId: string;
    /** Updated title */
    title: string;
    /** Update timestamp */
    updatedAt: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat delete result data
 */
export interface ChatDeleteResultData {
    /** Deleted chat ID */
    chatId: string;
    /** Delete timestamp */
    deletedAt: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Message send result data
 */
export interface MessageSendResultData {
    /** The ID of the chat the message was sent to */
    chatId: string;
    /** The message content that was sent */
    message: string;
    /** Final AI response content when available */
    aiResponse?: string | null;
    /** Reply receive timestamp when available */
    receivedAt?: number | null;
    /** Sent timestamp */
    sentAt: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Message send streaming event data
 */
export interface MessageSendStreamEventData {
    /** Event type, currently "start" or "chunk" */
    type: string;

    /** The ID of the chat receiving the streamed reply */
    chatId: string;

    /** The original message content that was sent */
    message: string;

    /** Whether waifu-style chunk aggregation is enabled for this stream */
    waifu: boolean;

    /** Incremental chunk content for "chunk" events */
    chunk?: string | null;

    /** Zero-based chunk index */
    chunkIndex?: number | null;

    /** Total received character count so far */
    receivedChars?: number | null;

    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Chat message entry
 */
export interface ChatMessageInfo {
    sender: string;
    content: string;
    timestamp: number;
    roleName?: string;
    provider?: string;
    modelName?: string;
}

/**
 * Chat messages query result data
 */
export interface ChatMessagesResultData {
    chatId: string;
    order: string;
    limit: number;
    messages: ChatMessageInfo[];
    start?: number;
    end?: number;
    toString(): string;
}

/**
 * Character card list result data
 */
export interface CharacterCardListResultData {
    totalCount: number;
    cards: CharacterCardInfo[];
    toString(): string;
}

/**
 * Character card information
 */
export interface CharacterCardInfo {
    id: string;
    name: string;
    description: string;
    isDefault: boolean;
    createdAt: number;
    updatedAt: number;
}

/** Full character-card tool access configuration. */
export interface CharacterCardToolAccessConfigResultItem {
    enabled: boolean;
    allowedBuiltinTools: string[];
    allowedPackages: string[];
    allowedSkills: string[];
    allowedMcpServers: string[];
}

/** Full character-card configuration. */
export interface CharacterCardResultItem {
    id: string;
    name: string;
    description: string;
    characterSetting: string;
    openingStatement: string;
    otherContentChat: string;
    otherContentVoice: string;
    attachedTagIds: string[];
    advancedCustomPrompt: string;
    marks: string;
    chatModelBindingMode: 'FOLLOW_GLOBAL' | 'FIXED_CONFIG';
    chatModelConfigId: string | null;
    chatModelIndex: number;
    memoryProfileBindingMode: 'FOLLOW_GLOBAL' | 'FIXED_PROFILE';
    memoryProfileId: string | null;
    toolAccessConfig: CharacterCardToolAccessConfigResultItem;
    isDefault: boolean;
    createdAt: number;
    updatedAt: number;
}

/** Full character-card list and active card state. */
export interface CharacterCardsResultData {
    totalCount: number;
    activeCharacterCardId: string | null;
    cards: CharacterCardResultItem[];
    toString(): string;
}

/** One full character-card result. */
export interface CharacterCardResultData {
    card: CharacterCardResultItem;
    activeCharacterCardId: string | null;
    toString(): string;
}

export interface CharacterCardCreateResultData {
    created: boolean;
    card: CharacterCardResultItem;
    activeCharacterCardId: string | null;
    changedFields: string[];
    toString(): string;
}

export interface CharacterCardUpdateResultData {
    updated: boolean;
    card: CharacterCardResultItem;
    activeCharacterCardId: string | null;
    changedFields: string[];
    toString(): string;
}

export interface CharacterCardDeleteResultData {
    deleted: boolean;
    characterCardId: string;
    activeCharacterCardId: string | null;
    toString(): string;
}

export interface CharacterCardActivationResultData {
    activeCharacterCardId: string | null;
    toString(): string;
}

export interface CharacterCardImportResultData {
    imported: boolean;
    card: CharacterCardResultItem;
    activeCharacterCardId: string | null;
    toString(): string;
}

export interface CharacterCardExportResultData {
    characterCardId: string;
    tavernJson: string;
    toString(): string;
}

/**
 * Chat find result data
 */
export interface ChatFindResultData {
    /** Total matched chats */
    matchedCount: number;
    /** The selected chat (if any) */
    chat: ChatInfo | null;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Agent status result data
 */
export interface AgentStatusResultData {
    /** Target chat id */
    chatId: string;
    /** Current state key */
    state: string;
    /** Optional detail message */
    message?: string | null;
    /** Whether the chat is idle */
    isIdle: boolean;
    /** Whether the chat is processing */
    isProcessing: boolean;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Result type wrappers for Chat Manager operations
 */
export interface ChatServiceStartResult extends BaseResult {
    data: ChatServiceStartResultData;
}

export interface ChatCreationResult extends BaseResult {
    data: ChatCreationResultData;
}

export interface ChatListResult extends BaseResult {
    data: ChatListResultData;
}

export interface ChatFindResult extends BaseResult {
    data: ChatFindResultData;
}

export interface AgentStatusResult extends BaseResult {
    data: AgentStatusResultData;
}

export interface ChatSwitchResult extends BaseResult {
    data: ChatSwitchResultData;
}

export interface ChatTitleUpdateResult extends BaseResult {
    data: ChatTitleUpdateResultData;
}

export interface ChatDeleteResult extends BaseResult {
    data: ChatDeleteResultData;
}

export interface MessageSendResult extends BaseResult {
    data: MessageSendResultData;
}

export interface ChatMessagesResult extends BaseResult {
    data: ChatMessagesResultData;
}

// ============================================================================
// Memory Management Types
// ============================================================================

/**
 * Single memory item returned by memory query
 */
export interface MemoryQueryResultMemoryInfo {
    /** Memory title */
    title: string;
    /** Memory content or formatted document chunk summary */
    content: string;
    /** Memory source */
    source: string;
    /** Memory tags */
    tags: string[];
    /** Formatted creation time */
    createdAt: string;
    /** Optional chunk summary for document memories */
    chunkInfo?: string | null;
    /** Optional matched chunk indices for document memories */
    chunkIndices?: number[] | null;
}

/**
 * Memory query result data
 */
export interface MemoryQueryResultData {
    /** Queried memories */
    memories: MemoryQueryResultMemoryInfo[];
    /** Snapshot id for de-duplicated follow-up or parallel queries; may be auto-generated or caller-specified */
    snapshotId?: string | null;
    /** Whether this call created a new snapshot, including when a caller-specified id was created on first use */
    snapshotCreated?: boolean;
    /** Number of matched memories excluded because they were already seen in the snapshot */
    excludedBySnapshotCount?: number;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Memory link result data
 */
export interface MemoryLinkResultData {
    /** The title of the source memory */
    sourceTitle: string;
    /** The title of the target memory */
    targetTitle: string;
    /** The type of link (e.g., "related", "causes", "explains", "part_of") */
    linkType: string;
    /** The strength of the link (0.0-1.0) */
    weight: number;
    /** Optional description of the link */
    description: string;
    /** Returns a formatted string representation */
    toString(): string;
}

/**
 * Memory link query result data
 */
export interface MemoryLinkQueryResultData {
    /** Number of links returned */
    totalCount: number;
    /** Queried links */
    links: Array<{
        /** Link ID */
        linkId: number;
        /** Source memory title */
        sourceTitle: string;
        /** Target memory title */
        targetTitle: string;
        /** Link type */
        linkType: string;
        /** Link weight (0.0-1.0) */
        weight: number;
        /** Optional relationship description */
        description: string;
    }>;
    /** Returns a formatted string representation */
    toString(): string;
}

export interface MemoryLinkResult extends BaseResult {
    data: MemoryLinkResultData;
}

export interface MemoryLinkQueryResult extends BaseResult {
    data: MemoryLinkQueryResultData;
}
