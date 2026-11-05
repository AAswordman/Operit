package com.ai.assistance.operit.ui.common.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BareUrlLinkifierTest {
    @Test
    fun findsHttpsUrlInBodyText() {
        val text = "see https://example.com here"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun findsHttpUrlAsWell() {
        val text = "http://example.com"

        val urls = findBareUrls(text)

        assertEquals(listOf("http://example.com"), urls.map { it.url })
        assertEquals(0, urls.single().start)
        assertEquals(text.length, urls.single().end)
    }

    @Test
    fun findsEveryUrlInOneRunInSourceOrder() {
        val text = "https://a.example then https://b.example/path?q=1"

        val urls = findBareUrls(text)

        assertEquals(
            listOf("https://a.example", "https://b.example/path?q=1"),
            urls.map { it.url },
        )
        assertCoversSourceText(text, urls)
    }

    @Test
    fun dropsSentencePeriodAfterUrl() {
        assertUrlAndRange(
            text = "https://example.com.",
            expectedUrl = "https://example.com",
            expectedEnd = "https://example.com".length,
        )
    }

    @Test
    fun dropsSentencePunctuationAfterUrl() {
        listOf(',', ';', ':', '!', '?', '.').forEach { punctuation ->
            assertUrlAndRange(
                text = "https://example.com$punctuation",
                expectedUrl = "https://example.com",
                expectedEnd = "https://example.com".length,
            )
        }
    }

    @Test
    fun keepsQueryAndFragmentBecauseTheyBelongToTheUrl() {
        val url = "https://example.com/search?q=a&page=2#results"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertEquals(url.length, urls.single().end)
    }

    @Test
    fun dropsClosingParenthesisThatBelongsToTheSentence() {
        val text = "(https://example.com)"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertEquals(text.indexOf("https://"), urls.single().start)
    }

    @Test
    fun keepsClosingParenthesisThatTheUrlOpened() {
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertEquals(url.length, urls.single().end)
    }

    @Test
    fun dropsOnlyTheUnmatchedClosingParentheses() {
        val text = "(https://en.wikipedia.org/wiki/Foo_(bar)))"
        val url = "https://en.wikipedia.org/wiki/Foo_(bar)"

        val urls = findBareUrls(text)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun stopsBeforeEmphasisMarker() {
        val text = "**https://example.com**"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun stopsBeforeFullWidthPunctuation() {
        val text = "https://example.com，然后继续"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun findsUrlDirectlyAfterChineseText() {
        val text = "链接https://example.com"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun keepsChinesePathQueryAndFragment() {
        val url = "https://example.com/文档/安装?搜索=中文#示例"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(url, urls)
    }

    @Test
    fun keepsInternationalizedHost() {
        val url = "https://例子.测试/文档"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(url, urls)
    }

    @Test
    fun keepsSupplementaryLettersAndCombiningMarks() {
        val url = "https://example.com/𠮷野家/cafe\u0301?q=中文"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(url, urls)
    }

    @Test
    fun stopsBeforeFullWidthPunctuationAfterChinesePath() {
        val url = "https://example.com/文档?搜索=中文"
        val text = "查看$url，然后继续。"

        val urls = findBareUrls(text)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun keepsIpv6HostAndPort() {
        val url = "http://[::1]:8094/文档"

        val urls = findBareUrls(url)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(url, urls)
    }

    @Test
    fun trimsProseBracketsWithoutTrimmingIpv6Host() {
        val url = "http://[2001:db8::1]:8094/文档"
        val text = "[$url]"

        val urls = findBareUrls(text)

        assertEquals(listOf(url), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun normalizesUppercaseSchemeForTheClickHandler() {
        val text = "HTTPS://example.com"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com"), urls.map { it.url })
        assertEquals(0, urls.single().start)
        assertEquals(text.length, urls.single().end)
    }

    @Test
    fun ignoresSchemeThatIsNotFollowedByAHost() {
        assertEquals(emptyList<BareUrl>(), findBareUrls("the https:// prefix alone"))
        assertEquals(emptyList<BareUrl>(), findBareUrls("https://"))
    }

    @Test
    fun ignoresSchemeGluedToTheEndOfAWord() {
        assertEquals(emptyList<BareUrl>(), findBareUrls("notaurlhttps://example.com"))
    }

    @Test
    fun ignoresOtherSchemes() {
        assertEquals(emptyList<BareUrl>(), findBareUrls("ftp://example.com"))
        assertEquals(emptyList<BareUrl>(), findBareUrls("mailto:someone@example.com"))
    }

    @Test
    fun leavesPlainTextWithoutUrlsUntouched() {
        assertEquals(emptyList<BareUrl>(), findBareUrls("no links here, just prose."))
        assertEquals(emptyList<BareUrl>(), findBareUrls(""))
    }

    @Test
    fun reportedRangesSliceTheUrlOutOfTheSourceText() {
        val text = "read https://example.com/a, then (https://b.example/(x)) and stop"

        val urls = findBareUrls(text)

        assertEquals(
            listOf("https://example.com/a", "https://b.example/(x)"),
            urls.map { it.url },
        )
        assertCoversSourceText(text, urls)
    }

    @Test
    fun rangesCoverTheUrlInsideMarkdownLinkSyntax() {
        val text = "[docs](https://example.com/guide)"

        val urls = findBareUrls(text)

        assertEquals(listOf("https://example.com/guide"), urls.map { it.url })
        assertCoversSourceText(text, urls)
    }

    @Test
    fun rangesContainOnlyUrlCharacters() {
        val text = "a https://example.com b"

        findBareUrls(text).forEach { bareUrl ->
            assertTrue("range must start at the scheme", text.startsWith("https://", bareUrl.start))
            assertTrue("range must end on a non-space", text[bareUrl.end - 1] != ' ')
        }
    }

    private fun assertUrlAndRange(text: String, expectedUrl: String, expectedEnd: Int) {
        val urls = findBareUrls(text)

        assertEquals(listOf(expectedUrl), urls.map { it.url })
        assertEquals(text.indexOf("https", ignoreCase = true), urls.single().start)
        assertEquals(expectedEnd, urls.single().end)
    }

    private fun assertCoversSourceText(text: String, urls: List<BareUrl>) {
        urls.forEach { bareUrl ->
            assertEquals(bareUrl.url, text.substring(bareUrl.start, bareUrl.end))
        }
        urls.zipWithNext().forEach { (current, next) ->
            assertTrue("ranges must not overlap", current.end <= next.start)
        }
    }
}
