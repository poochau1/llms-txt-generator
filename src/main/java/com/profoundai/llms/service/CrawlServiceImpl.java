package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageType;
import com.profoundai.llms.util.CsrRenderer;
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

    private static final int MAX_PAGES = 100;
    private static final int MAX_DEPTH = 3;
    private static final int TIMEOUT_MS = 8000;
    private static final int CSR_THRESHOLD_BYTES = 3 * 1024; // 3 KB

    private final CsrRenderer csrRenderer = new CsrRenderer();

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
                    String html = Jsoup.connect(url)
                            .userAgent("llms-txt-crawler")
                            .timeout(TIMEOUT_MS)
                            .execute()
                            .body();

                    // Check if page is likely CSR and render client-side if needed
                    if (isLikelyCSR(html)) {
                        log.debug("Page appears to be CSR, attempting client-side render: {}", url);
                        String renderedHtml = csrRenderer.renderClientSide(url);
                        if (renderedHtml != null && isRicherContent(renderedHtml, html)) {
                            log.debug("Client-side rendered DOM is richer, using rendered version for: {}", url);
                            html = renderedHtml;
                        } else if (renderedHtml != null) {
                            log.debug("Client-side rendered DOM not richer, using SSR version for: {}", url);
                        } else {
                            log.debug("Client-side rendering failed, using SSR version for: {}", url);
                        }
                    }

                    Document doc = Jsoup.parse(html, url);

                    String title = doc.title();
                    String description = Optional.ofNullable(doc.selectFirst("meta[name=description]"))
                            .map(el -> el.attr("content"))
                            .orElse(null);

                    String textContent = doc.body() != null ? doc.body().text() : "";
                    String hash = sha256(textContent);

                    pages.add(new PageInfo(url, title, description, hash, PageType.PAGE));
                    log.debug("Successfully processed page: {} (title: {}, hash: {})", url, title, hash);

                    // Stop if we've reached MAX_PAGES
                    if (pages.size() >= MAX_PAGES) {
                        log.debug("Reached MAX_PAGES limit ({}), stopping crawl", MAX_PAGES);
                        break;
                    }

                    Elements links = doc.select("a[href]");
                    int linksAdded = 0;
                    int staticAssetsSkipped = 0;
                    for (Element link : links) {
                        // Stop adding links if we've reached MAX_PAGES
                        if (pages.size() >= MAX_PAGES) {
                            break;
                        }
                        String href = link.absUrl("href");
                        String normalized = normalizeUrl(href);
                        
                        // Filter: Skip static assets (.js, .css, .map) - they should only be processed as assets, not BFS-crawled
                        if (normalized != null && isStaticAsset(normalized)) {
                            log.trace("Skipping static asset from BFS queue: {}", normalized);
                            staticAssetsSkipped++;
                            continue;
                        }
                        
                        if (normalized != null && !visited.contains(normalized)) {
                            queue.add(new UrlDepth(normalized, current.depth + 1));
                            linksAdded++;
                        }
                    }
                    log.debug("Found {} links on page {}, added {} new URLs to queue, skipped {} static assets", 
                            links.size(), url, linksAdded, staticAssetsSkipped);

                    // Process <script src="..."> tags as static assets
                    // For each external JS script: normalize URL, fetch contents, compute hash, add as STATIC_ASSET
                    // Note: We do NOT parse or follow any links within script contents - only fetch and hash
                    if (pages.size() < MAX_PAGES) {
                        Elements scripts = doc.select("script[src]");
                        int scriptsProcessed = 0;
                        for (Element script : scripts) {
                            // Stop if we've reached MAX_PAGES
                            if (pages.size() >= MAX_PAGES) {
                                break;
                            }
                            String src = script.absUrl("src");
                            String normalized = normalizeUrl(src);
                            if (normalized != null && !visited.contains(normalized)) {
                                try {
                                    log.debug("Fetching script asset: {}", normalized);
                                    // Fetch raw file contents (do not parse or follow links within script)
                                    String scriptContent = Jsoup.connect(normalized)
                                            .userAgent("llms-txt-crawler")
                                            .timeout(TIMEOUT_MS)
                                            .ignoreContentType(true)
                                            .execute()
                                            .body();
                                    // Compute SHA-256 hash of the script content
                                    String scriptHash = sha256(scriptContent);
                                    // Add as STATIC_ASSET - no BFS enqueuing, just track as asset
                                    pages.add(new PageInfo(normalized, null, null, scriptHash, PageType.STATIC_ASSET));
                                    visited.add(normalized);
                                    scriptsProcessed++;
                                    log.debug("Successfully processed script asset: {} (hash: {})", normalized, scriptHash);
                                    
                                    // Stop if we've reached MAX_PAGES after adding this asset
                                    if (pages.size() >= MAX_PAGES) {
                                        break;
                                    }
                                } catch (Exception e) {
                                    log.debug("Error processing script asset {}: {}", normalized, e.getMessage());
                                    // ignore per-script errors
                                }
                            }
                        }
                        log.debug("Found {} script sources on page {}, processed {} script assets", 
                                scripts.size(), url, scriptsProcessed);
                    }
                } catch (Exception e) {
                    log.debug("Error processing page {}: {}", url, e.getMessage());
                    // ignore per-page errors
                }
            }

            if (pages.size() >= MAX_PAGES) {
                log.info("Reached maximum page limit ({}), stopping crawl", MAX_PAGES);
            } else if (queue.isEmpty()) {
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

    private boolean isStaticAsset(String url) {
        if (url == null) {
            return false;
        }
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".js") || lowerUrl.endsWith(".css") || lowerUrl.endsWith(".map");
    }

    /**
     * Determines if a page is likely client-side rendered using heuristics.
     *
     * @param html The HTML content to check
     * @return true if the page appears to be CSR, false otherwise
     */
    private boolean isLikelyCSR(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        // Check for very small HTML length (e.g., < 3 KB)
        if (html.length() < CSR_THRESHOLD_BYTES) {
            log.debug("HTML length ({}) is below threshold ({}), likely CSR", html.length(), CSR_THRESHOLD_BYTES);
            return true;
        }

        // Check for presence of id="root" or <app-root>
        String lowerHtml = html.toLowerCase();
        if (lowerHtml.contains("id=\"root\"") || lowerHtml.contains("id='root'") || 
            lowerHtml.contains("<app-root") || lowerHtml.contains("</app-root>")) {
            log.debug("Found CSR markers (id='root' or <app-root>), likely CSR");
            return true;
        }

        return false;
    }

    /**
     * Determines if the rendered HTML is richer than the SSR version.
     * A rendered version is considered richer if it has significantly more content.
     *
     * @param renderedHtml The client-side rendered HTML
     * @param ssrHtml The server-side rendered HTML
     * @return true if rendered HTML is richer, false otherwise
     */
    private boolean isRicherContent(String renderedHtml, String ssrHtml) {
        if (renderedHtml == null) {
            return false;
        }
        if (ssrHtml == null) {
            return true;
        }

        // Compare HTML length - rendered should be at least 20% larger to be considered richer
        int renderedLength = renderedHtml.length();
        int ssrLength = ssrHtml.length();
        
        if (renderedLength > ssrLength * 1.2) {
            log.debug("Rendered HTML is richer: {} bytes vs {} bytes", renderedLength, ssrLength);
            return true;
        }

        // Also compare text content length
        try {
            Document renderedDoc = Jsoup.parse(renderedHtml);
            Document ssrDoc = Jsoup.parse(ssrHtml);
            
            String renderedText = renderedDoc.body() != null ? renderedDoc.body().text() : "";
            String ssrText = ssrDoc.body() != null ? ssrDoc.body().text() : "";
            
            if (renderedText.length() > ssrText.length() * 1.2) {
                log.debug("Rendered text content is richer: {} chars vs {} chars", 
                        renderedText.length(), ssrText.length());
                return true;
            }
        } catch (Exception e) {
            log.debug("Error comparing text content, using length comparison only: {}", e.getMessage());
        }

        return false;
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
