const SandboxPackageDevInstallerState = {
  logs: []
};

const SandboxPackageDevInstaller = (function () {
  const ENVIRONMENT = "android";
  const SKILL_NAME = "SandboxPackage_DEV";
  const SKILL_ROOT = `/sdcard/Download/Operit/skills/${SKILL_NAME}`;
  const REFERENCES_DIR = `${SKILL_ROOT}/references`;
  const TYPES_DIR = `${SKILL_ROOT}/types`;
  const DOCS_DIR = `${REFERENCES_DIR}/toolpkg_developer_documentation`;
  const SCRIPTS_DIR = `${SKILL_ROOT}/scripts`;
  const EXAMPLES_DIR = `${SKILL_ROOT}/examples`;
  const EXAMPLE_PACKAGES_DIR = `${EXAMPLES_DIR}/packages`;
  const BUILTIN_PACKAGES_ASSET_DIR = "packages";
  const CDN_BASE = "https://cdn.jsdelivr.net/gh/AAswordman/Operit@main";
  const DOCS_CDN_BASE = "https://cdn.jsdelivr.net/gh/3316891527/OperitAI-Toolpkg-Dev-Docs@2cd7f35d842b4d121592ec406de946b85f0ecfd0";
  const MAX_DOWNLOAD_CONCURRENCY = 8;
  const TYPE_FILES = [
    "android.d.ts",
    "chat.d.ts",
    "compose-dsl.d.ts",
    "compose-dsl.material3.generated.d.ts",
    "core.d.ts",
    "cryptojs.d.ts",
    "ffmpeg.d.ts",
    "files.d.ts",
    "index.d.ts",
    "java-bridge.d.ts",
    "jimp.d.ts",
    "memory.d.ts",
    "network.d.ts",
    "okhttp.d.ts",
    "pako.d.ts",
    "quickjs-runtime.d.ts",
    "results.d.ts",
    "software_settings.d.ts",
    "system.d.ts",
    "tasker.d.ts",
    "tool-types.d.ts",
    "toolpkg.d.ts",
    "ui.d.ts",
    "workflow.d.ts"
  ];
  const DOC_DIRECTORIES = [
    "01_getting_started",
    "02_package_model",
    "03_runtime",
    "04_modules",
    "05_hooks",
    "06_ui_and_compose",
    "07_types_and_libraries",
    "08_examples",
    "09_compatibility"
  ];
  const DOC_FILES = [
    "01_getting_started/quick_start.md",
    "02_package_model/manifest.md",
    "02_package_model/package_format.md",
    "02_package_model/package_structure.md",
    "03_runtime/global_api.md",
    "03_runtime/quickjs_runtime.md",
    "03_runtime/registry.md",
    "04_modules/android.md",
    "04_modules/chat.md",
    "04_modules/core.md",
    "04_modules/cryptojs.md",
    "04_modules/ffmpeg.md",
    "04_modules/files.md",
    "04_modules/index.md",
    "04_modules/jimp.md",
    "04_modules/memory.md",
    "04_modules/network.md",
    "04_modules/okhttp.md",
    "04_modules/results.md",
    "04_modules/software_settings.md",
    "04_modules/system.md",
    "04_modules/tasker.md",
    "04_modules/tool-types.md",
    "04_modules/toolpkg.md",
    "04_modules/ui.md",
    "04_modules/workflow.md",
    "05_hooks/ai_provider.md",
    "05_hooks/chat_input.md",
    "05_hooks/index.md",
    "05_hooks/lifecycle_and_chat.md",
    "05_hooks/message_processing.md",
    "05_hooks/prompt_pipeline.md",
    "05_hooks/render_and_toggle.md",
    "05_hooks/tool_lifecycle.md",
    "06_ui_and_compose/compose_dsl.md",
    "06_ui_and_compose/index.md",
    "06_ui_and_compose/material_icons.md",
    "06_ui_and_compose/material3_components.md",
    "07_types_and_libraries/index.md",
    "07_types_and_libraries/java_bridge.md",
    "07_types_and_libraries/libraries.md",
    "08_examples/index.md",
    "09_compatibility/api_versions.md",
    "09_compatibility/coverage.md",
    "index.md"
  ];

  const LEGACY_DOCUMENT_FILES = [
    "SCRIPT_DEV_GUIDE.md",
    "TOOLPKG_FORMAT_GUIDE.md"
  ];

  const DOWNLOADS = [
    {
      url: `${CDN_BASE}/docs/SCRIPT_DEV_SKILL.md`,
      destination: `${SKILL_ROOT}/SKILL.md`
    }
  ].concat(
    TYPE_FILES.map((fileName) => ({
      url: `${CDN_BASE}/examples/types/${fileName}`,
      destination: `${TYPES_DIR}/${fileName}`
    })),
    DOC_FILES.map((relativePath) => ({
      url: `${DOCS_CDN_BASE}/${relativePath}`,
      destination: `${DOCS_DIR}/${relativePath}`
    }))
  );

  function logStep(message) {
    SandboxPackageDevInstallerState.logs.push(message);
    console.log(message);
  }

  async function makeDirectory(path) {
    return await Tools.Files.mkdir(path, true, ENVIRONMENT);
  }

  async function downloadFileAsync(url, destination) {
    return await Tools.Files.download(url, destination, ENVIRONMENT);
  }

  async function downloadAllFiles() {
    let nextIndex = 0;

    async function worker() {
      while (nextIndex < DOWNLOADS.length) {
        const item = DOWNLOADS[nextIndex];
        nextIndex += 1;
        logStep(`Downloading -> ${item.destination}`);
        await downloadFileAsync(item.url, item.destination);
      }
    }

    const workerCount = Math.min(MAX_DOWNLOAD_CONCURRENCY, DOWNLOADS.length);
    const workers = [];
    for (let index = 0; index < workerCount; index += 1) {
      workers.push(worker());
    }
    await Promise.all(workers);
  }

  async function removeLegacyDocuments() {
    for (const fileName of LEGACY_DOCUMENT_FILES) {
      const destination = `${REFERENCES_DIR}/${fileName}`;
      logStep(`Removing legacy documentation -> ${destination}`);
      const result = await Tools.Files.deleteFile(destination, false, ENVIRONMENT);
      if (result && result.success === false) {
        logStep(`Legacy documentation cleanup skipped -> ${destination}`);
      }
    }
  }

  function collectRelativeFiles(directory, relativePrefix, collectedFiles) {
    const children = directory.listFiles();
    if (!children) {
      return;
    }

    for (let index = 0; index < children.length; index += 1) {
      const child = children[index];
      const relativePath = relativePrefix
        ? `${relativePrefix}/${String(child.getName())}`
        : String(child.getName());

      if (child.isDirectory()) {
        collectRelativeFiles(child, relativePath, collectedFiles);
        continue;
      }

      collectedFiles.push(relativePath);
    }
  }

  function syncBuiltInPackageExamples() {
    const File = Java.type("java.io.File");
    const AssetCopyUtils = Java.type("com.ai.assistance.operit.util.AssetCopyUtils");
    const context = Java.getApplicationContext();
    const outputDir = new File(EXAMPLE_PACKAGES_DIR);
    const copiedFiles = [];

    AssetCopyUtils.INSTANCE.copyAssetDirRecursive(
      context,
      BUILTIN_PACKAGES_ASSET_DIR,
      outputDir,
      true
    );

    collectRelativeFiles(outputDir, "", copiedFiles);
    copiedFiles.sort();
    return copiedFiles;
  }

  async function run() {
    logStep(`Preparing skill root -> ${SKILL_ROOT}`);
    await makeDirectory("/sdcard/Download/Operit/skills");
    await makeDirectory(SKILL_ROOT);
    await makeDirectory(REFERENCES_DIR);
    await makeDirectory(TYPES_DIR);
    await makeDirectory(DOCS_DIR);
    for (const directory of DOC_DIRECTORIES) {
      await makeDirectory(`${DOCS_DIR}/${directory}`);
    }
    await makeDirectory(SCRIPTS_DIR);
    await makeDirectory(EXAMPLES_DIR);
    await downloadAllFiles();
    await removeLegacyDocuments();

    logStep(`Syncing built-in package examples -> ${EXAMPLE_PACKAGES_DIR}`);
    const copiedExampleFiles = syncBuiltInPackageExamples();
    logStep(`Built-in package examples synced -> ${copiedExampleFiles.length} files`);

    return {
      success: true,
      message: `${SKILL_NAME} installed or updated successfully.`,
      data: {
        skill_name: SKILL_NAME,
        skill_root: SKILL_ROOT,
        references_dir: REFERENCES_DIR,
        types_dir: TYPES_DIR,
        docs_dir: DOCS_DIR,
        scripts_dir: SCRIPTS_DIR,
        examples_dir: EXAMPLES_DIR,
        examples_packages_dir: EXAMPLE_PACKAGES_DIR,
        downloaded_count: DOWNLOADS.length,
        type_count: TYPE_FILES.length,
        documentation_count: DOC_FILES.length,
        builtin_example_count: copiedExampleFiles.length,
        builtin_example_files: copiedExampleFiles,
        logs: SandboxPackageDevInstallerState.logs
      }
    };
  }

  return {
    run
  };
})();

SandboxPackageDevInstaller.run()
  .then((result) => {
    complete(result);
  })
  .catch((error) => {
    complete({
      success: false,
      message: String(error && error.message ? error.message : error),
      data: {
        skill_name: "SandboxPackage_DEV",
        logs: SandboxPackageDevInstallerState.logs
      }
    });
  });
