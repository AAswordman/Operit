// 核对离线设计稿的状态与交互，不连接真实服务或验证浏览器排版。
const fs = require("node:fs");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const { JSDOM, VirtualConsole } = require("jsdom");
const source = fs.readFileSync(
  require("node:path").join(__dirname, "design.html"),
  "utf8",
);
const results = [];
function environment() {
  const errors = [];
  let clipboard = "";
  let rejectCopy = false;
  const console = new VirtualConsole();
  console.on("jsdomError", (error) => errors.push(error.message));
  const dom = new JSDOM(source, {
    url: "https://design.operit.invalid/operit-speech-services.html",
    runScripts: "dangerously",
    pretendToBeVisual: true,
    virtualConsole: console,
    beforeParse(w) {
      w.scrollTo = () => {};
      w.HTMLDialogElement.prototype.showModal = function () {
        this.setAttribute("open", "");
      };
      w.HTMLDialogElement.prototype.close = function () {
        this.removeAttribute("open");
      };
      Object.defineProperty(w.navigator, "clipboard", {
        value: {
          writeText: async (text) => {
            if (rejectCopy) throw new Error("复制权限拒绝");
            clipboard = text;
          },
        },
      });
    },
  });
  const w = dom.window,
    d = w.document;
  const q = (selector) => {
    const element = d.querySelector(selector);
    assert.ok(element, "缺少元素 " + selector);
    return element;
  };
  const click = (selector) => q(selector).click();
  const choose = (name) => {
    const item = [...d.querySelectorAll("#choices .choice")].find(
      (button) => button.querySelector("strong").textContent === name,
    );
    assert.ok(item, "缺少选项 " + name);
    item.click();
  };
  const input = (selector, value) => {
    const element = q(selector);
    element.value = String(value);
    element.dispatchEvent(new w.Event("input", { bubbles: true }));
    element.dispatchEvent(new w.Event("change", { bubbles: true }));
  };
  const submit = () =>
    q("#sheet-form").dispatchEvent(
      new w.Event("submit", { bubbles: true, cancelable: true }),
    );
  const read = (expression) => w.eval(expression);
  const tab = (name) => click('[data-tab="' + name + '"]');
  const engine = (name) => {
    click('[data-action="engine"]');
    choose(name);
  };
  return {
    dom,
    w,
    d,
    q,
    click,
    choose,
    input,
    submit,
    read,
    tab,
    engine,
    errors,
    getClipboard: () => clipboard,
    rejectCopy: () => {
      rejectCopy = true;
    },
  };
}
async function check(id, name, fn) {
  const e = environment();
  try {
    await fn(e);
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(e.errors, [], "未处理的页面错误");
    results.push({ id, name, result: "passed" });
  } catch (error) {
    results.push({ id, name, result: "failed", error: error.stack });
  } finally {
    e.dom.window.close();
  }
}
(async () => {
  new vm.Script(source.match(/<script>([\s\S]*?)<\/script>/)[1]);
  await check("H01", "默认三页结构与当前档案", (e) => {
    assert.equal(e.d.querySelectorAll('[role="tab"]').length, 3);
    assert.equal(e.q(".profile-name").textContent, "系统朗读");
    assert.ok(e.d.querySelector('[data-action="test-tts"]'));
    assert.ok(source.includes("repeat(3, minmax(0, 1fr))"));
  });
  await check("H02", "标签键盘切换与可访问状态", (e) => {
    e.q("#tab-tts").dispatchEvent(
      new e.w.KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }),
    );
    assert.equal(e.q("#tab-stt").getAttribute("aria-selected"), "true");
    assert.equal(e.q("#panel").getAttribute("aria-labelledby"), "tab-stt");
    e.q("#tab-stt").dispatchEvent(
      new e.w.KeyboardEvent("keydown", { key: "End", bubbles: true }),
    );
    assert.equal(e.read("state.tab"), "wake");
  });
  await check("H03", "深浅主题保留当前参数", (e) => {
    e.input("#slider-rate", 1.5);
    e.click('[data-action="theme"]');
    assert.equal(e.d.documentElement.dataset.theme, "light");
    assert.equal(e.q("#slider-rate").value, "1.5");
  });
  await check("H04", "九种朗读引擎与字段", (e) => {
    const expected = [
      "系统 TTS",
      "VITS / Piper",
      "OpenAI 兼容",
      "OpenAI Realtime",
      "硅基流动",
      "MiniMax",
      "MiMo",
      "豆包",
      "自定义 HTTP",
    ];
    e.click('[data-action="engine"]');
    assert.equal(e.d.querySelectorAll("#choices .choice").length, 9);
    e.click('[data-action="close-sheet"]');
    for (const name of expected) {
      e.engine(name);
      assert.ok(e.q('[data-action="engine"]').textContent.includes(name));
      if (name === "豆包")
        assert.ok(
          e
            .q("#field-config-model")
            .closest("label")
            .textContent.includes("App ID"),
        );
      if (name === "自定义 HTTP")
        assert.ok(e.d.querySelector('[data-fold="http"]'));
    }
  });
  await check("H05", "三种识别引擎与连接字段", (e) => {
    e.tab("stt");
    for (const name of ["Sherpa NCNN", "OpenAI 兼容", "Deepgram"]) {
      e.engine(name);
      assert.equal(
        Boolean(e.d.querySelector("#field-config-apiKey")),
        name !== "Sherpa NCNN",
      );
    }
  });
  await check("H06", "朗读与识别档案互相隔离", (e) => {
    e.input("#slider-rate", 1.5);
    e.tab("stt");
    e.click('[data-action="profiles"]');
    e.choose("在线转写");
    e.input("#field-config-model", "custom-stt");
    e.tab("tts");
    assert.equal(e.q("#slider-rate").value, "1.5");
    assert.equal(e.q(".profile-name").textContent, "系统朗读");
  });
  await check("H07", "新建档案名称校验与文字转义", (e) => {
    e.click('[data-action="profiles"]');
    e.click('[data-action="new-profile"]');
    e.submit();
    assert.ok(e.q("#form-error").textContent.includes("名称"));
    e.input("#profile-name", "系统朗读");
    e.submit();
    assert.ok(e.q("#form-error").textContent.includes("存在"));
    const name = '甲"/><img src=x onerror=alert(1)>';
    e.input("#profile-name", name);
    e.submit();
    assert.equal(e.q(".profile-name").textContent, name);
    assert.equal(e.d.querySelectorAll("img").length, 0);
  });
  await check("H08", "重命名与复制档案独立参数", (e) => {
    e.input("#slider-rate", 1.75);
    e.click('[data-action="profile-menu"]');
    e.choose("重命名");
    e.input("#profile-name", "我的朗读");
    e.submit();
    assert.equal(e.q(".profile-name").textContent, "我的朗读");
    e.click('[data-action="profile-menu"]');
    e.choose("复制为新档案");
    e.submit();
    assert.equal(e.q("#slider-rate").value, "1.75");
    e.input("#slider-rate", 0.5);
    e.click('[data-action="profiles"]');
    e.choose("我的朗读");
    assert.equal(e.q("#slider-rate").value, "1.75");
  });
  await check("H09", "删除取消与最后一个档案保护", (e) => {
    e.click('[data-action="profile-menu"]');
    e.choose("删除档案");
    e.click('[data-action="close-sheet"]');
    assert.equal(e.read("state.profiles.tts.length"), 2);
    e.click('[data-action="profile-menu"]');
    e.choose("删除档案");
    e.submit();
    assert.equal(e.read("state.profiles.tts.length"), 1);
    e.click('[data-action="profile-menu"]');
    e.choose("删除档案");
    assert.equal(e.read("state.profiles.tts.length"), 1);
    assert.ok(e.q("#toast").textContent.includes("至少保留"));
  });
  await check("H10", "系统音色搜索、手填与恢复默认", (e) => {
    e.click('[data-action="voices"]');
    e.input("[data-choice-search]", "English");
    assert.equal(
      e.d.querySelectorAll("#choices .choice:not([hidden])").length,
      1,
    );
    e.choose("English（示例）");
    assert.ok(e.q('[data-action="voices"]').textContent.includes("en-US"));
    e.click('[data-action="reset-voice"]');
    e.click('[data-action="voices"]');
    e.click('[data-action="manual-voice"]');
    const value = 'voice" onclick="bad';
    e.input("#manual-voice", value);
    e.submit();
    e.click('[data-action="voices"]');
    e.click('[data-action="manual-voice"]');
    assert.equal(e.q("#manual-voice").value, value);
  });
  await check("H11", "在线模型、接口与密钥显隐", (e) => {
    e.engine("OpenAI 兼容");
    e.input("#field-config-apiKey", "example-key");
    assert.equal(e.q("#field-config-apiKey").type, "password");
    e.click('[data-action="reveal-key"]');
    assert.equal(e.q("#field-config-apiKey").type, "text");
    e.click('[data-action="models"]');
    e.input("[data-choice-search]", "custom");
    e.choose("custom-model-example");
    assert.equal(e.q("#field-config-model").value, "custom-model-example");
    e.input("#field-config-endpoint", "https://example.test/speech");
    assert.equal(e.read("config().endpoint"), "https://example.test/speech");
  });
  await check("H12", "语速音调属于档案且跨引擎保留", (e) => {
    e.input("#slider-rate", 1.5);
    e.input("#slider-pitch", 0.75);
    e.engine("OpenAI 兼容");
    assert.equal(e.q("#slider-rate").value, "1.5");
    assert.equal(e.q("#slider-pitch").value, "0.75");
    assert.equal(typeof e.read("current().rate"), "number");
    assert.equal(e.q("#slider-rate").min, "0.5");
    assert.equal(e.q("#slider-rate").max, "2");
  });
  await check("H13", "清理规则添加、编辑、移除与正则校验", (e) => {
    e.click('[data-action="add-cleaner"]');
    e.input("#new-cleaner", "[");
    e.submit();
    assert.ok(e.q("#form-error").textContent.includes("无效"));
    e.input("#new-cleaner", "(?s)<tag>.*?</tag>");
    e.submit();
    assert.equal(e.read("current().cleaners.length"), 3);
    e.click('[data-action="edit-cleaner"][data-index="2"]');
    assert.equal(e.q("#new-cleaner").value, "(?s)<tag>.*?</tag>");
    e.input("#new-cleaner", "abc");
    e.submit();
    e.click('[data-action="remove-cleaner"][data-index="2"]');
    assert.equal(e.read("current().cleaners.length"), 2);
  });
  await check("H14", "HTTP 请求方法与非 JSON 请求模板", (e) => {
    e.engine("自定义 HTTP");
    e.input('[data-field="method"]', "GET");
    assert.equal(e.read("config().method"), "GET");
    e.input("#field-config-contentType", "text/plain");
    e.input("#field-config-body", "text={example}");
    assert.equal(e.read("config().body"), "text={example}");
  });
  await check("H15", "请求头与响应流水线 JSON 校验", (e) => {
    e.engine("自定义 HTTP");
    e.input("#field-config-headers", "[]");
    assert.equal(
      e.q("#field-config-headers").getAttribute("aria-invalid"),
      "true",
    );
    assert.equal(e.read("config().headers"), "{}");
    e.input("#field-config-headers", '{"X-Test":"demo"}');
    assert.equal(e.q("#json-error").textContent, "");
    e.input("#field-config-pipeline", "{}");
    assert.equal(
      e.q("#field-config-pipeline").getAttribute("aria-invalid"),
      "true",
    );
    e.input("#field-config-pipeline", "[]");
    assert.equal(
      e.q("#field-config-pipeline").getAttribute("aria-invalid"),
      "false",
    );
  });
  await check("H16", "本地语音包、说话人和选项", (e) => {
    e.engine("VITS / Piper");
    e.input("#field-config-packagePath", "/example/voice");
    e.input("#field-config-speaker", "2");
    assert.equal(e.read("config().speaker"), 2);
    e.input("#field-config-options", "[]");
    assert.equal(
      e.q("#field-config-options").getAttribute("aria-invalid"),
      "true",
    );
    e.input("#field-config-options", '{"noise_scale":"0.5"}');
    assert.equal(
      e.q("#field-config-options").getAttribute("aria-invalid"),
      "false",
    );
  });
  await check("H17", "试听开始、停止与空文本", (e) => {
    e.click('[data-action="test-tts"]');
    assert.ok(e.q(".test-body").classList.contains("playing"));
    e.click('[data-action="test-tts"]');
    assert.ok(!e.q(".test-body").classList.contains("playing"));
    e.input('[data-field="testText"]', " ");
    e.click('[data-action="test-tts"]');
    assert.ok(e.q("#toast").textContent.includes("先写"));
  });
  await check("H18", "识别开始、结束、结果与复制", async (e) => {
    e.tab("stt");
    assert.ok(e.q('[data-action="copy-result"]').disabled);
    e.click('[data-action="test-stt"]');
    assert.ok(e.q(".mic-zone").classList.contains("recording"));
    e.click('[data-action="test-stt"]');
    assert.ok(e.q(".result p").textContent.includes("今天的安排"));
    e.click('[data-action="copy-result"]');
    await Promise.resolve();
    assert.ok(e.getClipboard().includes("今天的安排"));
  });
  await check("H19", "复制拒绝给出可见提示", async (e) => {
    e.tab("stt");
    e.click('[data-action="test-stt"]');
    e.click('[data-action="test-stt"]');
    e.rejectCopy();
    e.click('[data-action="copy-result"]');
    await new Promise((resolve) => setImmediate(resolve));
    assert.ok(
      e.q("#toast").textContent.includes("未允许复制"),
      "实际提示：" +
        e.q("#toast").textContent +
        "；页面错误：" +
        JSON.stringify(e.errors),
    );
  });
  await check("H20", "识别权限提示不影响 TTS", (e) => {
    e.click('[data-action="demo-state"]');
    e.choose("未授予麦克风权限");
    e.click('[data-action="test-tts"]');
    assert.equal(e.d.querySelectorAll(".errorbox").length, 0);
    e.tab("stt");
    e.click('[data-action="test-stt"]');
    assert.ok(e.q(".errorbox").textContent.includes("权限"));
  });
  await check("H21", "服务不可用状态与切页停止测试", (e) => {
    e.tab("stt");
    e.click('[data-action="test-stt"]');
    e.tab("wake");
    assert.equal(e.read("demo.kind"), null);
    e.click('[data-action="demo-state"]');
    e.choose("服务不可用");
    e.tab("tts");
    e.click('[data-action="test-tts"]');
    assert.ok(e.q(".errorbox").textContent.includes("暂不可用"));
  });
  await check("H22", "唤醒沿用本地引擎说明", (e) => {
    e.tab("stt");
    e.engine("Deepgram");
    e.tab("wake");
    assert.ok(e.q("#panel").textContent.includes("Sherpa NCNN"));
    assert.ok(!e.q("#panel").textContent.includes("Deepgram"));
    e.click('[data-field="enabled"]');
    assert.equal(e.read("state.wake.enabled"), true);
  });
  await check("H23", "唤醒正则与超时边界", (e) => {
    e.tab("wake");
    e.click('[data-field="regex"]');
    e.input("#field-wake-phrase", "[");
    assert.ok(e.q("#wake-regex-error").textContent.includes("无效"));
    assert.equal(e.read("state.wake.phrase"), "小欧");
    e.input("#field-wake-phrase", "小欧|你好");
    assert.equal(e.q("#wake-regex-error").textContent, "");
    for (const value of [0, 601, 1.5, ""]) {
      e.input("#field-wake-timeout", value);
      assert.ok(e.q("#timeout-error").textContent);
    }
    for (const value of [1, 600]) {
      e.input("#field-wake-timeout", value);
      assert.equal(e.q("#timeout-error").textContent, "");
    }
  });
  await check("H24", "问候语和新对话分组依赖", (e) => {
    e.tab("wake");
    e.click('[data-field="greeting"]');
    assert.ok(e.q("#field-wake-greetingText").disabled);
    assert.ok(e.q("#field-wake-group").disabled);
    e.click('[data-field="newChat"]');
    assert.ok(!e.q("#field-wake-group").disabled);
    e.input("#field-wake-group", "语音对话");
    assert.equal(e.read("state.wake.group"), "语音对话");
  });
  await check("H25", "个性化模板录制与清除确认", (e) => {
    e.tab("wake");
    e.click('[data-mode="personal"]');
    for (let i = 0; i < 3; i++) e.click('[data-action="enroll"]');
    assert.equal(e.d.querySelectorAll(".sample.done").length, 3);
    e.click('[data-action="clear-samples"]');
    e.click('[data-action="close-sheet"]');
    assert.equal(e.read("state.wake.samples"), 3);
    e.click('[data-action="clear-samples"]');
    e.submit();
    assert.equal(e.read("state.wake.samples"), 0);
  });
  await check("H26", "附件总开关和关键词编辑", (e) => {
    e.tab("wake");
    e.click('[data-field="autoAttach"]');
    assert.ok(
      [...e.d.querySelectorAll(".attach-tile")].every((b) => b.disabled),
    );
    e.click('[data-field="autoAttach"]');
    e.click('[data-action="attachment"][data-index="3"]');
    e.input("#attach-keywords", "几点， 时间,几点");
    e.q("#attach-enabled").checked = false;
    e.submit();
    assert.equal(e.read("state.wake.attachments[3].keywords"), "几点,时间");
    assert.equal(
      e
        .q('[data-action="attachment"][data-index="3"]')
        .getAttribute("aria-pressed"),
      "false",
    );
  });
  await check("H27", "重置取消与确认保留主题", (e) => {
    e.click('[data-action="theme"]');
    e.input("#slider-rate", 1.5);
    e.click('[data-action="reset"]');
    e.click('[data-action="close-sheet"]');
    assert.equal(e.q("#slider-rate").value, "1.5");
    e.click('[data-action="reset"]');
    e.submit();
    assert.equal(e.q("#slider-rate").value, "1");
    assert.equal(e.d.documentElement.dataset.theme, "light");
  });
  await check("H28", "离线依赖、图标引用与动态文本转义", (e) => {
    assert.equal(
      e.d.querySelectorAll('script[src],link[rel="stylesheet"],img[src]')
        .length,
      0,
    );
    e.engine("自定义 HTTP");
    const raw = '"></textarea><img src=x onerror=alert(1)>';
    e.input("#field-config-endpoint", raw);
    e.click('[data-action="theme"]');
    assert.equal(e.q("#field-config-endpoint").value, raw);
    assert.equal(e.d.querySelectorAll("img").length, 0);
    for (const mode of ["tts", "stt", "wake"]) {
      e.tab(mode);
      for (const use of e.d.querySelectorAll("use"))
        assert.ok(e.d.getElementById(use.getAttribute("href").slice(1)));
    }
  });
  const failed = results.filter((item) => item.result === "failed");
  if (process.argv[2])
    fs.writeFileSync(
      process.argv[2],
      JSON.stringify(
        {
          environment:
            "Node.js + jsdom，dialog/scroll/clipboard 使用环境桩，不验证浏览器布局或真实语音",
          total: results.length,
          passed: results.length - failed.length,
          failed: failed.length,
          results,
        },
        null,
        2,
      ) + "\n",
    );
  for (const result of results)
    process.stdout.write(
      result.id +
        " " +
        result.result +
        " " +
        result.name +
        (result.error ? "\n" + result.error : "") +
        "\n",
    );
  if (failed.length) process.exitCode = 1;
})();
