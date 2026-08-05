/**
 * System operation type definitions for Assistance Package Tools
 */

import {
    SleepResultData, SystemSettingData, AppOperationData, AppListData,
    AppUsageTimeResultData, DeviceInfoResultData, NotificationData, LocationData,
    BluetoothStateData, BluetoothBondedDevicesData, BluetoothScanResultData, BluetoothSessionData,
    BluetoothTransferData, BluetoothReadData, BluetoothBleServicesData, BluetoothBleNotificationData,
    ADBResultData, IntentResultData, TerminalCommandResultData, TerminalStreamEventData, HiddenTerminalCommandResultData,
    TerminalSessionCreationResultData, TerminalSessionCloseResultData, TerminalSessionScreenResultData,
    MusicPlaybackResultData
} from './results';

/**
 * System operations namespace
 */
export namespace System {

    /**
     * Sleep for specified milliseconds
     * @param milliseconds - Milliseconds to sleep
     */
    function sleep(milliseconds: string | number): Promise<SleepResultData>;

    /**
     * Get a system setting
     * @param setting - Setting name
     * @param namespace - Setting namespace
     */
    function getSetting(setting: string, namespace?: string): Promise<SystemSettingData>;

    /**
     * Modify a system setting
     * @param setting - Setting name
     * @param value - New value
     * @param namespace - Setting namespace
     */
    function setSetting(setting: string, value: string, namespace?: string): Promise<SystemSettingData>;

    /**
     * Get device information
     */
    function getDeviceInfo(): Promise<DeviceInfoResultData>;

    /**
     * Show a toast message on device.
     * @param message - The message to show
     */
    function toast(message: string): Promise<string>;

    /**
     * Send a notification using the same channel as AI reply completion.
     * @param message - Notification content
     * @param title - Optional notification title
     */
    function sendNotification(message: string, title?: string): Promise<string>;

    /**
     * Use a tool package
     * @param packageName - Package name
     */
    function usePackage(packageName: string): Promise<string>;

    /**
     * Install an application
     * @param path - Path to the APK file
     */
    function installApp(path: string): Promise<AppOperationData>;

    /**
     * Uninstall an application
     * @param packageName - Package name of the app to uninstall
     */
    function uninstallApp(packageName: string): Promise<AppOperationData>;

    /**
     * Stop a running app
     * @param packageName - Package name
     */
    function stopApp(packageName: string): Promise<AppOperationData>;

    /**
     * List installed apps
     * @param includeSystem - Whether to include system apps
     */
    function listApps(includeSystem?: boolean): Promise<AppListData>;

    /**
     * Start an app by package name
     * @param packageName - Package name
     * @param activity - Optional specific activity to launch
     */
    function startApp(packageName: string, activity?: string): Promise<AppOperationData>;

    /**
     * Get device notifications
     * @param limit - Maximum number of notifications to return (default: 10)
     * @param includeOngoing - Whether to include ongoing notifications (default: false)
     * @returns Promise resolving to notification data
     */
    function getNotifications(limit?: number, includeOngoing?: boolean): Promise<NotificationData>;

    /**
     * Get app foreground usage time from Android Usage Access.
     * @param options Query options
     */
    function getAppUsageTime(options?: {
        packageName?: string;
        sinceHours?: number | string;
        limit?: number | string;
        includeSystemApps?: boolean;
    }): Promise<AppUsageTimeResultData>;

    /**
     * Get device location
     * @param highAccuracy - Whether to use high accuracy mode (default: false)
     * @param timeout - Timeout in seconds (default: 10)
     * @param includeAddress - Whether to resolve a reverse-geocoded address (default: true)
     * @returns Promise resolving to location data
     */
    function getLocation(highAccuracy?: boolean, timeout?: number, includeAddress?: boolean): Promise<LocationData>;

    /**
     * Bluetooth operations.
     */
    namespace bluetooth {
        /** Request Bluetooth nearby devices permission. */
        function requestPermission(): Promise<string>;

        /** Get Bluetooth adapter state. */
        function getState(): Promise<BluetoothStateData>;

        /** Open the system dialog to enable Bluetooth. */
        function requestEnable(): Promise<string>;

        /** List bonded Bluetooth devices. */
        function listBondedDevices(): Promise<BluetoothBondedDevicesData>;

        /** Scan nearby Bluetooth classic and BLE devices. */
        function scan(options?: {
            durationMs?: number | string;
            includeBle?: boolean;
        }): Promise<BluetoothScanResultData>;

        /** Connect to a Bluetooth classic device. */
        function connect(options: {
            address: string;
            uuid?: string;
        }): Promise<BluetoothSessionData>;

        /** Listen for another device connecting to this phone over Bluetooth classic. */
        function listen(options?: {
            name?: string;
            uuid?: string;
        }): Promise<BluetoothSessionData>;

        /** Accept one incoming Bluetooth classic connection. */
        function accept(listenerSessionId: string, timeoutMs?: number | string): Promise<BluetoothSessionData>;

        /** Send text or bytes to a Bluetooth classic session. */
        function send(sessionId: string, options: {
            text?: string;
            dataBase64?: string;
        }): Promise<BluetoothTransferData>;

        /** Read text or bytes from a Bluetooth classic session. */
        function read(sessionId: string, options?: {
            maxBytes?: number | string;
            timeoutMs?: number | string;
        }): Promise<BluetoothReadData>;

        /** Send text or bytes and read the response from a Bluetooth classic session. */
        function sendAndRead(sessionId: string, options: {
            text?: string;
            dataBase64?: string;
            maxBytes?: number | string;
            timeoutMs?: number | string;
        }): Promise<BluetoothReadData>;

        /** Close a Bluetooth classic, listener, or BLE session. */
        function close(sessionId: string): Promise<string>;

        namespace ble {
            /** Connect to a BLE device. */
            function connect(options: {
                address: string;
                autoConnect?: boolean;
            }): Promise<BluetoothSessionData>;

            /** Discover BLE services and characteristics. */
            function discoverServices(sessionId: string, timeoutMs?: number | string): Promise<BluetoothBleServicesData>;

            /** Read a BLE characteristic. */
            function readCharacteristic(sessionId: string, options: {
                serviceUuid: string;
                characteristicUuid: string;
                timeoutMs?: number | string;
            }): Promise<BluetoothReadData>;

            /** Write text or bytes to a BLE characteristic. */
            function writeCharacteristic(sessionId: string, options: {
                serviceUuid: string;
                characteristicUuid: string;
                text?: string;
                dataBase64?: string;
            }): Promise<BluetoothTransferData>;

            /** Write text or bytes to one BLE characteristic and read another characteristic response. */
            function writeAndReadCharacteristic(sessionId: string, options: {
                writeServiceUuid: string;
                writeCharacteristicUuid: string;
                readServiceUuid: string;
                readCharacteristicUuid: string;
                text?: string;
                dataBase64?: string;
                timeoutMs?: number | string;
            }): Promise<BluetoothReadData>;

            /** Subscribe or unsubscribe BLE characteristic notifications. */
            function subscribe(sessionId: string, options: {
                serviceUuid: string;
                characteristicUuid: string;
                enable?: boolean;
            }): Promise<BluetoothTransferData>;

            /** Read received BLE notifications. */
            function readNotifications(sessionId: string, limit?: number | string): Promise<BluetoothBleNotificationData>;
        }
    }

    /**
     * Execute an shell command (requires root access)
     * @param command The shell command to execute
     */
    function shell(command: string): Promise<ADBResultData>;

    /**
     * Execute an Intent
     * @param options - Intent options object
     */
    function intent(options?: {
        action?: string;
        uri?: string;
        package?: string;
        component?: string;
        flags?: number | string;
        extras?: Record<string, any> | string;
        type?: 'activity' | 'broadcast' | 'service';
    }): Promise<IntentResultData>;

    /**
     * Send a broadcast intent (string extras supported via extra_key/extra_value pairs).
     */
    function sendBroadcast(options?: {
        action: string;
        uri?: string;
        package?: string;
        component?: string;
        extras?: Record<string, any> | string;
        extra_key?: string;
        extra_value?: string;
        extra_key2?: string;
        extra_value2?: string;
    }): Promise<IntentResultData>;

    /**
     * Terminal operations.
     */
    namespace terminal {
        /**
         * Create or get a terminal session.
         * @param sessionName The name for the session.
         * @returns Promise resolving to the session creation result.
         */
        function create(sessionName?: string): Promise<TerminalSessionCreationResultData>;

        /**
         * Execute a command in a terminal session.
         * @param sessionId The ID of the session.
         * @param command The command to execute.
         * @param timeoutMs Optional timeout in milliseconds. Strongly recommended to always pass explicitly.
         * @returns Promise resolving to the command execution result. On timeout, the current command is cancelled, the terminal session is kept, and the returned result has `timedOut === true`.
         */
        function exec(sessionId: string, command: string, timeoutMs?: number | string): Promise<TerminalCommandResultData>;

        /**
         * Execute a command in a terminal session and receive incremental output chunks.
         * Final resolution still returns the complete terminal command result.
         * @param sessionId The ID of the session.
         * @param command The command to execute.
         * @param options Streaming execution options.
         * @returns Promise resolving to the final command execution result.
         */
        function execStreaming(sessionId: string, command: string, options?: {
            timeoutMs?: number | string;
            onIntermediateResult?: (event: TerminalStreamEventData) => void;
        }): Promise<TerminalCommandResultData>;

        /**
         * Execute a command in a hidden non-PTY executor.
         * Commands using the same executorKey reuse the same hidden login context and are not shown in the visible terminal UI.
         * @param command The command to execute.
         * @param options Optional hidden executor options.
         * @returns Promise resolving to the hidden command execution result. On timeout, the current command is cancelled, the hidden executor session is kept, and the returned result has `timedOut === true`.
         */
        function hiddenExec(command: string, options?: {
            executorKey?: string;
            timeoutMs?: number | string;
        }): Promise<HiddenTerminalCommandResultData>;

        /**
         * Close a terminal session.
         * @param sessionId The ID of the session to close.
         * @returns Promise resolving to the session close result.
         */
        function close(sessionId: string): Promise<TerminalSessionCloseResultData>;

        /**
         * Get the current visible terminal screen content for a session (single screen only, no history).
         * @param sessionId The ID of the session.
         * @returns Promise resolving to the current screen snapshot result.
         */
        function screen(sessionId: string): Promise<TerminalSessionScreenResultData>;

        /**
         * Write input to a terminal session.
         * At least one of `input` or `control` should be provided.
         * - Typical usage: send input first, then send control=`enter` to submit.
         * - If `control` and `input` are provided together, it is treated as a key combo
         *   (for example, control=`ctrl`, input=`c` means Ctrl+C).
         * @param sessionId The ID of the session.
         * @param options Input options for this write.
         * @returns Promise resolving to the write result message.
         */
        function input(sessionId: string, options?: {
            input?: string;
            control?: string;
        }): Promise<string>;
    }

    /**
     * App music playback operations.
     */
    namespace music {
        /**
         * Play audio inside the app.
         * @param options Playback options
         */
        function play(options: {
            source: string;
            sourceType: 'path' | 'url' | 'uri';
            title?: string;
            artist?: string;
            loop?: boolean;
            volume?: number | string;
            startPositionMs?: number | string;
        }): Promise<MusicPlaybackResultData>;

        /**
         * Play an audio queue inside the app.
         * @param options Queue playback options
         */
        function playQueue(options: {
            items: Array<{
                source: string;
                sourceType: 'path' | 'url' | 'uri';
                title?: string;
                artist?: string;
            }>;
            loop?: boolean;
            volume?: number | string;
            startIndex?: number | string;
            startPositionMs?: number | string;
        }): Promise<MusicPlaybackResultData>;

        /** Pause current music playback. */
        function pause(): Promise<MusicPlaybackResultData>;

        /** Resume current music playback. */
        function resume(): Promise<MusicPlaybackResultData>;

        /** Stop current music playback. */
        function stop(): Promise<MusicPlaybackResultData>;

        /**
         * Seek current music playback.
         * @param positionMs Target position in milliseconds
         */
        function seek(positionMs: number | string): Promise<MusicPlaybackResultData>;

        /**
         * Set playback volume.
         * @param volume Volume from 0 to 1
         */
        function setVolume(volume: number | string): Promise<MusicPlaybackResultData>;

        /** Get current music playback status. */
        function status(): Promise<MusicPlaybackResultData>;
    }
}
