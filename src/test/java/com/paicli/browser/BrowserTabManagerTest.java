package com.paicli.browser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrowserTabManagerTest {

    // === extractDomain ===

    @Test
    void extractDomainReturnsHostFromHttpsUrl() {
        assertEquals("linux.do", BrowserTabManager.extractDomain("https://linux.do/c/news/34"));
    }

    @Test
    void extractDomainReturnsHostFromHttpUrl() {
        assertEquals("example.com", BrowserTabManager.extractDomain("http://example.com/path"));
    }

    @Test
    void extractDomainPreservesSubdomain() {
        assertEquals("www.linux.do", BrowserTabManager.extractDomain("https://www.linux.do/topic"));
    }

    @Test
    void extractDomainReturnsNullForNull() {
        assertNull(BrowserTabManager.extractDomain(null));
    }

    @Test
    void extractDomainReturnsNullForBlank() {
        assertNull(BrowserTabManager.extractDomain(" "));
    }

    @Test
    void extractDomainReturnsNullForInvalidUrl() {
        assertNull(BrowserTabManager.extractDomain("not-a-url"));
    }

    @Test
    void extractDomainLowercases() {
        assertEquals("LINUX.DO".toLowerCase(), BrowserTabManager.extractDomain("https://LINUX.DO/path"));
    }

    // === parseTabList ===

    @Test
    void parseTabListParsesJsonArray() {
        String json = """
                [{"id":"page-1","title":"Test","url":"https://example.com"}]
                """;

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertEquals(1, tabs.size());
        assertEquals("page-1", tabs.get(0).id());
        assertEquals("Test", tabs.get(0).title());
        assertEquals("https://example.com", tabs.get(0).url());
    }

    @Test
    void parseTabListParsesMultipleTabs() {
        String json = """
                [
                  {"id":"page-1","title":"A","url":"https://a.com"},
                  {"id":"page-2","title":"B","url":"https://b.com"}
                ]
                """;

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertEquals(2, tabs.size());
        assertEquals("page-1", tabs.get(0).id());
        assertEquals("page-2", tabs.get(1).id());
    }

    @Test
    void parseTabListReturnsEmptyForEmptyArray() {
        String json = "[]";

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertTrue(tabs.isEmpty());
    }

    @Test
    void parseTabListReturnsEmptyForNull() {
        assertTrue(BrowserTabManager.parseTabList(null).isEmpty());
    }

    @Test
    void parseTabListReturnsEmptyForBlank() {
        assertTrue(BrowserTabManager.parseTabList("  ").isEmpty());
    }

    @Test
    void parseTabListReturnsEmptyForMalformedJson() {
        assertTrue(BrowserTabManager.parseTabList("not json").isEmpty());
    }

    @Test
    void parseTabListFallsBackToRegexForNonArrayJson() {
        String json = "{\"pages\": [{\"id\":\"p1\"}]}";

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertTrue(tabs.isEmpty());
    }

    @Test
    void parseTabListSkipsEntriesWithoutId() {
        String json = """
                [
                  {"title":"No Id","url":"https://x.com"},
                  {"id":"page-2","title":"Has Id","url":"https://y.com"}
                ]
                """;

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertEquals(1, tabs.size());
        assertEquals("page-2", tabs.get(0).id());
    }

    @Test
    void parseTabListAcceptsPageIdField() {
        String json = """
                [{"pageId":"p_abc","title":"Page","url":"https://p.com"}]
                """;

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(json);

        assertEquals(1, tabs.size());
        assertEquals("p_abc", tabs.get(0).id());
    }

    @Test
    void regexFallbackParsesLineWithIdAndUrl() {
        // 非 JSON 文本（缺少引号），触发 Jackson 解析失败 → regex fallback
        String raw = "{id: page-1, url: https://example.com}";

        List<BrowserTabManager.TabInfo> tabs = BrowserTabManager.parseTabList(raw);

        assertEquals(1, tabs.size());
        assertEquals("page-1", tabs.get(0).id());
    }

    @Test
    void domainMatchingCaseInsensitive() {
        String domain1 = BrowserTabManager.extractDomain("https://LINUX.DO/topic");
        String domain2 = BrowserTabManager.extractDomain("https://linux.do/news");

        assertEquals(domain1, domain2);
    }

    // === normalizeUrl ===

    @Test
    void normalizeUrlAddsHttpsToBareDomain() {
        assertEquals("https://linux.do", BrowserTabManager.normalizeUrl("linux.do"));
    }

    @Test
    void normalizeUrlAddsHttpsToDomainWithPath() {
        assertEquals("https://linux.do/c/news/34", BrowserTabManager.normalizeUrl("linux.do/c/news/34"));
    }

    @Test
    void normalizeUrlPreservesExistingHttp() {
        assertEquals("http://linux.do", BrowserTabManager.normalizeUrl("http://linux.do"));
    }

    @Test
    void normalizeUrlPreservesExistingHttps() {
        assertEquals("https://linux.do", BrowserTabManager.normalizeUrl("https://linux.do"));
    }

    @Test
    void normalizeUrlReturnsNullForNull() {
        assertNull(BrowserTabManager.normalizeUrl(null));
    }

    @Test
    void normalizeUrlTrimsWhitespace() {
        assertEquals("https://linux.do", BrowserTabManager.normalizeUrl("  linux.do  "));
    }
}
