package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LlmsTxtGeneratorServiceTest {

    @InjectMocks
    private LlmsTxtGeneratorService generatorService;

    private String baseUrl;
    private Long snapshotId;

    @BeforeEach
    void setUp() {
        baseUrl = "https://example.com";
        snapshotId = 1L;
    }

    @Test
    void testGenerate_SinglePageWithTitleAndDescription() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", "Page Title", "Page Description", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# llms.txt generated for " + baseUrl));
        assertTrue(result.contains("# Generated at"));
        assertTrue(result.contains("URL: https://example.com/page1"));
        assertTrue(result.contains("TITLE: Page Title"));
        assertTrue(result.contains("DESCRIPTION: Page Description"));
    }

    @Test
    void testGenerate_PageWithNullTitle() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", null, "Page Description", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page1"));
        assertTrue(result.contains("DESCRIPTION: Page Description"));
        assertFalse(result.contains("TITLE:"));
    }

    @Test
    void testGenerate_PageWithNullDescription() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", "Page Title", null, "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page1"));
        assertTrue(result.contains("TITLE: Page Title"));
        assertFalse(result.contains("DESCRIPTION:"));
    }

    @Test
    void testGenerate_PageWithNullTitleAndDescription() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", null, null, "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page1"));
        assertFalse(result.contains("TITLE:"));
        assertFalse(result.contains("DESCRIPTION:"));
    }

    @Test
    void testGenerate_MultiplePages() {
        // Arrange
        PageMeta page1 = new PageMeta(snapshotId, "https://example.com/page1", "Title 1", "Desc 1", "hash1");
        PageMeta page2 = new PageMeta(snapshotId, "https://example.com/page2", "Title 2", "Desc 2", "hash2");
        PageMeta page3 = new PageMeta(snapshotId, "https://example.com/page3", null, "Desc 3", "hash3");
        List<PageMeta> pages = Arrays.asList(page1, page2, page3);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page1"));
        assertTrue(result.contains("URL: https://example.com/page2"));
        assertTrue(result.contains("URL: https://example.com/page3"));
        assertTrue(result.contains("TITLE: Title 1"));
        assertTrue(result.contains("TITLE: Title 2"));
        assertTrue(result.contains("DESCRIPTION: Desc 1"));
        assertTrue(result.contains("DESCRIPTION: Desc 2"));
        assertTrue(result.contains("DESCRIPTION: Desc 3"));
        // page3 should not have TITLE
        int title3Index = result.indexOf("URL: https://example.com/page3");
        int nextUrlIndex = result.indexOf("URL:", title3Index + 1);
        if (nextUrlIndex == -1) {
            nextUrlIndex = result.length();
        }
        String page3Section = result.substring(title3Index, nextUrlIndex);
        assertFalse(page3Section.contains("TITLE:"));
    }

    @Test
    void testGenerate_EmptyPagesList() {
        // Arrange
        List<PageMeta> pages = Collections.emptyList();

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# llms.txt generated for " + baseUrl));
        assertTrue(result.contains("# Generated at"));
        // Should only have header, no page content
        String[] lines = result.split("\n");
        int contentLines = 0;
        for (String line : lines) {
            if (line.startsWith("URL:") || line.startsWith("TITLE:") || line.startsWith("DESCRIPTION:")) {
                contentLines++;
            }
        }
        assertEquals(0, contentLines);
    }

    @Test
    void testGenerate_NullPagesList() {
        // Arrange
        List<PageMeta> pages = null;

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    void testGenerate_FormatVerification() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", "Test Title", "Test Description", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        String[] lines = result.split("\n");
        assertTrue(lines.length >= 5); // Header + page content
        assertEquals("# llms.txt generated for " + baseUrl, lines[0]);
        assertTrue(lines[1].startsWith("# Generated at"));
        assertEquals("", lines[2]); // Empty line after header
        assertEquals("URL: https://example.com/page1", lines[3]);
        assertEquals("TITLE: Test Title", lines[4]);
        assertEquals("DESCRIPTION: Test Description", lines[5]);
        // Check if there's an empty line after page (may or may not be present depending on trailing newline)
        if (lines.length > 6) {
            assertEquals("", lines[6]); // Empty line after page
        }
    }

    @Test
    void testGenerate_WithSpecialCharacters() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page?param=value&other=test", 
                "Title with \"quotes\" & special chars", "Description with\nnewline & <tags>", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page?param=value&other=test"));
        assertTrue(result.contains("TITLE: Title with \"quotes\" & special chars"));
        assertTrue(result.contains("DESCRIPTION: Description with\nnewline & <tags>"));
    }

    @Test
    void testGenerate_WithEmptyStrings() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", "", "", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("URL: https://example.com/page1"));
        // Empty strings are not null, so they should be included
        assertTrue(result.contains("TITLE: "));
        assertTrue(result.contains("DESCRIPTION: "));
    }

    @Test
    void testGenerate_BaseUrlInHeader() {
        // Arrange
        String customBaseUrl = "https://custom-site.com";
        PageMeta page = new PageMeta(snapshotId, "https://custom-site.com/page1", "Title", "Desc", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, customBaseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# llms.txt generated for " + customBaseUrl));
    }

    @Test
    void testGenerate_LargePageList() {
        // Arrange
        List<PageMeta> pages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            pages.add(new PageMeta(snapshotId, "https://example.com/page" + i, "Title " + i, "Desc " + i, "hash" + i));
        }

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# llms.txt generated for " + baseUrl));
        // Verify all pages are included
        for (int i = 0; i < 100; i++) {
            assertTrue(result.contains("URL: https://example.com/page" + i));
            assertTrue(result.contains("TITLE: Title " + i));
            assertTrue(result.contains("DESCRIPTION: Desc " + i));
        }
    }

    @Test
    void testGenerate_ResultContainsTimestamp() {
        // Arrange
        PageMeta page = new PageMeta(snapshotId, "https://example.com/page1", "Title", "Desc", "hash1");
        List<PageMeta> pages = Arrays.asList(page);

        // Act
        String result = generatorService.generate(pages, baseUrl);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("# Generated at"));
        // Should contain a timestamp - LocalDateTime format is YYYY-MM-DDTHH:MM:SS
        // Check that it contains the pattern after "# Generated at "
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.startsWith("# Generated at")) {
                // Extract the timestamp part
                String timestampPart = line.substring("# Generated at ".length());
                // Should match LocalDateTime format: YYYY-MM-DDTHH:MM:SS or with nanoseconds
                assertTrue(timestampPart.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"),
                        "Timestamp should match LocalDateTime format, got: " + timestampPart);
                break;
            }
        }
    }
}

