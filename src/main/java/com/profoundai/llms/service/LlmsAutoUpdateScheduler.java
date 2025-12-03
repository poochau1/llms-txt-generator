package com.profoundai.llms.service;

import com.profoundai.llms.repository.CrawlSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmsAutoUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(LlmsAutoUpdateScheduler.class);

    private final LlmsTxtMonitoringService monitoringService;
    private final CrawlSnapshotRepository snapshotRepository;

    public LlmsAutoUpdateScheduler(LlmsTxtMonitoringService monitoringService,
                                   CrawlSnapshotRepository snapshotRepository) {
        this.monitoringService = monitoringService;
        this.snapshotRepository = snapshotRepository;
    }

    @Scheduled(fixedDelayString = "${llms.monitor.interval-ms:30000}")
    public void runMonitoring() {
        log.debug("Scheduled monitoring task started");
        
        log.debug("Retrieving list of monitored base URLs from repository");
        List<String> monitoredSites = snapshotRepository.findAllBaseUrls();
        log.info("runMonitoring triggered, monitoredSites={}", monitoredSites);
        log.debug("Retrieved {} monitored site(s)", monitoredSites.size());

        if (monitoredSites.isEmpty()) {
            log.debug("No monitored sites found, skipping monitoring cycle");
            return;
        }

        log.debug("Starting monitoring cycle for {} site(s)", monitoredSites.size());
        int successCount = 0;
        int failureCount = 0;
        
        for (String baseUrl : monitoredSites) {
            try {
                log.debug("Processing monitoring for baseUrl: {}", baseUrl);
                log.info("Scheduled monitoring for {}", baseUrl);
                
                log.debug("Invoking crawlAndUpdate for baseUrl: {}", baseUrl);
                MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);
                log.debug("CrawlAndUpdate completed successfully for baseUrl: {}", baseUrl);
                
                log.info("Result for {} -> added={}, removed={}, modified={}",
                        baseUrl,
                        result.getAddedUrls().size(),
                        result.getRemovedUrls().size(),
                        result.getModifiedUrls().size());
                
                log.debug("Monitoring completed successfully for baseUrl: {}, added={}, removed={}, modified={}",
                        baseUrl,
                        result.getAddedUrls().size(),
                        result.getRemovedUrls().size(),
                        result.getModifiedUrls().size());
                successCount++;
            } catch (Exception e) {
                log.error("Monitoring failed for {}: {}", baseUrl, e.getMessage(), e);
                log.debug("Exception details for baseUrl {}: exception type={}, message={}",
                        baseUrl, e.getClass().getSimpleName(), e.getMessage());
                failureCount++;
            }
        }
        
        log.info("Monitoring cycle completed: {} succeeded, {} failed out of {} total sites",
                successCount, failureCount, monitoredSites.size());
        log.debug("Scheduled monitoring task finished");
    }
}
