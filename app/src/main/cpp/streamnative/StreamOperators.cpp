#include "StreamOperators.h"

#include <cstdint>
#include <deque>
#include <memory>

#include "plugins/StreamMarkdownPlugin.h"
#include "plugins/StreamXmlPlugin.h"

namespace streamnative {

namespace {

// Must match com.ai.assistance.operit.util.markdown.MarkdownProcessorType ordinals.
constexpr int MD_HEADER = 0;
constexpr int MD_BLOCK_QUOTE = 1;
constexpr int MD_CODE_BLOCK = 2;
constexpr int MD_ORDERED_LIST = 3;
constexpr int MD_UNORDERED_LIST = 4;
constexpr int MD_HORIZONTAL_RULE = 5;
constexpr int MD_BLOCK_LATEX = 6;
constexpr int MD_TABLE = 7;
constexpr int MD_XML_BLOCK = 8;
constexpr int MD_BOLD = 9;
constexpr int MD_ITALIC = 10;
constexpr int MD_INLINE_CODE = 11;
constexpr int MD_LINK = 12;
constexpr int MD_IMAGE = 13;
constexpr int MD_STRIKETHROUGH = 14;
constexpr int MD_UNDERLINE = 15;
constexpr int MD_INLINE_LATEX = 16;
constexpr int MD_PLAIN_TEXT = 17;
// Dedicated HTML break classification, matched before XML handling.
constexpr int MD_HTML_BREAK = 18;

// Segment type used only as a boundary marker between groups.
// Kotlin side must treat this as "close current group" and not map it to MarkdownProcessorType.
constexpr int SEG_BREAK = -1;

struct PluginEntry {
    std::unique_ptr<StreamPlugin> plugin;
    int tag;
};

inline void emitIndex(std::vector<Segment>& out, int tag, int index, int& runTag, int& runStart, int& runEnd) {
    if (runStart >= 0 && (runTag != tag || runEnd != index)) {
        out.push_back({runTag, runStart, runEnd});
        runStart = -1;
        runEnd = -1;
    }
    if (runStart < 0) {
        runTag = tag;
        runStart = index;
        runEnd = index + 1;
    } else {
        runEnd = index + 1;
    }
}

inline void flushRun(std::vector<Segment>& out, int& runTag, int& runStart, int& runEnd) {
    if (runStart >= 0) {
        out.push_back({runTag, runStart, runEnd});
    }
    runStart = -1;
    runEnd = -1;
}

inline void emitBreak(std::vector<Segment>& out, int pos, int& runTag, int& runStart, int& runEnd) {
    flushRun(out, runTag, runStart, runEnd);
    out.push_back({SEG_BREAK, pos, pos});
}

inline char16_t toAsciiLower(char16_t c) {
    if (c >= u'A' && c <= u'Z') {
        return static_cast<char16_t>(c - u'A' + u'a');
    }
    return c;
}

template <size_t N>
bool matchesCaseInsensitivePrefix(
        const std::vector<char16_t>& buffer,
        const char16_t (&pattern)[N]) {
    const size_t patternLen = N - 1;
    if (buffer.size() > patternLen) {
        return false;
    }

    for (size_t i = 0; i < buffer.size(); i++) {
        if (toAsciiLower(buffer[i]) != toAsciiLower(pattern[i])) {
            return false;
        }
    }
    return true;
}

template <size_t N>
bool matchesCaseInsensitiveFull(
        const std::vector<char16_t>& buffer,
        const char16_t (&pattern)[N]) {
    const size_t patternLen = N - 1;
    if (buffer.size() != patternLen) {
        return false;
    }

    for (size_t i = 0; i < patternLen; i++) {
        if (toAsciiLower(buffer[i]) != toAsciiLower(pattern[i])) {
            return false;
        }
    }
    return true;
}

inline bool isHtmlBreakPrefix(const std::vector<char16_t>& buffer) {
    return matchesCaseInsensitivePrefix(buffer, u"<br>") ||
           matchesCaseInsensitivePrefix(buffer, u"<br/>") ||
           matchesCaseInsensitivePrefix(buffer, u"<br />") ||
           matchesCaseInsensitivePrefix(buffer, u"<br >");
}

inline bool isHtmlBreakFullMatch(const std::vector<char16_t>& buffer) {
    return matchesCaseInsensitiveFull(buffer, u"<br>") ||
           matchesCaseInsensitiveFull(buffer, u"<br/>") ||
           matchesCaseInsensitiveFull(buffer, u"<br />") ||
           matchesCaseInsensitiveFull(buffer, u"<br >");
}

} // namespace

class MarkdownSession {
public:
    explicit MarkdownSession(std::vector<PluginEntry> plugins)
            : plugins_(std::move(plugins)) {
        for (auto& e : plugins_) {
            e.plugin->initPlugin();
        }
    }

    std::vector<Segment> push(const jchar* chars, int len) {
        std::vector<Segment> out;
        out.reserve(64);

        int runTag = 0;
        int runStart = -1;
        int runEnd = -1;

        auto processOne = [&](char16_t c, bool atStartOfLine, int forcedGlobalIndex) {
            const int globalIndex = (forcedGlobalIndex >= 0) ? forcedGlobalIndex : globalOffset_;
            if (forcedGlobalIndex < 0) {
                globalOffset_ += 1;
            }

            // WAITFOR handling deferred across pushes
            if (waitforActive_) {
                // Feed next char into active plugin to decide.
                const bool nextShouldEmit = activePlugin_->processChar(c, waitforAtStartOfLine_);

                if (activePlugin_->state() == PluginState::PROCESSING) {
                    // Confirmed: emit pending char(s) and current char.
                    for (const auto& pending : waitforPending_) {
                        if (pending.shouldEmit) {
                            emitIndex(out, activeTag_, pending.globalIndex, runTag, runStart, runEnd);
                        }
                    }
                    waitforPending_.clear();
                    waitforActive_ = false;
                    if (nextShouldEmit) {
                        emitIndex(out, activeTag_, globalIndex, runTag, runStart, runEnd);
                    }
                    // Note: do not change atStartOfLine here; caller maintains.
                    return;
                }

                // Rejected: return only previously-emitted chars as plain, current char must be reprocessed as idle.
                for (const auto& pending : waitforPending_) {
                    if (pending.shouldEmit) {
                        emitIndex(out, MD_PLAIN_TEXT, pending.globalIndex, runTag, runStart, runEnd);
                    }
                }
                waitforPending_.clear();
                waitforActive_ = false;

                // Close plugin
                emitBreak(out, globalIndex, runTag, runStart, runEnd);
                activePlugin_ = nullptr;
                activeTag_ = MD_PLAIN_TEXT;
                activeIndex_ = -1;

                // Reset all plugins after a failed waitfor
                for (auto& e : plugins_) {
                    e.plugin->reset();
                }

                // Reprocess current char as idle (do NOT advance global offset again)
                pendingChars_.push_front({c, globalIndex});
                return;
            }

            if (activePlugin_ != nullptr) {
                const bool shouldEmit = activePlugin_->processChar(c, atStartOfLine);
                if (activePlugin_->state() == PluginState::WAITFOR) {
                    // Defer emission decision until next char arrives.
                    waitforActive_ = true;
                    waitforAtStartOfLine_ = (c == u'\n');
                    waitforPending_.push_back({globalIndex, shouldEmit});
                    return;
                }
                if (shouldEmit) {
                    emitIndex(out, activeTag_, globalIndex, runTag, runStart, runEnd);
                }
                if (activePlugin_->state() != PluginState::PROCESSING) {
                    // 行内代码保护仍属于同一个正文段，不额外切开段落。
                    if (activeTag_ != MD_PLAIN_TEXT) {
                        emitBreak(out, globalIndex + 1, runTag, runStart, runEnd);
                    }
                    activePlugin_ = nullptr;
                    activeTag_ = MD_PLAIN_TEXT;
                    activeIndex_ = -1;
                }
                return;
            }

            // Evaluation mode
            if (evalStartGlobal_ < 0) {
                evalStartGlobal_ = globalIndex;
            }

            evaluationBuffer_.push_back(c);
            uint32_t emitMask = 0;

            int successful = -1;
            for (int pi = 0; pi < static_cast<int>(plugins_.size()); pi++) {
                const bool se = plugins_[pi].plugin->processChar(c, atStartOfLine);
                if (se) {
                    emitMask |= (1u << static_cast<uint32_t>(pi));
                }
            }
            evaluationEmitMask_.push_back(emitMask);

            for (int pi = 0; pi < static_cast<int>(plugins_.size()); pi++) {
                if (plugins_[pi].plugin->state() == PluginState::PROCESSING) {
                    successful = pi;
                    break;
                }
            }

            if (isHtmlBreakFullMatch(evaluationBuffer_)) {
                for (int bi = 0; bi < static_cast<int>(evaluationBuffer_.size()); bi++) {
                    emitIndex(out, MD_HTML_BREAK, evalStartGlobal_ + bi, runTag, runStart, runEnd);
                }
                evaluationBuffer_.clear();
                evaluationEmitMask_.clear();
                evalStartGlobal_ = -1;
                for (auto& e : plugins_) {
                    e.plugin->reset();
                }
                return;
            }

            if (successful != -1) {
                // Flush buffered as plugin group
                activeIndex_ = successful;
                activePlugin_ = plugins_[successful].plugin.get();
                activeTag_ = plugins_[successful].tag;

                // Ensure a new group boundary even if previous group had the same tag.
                flushRun(out, runTag, runStart, runEnd);

                for (int bi = 0; bi < static_cast<int>(evaluationBuffer_.size()); bi++) {
                    const uint32_t mask = evaluationEmitMask_[static_cast<size_t>(bi)];
                    if ((mask & (1u << static_cast<uint32_t>(successful))) != 0u) {
                        emitIndex(out, activeTag_, evalStartGlobal_ + bi, runTag, runStart, runEnd);
                    }
                }

                evaluationBuffer_.clear();
                evaluationEmitMask_.clear();
                evalStartGlobal_ = -1;

                // Reset other plugins
                for (int pi = 0; pi < static_cast<int>(plugins_.size()); pi++) {
                    if (pi != successful) {
                        plugins_[pi].plugin->reset();
                    }
                }

                return;
            }

            if (isHtmlBreakPrefix(evaluationBuffer_)) {
                return;
            }

            // If no plugin is trying, flush buffer as plain text
            bool anyTrying = false;
            for (auto& e : plugins_) {
                if (e.plugin->state() == PluginState::TRYING) {
                    anyTrying = true;
                    break;
                }
            }

            if (!anyTrying) {
                for (int bi = 0; bi < static_cast<int>(evaluationBuffer_.size()); bi++) {
                    emitIndex(out, MD_PLAIN_TEXT, evalStartGlobal_ + bi, runTag, runStart, runEnd);
                }
                evaluationBuffer_.clear();
                evaluationEmitMask_.clear();
                evalStartGlobal_ = -1;
                for (auto& e : plugins_) {
                    e.plugin->reset();
                }
            }
        };

        bool atStartOfLine = atStartOfLine_;

        int i = 0;
        while (i < len || !pendingChars_.empty()) {
            char16_t c;
            int forcedIndex = -1;

            if (!pendingChars_.empty()) {
                PendingChar pc = pendingChars_.front();
                pendingChars_.pop_front();
                c = pc.c;
                forcedIndex = pc.globalIndex;
            } else {
                c = static_cast<char16_t>(chars[i]);
                i += 1;
            }

            const bool sol = atStartOfLine;
            atStartOfLine = (c == u'\n');
            processOne(c, sol, forcedIndex);
        }

        atStartOfLine_ = atStartOfLine;

        flushRun(out, runTag, runStart, runEnd);

        return out;
    }

private:
    struct WaitforPending {
        int globalIndex;
        bool shouldEmit;
    };

    struct PendingChar {
        char16_t c;
        int globalIndex;
    };

    std::vector<PluginEntry> plugins_;

    int globalOffset_ = 0;
    bool atStartOfLine_ = true;

    // Active plugin
    StreamPlugin* activePlugin_ = nullptr;
    int activeTag_ = MD_PLAIN_TEXT;
    int activeIndex_ = -1;

    // Evaluation buffer
    int evalStartGlobal_ = -1;
    std::vector<char16_t> evaluationBuffer_;
    std::vector<uint32_t> evaluationEmitMask_;

    // WAITFOR support
    bool waitforActive_ = false;
    bool waitforAtStartOfLine_ = false;
    std::vector<WaitforPending> waitforPending_;
    std::deque<PendingChar> pendingChars_;
};

MarkdownSession* createMarkdownBlockSession() {
    std::vector<PluginEntry> plugins;
    plugins.reserve(16);
    // 块级先保护代码范围，保留反引号供下一阶段生成行内代码节点。
    plugins.push_back({std::make_unique<StreamMarkdownHeaderPlugin>(true), MD_HEADER});
    plugins.push_back({std::make_unique<StreamMarkdownFencedCodeBlockPlugin>(true), MD_CODE_BLOCK});
    plugins.push_back({std::make_unique<StreamMarkdownBlockQuotePlugin>(false), MD_BLOCK_QUOTE});
    plugins.push_back({std::make_unique<StreamMarkdownOrderedListPlugin>(true), MD_ORDERED_LIST});
    plugins.push_back({std::make_unique<StreamMarkdownUnorderedListPlugin>(false), MD_UNORDERED_LIST});
    plugins.push_back({std::make_unique<StreamMarkdownHorizontalRulePlugin>(true), MD_HORIZONTAL_RULE});
    plugins.push_back({std::make_unique<StreamMarkdownBlockLaTeXPlugin>(false), MD_BLOCK_LATEX});
    // Keep delimiters for \[...\] to avoid swallowing '\' in failed end-matcher branches.
    // Delimiters are removed later by extractLatexContent().
    plugins.push_back({std::make_unique<StreamMarkdownBlockBracketLaTeXPlugin>(true), MD_BLOCK_LATEX});
    plugins.push_back({std::make_unique<StreamMarkdownTablePlugin>(true), MD_TABLE});
    plugins.push_back({std::make_unique<StreamMarkdownImagePlugin>(true), MD_IMAGE});
    plugins.push_back({std::make_unique<StreamMarkdownInlineCodePlugin>(true, true), MD_PLAIN_TEXT});
    plugins.push_back({std::make_unique<StreamXmlPlugin>(true), MD_XML_BLOCK});
    return new MarkdownSession(std::move(plugins));
}

MarkdownSession* createMarkdownInlineSession() {
    std::vector<PluginEntry> plugins;
    plugins.reserve(16);
    // Order must match NestedMarkdownProcessor.getInlinePlugins()
    plugins.push_back({std::make_unique<StreamMarkdownBoldPlugin>(false), MD_BOLD});
    plugins.push_back({std::make_unique<StreamMarkdownItalicPlugin>(false), MD_ITALIC});
    plugins.push_back({std::make_unique<StreamMarkdownInlineCodePlugin>(false), MD_INLINE_CODE});
    plugins.push_back({std::make_unique<StreamMarkdownLinkPlugin>(), MD_LINK});
    plugins.push_back({std::make_unique<StreamMarkdownStrikethroughPlugin>(false), MD_STRIKETHROUGH});
    plugins.push_back({std::make_unique<StreamMarkdownUnderlinePlugin>(true), MD_UNDERLINE});
    plugins.push_back({std::make_unique<StreamMarkdownInlineLaTeXPlugin>(false), MD_INLINE_LATEX});
    // Keep delimiters for \(...\) to avoid swallowing '\' in failed end-matcher branches.
    // Delimiters are removed later by extractLatexContent().
    plugins.push_back({std::make_unique<StreamMarkdownInlineParenLaTeXPlugin>(true), MD_INLINE_LATEX});
    return new MarkdownSession(std::move(plugins));
}

void destroyMarkdownSession(MarkdownSession* session) {
    delete session;
}

std::vector<Segment> markdownSessionPush(MarkdownSession* session, const jchar* chars, int len) {
    if (session == nullptr || chars == nullptr || len <= 0) {
        return {};
    }
    return session->push(chars, len);
}

std::vector<Segment> splitByXml(const jchar* chars, int len) {
    std::vector<Segment> segments;
    segments.reserve(32);

    StreamXmlPlugin xmlPlugin(true);
    xmlPlugin.initPlugin();

    StreamPlugin* activePlugin = nullptr;
    int activeStart = -1;
    int defaultStart = 0;

    int evalStart = -1;
    bool atStartOfLine = true;

    auto flushDefaultIfNeeded = [&](int endExclusive) {
        if (defaultStart < endExclusive) {
            segments.push_back({0, defaultStart, endExclusive});
        }
        defaultStart = endExclusive;
    };

    auto openActive = [&](int start) {
        activePlugin = &xmlPlugin;
        activeStart = start;
    };

    auto closeActive = [&](int endExclusive) {
        if (activePlugin != nullptr && activeStart >= 0 && activeStart < endExclusive) {
            segments.push_back({1, activeStart, endExclusive});
        }
        activePlugin = nullptr;
        activeStart = -1;
    };

    for (int i = 0; i < len; i++) {
        const jchar c = chars[i];
        const bool isAtStartForCurrent = atStartOfLine;
        atStartOfLine = (c == '\n');

        if (activePlugin != nullptr) {
            (void)activePlugin->processChar(static_cast<char16_t>(c), isAtStartForCurrent);
            if (activePlugin->state() != PluginState::PROCESSING) {
                closeActive(i + 1);
                defaultStart = i + 1;
            }
            continue;
        }

        if (evalStart == -1) {
            evalStart = i;
        }

        (void)xmlPlugin.processChar(static_cast<char16_t>(c), isAtStartForCurrent);

        if (xmlPlugin.state() == PluginState::PROCESSING) {
            flushDefaultIfNeeded(evalStart);
            openActive(evalStart);
            evalStart = -1;
        } else if (xmlPlugin.state() != PluginState::TRYING) {
            evalStart = -1;
        }
    }

    if (activePlugin != nullptr) {
        closeActive(len);
        defaultStart = len;
    }

    flushDefaultIfNeeded(len);
    return segments;
}

} // namespace streamnative
