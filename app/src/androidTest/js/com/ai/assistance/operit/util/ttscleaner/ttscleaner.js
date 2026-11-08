'use strict';

const { assert, assertEq, test, runTests } = require('../../../../../../lib/harness');

function getTtsCleanerInstance() {
  return Java.type('com.ai.assistance.operit.core.speech.text.TtsCleaner').INSTANCE;
}

function getWaifuMessageProcessorInstance() {
  return Java.type('com.ai.assistance.operit.util.WaifuMessageProcessor').INSTANCE;
}

function makeJavaStringList(items) {
  return items.map((item) => String(item));
}

function cleanForSpeech(input, regexs = []) {
  const cleaner = getTtsCleanerInstance();
  const processor = getWaifuMessageProcessorInstance();
  const cleanedText = cleaner.clean(String(input), makeJavaStringList(regexs));
  return String(processor.cleanContentForWaifu(String(cleanedText)));
}

exports.run = async function run() {
  const cleaner = getTtsCleanerInstance();

  const tests = [
    test('single: blank pattern returns original', () => {
      const out = cleaner.clean('abc', '');
      assertEq(String(out), 'abc');
    }),
    test('single: invalid pattern returns original', () => {
      const out = cleaner.clean('abc', '(');
      assertEq(String(out), 'abc');
    }),
    test('single: removes bracket content (english)', () => {
      const out = cleaner.clean('a(b)c', '\\([^)]+\\)');
      assertEq(String(out), 'ac');
    }),
    test('list: empty list returns original', () => {
      const out = cleaner.clean('abc', makeJavaStringList([]));
      assertEq(String(out), 'abc');
    }),
    test('list: removes english and chinese parentheses content', () => {
      const patterns = makeJavaStringList(['\\([^)]+\\)', '（[^）]+）']);
      const out = cleaner.clean('a(b)c（d）e', patterns);
      assertEq(String(out), 'ace');
    }),
    test('list: skips invalid pattern but still applies valid ones', () => {
      const patterns = makeJavaStringList(['(', '\\([^)]+\\)']);
      const out = cleaner.clean('a(b)c', patterns);
      assertEq(String(out), 'ac');
    }),
    test('list: no match is stable', () => {
      const patterns = makeJavaStringList(['\\([^)]+\\)']);
      const out = cleaner.clean('abcdef', patterns);
      assertEq(String(out), 'abcdef');
    }),
    test('list: supports multiple removals', () => {
      const patterns = makeJavaStringList(['\\([^)]+\\)']);
      const out = cleaner.clean('a(b)c(d)e', patterns);
      assertEq(String(out), 'ace');
    }),
    test('list: preserves newlines if pattern does not include them', () => {
      const patterns = makeJavaStringList(['\\([^)]+\\)']);
      const out = cleaner.clean('a(b)\\n(c)', patterns);
      assertEq(String(out), 'a\\n');
    }),
    test('speech pipeline: removes closed think block and keeps answer', () => {
      const out = cleanForSpeech('<think>draft</think>final answer');
      assertEq(out, 'final answer');
    }),
    test('speech pipeline: removes closed thinking block and keeps answer', () => {
      const out = cleanForSpeech('<thinking>draft</thinking>final answer');
      assertEq(out, 'final answer');
    }),
    test('speech pipeline: removes unclosed think block to end', () => {
      const out = cleanForSpeech('answer<think>hidden forever');
      assertEq(out, 'answer');
    }),
    test('speech pipeline: removes unclosed think at start', () => {
      const out = cleanForSpeech('<think>hidden forever');
      assertEq(out, '');
    }),
    test('speech pipeline: removes search block and keeps spoken answer', () => {
      const out = cleanForSpeech('<search>source dump</search>spoken answer');
      assertEq(out, 'spoken answer');
    }),
    test('speech pipeline: removes multiline think block', () => {
      const out = cleanForSpeech('hello<think>line1\\nline2\\nline3</think>world');
      assertEq(out, 'helloworld');
    }),
    test('speech pipeline: strips think before markdown cleanup', () => {
      const out = cleanForSpeech('<think>ignore</think>**Hello** `world`');
      assertEq(out, 'Hello world');
    }),
    test('speech pipeline: applies regex cleaner before think cleanup', () => {
      const out = cleanForSpeech('A (aside) <think>hidden</think> B', ['\\([^)]+\\)']);
      assertEq(out, 'A B');
    }),
    test('speech pipeline: keeps visible text around multiple think/search blocks', () => {
      const out = cleanForSpeech('A<think>1</think>B<search>2</search>C<thinking>3</thinking>D');
      assertEq(out, 'ABCD');
    }),
    test('speech pipeline: nested think currently leaves trailing tail text', () => {
      const out = cleanForSpeech('A<think>outer<think>inner</think>tail</think>B');
      assertEq(out, 'AtailB');
    }),
    test('speech pipeline: removes think block containing search block', () => {
      const out = cleanForSpeech('A<think>draft<search>src</search>end</think>B');
      assertEq(out, 'AB');
    }),
    test('speech pipeline: unclosed search removes to end', () => {
      const out = cleanForSpeech('A<search>src dump');
      assertEq(out, 'A');
    }),
    test('speech pipeline: whitespace collapses after think removal', () => {
      const out = cleanForSpeech('A  <think>draft</think>   B');
      assertEq(out, 'A B');
    }),
    test('speech pipeline: chinese text survives around think blocks', () => {
      const out = cleanForSpeech('你好<think>内部推理</think>世界');
      assertEq(out, '你好世界');
    }),
    test('speech pipeline: emoji survives around think blocks', () => {
      const out = cleanForSpeech('🙂<think>draft</think>🙃');
      assertEq(out, '🙂🙃');
    }),
    test('speech pipeline: uppercase THINK currently leaks inner text', () => {
      const out = cleanForSpeech('A<THINK>secret</THINK>B');
      assertEq(out, 'AsecretB');
    }),
    test('speech pipeline: think tag with attributes currently leaks inner text', () => {
      const out = cleanForSpeech('A<think type="hidden">secret</think>B');
      assertEq(out, 'AsecretB');
    }),
    test('speech pipeline: uppercase SEARCH currently leaks inner text', () => {
      const out = cleanForSpeech('A<SEARCH>source</SEARCH>B');
      assertEq(out, 'AsourceB');
    }),
    test('speech pipeline: malformed closing think still removes to end', () => {
      const out = cleanForSpeech('A<think>draft</thinking>B');
      assertEq(out, 'AB');
    }),
    test('speech pipeline: empty think block is removed cleanly', () => {
      const out = cleanForSpeech('A<think></think>B');
      assertEq(out, 'AB');
    }),
    test('speech pipeline: multiple consecutive think blocks all disappear', () => {
      const out = cleanForSpeech('A<think>1</think><think>2</think><thinking>3</thinking>B');
      assertEq(out, 'AB');
    }),
    test('speech pipeline: markdown code fences around think are flattened after removal', () => {
      const out = cleanForSpeech('```txt\n<think>draft</think>\n```\nanswer');
      assertEq(out, 'answer');
    }),
  ];

  const result = await runTests(tests);
  assert(result.passed + result.failed > 0, 'no tests executed');
  return result;
};
