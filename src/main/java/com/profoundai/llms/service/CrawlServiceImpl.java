package com.profoundai.llms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.MessageDigest;
import java.util.*;

@Service
public class CrawlServiceImpl implements CrawlService {

    private static final int MAX_PAGES = 200;
    private static final int MAX_DEPTH = 3;
    private static final int TIMEOUT_MS = 8000;

    @Override
    public CrawlResult crawl(String baseUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            String baseHost = baseUri.getHost();

            Set<String> visited = new HashSet<>();
            List<PageInfo> pages = new ArrayList<>();

            Queue<UrlDepth> queue = new ArrayDeque<>();
            queue.add(new UrlDepth(baseUrl, 0));

            while (!queue.isEmpty() && pages.size() < MAX_PAGES) {
                UrlDepth current = queue.poll();
                String url = normalizeUrl(current.url);

                if (url == null || visited.contains(url) || current.depth > MAX_DEPTH) {
                    continue;
                }
                visited.add(url);

                URI currentUri;
                try {
                    currentUri = new URI(url);
                } catch (Exception e) {
                    continue;
                }

                if (currentUri.getHost() == null || !currentUri.getHost().endsWith(baseHost)) {
                    continue;
                }

                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("llms-txt-crawler")
                            .timeout(TIMEOUT_MS)
                            .get();

                    String title = doc.title();
                    String description = Optional.ofNullable(doc.selectFirst("meta[name=description]"))
                            .map(el -> el.attr("content"))
                            .orElse(null);

                    String textContent = doc.body() != null ? doc.body().text() : "";
                    String hash = sha256(textContent);

                    pages.add(new PageInfo(url, title, description, hash));

                    Elements links = doc.select("a[href]");
                    for (Element link : links) {
                        String href = link.absUrl("href");
                        String normalized = normalizeUrl(href);
                        if (normalized != null && !visited.contains(normalized)) {
                            queue.add(new UrlDepth(normalized, current.depth + 1));
                        }
                    }
                } catch (Exception e) {
                    // ignore per-page errors
                }
            }

            return new CrawlResult(baseUrl, pages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to crawl " + baseUrl, e);
        }
    }

    private String normalizeUrl(String url) {
        try {
            if (url == null || url.isBlank()) return null;
            URI uri = new URI(url);
            if (uri.getScheme() == null) {
                return null;
            }
            return uri.toString().split("#")[0]; // drop fragment
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(text.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class UrlDepth {
        final String url;
        final int depth;

        UrlDepth(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }
    }
}
