package com.profoundai.llms.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.MessageDigest;
import java.util.*;

@Service
public class CrawlServiceImpl implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlServiceImpl.class);

    private static final int MAX_PAGES = 200;
    private static final int MAX_DEPTH = 3;
    private static final int TIMEOUT_MS = 8000;

    @Override
    public CrawlResult crawl(String baseUrl) {
        log.info("Starting crawl for baseUrl={}", baseUrl);
        try {
            log.debug("Parsing base URL: {}", baseUrl);
            URI baseUri = new URI(baseUrl);
            String baseHost = baseUri.getHost();
            log.debug("Extracted base host: {}", baseHost);

            Set<String> visited = new HashSet<>();
            List<PageInfo> pages = new ArrayList<>();

            Queue<UrlDepth> queue = new ArrayDeque<>();
            queue.add(new UrlDepth(baseUrl, 0));
            log.debug("Initialized crawl queue with base URL at depth 0");

            int processedCount = 0;
            while (!queue.isEmpty() && pages.size() < MAX_PAGES) {
                UrlDepth current = queue.poll();
                String url = normalizeUrl(current.url);

                if (url == null) {
                    log.debug("Skipping null URL at depth {}", current.depth);
                    continue;
                }
                if (visited.contains(url)) {
                    log.debug("Skipping already visited URL: {}", url);
                    continue;
                }
                if (current.depth > MAX_DEPTH) {
                    log.debug("Skipping URL at depth {} (max depth: {}): {}", current.depth, MAX_DEPTH, url);
                    continue;
                }
                visited.add(url);
                processedCount++;

                URI currentUri;
                try {
                    currentUri = new URI(url);
                } catch (Exception e) {
                    log.debug("Failed to parse URL, skipping: {}", url);
                    continue;
                }

                if (currentUri.getHost() == null || !currentUri.getHost().endsWith(baseHost)) {
                    log.debug("Skipping URL with different host: {} (expected: {})", 
                            currentUri.getHost(), baseHost);
                    continue;
                }

                try {
                    log.debug("Fetching page: {} (depth: {})", url, current.depth);
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
                    log.debug("Successfully processed page: {} (title: {}, hash: {})", url, title, hash);

                    Elements links = doc.select("a[href]");
                    int linksAdded = 0;
                    for (Element link : links) {
                        String href = link.absUrl("href");
                        String normalized = normalizeUrl(href);
                        if (normalized != null && !visited.contains(normalized)) {
                            queue.add(new UrlDepth(normalized, current.depth + 1));
                            linksAdded++;
                        }
                    }
                    log.debug("Found {} links on page {}, added {} new URLs to queue", 
                            links.size(), url, linksAdded);
                } catch (Exception e) {
                    log.debug("Error processing page {}: {}", url, e.getMessage());
                    // ignore per-page errors
                }
            }

            if (pages.size() >= MAX_PAGES) {
                log.warn("Reached maximum page limit ({}), stopping crawl", MAX_PAGES);
            }
            if (queue.isEmpty()) {
                log.debug("Crawl queue exhausted, crawl complete");
            }

            log.info("Crawl completed for baseUrl={}, processed {} pages, found {} valid pages", 
                    baseUrl, processedCount, pages.size());
            return new CrawlResult(baseUrl, pages);
        } catch (Exception e) {
            log.error("Failed to crawl baseUrl={}: {}", baseUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to crawl " + baseUrl, e);
        }
    }

    private String normalizeUrl(String url) {
        try {
            if (url == null || url.isBlank()) {
                log.trace("Normalizing null or blank URL, returning null");
                return null;
            }
            URI uri = new URI(url);
            if (uri.getScheme() == null) {
                log.trace("URL missing scheme, returning null: {}", url);
                return null;
            }
            String normalized = uri.toString().split("#")[0]; // drop fragment
            log.trace("Normalized URL: {} -> {}", url, normalized);
            return normalized;
        } catch (Exception e) {
            log.trace("Error normalizing URL {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String sha256(String text) throws Exception {
        log.trace("Computing SHA-256 hash for text of length: {}", text != null ? text.length() : 0);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(text.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        String hash = sb.toString();
        log.trace("Generated hash: {}", hash);
        return hash;
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
