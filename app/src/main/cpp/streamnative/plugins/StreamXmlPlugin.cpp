#include "StreamXmlPlugin.h"

namespace streamnative {

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
    endMatcher_.reset();
    endPattern_.clear();
    haveEndPattern_ = false;
    lastChar_ = 0;
}

bool StreamXmlPlugin::isAsciiLetter(char16_t c) {
    return (c >= u'A' && c <= u'Z') || (c >= u'a' && c <= u'z');
}

bool StreamXmlPlugin::isTagNameContinuationChar(char16_t c) {
    // ':' supports the reserved `package:function` tool tag form (e.g. subagent:subagent_run).
    // First char must still be a letter (enforced in WAIT_FIRST_LETTER), so `:<abc>` never starts.
    return isAsciiLetter(c) || (c >= u'0' && c <= u'9') || c == u'_' || c == u':';
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
            if (c == u' ') {
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
            state_ = PluginState::TRYING;
            return false;
        }
    }
    return false;
}

void StreamXmlPlugin::buildEndPattern() {
    endPattern_.clear();
    endPattern_.reserve(tagName_.size() + 3);
    endPattern_.push_back(u'<');
    endPattern_.push_back(u'/');
    endPattern_.append(tagName_);
    endPattern_.push_back(u'>');
    endMatcher_.setPattern(endPattern_);
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
            if (endMatcher_.process(c)) {
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
        if (!allowStart) {
            return finish(handleDefaultCharacter(c));
        }
        if (c == u' ' || c == u'\t' || isEmojiContinuationChar(c)) {
            return finish(handleDefaultCharacter(c));
        }
    }

    const PluginState previousState = state_;
    const bool startMatched = processStartMatcher(c);

    if (startMatched) {
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
