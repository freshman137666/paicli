package com.paicli.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicli.mcp.McpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserTabManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("\"?(?:id|pageId)\"?\\s*:\\s*\"?([^\"\\s,}]+)");
    private static final Pattern URL_PATTERN = Pattern.compile("\"?url\"?\\s*:\\s*\"?([^\"\\s,}]+)");

    private final AtomicBoolean pagesListed = new AtomicBoolean(false);

    public BrowserTabManager() {
    }

    /**
     * 规范化 URL：如果缺少协议前缀，自动补 https://。
     * 避免 chrome-devtools-mcp 因裸域名超时。
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    /**
     * 在 navigate_page 前调用：检查 Chrome 是否有同域标签页，有则切换到该标签再导航。
     * 首次导航跳过 list_pages（避免 CDP 首次连接额外延迟导致总耗时超预期）。
     * 仅当已至少成功完成过一次 list_pages 时才执行检查。
     *
     * @param client    MCP client
     * @param targetUrl 要导航的目标 URL（已规范化）
     * @return true 表示已成功切换到同域标签（调用方可继续导航），false 表示无需切换或出错
     */
    public boolean tryReuseExistingTab(McpClient client, String targetUrl) {
        String targetDomain = extractDomain(targetUrl);
        if (targetDomain == null) {
            return false;
        }
        if (!pagesListed.get()) {
            // 首次：只做一次探活 list_pages 但不做匹配（后续才复用）
            try {
                client.callTool("list_pages", "{}");
            } catch (Exception ignored) {
            }
            pagesListed.set(true);
            return false;
        }
        List<TabInfo> tabs;
        try {
            String pagesText = client.callTool("list_pages", "{}");
            tabs = parseTabList(pagesText);
        } catch (Exception e) {
            return false;
        }
        if (tabs.isEmpty()) {
            return false;
        }
        String matchedPageId = null;
        for (TabInfo tab : tabs) {
            String tabDomain = extractDomain(tab.url);
            if (tabDomain != null && tabDomain.equals(targetDomain)
                    && tab.id != null && !tab.id.isBlank()) {
                matchedPageId = tab.id;
                break;
            }
        }
        if (matchedPageId == null) {
            return false;
        }
        try {
            String switchArgs = "{\"pageId\":\"" + escapeJson(matchedPageId) + "\"}";
            client.callTool("select_page", switchArgs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    static List<TabInfo> parseTabList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root.isArray()) {
                List<TabInfo> result = new ArrayList<>();
                for (JsonNode node : root) {
                    String id = text(node, "id");
                    if (id == null || id.isBlank()) {
                        id = text(node, "pageId");
                    }
                    String title = text(node, "title");
                    String url = text(node, "url");
                    if (id != null && !id.isBlank()) {
                        result.add(new TabInfo(id, title, url));
                    }
                }
                return result;
            }
            // 有效 JSON 但非数组：非预期格式，不继续 regex fallback
            return List.of();
        } catch (Exception e) {
            // JSON 解析失败，尝试 regex fallback
            return parseByRegex(raw);
        }
    }

    private static List<TabInfo> parseByRegex(String raw) {
        List<TabInfo> result = new ArrayList<>();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            Matcher idMatcher = PAGE_ID_PATTERN.matcher(line);
            if (!idMatcher.find()) {
                continue;
            }
            String id = idMatcher.group(1);
            Matcher urlMatcher = URL_PATTERN.matcher(line);
            String url = urlMatcher.find() ? urlMatcher.group(1) : "";
            result.add(new TabInfo(id, "", url));
        }
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record TabInfo(String id, String title, String url) {
    }
}
