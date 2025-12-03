package com.profoundai.llms.service;

import com.profoundai.llms.entity.CrawlSnapshot;
import com.profoundai.llms.entity.PageMeta;
import com.profoundai.llms.repository.CrawlSnapshotRepository;
import com.profoundai.llms.repository.PageMetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmsTxtMonitoringServiceTest {

    @Mock
    private CrawlService crawlService;

    @Mock
    private CrawlSnapshotRepository snapshotRepository;

    @Mock
    private PageMetaRepository pageMetaRepository;

    @Mock
    private LlmsTxtGeneratorService llmsTxtGeneratorService;

    @InjectMocks
    private LlmsTxtMonitoringService monitoringService;

    private String baseUrl;
    private CrawlService.CrawlResult crawlResult;
    private List<CrawlService.PageInfo> pageInfos;

    @BeforeEach
    void setUp() {
        baseUrl = "https://example.com";
        pageInfos = new ArrayList<>();
    }

    private CrawlSnapshot createSnapshotWithId(String baseUrl, LocalDateTime createdAt, Long id) {
        CrawlSnapshot snapshot = new CrawlSnapshot(baseUrl, createdAt);
        try {
            Field idField = CrawlSnapshot.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(snapshot, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set snapshot ID", e);
        }
        return snapshot;
    }

    @Test
    void testCrawlAndUpdate_FirstCrawl_NoPreviousSnapshot() {
        // Arrange
        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page1",
                "Page 1",
                "Description 1",
                "hash1"
        ));
        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page2",
                "Page 2",
                "Description 2",
                "hash2"
        ));
        crawlResult = new CrawlService.CrawlResult(baseUrl, pageInfos);

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.empty());
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getAddedUrls().size());
        assertTrue(result.getAddedUrls().contains("https://example.com/page1"));
        assertTrue(result.getAddedUrls().contains("https://example.com/page2"));
        assertEquals(0, result.getRemovedUrls().size());
        assertEquals(0, result.getModifiedUrls().size());

        verify(snapshotRepository).findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl);
        verify(crawlService).crawl(baseUrl);
        verify(snapshotRepository).save(any(CrawlSnapshot.class));
        verify(pageMetaRepository).saveAll(anyList());
    }

    @Test
    void testCrawlAndUpdate_WithPreviousSnapshot_AddedPages() {
        // Arrange
        Long previousSnapshotId = 1L;
        CrawlSnapshot previousSnapshot = createSnapshotWithId(baseUrl, LocalDateTime.now().minusDays(1), previousSnapshotId);

        List<PageMeta> previousPages = Arrays.asList(
                new PageMeta(previousSnapshotId, "https://example.com/page1", "Page 1", "Desc 1", "hash1")
        );

        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page1",
                "Page 1",
                "Description 1",
                "hash1"
        ));
        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page2",
                "Page 2",
                "Description 2",
                "hash2"
        ));
        crawlResult = new CrawlService.CrawlResult(baseUrl, pageInfos);

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.of(previousSnapshot));
        when(pageMetaRepository.findBySnapshotId(previousSnapshotId)).thenReturn(previousPages);
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getAddedUrls().size());
        assertTrue(result.getAddedUrls().contains("https://example.com/page2"));
        assertEquals(0, result.getRemovedUrls().size());
        assertEquals(0, result.getModifiedUrls().size());
    }

    @Test
    void testCrawlAndUpdate_WithPreviousSnapshot_RemovedPages() {
        // Arrange
        Long previousSnapshotId = 1L;
        CrawlSnapshot previousSnapshot = createSnapshotWithId(baseUrl, LocalDateTime.now().minusDays(1), previousSnapshotId);

        List<PageMeta> previousPages = Arrays.asList(
                new PageMeta(previousSnapshotId, "https://example.com/page1", "Page 1", "Desc 1", "hash1"),
                new PageMeta(previousSnapshotId, "https://example.com/page2", "Page 2", "Desc 2", "hash2")
        );

        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page1",
                "Page 1",
                "Description 1",
                "hash1"
        ));
        crawlResult = new CrawlService.CrawlResult(baseUrl, pageInfos);

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.of(previousSnapshot));
        when(pageMetaRepository.findBySnapshotId(previousSnapshotId)).thenReturn(previousPages);
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getAddedUrls().size());
        assertEquals(1, result.getRemovedUrls().size());
        assertTrue(result.getRemovedUrls().contains("https://example.com/page2"));
        assertEquals(0, result.getModifiedUrls().size());
    }

    @Test
    void testCrawlAndUpdate_WithPreviousSnapshot_ModifiedPages() {
        // Arrange
        Long previousSnapshotId = 1L;
        CrawlSnapshot previousSnapshot = createSnapshotWithId(baseUrl, LocalDateTime.now().minusDays(1), previousSnapshotId);

        List<PageMeta> previousPages = Arrays.asList(
                new PageMeta(previousSnapshotId, "https://example.com/page1", "Page 1", "Desc 1", "oldHash1")
        );

        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page1",
                "Page 1 Updated",
                "Description 1 Updated",
                "newHash1"
        ));
        crawlResult = new CrawlService.CrawlResult(baseUrl, pageInfos);

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.of(previousSnapshot));
        when(pageMetaRepository.findBySnapshotId(previousSnapshotId)).thenReturn(previousPages);
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getAddedUrls().size());
        assertEquals(0, result.getRemovedUrls().size());
        assertEquals(1, result.getModifiedUrls().size());
        assertTrue(result.getModifiedUrls().contains("https://example.com/page1"));
    }

    @Test
    void testCrawlAndUpdate_WithPreviousSnapshot_MixedChanges() {
        // Arrange
        Long previousSnapshotId = 1L;
        CrawlSnapshot previousSnapshot = createSnapshotWithId(baseUrl, LocalDateTime.now().minusDays(1), previousSnapshotId);

        List<PageMeta> previousPages = Arrays.asList(
                new PageMeta(previousSnapshotId, "https://example.com/page1", "Page 1", "Desc 1", "hash1"),
                new PageMeta(previousSnapshotId, "https://example.com/page2", "Page 2", "Desc 2", "oldHash2"),
                new PageMeta(previousSnapshotId, "https://example.com/page3", "Page 3", "Desc 3", "hash3")
        );

        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page1",
                "Page 1",
                "Description 1",
                "hash1"
        ));
        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page2",
                "Page 2 Updated",
                "Description 2 Updated",
                "newHash2"
        ));
        pageInfos.add(new CrawlService.PageInfo(
                "https://example.com/page4",
                "Page 4",
                "Description 4",
                "hash4"
        ));
        crawlResult = new CrawlService.CrawlResult(baseUrl, pageInfos);

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.of(previousSnapshot));
        when(pageMetaRepository.findBySnapshotId(previousSnapshotId)).thenReturn(previousPages);
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getAddedUrls().size());
        assertTrue(result.getAddedUrls().contains("https://example.com/page4"));
        assertEquals(1, result.getRemovedUrls().size());
        assertTrue(result.getRemovedUrls().contains("https://example.com/page3"));
        assertEquals(1, result.getModifiedUrls().size());
        assertTrue(result.getModifiedUrls().contains("https://example.com/page2"));
    }

    @Test
    void testGetLatestLlmsTxt_Success() {
        // Arrange
        Long snapshotId = 1L;
        CrawlSnapshot snapshot = createSnapshotWithId(baseUrl, LocalDateTime.now(), snapshotId);

        List<PageMeta> pages = Arrays.asList(
                new PageMeta(snapshotId, "https://example.com/page1", "Page 1", "Desc 1", "hash1"),
                new PageMeta(snapshotId, "https://example.com/page2", "Page 2", "Desc 2", "hash2")
        );

        String expectedTxt = "# llms.txt generated for " + baseUrl + "\nGenerated content";

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.of(snapshot));
        when(pageMetaRepository.findBySnapshotId(snapshotId)).thenReturn(pages);
        when(llmsTxtGeneratorService.generate(pages, baseUrl)).thenReturn(expectedTxt);

        // Act
        String result = monitoringService.getLatestLlmsTxt(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(expectedTxt, result);
        verify(snapshotRepository).findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl);
        verify(pageMetaRepository).findBySnapshotId(snapshotId);
        verify(llmsTxtGeneratorService).generate(pages, baseUrl);
    }

    @Test
    void testGetLatestLlmsTxt_NoSnapshotFound() {
        // Arrange
        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            monitoringService.getLatestLlmsTxt(baseUrl);
        });

        assertEquals("No snapshot found for baseUrl=" + baseUrl, exception.getMessage());
        verify(snapshotRepository).findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl);
        verify(pageMetaRepository, never()).findBySnapshotId(any());
        verify(llmsTxtGeneratorService, never()).generate(anyList(), anyString());
    }

    @Test
    void testCrawlAndUpdate_EmptyCrawlResult() {
        // Arrange
        crawlResult = new CrawlService.CrawlResult(baseUrl, Collections.emptyList());

        when(snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl))
                .thenReturn(Optional.empty());
        when(crawlService.crawl(baseUrl)).thenReturn(crawlResult);
        when(snapshotRepository.save(any(CrawlSnapshot.class))).thenAnswer(invocation -> {
            CrawlSnapshot snapshot = invocation.getArgument(0);
            // Simulate ID generation by returning a snapshot with ID = 2L
            return createSnapshotWithId(snapshot.getBaseUrl(), snapshot.getCreatedAt(), 2L);
        });
        when(pageMetaRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getAddedUrls().size());
        assertEquals(0, result.getRemovedUrls().size());
        assertEquals(0, result.getModifiedUrls().size());
        verify(pageMetaRepository).saveAll(Collections.emptyList());
    }
}

