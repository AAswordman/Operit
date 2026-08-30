# SOLUTIONS — NonO Assistant (OperitX)

> Format: `## [YYYY-MM-DD] ปัญหา: <หัวข้อ>`
> Tags: #provider-x #tool-calling #ui #memory etc.

---

## [2026-08-29] ปัญหา: Operit มีระบบ Provider ที่ครบแล้ว ต้องทำยังไงกับ Phase 1?
**อาการ:** ค้นพบว่า Operit ต้นทางมี ApiProviderType (35+ providers), AIService, DeepseekProvider, GeminiProvider ฯลฯ ครบแล้ว
**สาเหตุ:** PROJECT_PLAN.md บอกให้ "ต่อยอดจากของ Operit เดิม" แต่ Phase 0 สร้าง AIProvider interface ใหม่ที่แยกต่างหาก
**วิธีแก้:** ใช้ทั้งสองระบบคู่กัน — เก็บระบบเดิมไว้ สร้าง OperitProviderAdapter เป็น bridge ที่ wrap AIService เดิมให้ implement AIProvider interface ใหม่ ทำให้ Orchestration Engine ใช้ provider เดิมได้โดยไม่ต้อง rewrite
**Tag:** #provider #architecture #bridge-pattern

---

No more issues yet.

---

## [2026-08-30] ปัญหา: App module build ไม่ได้ — missing native libs
**อาการ:** `:app:compileDebugKotlin` fails with missing `liboperit_ripgrep.so` and `ffmpeg-kit-local.aar`
**สาเหตุ:** These native libraries are built outside Gradle (Rust build script + FFmpeg WSL build). They are pre-existing build dependencies, not caused by OperitX changes.
**วิธีแก้:** Skip native verification tasks (`-x verifyExternallyBuiltNativeLibraries`) for Kotlin-only compilation. Full APK build requires the native libraries to be built first via `tools/native_ripgrep/build_native_ripgrep.ps1` and `tools/ffmpeg/build_ffmpeg_kit_wsl.sh`.
**Tag:** #build #native #environment

---

## [2026-08-30] ปัญหา: Terminal module AIDL compilation fails in Termux
**อาการ:** `:terminal:compileDebugAidl` fails — AIDL tool not available in Termux SDK
**สาเหตุ:** The Android SDK installed in Termux is minimal and doesn't include the full `aidl` binary. The terminal module uses AIDL for IPC.
**วิธีแก้:** Skip AIDL tasks (`-x compileDebugAidl`) for Kotlin-only compilation. Full build requires a complete Android SDK with build-tools that include `aidl`.
**Tag:** #build #aidl #termux

---

## [2026-08-30] ปัญหา: Tool call XML contamination — sanitizer integration
**อาการ:** AI providers sometimes inject XML tags, CDATA, and markdown artifacts into tool call parameters, causing JSON parsing failures (§10 of PROJECT_PLAN.md)
**สาเหตุ:** Some providers wrap tool results in XML tags or add metadata that pollutes the parameter values
**วิธีแก้:** Integrated `ToolCallSanitizer` into `ToolExecutionManager.extractToolInvocations()` — sanitizes each parameter value right after extraction. Also registered `ToolSanitizerHook` in `AIToolHandler` for additional detection layer.
**Tag:** #tool-calling #xml #sanitizer #integration

---

## [2026-08-30] ปัญหา: UI screens integration for OperitX features
**อาการ:** Need to add ModeSwitcher, ModelPricing, and BudgetStats screens to the existing app
**สาเหตุ:** OperitX adds new features that require UI surfaces for user interaction
**วิธีแก้:** Created 3 new screens following existing patterns:
- `ModeSwitcherScreen` — 4 mode cards with selection state
- `ModelPricingScreen` — pricing cards with provider filter
- `BudgetStatsScreen` — usage progress and stats grid
Integrated into navigation via `Screen` sealed class + `ScreenRouteRegistry` + `SettingsScreen` new section.
**Tag:** #ui #navigation #compose #settings

---

## [2026-08-30] ปัญหา: RootExecutionGuard integration into tool execution pipeline
**อาการ:** RootExecutionGuard existed but wasn't connected to the actual tool execution flow
**สาเหตุ:** The guard was created as a standalone class but never wired into ToolExecutionManager
**วิธีแก้:** Integrated into `ToolExecutionManager.executeInvocations()` as step 3.5:
- Detects shell execution tools (`execute_shell`, `execute_in_terminal_session`, `execute_in_terminal_session_streaming`)
- Extracts command parameter and checks against RootExecutionGuard
- Blocked/RequiresConfirmation decisions emit denied results and skip execution
- Worker role always blocked from root commands
- Leader role requires user confirmation for dangerous commands
- Added 25 unit tests covering non-root, always-blocked, worker/leader role restrictions
**Tag:** #permission #root #security #integration

---

## [2026-08-30] ปัญหา: NonOX screens need ViewModel for state management
**อาการ:** NonOX screens (ModeSwitcher, Pricing, BudgetStats) used local state and direct service calls
**สาเหตุ:** Screens were created without proper MVVM pattern, making them hard to test and maintain
**วิธีแก้:** Created `NonOXViewModel` with AndroidViewModel pattern:
- Manages state for all 3 screens via StateFlow
- Provides error handling and loading states
- Adds refresh functionality for pricing and budget data
- Updated all screens to use ViewModel state instead of local state
- Updated OperitScreens.kt to use default ViewModel parameter
**Tag:** #ui #viewmodel #mvvm #architecture
