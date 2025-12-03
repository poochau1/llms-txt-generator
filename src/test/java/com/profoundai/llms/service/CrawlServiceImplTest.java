package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CrawlServiceImplTest {

    @InjectMocks
    private CrawlServiceImpl crawlService;

    @Test
    void testNormalizeUrl_ValidUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "https://example.com/page#fragment");
        assertEquals("https://example.com/page", result);
    }

    @Test
    void testNormalizeUrl_WithFragment() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "https://example.com/test?param=value#anchor");
        assertEquals("https://example.com/test?param=value", result);
    }

    @Test
    void testNormalizeUrl_NullUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, (String) null);
        assertNull(result);
    }

    @Test
    void testNormalizeUrl_BlankUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "   ");
        assertNull(result);
    }

    @Test
    void testNormalizeUrl_NoScheme() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "example.com/page");
        assertNull(result);
    }

    @Test
    void testNormalizeUrl_InvalidUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "not a valid url!!!");
        assertNull(result);
    }

    @Test
    void testSha256_ValidText() throws Exception {
        Method sha256Method = CrawlServiceImpl.class.getDeclaredMethod("sha256", String.class);
        sha256Method.setAccessible(true);

        String result = (String) sha256Method.invoke(crawlService, "test");
        
        // SHA-256 of "test" is: 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", result);
        assertEquals(64, result.length()); // SHA-256 produces 64 hex characters
    }

    @Test
    void testSha256_EmptyString() throws Exception {
        Method sha256Method = CrawlServiceImpl.class.getDeclaredMethod("sha256", String.class);
        sha256Method.setAccessible(true);

        String result = (String) sha256Method.invoke(crawlService, "");
        
        // SHA-256 of empty string is: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }

    @Test
    void testSha256_LongText() throws Exception {
        Method sha256Method = CrawlServiceImpl.class.getDeclaredMethod("sha256", String.class);
        sha256Method.setAccessible(true);

        String longText = "This is a very long text that will be hashed. ".repeat(100);
        String result = (String) sha256Method.invoke(crawlService, longText);
        
        assertNotNull(result);
        assertEquals(64, result.length());
    }

    @Test
    void testSha256_ConsistentHashing() throws Exception {
        Method sha256Method = CrawlServiceImpl.class.getDeclaredMethod("sha256", String.class);
        sha256Method.setAccessible(true);

        String text = "consistent test";
        String hash1 = (String) sha256Method.invoke(crawlService, text);
        String hash2 = (String) sha256Method.invoke(crawlService, text);
        
        assertEquals(hash1, hash2);
    }

    @Test
    void testCrawl_InvalidBaseUrl() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            crawlService.crawl("not a valid url");
        });
        
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Failed to crawl"));
    }

    @Test
    void testCrawl_NullBaseUrl() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            crawlService.crawl(null);
        });
        
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Failed to crawl"));
    }

    @Test
    void testCrawl_EmptyBaseUrl() {
        // Empty string URL normalizes to null and returns empty result instead of throwing exception
        CrawlService.CrawlResult result = crawlService.crawl("");
        assertNotNull(result);
        assertEquals("", result.getBaseUrl());
        assertNotNull(result.getPages());
        assertEquals(0, result.getPages().size());
    }

    @Test
    void testCrawlResult_Structure() {
        CrawlService.CrawlResult result = new CrawlService.CrawlResult(
                "https://example.com",
                java.util.Collections.emptyList()
        );

        assertEquals("https://example.com", result.getBaseUrl());
        assertNotNull(result.getPages());
        assertEquals(0, result.getPages().size());
    }

    @Test
    void testPageInfo_Structure() {
        CrawlService.PageInfo pageInfo = new CrawlService.PageInfo(
                "https://example.com/page",
                "Test Page",
                "Test Description",
                "testhash123",
                PageType.PAGE
        );

        assertEquals("https://example.com/page", pageInfo.getUrl());
        assertEquals("Test Page", pageInfo.getTitle());
        assertEquals("Test Description", pageInfo.getDescription());
        assertEquals("testhash123", pageInfo.getContentHash());
        assertEquals(PageType.PAGE, pageInfo.getPageType());
    }

    @Test
    void testPageInfo_WithNullValues() {
        CrawlService.PageInfo pageInfo = new CrawlService.PageInfo(
                "https://example.com/page",
                null,
                null,
                "hash",
                PageType.STATIC_ASSET
        );

        assertEquals("https://example.com/page", pageInfo.getUrl());
        assertNull(pageInfo.getTitle());
        assertNull(pageInfo.getDescription());
        assertEquals("hash", pageInfo.getContentHash());
        assertEquals(PageType.STATIC_ASSET, pageInfo.getPageType());
    }

    @Test
    void testNormalizeUrl_HttpUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "http://example.com/page");
        assertEquals("http://example.com/page", result);
    }

    @Test
    void testNormalizeUrl_HttpsUrl() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "https://example.com/page");
        assertEquals("https://example.com/page", result);
    }

    @Test
    void testNormalizeUrl_WithQueryParams() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        String result = (String) normalizeMethod.invoke(crawlService, "https://example.com/page?param=value#fragment");
        assertEquals("https://example.com/page?param=value", result);
    }

    @Test
    void testNormalizeUrl_MultipleFragments() throws Exception {
        Method normalizeMethod = CrawlServiceImpl.class.getDeclaredMethod("normalizeUrl", String.class);
        normalizeMethod.setAccessible(true);

        // Multiple fragments are invalid per URI spec, so URI parsing fails and returns null
        String result = (String) normalizeMethod.invoke(crawlService, "https://example.com/page#fragment1#fragment2");
        assertNull(result); // Invalid URI format, so normalization returns null
    }

    // Note: Testing the actual crawl() method with real HTTP connections would require:
    // 1. A mock HTTP server (like WireMock) - would need to add dependency
    // 2. Or actual internet access and real URLs (integration test)
    // 3. Or refactoring to inject a connection factory for better testability
    
    // For now, we test:
    // - URL normalization logic (all edge cases)
    // - SHA-256 hash calculation (consistency and correctness)
    // - Data structure validation (CrawlResult, PageInfo)
    // - Error handling for invalid URLs
    // - Integration tests with real URLs would be in a separate test class
}

