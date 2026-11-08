#pragma once

#include <string>

#include "../StreamKmpGraph.h"
#include "StreamPlugin.h"

namespace streamnative {

class StreamXmlPlugin final : public StreamPlugin {
public:
    explicit StreamXmlPlugin(bool includeTagsInOutput = true);

    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    enum class StartState {
        WAIT_LT,
        WAIT_FIRST_LETTER,
        IN_TAG_NAME,
        IN_ATTRS,
    };

    bool includeTagsInOutput_;
    PluginState state_;
    StartState startState_;

    bool allowStartAfterEndTag_;
    bool allowStartAfterPunctuation_;
    bool inlineToolOnly_ = false;

    std::u16string tagName_;
    std::u16string attributes_;
    std::u16string endPattern_;
    std::u16string alternateEndPattern_;
    bool haveEndPattern_;
    KmpMatcher endMatcher_;
    KmpMatcher alternateEndMatcher_;
    char16_t lastChar_ = 0;

    bool handleDefaultCharacter(char16_t c);
    void updatePunctuationAllowance(char16_t c);
    bool processStartMatcher(char16_t c);
    void buildEndPattern();

    static bool isAsciiLetter(char16_t c);
    static bool isTagNameContinuationChar(char16_t c);
    static bool isPunctuationTrigger(char16_t c);
    static bool isEmojiTrigger(char16_t c);
    static bool isEmojiContinuationChar(char16_t c);
};

} // namespace streamnative
