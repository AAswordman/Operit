/**
 * TypeScript definitions for Assistance Package Tools
 * 
 * This file provides type definitions for the JavaScript environment
 * available in package tools execution.
 */

// Import types that will be used in global declarations
import { ToolReturnType, NativeInterface as CoreNativeInterface } from './core';
import {
    JavaBridgeApi as JavaBridgeApiType,
    JavaBridgeClass as JavaBridgeClassType,
    JavaBridgeInstance as JavaBridgeInstanceType,
    JavaBridgeHandle as JavaBridgeHandleType,
    JavaBridgePackage as JavaBridgePackageType,
    JavaBridgeJsInterfaceMarker as JavaBridgeJsInterfaceMarkerType,
    JavaBridgeJsInterfaceImpl as JavaBridgeJsInterfaceImplType,
    JavaBridgeJsMethod as JavaBridgeJsMethodType,
    JavaBridgeInterfaceRef as JavaBridgeInterfaceRefType,
    JavaBridgeCallbackResult as JavaBridgeCallbackResultType,
    JavaBridgeExternalCodeLoadOptions as JavaBridgeExternalCodeLoadOptionsType,
    JavaBridgeLoadedCodePath as JavaBridgeLoadedCodePathType
} from './java-bridge';
import {
    CalculationResultData as _CalculationResultData,
    SleepResultData as _SleepResultData,
    SystemSettingData as _SystemSettingData,
    SystemSettingResult as _SystemSettingResult,
    AppOperationData as _AppOperationData,
    AppListData as _AppListData,
    DeviceInfoResultData as _DeviceInfoResultData,
    UIPageResultData as _UIPageResultData,
    UIActionResultData as _UIActionResultData,
    SimplifiedUINode as _SimplifiedUINode,
    FileOperationData as _FileOperationData,
    DirectoryListingData as _DirectoryListingData,
    FileContentData as _FileContentData,
    FileExistsData as _FileExistsData,
    FindFilesResultData as _FindFilesResultData,
    FileInfoData as _FileInfoData,
    HttpResponseData as _HttpResponseData,
    VisitWebResultData as _VisitWebResultData,
    TerminalCommandResultData as _TerminalCommandResultData,
    TerminalStreamEventData as _TerminalStreamEventData,
    HiddenTerminalCommandResultData as _HiddenTerminalCommandResultData,
    MusicPlaybackState as _MusicPlaybackState,
    MusicPlaybackResultData as _MusicPlaybackResultData,
    CombinedOperationResultData as _CombinedOperationResultData,
    AutomationExecutionResultData as _AutomationExecutionResultData,
    FilePartContentData as _FilePartContentData,
    FileApplyResultData as _FileApplyResultData,
    GrepResultData as _GrepResultData,
    GrepFileMatch as _GrepFileMatch,
    GrepLineMatch as _GrepLineMatch,
    EnvironmentVariableReadResultData as _EnvironmentVariableReadResultData,
    EnvironmentVariableWriteResultData as _EnvironmentVariableWriteResultData,
    SandboxPackageResultItem as _SandboxPackageResultItem,
    SandboxPackagesResultData as _SandboxPackagesResultData,
    SandboxPackageUpdateResultData as _SandboxPackageUpdateResultData,
    SandboxScriptExecutionResultData as _SandboxScriptExecutionResultData,
    McpRestartLogPluginResultItem as _McpRestartLogPluginResultItem,
    McpRestartWithLogsResultData as _McpRestartWithLogsResultData,
    SpeechTtsHttpConfigResultItem as _SpeechTtsHttpConfigResultItem,
    SpeechSttHttpConfigResultItem as _SpeechSttHttpConfigResultItem,
    SpeechServicesConfigResultData as _SpeechServicesConfigResultData,
    SpeechServicesUpdateResultData as _SpeechServicesUpdateResultData,
    SpeechServicesTtsPlaybackTestResultData as _SpeechServicesTtsPlaybackTestResultData,
    ModelConfigResultItem as _ModelConfigResultItem,
    FunctionModelMappingResultItem as _FunctionModelMappingResultItem,
    ModelConfigsResultData as _ModelConfigsResultData,
    ModelConfigCreateResultData as _ModelConfigCreateResultData,
    ModelConfigUpdateResultData as _ModelConfigUpdateResultData,
    ModelConfigDeleteResultData as _ModelConfigDeleteResultData,
    FunctionModelConfigsResultData as _FunctionModelConfigsResultData,
    FunctionModelConfigResultData as _FunctionModelConfigResultData,
    FunctionModelBindingResultData as _FunctionModelBindingResultData,
    ModelConfigConnectionTestItemResultData as _ModelConfigConnectionTestItemResultData,
    ModelConfigConnectionTestResultData as _ModelConfigConnectionTestResultData,
    ChatCallResultData as _ChatCallResultData
} from './results';
import { Intent as AndroidIntent, IntentFlag as AndroidIntentFlag, IntentAction as AndroidIntentAction, IntentCategory as AndroidIntentCategory } from './android';
import { UINode as UINodeClass, UI as UINamespace } from './ui';
import { Android as AndroidClass } from './android';
import type { MaterialIconsRegistry as MaterialIconsRegistryType } from './material-icons';
import {
    ComposeDslContext as ComposeDslContextType,
    ComposeDslScreen as ComposeDslScreenType,
    ComposeNode as ComposeNodeType,
    ComposeCanvasCommand as ComposeCanvasCommandType
} from './compose-dsl';
import { ToolPkg as ToolPkgType } from './toolpkg';

// Export core interfaces and functions
export * from './core';

// Export all result types
export * from './results';

// Export tool type definitions
export * from './tool-types';
export * from './java-bridge';
export * from './toolpkg';
export * from './material-icons';

// Export compose-dsl definitions for toolpkg runtime modules
export * from './compose-dsl';
export * from './compose-dsl.material3.generated';

import { Files as FilesType } from './files';
import { Net as NetType } from './network';
import { System as SystemType } from './system';
import { SoftwareSettings as SoftwareSettingsType } from './software_settings';
import { UI as UIType } from './ui';
import { FFmpeg as FFmpegType } from './ffmpeg';
import { Tasker as TaskerType } from './tasker';
import { Workflow as WorkflowType } from './workflow';
import { Chat as ChatType } from './chat';
import { Memory as MemoryType } from './memory';

export { Net } from './network';
export { System } from './system';
export { SoftwareSettings } from './software_settings';
export { UI, UINode } from './ui';
export { FFmpegVideoCodec, FFmpegAudioCodec, FFmpegResolution, FFmpegBitrate } from './ffmpeg';
export { Tasker } from './tasker';
export { Workflow } from './workflow';
export { ToolPkg } from './toolpkg';
export { Chat } from './chat';
export { Memory } from './memory';

// Export Android utilities
export {
    AdbExecutor,
    IntentFlag,
    IntentAction,
    IntentCategory,
    Intent,
    PackageManager,
    ContentProvider,
    SystemManager,
    DeviceController,
    Android
} from './android';


// Global declarations (these will be available without imports)
declare global {
    // Make Android classes/constructs available globally
    const Intent: typeof AndroidIntent;
    const IntentFlag: typeof AndroidIntentFlag;
    const IntentAction: typeof AndroidIntentAction;
    const IntentCategory: typeof AndroidIntentCategory;
    const UINode: typeof UINodeClass;
    const Android: typeof AndroidClass;
    const Icons: MaterialIconsRegistryType;

    // Make classes available as types too
    type UINode = UINodeClass;
    type Android = AndroidClass;
    type MaterialIconName = MaterialIconNameType;
    type MaterialIconsRegistry = MaterialIconsRegistryType;
    type ComposeDslContext = ComposeDslContextType;
    type ComposeDslScreen = ComposeDslScreenType;
    type ComposeNode = ComposeNodeType;
    type ComposeCanvasCommand = ComposeCanvasCommandType;
    type JavaBridgeApi = JavaBridgeApiType;
    type JavaBridgeClass = JavaBridgeClassType;
    type JavaBridgeInstance = JavaBridgeInstanceType;
    type JavaBridgeHandle = JavaBridgeHandleType;
    type JavaBridgePackage = JavaBridgePackageType;
    type JavaBridgeJsInterfaceMarker = JavaBridgeJsInterfaceMarkerType;
    type JavaBridgeJsInterfaceImpl = JavaBridgeJsInterfaceImplType;
    type JavaBridgeJsMethod = JavaBridgeJsMethodType;
    type JavaBridgeInterfaceRef = JavaBridgeInterfaceRefType;
    type JavaBridgeCallbackResult = JavaBridgeCallbackResultType;
    type JavaBridgeExternalCodeLoadOptions = JavaBridgeExternalCodeLoadOptionsType;
    type JavaBridgeLoadedCodePath = JavaBridgeLoadedCodePathType;

    // Make result types available globally
    type CalculationResultData = _CalculationResultData;
    type SleepResultData = _SleepResultData;
    type SystemSettingData = _SystemSettingData;
    type SystemSettingResult = _SystemSettingResult;
    type AppOperationData = _AppOperationData;
    type AppListData = _AppListData;
    type DeviceInfoResultData = _DeviceInfoResultData;
    type UIPageResultData = _UIPageResultData;
    type UIActionResultData = _UIActionResultData;
    type SimplifiedUINode = _SimplifiedUINode;
    type FileOperationData = _FileOperationData;
    type DirectoryListingData = _DirectoryListingData;
    type FileContentData = _FileContentData;
    type FileExistsData = _FileExistsData;
    type FindFilesResultData = _FindFilesResultData;
    type FileInfoData = _FileInfoData;
    type HttpResponseData = _HttpResponseData;
    type VisitWebResultData = _VisitWebResultData;
    type TerminalCommandResultData = _TerminalCommandResultData;
    type TerminalStreamEventData = _TerminalStreamEventData;
    type HiddenTerminalCommandResultData = _HiddenTerminalCommandResultData;
    type MusicPlaybackState = _MusicPlaybackState;
    type MusicPlaybackResultData = _MusicPlaybackResultData;
    type CombinedOperationResultData = _CombinedOperationResultData;
    type AutomationExecutionResultData = _AutomationExecutionResultData;
    type FilePartContentData = _FilePartContentData;
    type FileApplyResultData = _FileApplyResultData;
    type GrepResultData = _GrepResultData;
    type GrepFileMatch = _GrepFileMatch;
    type GrepLineMatch = _GrepLineMatch;
    type EnvironmentVariableReadResultData = _EnvironmentVariableReadResultData;
    type EnvironmentVariableWriteResultData = _EnvironmentVariableWriteResultData;
    type SandboxPackageResultItem = _SandboxPackageResultItem;
    type SandboxPackagesResultData = _SandboxPackagesResultData;
    type SandboxPackageUpdateResultData = _SandboxPackageUpdateResultData;
    type SandboxScriptExecutionResultData = _SandboxScriptExecutionResultData;
    type McpRestartLogPluginResultItem = _McpRestartLogPluginResultItem;
    type McpRestartWithLogsResultData = _McpRestartWithLogsResultData;
    type SpeechTtsHttpConfigResultItem = _SpeechTtsHttpConfigResultItem;
    type SpeechSttHttpConfigResultItem = _SpeechSttHttpConfigResultItem;
    type SpeechServicesConfigResultData = _SpeechServicesConfigResultData;
    type SpeechServicesUpdateResultData = _SpeechServicesUpdateResultData;
    type SpeechServicesTtsPlaybackTestResultData = _SpeechServicesTtsPlaybackTestResultData;
    type ModelConfigResultItem = _ModelConfigResultItem;
    type FunctionModelMappingResultItem = _FunctionModelMappingResultItem;
    type ModelConfigsResultData = _ModelConfigsResultData;
    type ModelConfigCreateResultData = _ModelConfigCreateResultData;
    type ModelConfigUpdateResultData = _ModelConfigUpdateResultData;
    type ModelConfigDeleteResultData = _ModelConfigDeleteResultData;
    type FunctionModelConfigsResultData = _FunctionModelConfigsResultData;
    type FunctionModelConfigResultData = _FunctionModelConfigResultData;
    type FunctionModelBindingResultData = _FunctionModelBindingResultData;
    type ModelConfigConnectionTestItemResultData = _ModelConfigConnectionTestItemResultData;
    type ModelConfigConnectionTestResultData = _ModelConfigConnectionTestResultData;
    type ChatCallResultData = _ChatCallResultData;

    export import Tasker = TaskerType;

    export import Workflow = WorkflowType;

    export import ToolPkg = ToolPkgType;

    // Global interface definitions
    interface ToolParams {
        [key: string]: string | number | boolean | object;
    }

    interface ToolConfig {
        type?: string;
        name: string;
        params?: ToolParams;
        onIntermediateResult?: (value: unknown) => void;
    }

    interface ToolCallOptions<TIntermediate = unknown> {
        onIntermediateResult?: (value: TIntermediate) => void;
    }

    // Tool call functions
    function toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
    function toolCall<T extends string>(toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
    function toolCall<T extends string>(config: ToolConfig & { name: T }): Promise<ToolReturnType<T>>;
    function toolCall<T extends string, TIntermediate = unknown>(toolType: string, toolName: T, toolParams: ToolParams | undefined, options: ToolCallOptions<TIntermediate>): Promise<ToolReturnType<T>>;
    function toolCall<T extends string, TIntermediate = unknown>(toolName: T, toolParams: ToolParams | undefined, options: ToolCallOptions<TIntermediate>): Promise<ToolReturnType<T>>;
    function toolCall(toolName: string): Promise<any>;

    // Complete function
    function complete<T>(result: T): void;

    // Send intermediate result function
    function sendIntermediateResult<T>(result: T): void;

    // Get environment variable function
    function getEnv(key: string): string | undefined;

    // Get persistent plugin config directory under /sdcard/Download/Operit/plugins/<id>
    function getPluginConfigDir(pluginId?: string): string;

    function getState(): string | undefined;

    function getLang(): string;

    function getCallerName(): string | undefined;

    function getChatId(): string | undefined;

    function getCallerCardId(): string | undefined;

    const OPERIT_DOWNLOAD_DIR: string;
    const OPERIT_CLEAN_ON_EXIT_DIR: string;

    // Utility objects
    const _: {
        isEmpty(value: any): boolean;
        isString(value: any): boolean;
        isNumber(value: any): boolean;
        isBoolean(value: any): boolean;
        isObject(value: any): boolean;
        isArray(value: any): boolean;
        forEach<T>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => void): any;
        map<T, R>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => R): R[];
    };

    const dataUtils: {
        parseJson(jsonString: string): any;
        stringifyJson(obj: any): string;
        formatDate(date?: Date | string): string;
    };

    // Tools namespace available globally
    const Tools: {
        Files: typeof FilesType;
        Net: typeof NetType;
        System: typeof SystemType;
        SoftwareSettings: typeof SoftwareSettingsType;
        UI: typeof UIType;
        FFmpeg: typeof FFmpegType;
        Tasker: TaskerType.Runtime;
        Workflow: WorkflowType.Runtime;
        Chat: typeof ChatType;
        Memory: typeof MemoryType;
        calc: (expression: string) => Promise<CalculationResultData>;
    };

    // CommonJS exports
    const exports: Record<string, any>;

    // Java/Kotlin bridge (Rhino-like)
    const Java: JavaBridgeApiType;
    const Kotlin: JavaBridgeApiType;

    // NativeInterface
    const NativeInterface: typeof CoreNativeInterface;
}
