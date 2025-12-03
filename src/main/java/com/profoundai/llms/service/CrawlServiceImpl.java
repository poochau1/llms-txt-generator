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
import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

@Service
public class CrawlServiceImpl implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlServiceImpl.class);

    private static final int MAX_PAGES = 100;
    private static final int MAX_DEPTH = 3;
    private static final int TIMEOUT_MS = 8000;
    private static final int CSR_THRESHOLD_BYTES = 3 * 1024; // 3 KB
    private static final int CONCURRENCY = 4;

    private final CsrRenderer csrRenderer = new CsrRenderer();
    private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

    @Override
    public CrawlResult crawl(String baseUrl) {
        log.info("Starting crawl for baseUrl={}", baseUrl);
        try {
            log.debug("Parsing base URL: {}", baseUrl);
            URI baseUri = new URI(baseUrl);
            String baseHost = baseUri.getHost();
            log.debug("Extracted base host: {}", baseHost);

            Set<String> visited = ConcurrentHashMap.newKeySet();
            List<PageInfo> pages = Collections.synchronizedList(new ArrayList<>());

            List<UrlDepth> currentLevel = List.of(new UrlDepth(baseUrl, 0));
            log.debug("Initialized crawl with base URL at depth 0");

            int processedCount = 0;
            for (int depth = 0; depth <= MAX_DEPTH && !currentLevel.isEmpty() && pages.size() < MAX_PAGES; depth++) {
                final int currentDepth = depth; // Make effectively final for lambda
                log.debug("Processing depth level {} with {} URLs", currentDepth, currentLevel.size());
                
                // Submit all URLs in current level to thread pool
                List<Future<List<UrlDepth>>> futures = new ArrayList<>();
                for (UrlDepth urlDepth : currentLevel) {
                    // Check MAX_PAGES before submitting
                    if (pages.size() >= MAX_PAGES) {
                        log.debug("Reached MAX_PAGES limit ({}), stopping submission", MAX_PAGES);
                        break;
                    }
                    
                    String url = normalizeUrl(urlDepth.url);
                    
                    if (url == null) {
                        log.debug("Skipping null URL at depth {}", currentDepth);
                        continue;
                    }
                    if (visited.contains(url)) {
                        log.debug("Skipping already visited URL: {}", url);
                        continue;
                    }
                    
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
                    
                    // Mark as visited before submitting to avoid duplicate processing
                    if (visited.add(url)) {
                        processedCount++;
                        final String finalUrl = url; // Make effectively final for lambda
                        Future<List<UrlDepth>> future = pool.submit(() -> 
                            processPage(finalUrl, currentDepth, baseHost, visited, pages));
                        futures.add(future);
                    }
                }
                
                // Collect results and build next level
                List<UrlDepth> nextLevel = new ArrayList<>();
                for (Future<List<UrlDepth>> future : futures) {
                    // Check MAX_PAGES before processing each result
                    if (pages.size() >= MAX_PAGES) {
                        log.debug("Reached MAX_PAGES limit ({}), stopping result collection", MAX_PAGES);
                        break;
                    }
                    
                    try {
                        List<UrlDepth> discoveredUrls = future.get();
                        if (discoveredUrls != null) {
                            // Add discovered links to next level
                            for (UrlDepth urlDepth : discoveredUrls) {
                                // Check MAX_PAGES and MAX_DEPTH before adding
                                if (pages.size() >= MAX_PAGES) {
                                    break;
                                }
                                if (urlDepth.depth > MAX_DEPTH) {
                                    continue;
                                }
                                String normalized = normalizeUrl(urlDepth.url);
                                if (normalized != null && !visited.contains(normalized) && !isStaticAsset(normalized)) {
                                    nextLevel.add(urlDepth);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Error getting result from future: {}", e.getMessage());
                    }
                }
                
                currentLevel = nextLevel;
                log.debug("Depth {} completed, found {} URLs for next level", currentDepth, nextLevel.size());
                
                // Stop if we've reached MAX_PAGES
                if (pages.size() >= MAX_PAGES) {
                    log.debug("Reached MAX_PAGES limit ({}), stopping crawl", MAX_PAGES);
                    break;
                }
            }

            if (pages.size() >= MAX_PAGES) {
                log.info("Reached maximum page limit ({}), stopping crawl", MAX_PAGES);
            } else if (currentLevel.isEmpty()) {
                log.debug("Crawl exhausted all levels, crawl complete");
            }

            log.info("Crawl completed for baseUrl={}, processed {} pages, found {} valid pages", 
                    baseUrl, processedCount, pages.size());
            
            return new CrawlResult(baseUrl, pages);
        } catch (Exception e) {
            log.error("Failed to crawl baseUrl={}: {}", baseUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to crawl " + baseUrl, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down thread pool for CrawlServiceImpl");
        pool.shutdown();
    }

    /**
     * Processes a single page and returns discovered URLs as UrlDepth objects.
     * This method is called concurrently from the thread pool.
     * All MAX_PAGES checks are done locally without synchronized blocks.
     */
    private List<UrlDepth> processPage(String url, int depth, String baseHost, 
                                      Set<String> visited, List<PageInfo> pages) {
        try {
            log.debug("Fetching page: {} (depth: {})", url, depth);
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

            // Add page info - check MAX_PAGES locally without synchronized block
            // The synchronized list handles thread-safety, we just check size
            if (pages.size() < MAX_PAGES) {
                pages.add(new PageInfo(url, title, description, hash, PageType.PAGE));
                log.debug("Successfully processed page: {} (title: {}, hash: {})", url, title, hash);
            }

            // Collect discovered links as UrlDepth objects
            List<UrlDepth> discoveredUrls = new ArrayList<>();
            Elements links = doc.select("a[href]");
            int linksAdded = 0;
            int staticAssetsSkipped = 0;
            for (Element link : links) {
                // Check MAX_PAGES locally
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
                
                if (normalized != null) {
                    discoveredUrls.add(new UrlDepth(normalized, depth + 1));
                    linksAdded++;
                }
            }
            log.debug("Found {} links on page {}, discovered {} new URLs, skipped {} static assets", 
                    links.size(), url, linksAdded, staticAssetsSkipped);

            // Process <script src="..."> tags as static assets
            // For each external JS script: normalize URL, fetch contents, compute hash, add as STATIC_ASSET
            // Note: We do NOT parse or follow any links within script contents - only fetch and hash
            if (pages.size() < MAX_PAGES) {
                Elements scripts = doc.select("script[src]");
                int scriptsProcessed = 0;
                for (Element script : scripts) {
                    if (pages.size() >= MAX_PAGES) {
                        break;
                    }
                    String src = script.absUrl("src");
                    String normalized = normalizeUrl(src);
                    if (normalized != null && visited.add(normalized)) {
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
                            scriptsProcessed++;
                            log.debug("Successfully processed script asset: {} (hash: {})", normalized, scriptHash);
                        } catch (Exception e) {
                            log.debug("Error processing script asset {}: {}", normalized, e.getMessage());
                            // ignore per-script errors
                        }
                    }
                }
                log.debug("Found {} script sources on page {}, processed {} script assets", 
                        scripts.size(), url, scriptsProcessed);
            }

            return discoveredUrls;
        } catch (Exception e) {
            log.debug("Error processing page {}: {}", url, e.getMessage());
            return Collections.emptyList();
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
