package com.profoundai.llms.service;

import com.profoundai.llms.repository.CrawlSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmsAutoUpdateSchedulerTest {

    @Mock
    private LlmsTxtMonitoringService monitoringService;

    @Mock
    private CrawlSnapshotRepository snapshotRepository;

    @InjectMocks
    private LlmsAutoUpdateScheduler scheduler;

    private String baseUrl1;
    private String baseUrl2;
    private MonitoringResult successResult;

    @BeforeEach
    void setUp() {
        baseUrl1 = "https://example.com";
        baseUrl2 = "https://test.com";
        
        successResult = new MonitoringResult(
                new HashSet<>(Arrays.asList("https://example.com/page1")),
                new HashSet<>(),
                new HashSet<>()
        );
    }

    @Test
    void testRunMonitoring_EmptyMonitoredSites() {
        // Arrange
        when(snapshotRepository.findAllBaseUrls()).thenReturn(Collections.emptyList());

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService, never()).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_SingleSiteSuccess() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1);
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(successResult);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService, times(1)).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_MultipleSitesSuccess() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1, baseUrl2);
        MonitoringResult result2 = new MonitoringResult(
                new HashSet<>(),
                new HashSet<>(Arrays.asList("https://test.com/old")),
                new HashSet<>(Arrays.asList("https://test.com/modified"))
        );

        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(successResult);
        when(monitoringService.crawlAndUpdate(baseUrl2)).thenReturn(result2);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService).crawlAndUpdate(baseUrl2);
        verify(monitoringService, times(2)).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_SingleSiteFailure() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1);
        RuntimeException exception = new RuntimeException("Crawl failed");
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenThrow(exception);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService, times(1)).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_MultipleSitesWithFailure() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1, baseUrl2);
        RuntimeException exception = new RuntimeException("Crawl failed for site 2");
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(successResult);
        when(monitoringService.crawlAndUpdate(baseUrl2)).thenThrow(exception);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService).crawlAndUpdate(baseUrl2);
        verify(monitoringService, times(2)).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_AllSitesFail() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1, baseUrl2);
        RuntimeException exception1 = new RuntimeException("Crawl failed for site 1");
        RuntimeException exception2 = new RuntimeException("Crawl failed for site 2");
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenThrow(exception1);
        when(monitoringService.crawlAndUpdate(baseUrl2)).thenThrow(exception2);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService).crawlAndUpdate(baseUrl2);
        verify(monitoringService, times(2)).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_WithNullResult() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1);
        MonitoringResult nullResult = new MonitoringResult(
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet()
        );
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(nullResult);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
    }

    @Test
    void testRunMonitoring_WithLargeResult() {
        // Arrange
        List<String> monitoredSites = Arrays.asList(baseUrl1);
        Set<String> largeAddedSet = new HashSet<>();
        Set<String> largeRemovedSet = new HashSet<>();
        Set<String> largeModifiedSet = new HashSet<>();
        
        for (int i = 0; i < 100; i++) {
            largeAddedSet.add("https://example.com/page" + i);
            largeRemovedSet.add("https://example.com/old" + i);
            largeModifiedSet.add("https://example.com/modified" + i);
        }
        
        MonitoringResult largeResult = new MonitoringResult(largeAddedSet, largeRemovedSet, largeModifiedSet);
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(largeResult);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
    }

    @Test
    void testRunMonitoring_RepositoryThrowsException() {
        // Arrange
        RuntimeException repositoryException = new RuntimeException("Database error");
        when(snapshotRepository.findAllBaseUrls()).thenThrow(repositoryException);

        // Act & Assert
        RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> scheduler.runMonitoring()
        );

        assertEquals("Database error", exception.getMessage());
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService, never()).crawlAndUpdate(anyString());
    }

    @Test
    void testRunMonitoring_ThreeSitesMixedResults() {
        // Arrange
        String baseUrl3 = "https://third.com";
        List<String> monitoredSites = Arrays.asList(baseUrl1, baseUrl2, baseUrl3);
        
        MonitoringResult result1 = new MonitoringResult(
                new HashSet<>(Arrays.asList("page1")),
                new HashSet<>(),
                new HashSet<>()
        );
        MonitoringResult result3 = new MonitoringResult(
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(Arrays.asList("modified1"))
        );
        RuntimeException exception = new RuntimeException("Site 2 failed");
        
        when(snapshotRepository.findAllBaseUrls()).thenReturn(monitoredSites);
        when(monitoringService.crawlAndUpdate(baseUrl1)).thenReturn(result1);
        when(monitoringService.crawlAndUpdate(baseUrl2)).thenThrow(exception);
        when(monitoringService.crawlAndUpdate(baseUrl3)).thenReturn(result3);

        // Act
        scheduler.runMonitoring();

        // Assert
        verify(snapshotRepository).findAllBaseUrls();
        verify(monitoringService).crawlAndUpdate(baseUrl1);
        verify(monitoringService).crawlAndUpdate(baseUrl2);
        verify(monitoringService).crawlAndUpdate(baseUrl3);
        verify(monitoringService, times(3)).crawlAndUpdate(anyString());
    }
}

