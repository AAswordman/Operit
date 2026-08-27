/**
 * Core type definitions for Assistance Package Tools
 * 
 * This file provides base type definitions for the JavaScript environment
 * available in package tools execution.
 */

/**
 * Tool call parameters object
 */
export interface ToolParams {
    [key: string]: string | number | boolean | object;
}

/**
 * Tool configuration for object-style calls
 */
export interface ToolConfig {
    type?: string;
    name: string;
    params?: ToolParams;
    onIntermediateResult?: (value: unknown) => void;
}

export interface ToolCallOptions<TIntermediate = unknown> {
    onIntermediateResult?: (value: TIntermediate) => void;
}

/**
 * Common result interfaces for structured data
 */
export interface BaseResult {
    success: boolean;
    error?: string;
}

/**
 * Basic result data types
 */
export interface StringResult extends BaseResult {
    data: string;
    toString(): string;
}

export interface BooleanResult extends BaseResult {
    data: boolean;
    toString(): string;
}

export interface NumberResult extends BaseResult {
    data: number;
    toString(): string;
}

/**
 * Generic tool result type
 */
export type ToolResult = StringResult | BooleanResult | NumberResult |
    (BaseResult & { data: any });

/**
 * Get return type for a specific tool name
 */
export type ToolReturnType<T extends string> = T extends keyof import('./tool-types').ToolResultMap
    ? import('./tool-types').ToolResultMap[T]
    : any;

// ============================================================================
// Tool Call Function Declarations
// ============================================================================

/**
 * Global function to call a tool and get a result
 * Note: Promise-based waiting does not guarantee the underlying tool work is truly parallel.
 * @returns A Promise with the tool result data of the appropriate type
 */
export declare function toolCall<T extends string>(toolType: string, toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string>(toolName: T, toolParams?: ToolParams): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string>(config: ToolConfig & { name: T }): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string, TIntermediate = unknown>(toolType: string, toolName: T, toolParams: ToolParams | undefined, options: ToolCallOptions<TIntermediate>): Promise<ToolReturnType<T>>;
export declare function toolCall<T extends string, TIntermediate = unknown>(toolName: T, toolParams: ToolParams | undefined, options: ToolCallOptions<TIntermediate>): Promise<ToolReturnType<T>>;
export declare function toolCall(toolName: string): Promise<any>;

/**
 * Global function to complete tool execution with a result
 * Result values must be JSON-serializable. Java bridge instances are serialized as bridge handles.
 * @param result - The result to return
 */
export declare function complete<T>(result: T): void;

/**
 * Native interface for direct calls to Android
 */
export namespace NativeInterface {
    /**
     * Call a tool synchronously (legacy method)
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     * @returns A JSON string representing a ToolResult object
     */
    function callTool(toolType: string, toolName: string, paramsJson: string): string;

    /**
     * Call a tool asynchronously
     * @param callbackId - Unique callback ID
     * @param toolType - Tool type
     * @param toolName - Tool name
     * @param paramsJson - Parameters as JSON string
     * The callback will receive a ToolResult object
     */
    function callToolAsync(callbackId: string, toolType: string, toolName: string, paramsJson: string): void;
    function callToolAsyncStreaming(callbackId: string, intermediateCallbackId: string, toolType: string, toolName: string, paramsJson: string): void;

    /**
     * Log informational message
     * @param message - Message to log
     */
    function logInfo(message: string): void;

    /**
     * Log error message
     * @param message - Error message to log
     */
    function logError(message: string): void;

    /**
     * Log debug message with data
     * @param message - Debug message
     * @param data - Debug data
     */
    function logDebug(message: string, data: string): void;

    /**
     * Register a toolbox UI module for current toolpkg main registration session.
     * @param specJson - JSON object string describing a toolbox UI module
     */
    function registerToolPkgToolboxUiModule(specJson: string): void;

    /**
     * Register an app lifecycle hook for current toolpkg main registration session.
     * @param specJson - JSON object string describing an app lifecycle hook
     */
    function registerToolPkgAppLifecycleHook(specJson: string): void;

    /**
     * Register a message processing plugin for current toolpkg main registration session.
     * @param specJson - JSON object string describing a message processing plugin
     */
    function registerToolPkgMessageProcessingPlugin(specJson: string): void;

    /**
     * Register an XML render plugin for current toolpkg main registration session.
     * @param specJson - JSON object string describing an XML render plugin
     */
    function registerToolPkgXmlRenderPlugin(specJson: string): void;

    /**
     * Resolve the persistent config directory for a package or toolpkg.
     * Returns an absolute path under `/sdcard/Download/Operit/plugins/<id>`.
     */
    function getPluginConfigDir(pluginId: string): string;

    /**
     * Register an input menu toggle plugin for current toolpkg main registration session.
     * @param specJson - JSON object string describing an input menu toggle plugin
     */
    function registerToolPkgInputMenuTogglePlugin(specJson: string): void;

    /**
     * Register a chat input hook for current toolpkg main registration session.
     * @param specJson - JSON object string describing a chat input hook
     */
    function registerToolPkgChatInputHook(specJson: string): void;

    /**
     * Register a chat message hook for current toolpkg main registration session.
     * @param specJson - JSON object string describing a chat message hook
     */
    function registerToolPkgChatMessageHook(specJson: string): void;

    /**
     * Register a chat runtime-state hook for current toolpkg main registration session.
     * @param specJson - JSON object string describing a chat runtime-state hook
     */
    function registerToolPkgChatRuntimeStateHook(specJson: string): void;

    /**
     * Register an image from base64-encoded data into the global image pool
     * and return a `<link type="image" id="...">` tag string that can be
     * embedded into tool results or messages.
     */
    function registerImageFromBase64(base64: string, mimeType: string): string;

    /**
     * Register an image from a file path on the device into the global image
     * pool and return a `<link type="image" id="...">` tag string that can
     * be embedded into tool results or messages.
     */
    function registerImageFromPath(path: string): string;

    /**
     * Report detailed JavaScript error
     * @param errorType - Error type
     * @param errorMessage - Error message
     * @param errorLine - Line number where error occurred
     * @param errorStack - Error stack trace
     */
    function reportError(errorType: string, errorMessage: string, errorLine: number, errorStack: string): void;

    /**
     * Check whether a Java/Kotlin class exists.
     */
    function javaClassExists(className: string): boolean;

    /**
     * Load an external `.dex` file into the Java bridge class loader chain.
     * @param path - Absolute or app-accessible path to the dex file
     * @param optionsJson - JSON object string, currently supports `nativeLibraryDir`
     * @returns Bridge JSON string: {"success":boolean,"data"?:{"index":number,"type":"dex","path":string,"nativeLibraryDir":string|null,"alreadyLoaded":boolean},"error"?:string}
     */
    function javaLoadDex(path: string, optionsJson: string): string;

    /**
     * Load an external Android-executable `.jar` file into the Java bridge class loader chain.
     * The jar must contain `classes.dex`.
     * @param path - Absolute or app-accessible path to the jar file
     * @param optionsJson - JSON object string, currently supports `nativeLibraryDir`
     * @returns Bridge JSON string: {"success":boolean,"data"?:{"index":number,"type":"jar","path":string,"nativeLibraryDir":string|null,"alreadyLoaded":boolean},"error"?:string}
     */
    function javaLoadJar(path: string, optionsJson: string): string;

    /**
     * List external dex/jar artifacts already loaded in the current engine session.
     * @returns Bridge JSON string: {"success":boolean,"data"?:Array<{"index":number,"type":"dex"|"jar","path":string,"nativeLibraryDir":string|null,"alreadyLoaded":boolean}>,"error"?:string}
     */
    function javaListLoadedCodePaths(): string;

    /**
     * Get the Android application Context as a bridge handle payload.
     * @returns Bridge JSON string: {"success":boolean,"data"?:{"__javaHandle":string,"__javaClass":string},"error"?:string}
     */
    function javaGetApplicationContext(): string;

    /**
     * Get the current foreground Activity as a bridge handle payload.
     * @returns Bridge JSON string: {"success":boolean,"data"?:{"__javaHandle":string,"__javaClass":string},"error"?:string}
     */
    function javaGetCurrentActivity(): string;

    /**
     * Create a Java/Kotlin instance.
     * @param className - Fully qualified class name
     * @param argsJson - JSON array string of arguments
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaNewInstance(className: string, argsJson: string): string;

    /**
     * Invoke a static Java/Kotlin method.
     * @param className - Fully qualified class name
     * @param methodName - Static method name
     * @param argsJson - JSON array string of arguments
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaCallStatic(className: string, methodName: string, argsJson: string): string;

    /**
     * Invoke an instance Java/Kotlin method.
     * @param instanceHandle - Bridge object handle
     * @param methodName - Instance method name
     * @param argsJson - JSON array string of arguments
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaCallInstance(instanceHandle: string, methodName: string, argsJson: string): string;
    function javaHasInstanceMethod(instanceHandle: string, methodName: string): string;

    /**
     * Get a static field/property from a class.
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaGetStaticField(className: string, fieldName: string): string;

    /**
     * Set a static field/property on a class.
     * @param valueJson - JSON value string
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaSetStaticField(className: string, fieldName: string, valueJson: string): string;

    /**
     * Get an instance field/property.
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaGetInstanceField(instanceHandle: string, fieldName: string): string;

    /**
     * Set an instance field/property.
     * @param valueJson - JSON value string
     * @returns Bridge JSON string: {"success":boolean,"data"?:any,"error"?:string}
     */
    function javaSetInstanceField(instanceHandle: string, fieldName: string, valueJson: string): string;

}

/**
 * Lodash-like utility library
 */
export declare const _: {
    isEmpty(value: any): boolean;
    isString(value: any): boolean;
    isNumber(value: any): boolean;
    isBoolean(value: any): boolean;
    isObject(value: any): boolean;
    isArray(value: any): boolean;
    forEach<T>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => void): any;
    map<T, R>(collection: T[] | object, iteratee: (value: any, key: any, collection: any) => R): R[];
};

/**
 * Data utilities
 */
export declare const dataUtils: {
    /**
     * Parse JSON string to object
     * @param jsonString - JSON string to parse
     */
    parseJson(jsonString: string): any;

    /**
     * Convert object to JSON string
     * @param obj - Object to stringify
     */
    stringifyJson(obj: any): string;

    /**
     * Format date to string
     * @param date - Date to format
     */
    formatDate(date?: Date | string): string;
};

/**
 * Module exports object for CommonJS-style exports
 */
export declare var exports: {
    [key: string]: any;
}; 
