/**
 * File operation type definitions for Assistance Package Tools
 */

import {
    DirectoryListingData, FileContentData, BinaryFileContentData, FileOperationData, FileExistsData,
    FindFilesResultData, FileInfoData, FilePartContentData,
    FileApplyResultData, GrepResultData
} from './results';

import { FFmpegVideoCodec, FFmpegAudioCodec, FFmpegResolution, FFmpegBitrate } from './ffmpeg';

/**
 * Execution environment for file operations
 */
export type FileEnvironment = "android" | "linux";

export type ApplyFileType = "replace" | "delete" | "create";

/**
 * File operations namespace
 */
export namespace Files {
    /**
     * List files in a directory
     * @param path - Path to directory
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function list(path: string, environment?: FileEnvironment): Promise<DirectoryListingData>;

    /**
     * Read file contents (always reads complete file)
     * @param path - Path to file
     */
    function read(path: string): Promise<FileContentData>;

    interface ReadFileOptions {
        path: string;
        environment?: FileEnvironment;
        intent?: string;
        direct_image?: boolean;
    }

    function read(options: ReadFileOptions): Promise<FileContentData>;

    /**
     * Read file content by line range
     * @param path - Path to file
     * @param startLine - Starting line number (1-indexed, default 1)
     * @param endLine - Ending line number (1-indexed, inclusive, optional, default startLine + 199 (200 lines))
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function readPart(path: string, startLine?: number, endLine?: number, environment?: FileEnvironment): Promise<FilePartContentData>;

    /**
     * Write content to file
     * @param path - Path to file
     * @param content - Content to write
     * @param append - Whether to append to file
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function write(path: string, content: string, append?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Write base64 encoded content to a binary file
     * @param path - Path to file
     * @param base64Content - Base64 encoded content to write
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function writeBinary(path: string, base64Content: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Read binary file content as a structured result with Base64 data
     * @param path - Path to file
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function readBinary(path: string, environment?: FileEnvironment): Promise<BinaryFileContentData>;

    /**
     * Delete a file or directory
     * @param path - Path to file or directory
     * @param recursive - Delete recursively
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function deleteFile(path: string, recursive?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Check if file exists
     * @param path - Path to check
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function exists(path: string, environment?: FileEnvironment): Promise<FileExistsData>;

    /**
     * Move file from source to destination
     * @param source - Source path
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function move(source: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Copy file from source to destination. Supports cross-environment copying between Android and Linux.
     * @param source - Source path
     * @param destination - Destination path
     * @param recursive - Copy recursively
     * @param sourceEnvironment - Source execution environment ("android" or "linux"), default "android"
     * @param destEnvironment - Destination execution environment ("android" or "linux"), default "android"
     */
    function copy(source: string, destination: string, recursive?: boolean, sourceEnvironment?: FileEnvironment, destEnvironment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Create a directory
     * @param path - Directory path
     * @param create_parents - Create parent directories
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function mkdir(path: string, create_parents?: boolean, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Find files matching a pattern
     * @param path - Base directory
     * @param pattern - Search pattern
     * @param options - Search options
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function find(path: string, pattern: string, options?: Record<string, any>, environment?: FileEnvironment): Promise<FindFilesResultData>;

    /**
     * Search code content matching a regex pattern in files
     * @param path - Base directory to search
     * @param pattern - Regex pattern to search for
     * @param options - Search options
     * @param options.file_pattern - File filter pattern (e.g., "*.kt"), default "*"
     * @param options.case_insensitive - Ignore case in pattern matching, default false
     * @param options.context_lines - Number of context lines before/after each match, default 3
     * @param options.max_results - Maximum number of matches to return, default 100
     * @param options.environment - Execution environment ("android" or "linux"), default "android"
     */
    function grep(path: string, pattern: string, options?: {
        file_pattern?: string;
        case_insensitive?: boolean;
        context_lines?: number;
        max_results?: number;
        environment?: FileEnvironment;
    }): Promise<GrepResultData>;

    /**
     * Search for relevant content based on intent/context understanding
     * Supports two modes:
     * 1) Directory mode: when path is a directory, finds most relevant files
     * 2) File mode: when path is a file, finds most relevant code segments within that file
     * Uses semantic relevance scoring
     * @param path - Directory or file path
     * @param intent - Intent or context description string
     * @param options - Search options
     * @param options.file_pattern - File filter pattern for directory mode (e.g., "*.kt"), default "*"
     * @param options.max_results - Maximum number of items to return, default 10
     * @param options.environment - Execution environment ("android" or "linux"), default "android"
     */
    function grepContext(path: string, intent: string, options?: {
        file_pattern?: string;
        max_results?: number;
        environment?: FileEnvironment;
    }): Promise<GrepResultData>;

    /**
     * Get information about a file
     * @param path - File path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function info(path: string, environment?: FileEnvironment): Promise<FileInfoData>;

    /**
     * Apply AI-generated content to a file with intelligent merging
     * @param path - Path to file
     * @param type - Operation type: replace | delete | create
     * @param old - Exact content to match (required for replace/delete)
     * @param newContent - New content to insert (required for replace/create)
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function apply(path: string, type: ApplyFileType, old?: string, newContent?: string, environment?: FileEnvironment): Promise<FileApplyResultData>;

    /**
     * Create a new file. Internally delegates to apply_file with type=create.
     * @param path - Path to file
     * @param newContent - Full file content
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function create(path: string, newContent: string, environment?: FileEnvironment): Promise<FileApplyResultData>;

    /**
     * Edit an existing file. Internally delegates to apply_file with type=replace.
     * @param path - Path to file
     * @param oldContent - Exact content to match
     * @param newContent - New content to insert
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function edit(path: string, oldContent: string, newContent: string, environment?: FileEnvironment): Promise<FileApplyResultData>;

    /**
     * Zip files/directories
     * @param source - Source path
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     * @param include_root_directory - When zipping a directory, whether to keep the source directory itself as the top-level folder, default true
     */
    function zip(source: string, destination: string, environment?: FileEnvironment, include_root_directory?: boolean): Promise<FileOperationData>;

    /**
     * Unzip an archive
     * @param source - Source archive
     * @param destination - Target directory
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function unzip(source: string, destination: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Open a file with system handler
     * @param path - File path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function open(path: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Share a file with other apps
     * @param path - File path
     * @param title - Share title
     * @param environment - Execution environment ("android" or "linux"), default "android"
     */
    function share(path: string, title?: string, environment?: FileEnvironment): Promise<FileOperationData>;

    /**
     * Download a file from URL
     * @param url - Source URL
     * @param destination - Destination path
     * @param environment - Execution environment ("android" or "linux"), default "android"
     * @param headers - Optional headers for the request
     */
    function download(url: string, destination: string, environment?: FileEnvironment, headers?: Record<string, string>): Promise<FileOperationData>;

    function download(options: {
        url?: string;
        visit_key?: string;
        link_number?: number;
        image_number?: number;
        destination: string;
        environment?: FileEnvironment;
        headers?: Record<string, string>;
    }): Promise<FileOperationData>;

} 
