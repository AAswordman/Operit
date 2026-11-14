#include <algorithm>
#include <iostream>
#include <string>
#include <vector>

#include "StreamOperators.h"

struct Example {
    const char* name;
    std::u16string text;
    int expectedStart;
    int expectedLength;
    bool inlineMode;
};

int main() {
    const std::vector<Example> examples = {
        {"digits_at_eof", u"42", 0, 2, false},
        {"digits_with_newline", u"42\n", 0, 2, false},
        {"think_then_digits", u"<think>sample</think>\n\n42", 23, 2, false},
        {"think_then_digits_with_newline", u"<think>sample</think>\n\n42\n", 23, 2, false},
        {"text_answer", u"answer42", 6, 2, false},
        {"incomplete_ordered_list", u"42.", 0, 3, false},
        {"second_line_digits", u"text\n42", 5, 2, false},
        {"incomplete_bullet", u"*", 0, 1, false},
        {"incomplete_xml", u"<", 0, 1, false},
        {"incomplete_html_break", u"<br", 0, 3, false},
        {"list_then_digits", u"1. item\n42", 8, 2, false},
        {"matched_xml", u"<think>sample</think>", 0, 21, false},
        {"quoted_text", u"> quote", 2, 5, false},
        {"inline_digits", u"42", 0, 2, true},
        {"incomplete_link", u"[", 0, 1, true},
        {"trailing_incomplete_link", u"text[", 4, 1, true},
        {"inline_text", u"answer42", 6, 2, true},
        {"matched_bold", u"**ok**", 2, 2, true},
    };

    int failures = 0;
    int checks = 0;
    for (const auto& example : examples) {
        for (const bool incremental : {false, true}) {
            auto* session = example.inlineMode
                ? streamnative::createMarkdownInlineSession()
                : streamnative::createMarkdownBlockSession();
            std::vector<streamnative::Segment> segments;
            const int chunkSize = incremental ? 1 : static_cast<int>(example.text.size());
            for (int offset = 0; offset < static_cast<int>(example.text.size()); offset += chunkSize) {
                const int count = std::min(chunkSize, static_cast<int>(example.text.size()) - offset);
                auto next = streamnative::markdownSessionPush(
                    session, reinterpret_cast<const jchar*>(example.text.data() + offset), count);
                segments.insert(segments.end(), next.begin(), next.end());
            }
            const auto tail = streamnative::markdownSessionFinish(session);
            segments.insert(segments.end(), tail.begin(), tail.end());
            const bool repeatedFinishIsEmpty = streamnative::markdownSessionFinish(session).empty();
            streamnative::destroyMarkdownSession(session);

            std::vector<int> emittedCounts(example.text.size(), 0);
            bool validRanges = true;
            for (const auto& segment : segments) {
                if (segment.type < 0) continue;
                if (segment.start < 0 || segment.end < segment.start ||
                    segment.end > static_cast<int>(example.text.size())) {
                    validRanges = false;
                    continue;
                }
                for (int index = segment.start; index < segment.end; index++) {
                    emittedCounts[index]++;
                }
            }
            bool expectedTextAppearsOnce = true;
            for (int index = example.expectedStart;
                 index < example.expectedStart + example.expectedLength; index++) {
                expectedTextAppearsOnce = expectedTextAppearsOnce && emittedCounts[index] == 1;
            }
            const bool passed = validRanges && expectedTextAppearsOnce && repeatedFinishIsEmpty;
            checks++;
            failures += passed ? 0 : 1;
            std::cout << (passed ? "PASS " : "FAIL ") << example.name
                      << " incremental=" << incremental << '\n';
        }
    }
    auto* empty = streamnative::createMarkdownBlockSession();
    const bool emptyFinish = streamnative::markdownSessionFinish(empty).empty();
    streamnative::destroyMarkdownSession(empty);
    checks++;
    failures += emptyFinish ? 0 : 1;

    struct SeedExample {
        std::u16string prefix;
        std::u16string suffix;
        bool inlineMode;
    };
    const std::vector<SeedExample> seededExamples = {
        {u"42", u"", false},
        {u"4", u"2", false},
        {u"[", u"", true},
        {u"[", u"a", true},
    };
    for (const auto& example : seededExamples) {
        for (const bool incremental : {false, true}) {
            auto* session = example.inlineMode
                ? streamnative::createMarkdownInlineSession()
                : streamnative::createMarkdownBlockSession();
            streamnative::markdownSessionPush(session,
                reinterpret_cast<const jchar*>(example.prefix.data()), example.prefix.size());
            std::vector<streamnative::Segment> segments;
            const int chunkSize = incremental ? 1 : std::max(1, static_cast<int>(example.suffix.size()));
            for (int offset = 0; offset < static_cast<int>(example.suffix.size()); offset += chunkSize) {
                const int count = std::min(chunkSize, static_cast<int>(example.suffix.size()) - offset);
                const auto next = streamnative::markdownSessionPush(session,
                    reinterpret_cast<const jchar*>(example.suffix.data() + offset), count);
                segments.insert(segments.end(), next.begin(), next.end());
            }
            const auto tail = streamnative::markdownSessionFinish(session);
            segments.insert(segments.end(), tail.begin(), tail.end());
            streamnative::destroyMarkdownSession(session);

            const auto fullText = example.prefix + example.suffix;
            std::u16string newText;
            for (const auto& segment : segments) {
                if (segment.type < 0) continue;
                // Kotlin consumers render the seed separately and apply this same range contract.
                const int visibleStart = std::max(segment.start, static_cast<int>(example.prefix.size()));
                if (visibleStart < segment.end) {
                    newText.append(fullText, visibleStart, segment.end - visibleStart);
                }
            }
            const bool passed = newText == example.suffix;
            checks++;
            failures += passed ? 0 : 1;
            std::cout << (passed ? "PASS " : "FAIL ") << "seed_suffix inline="
                      << example.inlineMode << " incremental=" << incremental << '\n';
        }
    }
    std::cout << "Checks=" << checks << " failures=" << failures << '\n';
    return failures == 0 ? 0 : 1;
}
