#pragma once

#include <string>

#include "StreamPlugin.h"

namespace streamnative {

class StreamMarkdownFencedCodeBlockPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownFencedCodeBlockPlugin(bool includeFences = true);

    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeFences_;
    PluginState state_;
    int fenceLen_;
    int openingFenceLen_;
    int fenceIndent_;
    bool isMatchingEndFence_;
    bool hasStartedMatchingFence_;
    bool isReadingFenceInfo_;
    bool hasFenceTrailingWhitespace_;
};

class StreamMarkdownInlineCodePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownInlineCodePlugin(bool includeTicks = true);

    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeTicks_;
    PluginState state_;
    int tickLen_;
    int endMatch_; // number matched at end
};

class StreamMarkdownBoldPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownBoldPlugin(bool includeAsterisks = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeAsterisks_;
    PluginState state_;
    int startMatch_;
    int endMatch_;
};

class StreamMarkdownItalicPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownItalicPlugin(bool includeAsterisks = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeAsterisks_;
    PluginState state_;
    int startMatch_;
    int endMatch_;
    char16_t lastChar_;
    bool hasLastChar_;
};

class StreamMarkdownHeaderPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownHeaderPlugin(bool includeMarker = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeMarker_;
    PluginState state_;
    int hashCount_;
    bool inMatch_;
};

class StreamMarkdownLinkPlugin final : public StreamPlugin {
public:
    StreamMarkdownLinkPlugin();
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    PluginState state_;
    int phase_;
};

class StreamMarkdownBlockQuotePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownBlockQuotePlugin(bool includeMarker = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeMarker_;
    PluginState state_;
    int matchIndex_;
};

class StreamMarkdownHorizontalRulePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownHorizontalRulePlugin(bool includeMarker = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeMarker_;
    PluginState state_;
    char16_t currentMarker_;
    bool hasMarker_;
    int markerCount_;
};

class StreamMarkdownOrderedListPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownOrderedListPlugin(bool includeMarker = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeMarker_;
    PluginState state_;
    int matchState_;
};

class StreamMarkdownUnorderedListPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownUnorderedListPlugin(bool includeMarker = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeMarker_;
    PluginState state_;
    int matchState_;
};

class StreamMarkdownStrikethroughPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownStrikethroughPlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownUnderlinePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownUnderlinePlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownInlineLaTeXPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownInlineLaTeXPlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownInlineParenLaTeXPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownInlineParenLaTeXPlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownBlockLaTeXPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownBlockLaTeXPlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownBlockBracketLaTeXPlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownBlockBracketLaTeXPlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int startState_;
    int endState_;
};

class StreamMarkdownImagePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownImagePlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int phase_;
};

class StreamMarkdownTablePlugin final : public StreamPlugin {
public:
    explicit StreamMarkdownTablePlugin(bool includeDelimiters = true);
    PluginState state() const override;
    bool processChar(char16_t c, bool atStartOfLine) override;
    bool initPlugin() override;
    void reset() override;

private:
    bool includeDelimiters_;
    PluginState state_;
    int tableRowCount_;
    bool foundHeaderSeparator_;
    int headerSepMatchState_;
};

} // namespace streamnative
