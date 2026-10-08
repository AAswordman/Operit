#include "StreamXmlPlugin.h"

namespace streamnative {

namespace {

bool isAttributeSpace(char16_t c) {
    return c == u' ' || c == u'\t' || c == u'\r' || c == u'\n';
}

std::u16string lowerAscii(std::u16string value) {
    for (auto& c : value) {
        if (c >= u'A' && c <= u'Z') c += u'a' - u'A';
    }
    return value;
}

// 与 ChatMarkupRegex 的工具标签范围一致，仅工具标签可以紧接普通正文。
bool isToolLikeTagName(const std::u16string& original) {
    const auto name = lowerAscii(original);
    if (name == u"tool" || name == u"tool_result") return true;
    std::u16string suffix;
    if (name.rfind(u"tool_result_", 0) == 0) {
        suffix = name.substr(12);
    } else if (name.rfind(u"tool_", 0) == 0) {
        suffix = name.substr(5);
        if (suffix == u"result" || suffix.rfind(u"result_", 0) == 0) return false;
    } else {
        return false;
    }
    if (suffix.empty()) return false;
    for (const char16_t c : suffix) {
        if (!((c >= u'a' && c <= u'z') || (c >= u'0' && c <= u'9') || c == u'_')) return false;
    }
    return true;
}

// 按引号边界读取属性，避免 data-token 或其他属性值中的 token 触发思考闭合。
bool readThinkingToken(const std::u16string& attributes, std::u16string& token) {
    size_t cursor = 0;
    while (cursor < attributes.size()) {
        while (cursor < attributes.size() && isAttributeSpace(attributes[cursor])) ++cursor;
        const size_t nameStart = cursor;
        while (cursor < attributes.size() && !isAttributeSpace(attributes[cursor]) &&
               attributes[cursor] != u'=') ++cursor;
        const auto name = lowerAscii(attributes.substr(nameStart, cursor - nameStart));
        while (cursor < attributes.size() && isAttributeSpace(attributes[cursor])) ++cursor;
        if (cursor >= attributes.size()) break;
        if (attributes[cursor] != u'=') {
            ++cursor;
            continue;
        }
        ++cursor;
        while (cursor < attributes.size() && isAttributeSpace(attributes[cursor])) ++cursor;
        if (cursor >= attributes.size()) break;
        const char16_t quote = attributes[cursor++];
        if (quote != u'\"' && quote != u'\'') {
            while (cursor < attributes.size() && !isAttributeSpace(attributes[cursor])) ++cursor;
            continue;
        }
        const size_t valueStart = cursor;
        const size_t valueEnd = attributes.find(quote, cursor);
        if (valueEnd == std::u16string::npos) break;
        cursor = valueEnd + 1;
        if (name == u"token") {
            token = attributes.substr(valueStart, valueEnd - valueStart);
            return true;
        }
    }
    return false;
}

} // 匿名命名空间

StreamXmlPlugin::StreamXmlPlugin(bool includeTagsInOutput)
        : includeTagsInOutput_(includeTagsInOutput),
          state_(PluginState::IDLE),
          startState_(StartState::WAIT_LT),
          allowStartAfterEndTag_(false),
          allowStartAfterPunctuation_(false),
          haveEndPattern_(false) {
    reset();
}

PluginState StreamXmlPlugin::state() const {
    return state_;
}

bool StreamXmlPlugin::initPlugin() {
    reset();
    return true;
}

void StreamXmlPlugin::reset() {
    state_ = PluginState::IDLE;
    startState_ = StartState::WAIT_LT;
    tagName_.clear();
    attributes_.clear();
    endMatcher_.reset();
    alternateEndMatcher_.reset();
    endPattern_.clear();
    alternateEndPattern_.clear();
    haveEndPattern_ = false;
    inlineToolOnly_ = false;
    lastChar_ = 0;
}

bool StreamXmlPlugin::isAsciiLetter(char16_t c) {
    return (c >= u'A' && c <= u'Z') || (c >= u'a' && c <= u'z');
}

bool StreamXmlPlugin::isTagNameContinuationChar(char16_t c) {
    return isAsciiLetter(c) || (c >= u'0' && c <= u'9') || c == u'_';
}

bool StreamXmlPlugin::isPunctuationTrigger(char16_t c) {
    switch (c) {
        case u'\uFF0C': // ，
        case u'\u3002': // 。
        case u'\uFF1F': // ？
        case u'\uFF01': // ！
        case u'\uFF1A': // ：
        case u'\uFF08': // （
        case u'\uFF09': // ）
        case u'\u3010': // 【
        case u'\u3011': // 】
        case u'\u300A': // 《
        case u'\u300B': // 》
        case u':':
        case u',':
        case u'.':
        case u'?':
        case u'!':
        case u'~':
        case u'\uFF5E': // ～
            return true;
        default:
            return false;
    }
}

bool StreamXmlPlugin::isEmojiTrigger(char16_t c) {
    // Most modern emojis are surrogate pairs in UTF-16.
    if (c >= u'\xD800' && c <= u'\xDFFF') {
        return true;
    }

    // Common BMP emoji/symbol blocks (e.g. ☀, ❤, ✨, etc.).
    if ((c >= u'\x2300' && c <= u'\x23FF') ||
        (c >= u'\x2600' && c <= u'\x27BF') ||
        (c >= u'\x2B00' && c <= u'\x2BFF')) {
        return true;
    }

    return false;
}

bool StreamXmlPlugin::isEmojiContinuationChar(char16_t c) {
    switch (c) {
        case u'\u200D': // ZERO WIDTH JOINER
        case u'\uFE0E': // text presentation selector
        case u'\uFE0F': // emoji presentation selector
        case u'\u20E3': // combining enclosing keycap
            return true;
        default:
            return false;
    }
}

bool StreamXmlPlugin::handleDefaultCharacter(char16_t c) {
    updatePunctuationAllowance(c);
    return true;
}

void StreamXmlPlugin::updatePunctuationAllowance(char16_t c) {
    if (isPunctuationTrigger(c) || isEmojiTrigger(c)) {
        allowStartAfterPunctuation_ = true;
    } else if (c == u' ' || c == u'\t' || isEmojiContinuationChar(c)) {
        // keep
    } else {
        allowStartAfterPunctuation_ = false;
    }
}

bool StreamXmlPlugin::processStartMatcher(char16_t c) {
    switch (startState_) {
        case StartState::WAIT_LT: {
            if (c == u'<') {
                tagName_.clear();
                attributes_.clear();
                startState_ = StartState::WAIT_FIRST_LETTER;
                state_ = PluginState::TRYING;
            }
            return false;
        }
        case StartState::WAIT_FIRST_LETTER: {
            if (isAsciiLetter(c)) {
                tagName_.push_back(c);
                startState_ = StartState::IN_TAG_NAME;
                state_ = PluginState::TRYING;
                return false;
            }
            startState_ = StartState::WAIT_LT;
            state_ = PluginState::IDLE;
            return false;
        }
        case StartState::IN_TAG_NAME: {
            if (isAttributeSpace(c)) {
                attributes_.push_back(c);
                startState_ = StartState::IN_ATTRS;
                state_ = PluginState::TRYING;
                return false;
            }
            if (c == u'>') {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            if (!isTagNameContinuationChar(c)) {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::IDLE;
                tagName_.clear();
                return false;
            }
            tagName_.push_back(c);
            state_ = PluginState::TRYING;
            return false;
        }
        case StartState::IN_ATTRS: {
            if (c == u'>') {
                startState_ = StartState::WAIT_LT;
                state_ = PluginState::TRYING;
                return true;
            }
            attributes_.push_back(c);
            state_ = PluginState::TRYING;
            return false;
        }
    }
    return false;
}

void StreamXmlPlugin::buildEndPattern() {
    const auto name = lowerAscii(tagName_);
    std::u16string token;
    const bool hasThinkingToken =
            (name == u"think" || name == u"thinking") && readThinkingToken(attributes_, token);

    auto buildPattern = [&](char16_t quote, std::u16string& pattern) {
        pattern.clear();
        pattern.reserve(tagName_.size() + token.size() + 12);
        pattern.push_back(u'<');
        pattern.push_back(u'/');
        pattern.append(tagName_);
        if (hasThinkingToken) {
            pattern.append(u" token=");
            pattern.push_back(quote);
            pattern.append(token);
            pattern.push_back(quote);
        }
        pattern.push_back(u'>');
    };

    buildPattern(u'"', endPattern_);
    endMatcher_.setPattern(endPattern_);
    if (hasThinkingToken) {
        // ThinkingMarkup 兼容单双引号，原生流式分段也必须保持相同边界。
        buildPattern(u'\'', alternateEndPattern_);
        alternateEndMatcher_.setPattern(alternateEndPattern_);
    } else {
        alternateEndPattern_.clear();
        alternateEndMatcher_.reset();
    }
    haveEndPattern_ = true;
}

bool StreamXmlPlugin::processChar(char16_t c, bool atStartOfLine) {
    const char16_t prevChar = lastChar_;
    auto finish = [&](bool result) {
        lastChar_ = c;
        return result;
    };

    if (state_ == PluginState::PROCESSING) {
        if (haveEndPattern_) {
            const bool matched = endMatcher_.process(c) ||
                    (!alternateEndPattern_.empty() && alternateEndMatcher_.process(c));
            if (matched) {
                allowStartAfterEndTag_ = true;
                allowStartAfterPunctuation_ = false;
                reset();
                return finish(includeTagsInOutput_);
            }
        }
        return finish(includeTagsInOutput_);
    }

    if (state_ == PluginState::IDLE && !atStartOfLine) {
        const bool allowStart = allowStartAfterEndTag_ || allowStartAfterPunctuation_;
        if (!allowStart && c != u'<') {
            return finish(handleDefaultCharacter(c));
        }
        inlineToolOnly_ = !allowStart;
        if (c == u' ' || c == u'\t' || isEmojiContinuationChar(c)) {
            return finish(handleDefaultCharacter(c));
        }
    }

    const PluginState previousState = state_;
    const bool startMatched = processStartMatcher(c);

    if (startMatched) {
        if (inlineToolOnly_ && !isToolLikeTagName(tagName_)) {
            reset();
            return finish(true);
        }
        if (prevChar == u'/') {
            // Treat self-closing tags like <br/> as plain text to avoid entering XML mode.
            reset();
            return finish(true);
        }
        state_ = PluginState::PROCESSING;
        allowStartAfterEndTag_ = false;
        allowStartAfterPunctuation_ = false;
        buildEndPattern();
        startState_ = StartState::WAIT_LT;
        return finish(includeTagsInOutput_);
    }

    if (state_ == PluginState::TRYING) {
        allowStartAfterPunctuation_ = false;
        return finish(includeTagsInOutput_);
    }

    if (previousState == PluginState::TRYING) {
        reset();
    }
    allowStartAfterEndTag_ = false;
    allowStartAfterPunctuation_ = false;
    return finish(handleDefaultCharacter(c));
}

} // namespace streamnative
