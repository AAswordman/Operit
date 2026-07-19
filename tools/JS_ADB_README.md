# ADB JavaScript Executor

This repo's ADB JS executors are the main **device-side JavaScript runtime launchers** for Operit.

They are used for:

- directly running JS on a real Android device during development
- debugging exported JS functions without a full package import cycle
- validating sandbox runtime behavior and structured results
- running JS bridge / script-mode / smoke tests from `app/src/androidTest/js`
- reproducing device-only runtime bugs quickly

For direct whole-script execution without specifying an exported function, use `run_sandbox_script.sh` / `run_sandbox_script.bat`.

## Features

- Push JavaScript files to Android devices using ADB
- Run JS directly inside the Operit Android runtime
- Execute specified JavaScript functions
- Execute complete sandbox scripts
- Execute a whole JS directory with local module dependencies
- Support for JSON parameter passing
- Temporary file option (auto-deletion after execution)
- Implementations in Shell script, Windows Batch file, and Python script
- **Device selection for multi-device setups**
- Structured result capture instead of fragile log scraping
- Useful as both a **runtime debugging launcher** and a **JS bridge test harness**

## Tool Roles

In this repo, the following tools form the main on-device JS execution workflow:

- `tools/execute_js.bat` / `tools/execute_js.sh`
  The main single-file runtime launcher.
  Pushes one JS file to the Android device and executes an exported function inside the Operit runtime.
  This is the most direct tool for device-side JS debugging.
- `tools/execute_js_dir.bat` / `tools/execute_js_dir.sh`
  The directory-entry launcher.
  Bundles and runs a JS entry from a directory, so local `require(...)` chains and test suites work.
- `tools/run_sandbox_script.bat` / `tools/run_sandbox_script.sh`
  The script-mode launcher.
  Runs top-level sandbox script code directly instead of calling an exported function.

This makes them suitable for:

- directly running and debugging JS on Android
- validating `JsEngine` behavior on a real device
- reproducing package/runtime bugs quickly
- debugging package tools without a full app rebuild loop
- running JS-side contract tests and smoke tests under `app/src/androidTest/js`

## JS Test Suites In This Repo

The directory [app/src/androidTest/js](/d:/Code/prog/assistance/app/src/androidTest/js) contains real JS runtime tests and probes, including:

- bridge contract tests
- bridge edge-case tests
- script-mode contract tests
- browser tool smoke tests
- ffmpeg / utility probes

Representative locations:

- [app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_contract](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_contract)
- [app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_edges](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/bridge_edges)
- [app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/script_mode_contract](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/javascript/script_mode_contract)
- [app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/browser/main.js](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/core/tools/defaultTool/standard/browser/main.js)
- [app/src/androidTest/js/com/ai/assistance/operit/util/ttscleaner/ttscleaner.js](/d:/Code/prog/assistance/app/src/androidTest/js/com/ai/assistance/operit/util/ttscleaner/ttscleaner.js)

## Quick Choice

If you just want the short answer for "which tool should I use":

- Use `execute_js.*` when you want to call one exported function from one normal `.js` file
- Use `execute_js_dir.*` when the entry depends on sibling modules, or when you are running directory-based tests under `app/src/androidTest/js`
- Use `run_sandbox_script.*` when you want top-level script-mode execution instead of calling an exported function
- Use `tools/sync_example_packages.py` when you changed an `examples/` package or toolpkg and want it synced into the real package-loading path
- If you are debugging `operit_editor` tools such as `debug_install_toolpkg`, the usual flow is: sync with `tools/sync_example_packages.py`, then call the debug tool with `execute_js.bat`

## Prerequisites

- Android SDK (ADB)
- Android device with USB debugging enabled
- ADB debugging permission granted on the device
- Your application installed on the device

## Quick Start

### Using Shell Script (Linux/macOS)

1. Make the script executable:
```bash
chmod +x execute_js.sh
```

2. Execute the script:
```bash
./execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

3. Execute a whole sandbox script directly:
```bash
./run_sandbox_script.sh path/to/your/script.js '{"param1":"value1"}'
```

### Using Batch Script (Windows)

1. Execute the batch file:
```cmd
execute_js.bat path\to\your\script.js functionName @params.json
```

2. Execute a whole sandbox script directly:
```cmd
run_sandbox_script.bat path\to\your\script.js @params.json
```

### Execute A Whole Directory (supports `require(...)`)

Sometimes a script needs `require('./helper')`, shared local modules, or a whole test suite layout.
The single-file executors only push one JS file, so this repo also provides directory-based executors.

Windows:
```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\util\ttscleaner\main.js run "{}"
```

Linux/macOS:
```bash
./tools/execute_js_dir.sh app/src/androidTest/js com/ai/assistance/operit/util/ttscleaner/ttscleaner.js run '{}'
```

More realistic examples:

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\defaultTool\standard\browser\main.js run "{}"
```

You can still pass inline JSON directly. The scripts now write that JSON into a temporary file and push it to the device, which avoids the old `adb shell am broadcast --es params ...` quoting breakage. On PowerShell, prefer single-quoted JSON like `'{"param1":"value1"}'` or use `@params.json`.

### Using Python Script (Cross-platform)

1. Ensure Python 3 is installed

2. Execute the script:
```bash
python execute_js.py path/to/your/script.js functionName --params '{"param1":"value1"}'
```

3. Additional options:
```bash
# Specify a device directly (skip device selection prompt)
python execute_js.py path/to/script.js functionName --device DEVICE_SERIAL

# Keep the file on the device after execution
python execute_js.py path/to/script.js functionName --keep
```

## Device Selection

When multiple devices are connected:

1. The script will display a list of available devices
2. Enter the number corresponding to your target device
3. All ADB operations will be performed on the selected device

Example:
```
Checking connected devices...
1: emulator-5554
2: 192.168.1.100:5555
Enter device number (1-2): 
```

## Examples

### 1. Execute the greeting function

```bash
# Linux/macOS
./execute_js.sh test_script.js sayHello '{"name":"John"}'

# Windows
execute_js.bat test_script.js sayHello @params.json

# Python (any platform)
python execute_js.py test_script.js sayHello --params '{"name":"John"}'
```

### 2. Execute the calculation function

```bash
# Linux/macOS
./execute_js.sh test_script.js calculate '{"num1":10,"num2":5,"operation":"multiply"}'

# Windows
execute_js.bat test_script.js calculate @params.json

# Python (any platform)
python execute_js.py test_script.js calculate --params '{"num1":10,"num2":5,"operation":"multiply"}'
```

## Viewing Execution Results

The executor now waits for a dedicated structured result file instead of scraping `adb logcat`.
After execution it prints a JSON payload containing:

- `success`
- `result`
- `error`
- `events` (including `console.log/info/warn/error` and intermediate updates)
- `durationMs`

This is especially useful for JS runtime tests, because failures come back as structured payloads rather than "check logcat manually".

You can adjust the wait timeout with:

```bash
OPERIT_RESULT_WAIT_SECONDS=30 ./execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

## Sandbox Script Debug Notes

When you use `run_sandbox_script.sh` / `run_sandbox_script.bat`, or call package tools such as `operit_editor:debug_run_sandbox_script` with `source_code`, the code is executed as a **top-level script snippet**, not as `function(params) { ... }`.

That means:

- Use `console.log(...)`, `console.info(...)`, `console.warn(...)`, `console.error(...)` for logs
- Use `emit(...)` to send intermediate events
- Use `return result` or `complete(...)` to finish with a structured result
- Do **not** assume `params` is directly available inside `source_code`
- Do **not** call `intermediate(...)`; the supported helper name is `emit(...)`

Recommended inline snippet:

```javascript
console.log("inline debug start");
emit({ stage: "inline", ok: true });
complete({
  success: true,
  message: "inline debug finished"
});
```

If you need parameter-driven logic, prefer one of these approaches:

- Use `execute_js.*` and call an exported function like `function myFunc(params) { ... }`
- Use `debug_run_sandbox_script` with `source_path` and put the logic in a real script file
- Keep `source_code` for quick top-level smoke tests, logging, and minimal one-off checks

## Purpose And Invocation

### 1. What `execute_js.*` is for

This is the most direct Android-side JS runner.

You give it:

- one JS file
- one exported function name
- one JSON parameter payload

It pushes that file to the device and calls that function inside Operit's Android JS runtime.

Use it for:

- single-file behavior checks
- direct exported-function debugging
- reproducing runtime-only device errors
- debugging one tool implementation inside a package

Most common usage:

```cmd
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

Minimum file requirements:

- the file must be valid JavaScript, not raw TypeScript
- the target function must be exported
- params must be passed as JSON

Example:

```javascript
function main(params) {
  return {
    success: true,
    echo: params
  };
}

exports.main = main;
```

### 2. What `execute_js_dir.*` is for

This is the directory-entry runner.

If your entry file is not standalone and depends on local modules through `require("./x")`, sibling helpers, shared runners, or directory-local test harnesses, switch to this instead of forcing the single-file runner.

Use it for:

- test entries under `app/src/androidTest/js`
- bridge contract / browser smoke / harness-style test directories
- any new multi-file JS runtime test suite you add

Most common usage:

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
```

Call format:

```cmd
tools\execute_js_dir.bat <root_dir> <entry_relative_path> <function_name> [JSON params|@params_file]
```

The key points are:

- the first argument is the suite root
- the second argument is the entry path relative to that root
- the third argument is usually a runner function such as `run`

### 3. What `run_sandbox_script.*` is for

This does not call an exported function. It runs the whole file as a top-level script.

Use it for:

- script-mode contract checks
- quick `emit(...)` verification
- quick `complete(...)` verification
- temporary runtime probe scripts

Most common usage:

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

If your code is naturally top-level script logic, this is usually simpler than wrapping everything in `exports.main = ...`.

### 4. What `tools/sync_example_packages.py` is for

This is not just a copy script. It is the sync entrypoint for `examples/` package development.

Its main jobs are:

- prebuild relevant TypeScript under `examples/` into JS
- pack manifest-based directories into `.toolpkg`
- sync normal `.js` or `.toolpkg` outputs into `app/src/main/assets/packages`
- hot-reload changed packages to a connected device when available

Use it when:

- you changed a real package under `examples/`
- you want package-management, sandbox package lists, or built-in loading logic to see the latest output
- you want to validate the folder -> `.toolpkg` -> debug install path

Most common usage:

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action
```

To sync all syncable example items for broad validation:

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --mode test
```

To update local assets without device hot reload:

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action --no-hot-reload
```

## How To Add Tests

The easy thing to get wrong is assuming every kind of test belongs in the same place. They do not.

### 1. Add a single-file function test

If you only want to verify one function's logic, the lightest approach is:

- write a normal JS file
- export `main` or `run`
- call it with `execute_js.*`

This is best for:

- small logic checks
- parameter parsing checks
- return-shape checks
- minimal bug reproductions

### 2. Add a directory-based runtime test

If the test needs multiple local modules, put it under [app/src/androidTest/js](/d:/Code/prog/assistance/app/src/androidTest/js) and organize it as a small directory-based suite.

Recommended shape:

- one runner file such as `xxx_runner.js`
- a few test files or helpers
- the runner exports `run(params)`

Then call it with:

```cmd
tools\execute_js_dir.bat app\src\androidTest\js <your_entry_relative_path> run "{}"
```

This is best for:

- bridge contract tests
- multi-module coordination
- real runtime smoke tests
- anything that needs local `require(...)`

### 3. Add a script-mode test

If what you need to verify is:

- top-level `return`
- `emit(...)`
- `complete(...)`
- differences between script mode and function mode

then do not force it into an exported-function test. Write it as a script-mode file and run it with `run_sandbox_script.*`.

### 4. Add validation for an `examples/` package or toolpkg

If you are adding a real package that should be loaded by the package system, the point is not only "can this JS run" but also "can this package be synced, refreshed, installed, and recognized".

Recommended approach:

- add or modify the `.ts`, `.js`, or manifest-based directory under `examples/`
- if it is a directory-based package, make sure it has `manifest.json` or `manifest.hjson`
- run `tools/sync_example_packages.py`
- then use `execute_js.bat` to call related debug tools, or validate inside the app

In other words:

- the runners test whether JS executes correctly inside the Android runtime
- `tools/sync_example_packages.py` tests whether the `examples/` package enters the real package-loading path correctly

## How To Use It With `tools/sync_example_packages.py`

### Scenario 1: You changed a normal JS script

In this case you usually do not need sync first.

Just run:

```cmd
tools\execute_js.bat path\to\script.js main @params.json
```

because you are validating script logic, not package loading.

### Scenario 2: You changed a package under `examples/`

Recommended order:

1. Edit `examples/...`
2. Run `tools/sync_example_packages.py`
3. Confirm the outputs were synced into `app/src/main/assets/packages`
4. If a device is connected, also validate hot reload
5. Then verify behavior inside the app or through debug tools

This fits:

- normal `.js` packages
- example packages that should appear in package lists
- changes to package description, manifest, or dependency structure

### Scenario 3: You changed a directory-based toolpkg

This is the case that most needs `tools/sync_example_packages.py`.

Recommended order:

1. Edit `examples/<toolpkg_dir>`
2. Run `tools/sync_example_packages.py --include <dir_name>`
3. Let it pack the directory into `.toolpkg`
4. Let it sync the generated artifact into assets and the device
5. Then verify installation through `operit_editor`'s `debug_install_toolpkg` or a related debug entry

This validates the full chain:

- whether the directory packs correctly
- whether the `.toolpkg` hot-reloads to device correctly
- whether debug install recognizes it
- whether the refreshed package actually enters the loading list

### Scenario 4: You changed a debug tool such as `operit_editor`

Recommended order:

1. Sync `operit_editor` itself with `tools/sync_example_packages.py`
2. Prepare the package or toolpkg you want it to install
3. Call the debug function through `execute_js.bat`
4. Inspect the structured result for success, failure, and related warnings

Example:

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include operit_editor --include sidebar_bing_action
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

This is good for validating:

- whether the debug install call shape is correct
- whether a folder packaged into `.toolpkg` installs correctly
- whether install-time warnings or errors are returned back to the caller

## Recommended Workflows

### 1. Direct Android-side JS debugging

Use `execute_js.bat` / `execute_js.sh` when you want to directly run and debug one JS file on the Android device.
This is the default choice for ordinary sandbox/package JS development.

```cmd
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

Typical use cases:

- test one exported function quickly
- reproduce a runtime bug on device
- verify parameter parsing / `complete(...)` / structured output
- debug a normal `.js` package before turning it into something larger

### 2. Real test suite / local-module run

Use `execute_js_dir.*` when the entry file depends on sibling modules or you are running material under `app/src/androidTest/js`.

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\script_mode_contract\return_object.js run "{}"
```

### 3. Direct sandbox snippet probe

Use `run_sandbox_script.*` for quick script-mode behavior checks.

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

## Quick Command List

### 1. Run one normal exported function

```cmd
tools\execute_js.bat examples\my_script.js main @params.json
```

### 2. Run one `androidTest/js` directory-based test entry

```cmd
tools\execute_js_dir.bat app\src\androidTest\js com\ai\assistance\operit\core\tools\javascript\bridge_contract\bridge_contract_runner.js run @params.json
```

### 3. Run one top-level script-mode probe

```cmd
tools\run_sandbox_script.bat temp\probe.js @params.json
```

### 4. Sync one `examples/` package into assets and device

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include sidebar_bing_action
```

### 5. Sync first, then call the debug installer tool

```cmd
d:\Code\prog\assistance\.venv\Scripts\python.exe d:\Code\prog\assistance\tools\sync_example_packages.py --include operit_editor --include sidebar_bing_action
tools\execute_js.bat examples\operit_editor.js debug_install_toolpkg @params.json
```

## JavaScript File Requirements

- Files must be valid JavaScript (TypeScript not supported)
- Functions must be exported (using `exports.functionName = functionName`)
- Functions must accept a params parameter (containing the passed parameters)
- Functions can either `return result` directly or call `complete(result)`

## Required Function Example

```javascript
function myFunction(params) {
    // Process parameters
    const name = params.name || "default";
    
    // Execute business logic
    const result = `Result: ${name}`;
    
    // Return result
    return {
        success: true,
        result: result
    };
}

// Export function
exports.myFunction = myFunction;
```

## Technical Details

1. The Shell/Batch script pushes the JavaScript file to a temporary directory on the device
2. `execute_js.bat` / `execute_js.sh` directly execute that pushed file inside the Android app's JS runtime
3. `execute_js_dir.*` bundles a directory entry first, so local modules can be resolved in one pushed artifact
4. Inline JSON parameters are first written to a temp JSON file, then pushed to the device as `params_file_path`
5. An ADB broadcast is sent to the application with the execution request
6. The application's BroadcastReceiver receives the request and uses `JsEngine` to execute the function or whole script
7. Execution results are written to a dedicated JSON file on the device
8. The executor waits for that file, prints it, and then cleans it up

## Troubleshooting

### Common Issues

1. **ADB command not found**  
   Ensure the Android SDK's platform-tools directory is in your PATH environment variable.

2. **No devices detected**
   - Make sure the device is connected
   - Ensure USB debugging is enabled
   - Check that the computer is authorized for USB debugging

3. **Broadcast sent but function not executed**
   - Check the logs for error messages
   - Confirm the application is running
   - Verify the receiver is correctly registered

4. **JavaScript errors**
   - Check for TypeScript-specific syntax (like type annotations)
   - Ensure functions are properly exported
   - Verify parameter formats are correct
   
## Required Permissions

This feature requires the following permissions:

- Read and write external storage
- Permission to receive broadcasts

## Security Considerations

- This feature should only be used for development and debugging purposes
- In production environments, this feature should be disabled or restricted to prevent security risks 
