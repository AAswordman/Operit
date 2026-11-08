"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const shared_1 = require("../shared");
function useStateValue(ctx, key, initialValue) {
    const pair = ctx.useState(key, initialValue);
    return { value: pair[0], set: pair[1] };
}
function readSettings() {
    return (0, shared_1.loadSettings)();
}
function createSectionTitle(ctx, icon, title) {
    return ctx.UI.Row({ verticalAlignment: "center" }, [
        ctx.UI.Icon({ name: icon, tint: "primary", size: 20 }),
        ctx.UI.Spacer({ width: 8 }),
        ctx.UI.Text({
            text: title,
            style: "titleMedium",
            fontWeight: "bold",
            color: "primary",
        }),
    ]);
}
const toggleCardStyle = {
    fillMaxWidth: true,
    shape: { cornerRadius: 8 },
    containerColor: "surfaceVariant",
    alpha: 0.38,
};
function createDivider(ctx) {
    return ctx.UI.HorizontalDivider({
        padding: { horizontal: 14 },
        color: "outlineVariant",
        thickness: 1,
    });
}
function createToggleCard(ctx, title, subtitle, checked, onCheckedChange, enabled = true) {
    return ctx.UI.Surface(toggleCardStyle, [
        createToggleRow(ctx, title, subtitle, checked, onCheckedChange, enabled),
    ]);
}
function createToggleRow(ctx, title, subtitle, checked, onCheckedChange, enabled = true) {
    return ctx.UI.Row({
        fillMaxWidth: true,
        padding: { horizontal: 14, vertical: 12 },
        verticalAlignment: "center",
        horizontalArrangement: "spaceBetween",
    }, [
        ctx.UI.Column({ weight: 1, spacing: 4 }, [
            ctx.UI.Text({
                text: title,
                style: "bodyMedium",
                fontWeight: "medium",
            }),
            ctx.UI.Text({
                text: subtitle,
                style: "bodySmall",
                color: "onSurfaceVariant",
            }),
        ]),
        ctx.UI.Spacer({ width: 12 }),
        ctx.UI.Switch({
            checked,
            enabled,
            onCheckedChange,
        }),
    ]);
}
function createMemoryConfigSection(ctx, text, enabled, allowRepeatedMemorySearch, onAllowRepeatedMemorySearchChange, limitValue, onLimitChange, onApply) {
    return ctx.UI.Column({
        fillMaxWidth: true,
        padding: { horizontal: 14, vertical: 12 },
        spacing: 10,
    }, [
        ctx.UI.Text({
            text: text.memoryConfigTitle,
            style: "bodyMedium",
            fontWeight: "medium",
        }),
        ctx.UI.Text({
            text: text.memoryConfigDescription,
            style: "bodySmall",
            color: "onSurfaceVariant",
        }),
        createToggleRow(ctx, text.memoryRepeatToggleTitle, text.memoryRepeatToggleDescription, allowRepeatedMemorySearch, onAllowRepeatedMemorySearchChange, enabled),
        createDivider(ctx),
        ctx.UI.TextField({
            enabled,
            label: text.memoryLimitFieldLabel,
            placeholder: text.memoryLimitFieldPlaceholder,
            value: limitValue,
            onValueChange: onLimitChange,
            singleLine: true,
        }),
        ctx.UI.Text({
            text: text.memoryLimitFieldDescription,
            style: "bodySmall",
            color: "onSurfaceVariant",
        }),
        ctx.UI.Button({
            text: text.memoryConfigApplyButton,
            enabled,
            fillMaxWidth: true,
            onClick: onApply,
        }),
    ]);
}
function createInjectionItemsCard(ctx, items, memoryConfigSection) {
    const children = [];
    items.forEach((item, index) => {
        children.push(createToggleRow(ctx, item.title, item.subtitle, item.checked, item.onCheckedChange, item.enabled ?? true));
        children.push(createDivider(ctx));
        if (index === items.length - 1) {
            children.push(memoryConfigSection);
        }
    });
    return ctx.UI.Surface(toggleCardStyle, [
        ctx.UI.Column({ fillMaxWidth: true }, children),
    ]);
}
function createInjectionTimeoutConfigSection(ctx, text, value, onValueChange, onApply) {
    return ctx.UI.Surface(toggleCardStyle, [
        ctx.UI.Column({ fillMaxWidth: true, padding: { horizontal: 14, vertical: 12 }, spacing: 10 }, [
            ctx.UI.TextField({
                label: text.injectionTimeoutFieldLabel,
                placeholder: text.injectionTimeoutFieldPlaceholder,
                value,
                onValueChange,
                singleLine: true,
            }),
            ctx.UI.Text({
                text: text.injectionTimeoutFieldDescription,
                style: "bodySmall",
                color: "onSurfaceVariant",
            }),
            ctx.UI.Button({
                text: text.injectionTimeoutApplyButton,
                fillMaxWidth: true,
                onClick: onApply,
            }),
        ]),
    ]);
}
function Screen(ctx) {
    const text = (0, shared_1.resolveExtraInfoI18n)();
    const initial = readSettings();
    const masterEnabledState = useStateValue(ctx, "masterEnabled", initial.masterEnabled);
    const persistInjectedContentState = useStateValue(ctx, "persistInjectedContent", initial.persistInjectedContent);
    const injectTimeState = useStateValue(ctx, "injectTime", initial.injectTime);
    const injectBatteryState = useStateValue(ctx, "injectBattery", initial.injectBattery);
    const injectWeatherState = useStateValue(ctx, "injectWeather", initial.injectWeather);
    const injectLocationState = useStateValue(ctx, "injectLocation", initial.injectLocation);
    const usePreciseLocationState = useStateValue(ctx, "usePreciseLocation", initial.usePreciseLocation);
    const injectCurrentScreenAppState = useStateValue(ctx, "injectCurrentScreenApp", initial.injectCurrentScreenApp);
    const injectRecentAppUsageState = useStateValue(ctx, "injectRecentAppUsage", initial.injectRecentAppUsage);
    const injectScreenTextState = useStateValue(ctx, "injectScreenText", initial.injectScreenText);
    const injectNotificationsState = useStateValue(ctx, "injectNotifications", initial.injectNotifications);
    const injectMemoryState = useStateValue(ctx, "injectMemory", initial.injectMemory);
    const allowRepeatedMemorySearchState = useStateValue(ctx, "allowRepeatedMemorySearch", initial.allowRepeatedMemorySearch);
    const memoryLimitState = useStateValue(ctx, "memoryLimit", initial.memoryLimit);
    const memoryLimitInputState = useStateValue(ctx, "memoryLimitInput", String(initial.memoryLimit));
    const injectionTimeoutSecondsState = useStateValue(ctx, "injectionTimeoutSeconds", initial.injectionTimeoutSeconds);
    const injectionTimeoutInputState = useStateValue(ctx, "injectionTimeoutInput", String(initial.injectionTimeoutSeconds));
    const successMessageState = useStateValue(ctx, "successMessage", "");
    const errorMessageState = useStateValue(ctx, "errorMessage", "");
    const hasInitializedState = useStateValue(ctx, "hasInitialized", false);
    const syncSettings = (next) => {
        masterEnabledState.set(next.masterEnabled);
        persistInjectedContentState.set(next.persistInjectedContent);
        injectTimeState.set(next.injectTime);
        injectBatteryState.set(next.injectBattery);
        injectWeatherState.set(next.injectWeather);
        injectLocationState.set(next.injectLocation);
        usePreciseLocationState.set(next.usePreciseLocation);
        injectCurrentScreenAppState.set(next.injectCurrentScreenApp);
        injectRecentAppUsageState.set(next.injectRecentAppUsage);
        injectScreenTextState.set(next.injectScreenText);
        injectNotificationsState.set(next.injectNotifications);
        injectMemoryState.set(next.injectMemory);
        allowRepeatedMemorySearchState.set(next.allowRepeatedMemorySearch);
        memoryLimitState.set(next.memoryLimit);
        memoryLimitInputState.set(String(next.memoryLimit));
        injectionTimeoutSecondsState.set(next.injectionTimeoutSeconds);
        injectionTimeoutInputState.set(String(next.injectionTimeoutSeconds));
    };
    const persistSettings = (patch, successMessage = "") => {
        try {
            const next = (0, shared_1.saveSettings)(patch);
            syncSettings(next);
            errorMessageState.set("");
            successMessageState.set(successMessage);
        }
        catch (error) {
            (0, shared_1.logExtraInfoInjectionError)("settings.save_failed", error);
            const message = error instanceof Error ? error.message : String(error || "unknown");
            successMessageState.set("");
            errorMessageState.set(`${text.saveErrorPrefix}${message}`);
        }
    };
    const applyMemorySettings = () => {
        const limit = Number(memoryLimitInputState.value.trim());
        if (!Number.isFinite(limit) || limit < 1) {
            successMessageState.set("");
            errorMessageState.set(`${text.saveErrorPrefix}${text.invalidMemoryLimitMessage}`);
            return;
        }
        persistSettings({
            memoryLimit: Math.floor(limit),
        });
    };
    const applyInjectionTimeoutSettings = () => {
        const timeoutSeconds = Number(injectionTimeoutInputState.value.trim());
        if (!Number.isFinite(timeoutSeconds) ||
            timeoutSeconds < shared_1.MIN_INJECTION_TIMEOUT_SECONDS ||
            timeoutSeconds > shared_1.MAX_INJECTION_TIMEOUT_SECONDS ||
            !Number.isInteger(timeoutSeconds)) {
            successMessageState.set("");
            errorMessageState.set(`${text.saveErrorPrefix}${text.invalidInjectionTimeoutMessage}`);
            return;
        }
        persistSettings({ injectionTimeoutSeconds: timeoutSeconds });
    };
    const summaryLines = [
        masterEnabledState.value ? text.summaryMasterEnabled : text.summaryMasterDisabled,
        persistInjectedContentState.value
            ? text.summaryPersistEnabled
            : text.summaryPersistDisabled,
        text.summaryInjectionTimeout.replace("{seconds}", injectionTimeoutSecondsState.value.toString()),
        injectTimeState.value ? text.summaryTimeEnabled : text.summaryTimeDisabled,
        injectBatteryState.value ? text.summaryBatteryEnabled : text.summaryBatteryDisabled,
        injectWeatherState.value ? text.summaryWeatherEnabled : text.summaryWeatherDisabled,
        injectLocationState.value ? text.summaryLocationEnabled : text.summaryLocationDisabled,
        usePreciseLocationState.value
            ? text.summaryPreciseLocationEnabled
            : text.summaryPreciseLocationDisabled,
        injectCurrentScreenAppState.value
            ? text.summaryCurrentScreenAppEnabled
            : text.summaryCurrentScreenAppDisabled,
        injectRecentAppUsageState.value
            ? text.summaryRecentAppUsageEnabled
            : text.summaryRecentAppUsageDisabled,
        injectScreenTextState.value
            ? text.summaryScreenTextEnabled
            : text.summaryScreenTextDisabled,
        injectNotificationsState.value
            ? text.summaryNotificationsEnabled
            : text.summaryNotificationsDisabled,
        injectMemoryState.value
            ? `${text.summaryMemoryEnabled} (${text.memoryLimitLabel}: ${memoryLimitState.value}; ${allowRepeatedMemorySearchState.value
                ? text.summaryMemoryRepeatEnabled
                : text.summaryMemoryRepeatDisabled})`
            : text.summaryMemoryDisabled,
        text.summaryRulesHint,
    ];
    const rootChildren = [
        ctx.UI.Row({ verticalAlignment: "center" }, [
            ctx.UI.Icon({ name: "settings", tint: "primary", size: 24 }),
            ctx.UI.Spacer({ width: 8 }),
            ctx.UI.Text({
                text: text.settingsTitle,
                style: "headlineSmall",
                fontWeight: "bold",
            }),
        ]),
        ctx.UI.Text({
            text: text.settingsSubtitle,
            style: "bodyMedium",
            color: "onSurfaceVariant",
        }),
        ctx.UI.Surface({
            fillMaxWidth: true,
            shape: { cornerRadius: 12 },
            containerColor: "secondaryContainer",
        }, [
            ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "info", tint: "onSecondaryContainer", size: 18 }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: text.settingsBanner,
                    style: "bodySmall",
                    color: "onSecondaryContainer",
                }),
            ]),
        ]),
        createSectionTitle(ctx, "settings", text.masterSectionTitle),
        createToggleCard(ctx, text.masterToggleTitle, text.masterToggleDescription, masterEnabledState.value, checked => {
            persistSettings({ masterEnabled: checked });
        }),
        createToggleCard(ctx, text.persistToggleTitle, text.persistToggleDescription, persistInjectedContentState.value, checked => {
            persistSettings({ persistInjectedContent: checked });
        }),
        createInjectionTimeoutConfigSection(ctx, text, injectionTimeoutInputState.value, value => {
            injectionTimeoutInputState.set(value);
        }, applyInjectionTimeoutSettings),
        createSectionTitle(ctx, "bolt", text.itemsSectionTitle),
        createInjectionItemsCard(ctx, [
            {
                title: text.timeToggleTitle,
                subtitle: text.timeToggleDescription,
                checked: injectTimeState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectTime: checked });
                },
            },
            {
                title: text.batteryToggleTitle,
                subtitle: text.batteryToggleDescription,
                checked: injectBatteryState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectBattery: checked });
                },
            },
            {
                title: text.weatherToggleTitle,
                subtitle: text.weatherToggleDescription,
                checked: injectWeatherState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectWeather: checked });
                },
            },
            {
                title: text.locationToggleTitle,
                subtitle: text.locationToggleDescription,
                checked: injectLocationState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectLocation: checked });
                },
            },
            {
                title: text.preciseLocationToggleTitle,
                subtitle: text.preciseLocationToggleDescription,
                checked: usePreciseLocationState.value,
                enabled: injectLocationState.value,
                onCheckedChange: checked => {
                    persistSettings({ usePreciseLocation: checked });
                },
            },
            {
                title: text.currentScreenAppToggleTitle,
                subtitle: text.currentScreenAppToggleDescription,
                checked: injectCurrentScreenAppState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectCurrentScreenApp: checked });
                },
            },
            {
                title: text.recentAppUsageToggleTitle,
                subtitle: text.recentAppUsageToggleDescription,
                checked: injectRecentAppUsageState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectRecentAppUsage: checked });
                },
            },
            {
                title: text.screenTextToggleTitle,
                subtitle: text.screenTextToggleDescription,
                checked: injectScreenTextState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectScreenText: checked });
                },
            },
            {
                title: text.notificationsToggleTitle,
                subtitle: text.notificationsToggleDescription,
                checked: injectNotificationsState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectNotifications: checked });
                },
            },
            {
                title: text.memoryToggleTitle,
                subtitle: text.memoryToggleDescription,
                checked: injectMemoryState.value,
                onCheckedChange: checked => {
                    persistSettings({ injectMemory: checked });
                },
            },
        ], createMemoryConfigSection(ctx, text, injectMemoryState.value, allowRepeatedMemorySearchState.value, checked => {
            persistSettings({ allowRepeatedMemorySearch: checked });
        }, memoryLimitInputState.value, value => {
            memoryLimitInputState.set(value);
        }, applyMemorySettings)),
        createSectionTitle(ctx, "checkCircle", text.summarySectionTitle),
        ctx.UI.Card({
            fillMaxWidth: true,
            shape: { cornerRadius: 12 },
            containerColor: "primaryContainer",
            elevation: 1,
        }, [
            ctx.UI.Column({ padding: 16, spacing: 8 }, summaryLines.map((line, index) => ctx.UI.Text({
                key: `summary-${index}`,
                text: line,
                style: index === summaryLines.length - 1 ? "bodySmall" : "bodyMedium",
                color: "onPrimaryContainer",
            }))),
        ]),
    ];
    if (successMessageState.value.trim()) {
        rootChildren.push(ctx.UI.Card({ containerColor: "primaryContainer", fillMaxWidth: true }, [
            ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "checkCircle", tint: "onPrimaryContainer" }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: successMessageState.value,
                    style: "bodyMedium",
                    color: "onPrimaryContainer",
                }),
            ]),
        ]));
    }
    if (errorMessageState.value.trim()) {
        rootChildren.push(ctx.UI.Card({ containerColor: "errorContainer", fillMaxWidth: true }, [
            ctx.UI.Row({ padding: { horizontal: 14, vertical: 12 }, verticalAlignment: "center" }, [
                ctx.UI.Icon({ name: "error", tint: "onErrorContainer" }),
                ctx.UI.Spacer({ width: 8 }),
                ctx.UI.Text({
                    text: errorMessageState.value,
                    style: "bodyMedium",
                    color: "onErrorContainer",
                }),
            ]),
        ]));
    }
    return ctx.UI.LazyColumn({
        fillMaxSize: true,
        padding: 16,
        spacing: 16,
        onLoad: async () => {
            if (!hasInitializedState.value) {
                hasInitializedState.set(true);
                syncSettings(readSettings());
            }
        },
    }, rootChildren);
}
