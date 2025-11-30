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
        List<String> monitoredSites = snapshotRepository.findAllBaseUrls();
        log.info("runMonitoring triggered, monitoredSites={}", monitoredSites);

        if (monitoredSites.isEmpty()) {
            return;
        }

        for (String baseUrl : monitoredSites) {
            try {
                log.info("Scheduled monitoring for {}", baseUrl);
                MonitoringResult result = monitoringService.crawlAndUpdate(baseUrl);
                log.info("Result for {} -> added={}, removed={}, modified={}",
                        baseUrl,
                        result.getAddedUrls().size(),
                        result.getRemovedUrls().size(),
                        result.getModifiedUrls().size());
            } catch (Exception e) {
                log.error("Monitoring failed for {}: {}", baseUrl, e.getMessage(), e);
            }
        }
    }
}
