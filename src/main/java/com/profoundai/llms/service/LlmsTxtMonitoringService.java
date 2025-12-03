package com.profoundai.llms.service;

import com.profoundai.llms.entity.CrawlSnapshot;
import com.profoundai.llms.entity.PageMeta;
import com.profoundai.llms.repository.CrawlSnapshotRepository;
import com.profoundai.llms.repository.PageMetaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LlmsTxtMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(LlmsTxtMonitoringService.class);

    private final CrawlService crawlService;
    private final CrawlSnapshotRepository snapshotRepository;
    private final PageMetaRepository pageMetaRepository;
    private final LlmsTxtGeneratorService llmsTxtGeneratorService;

    public LlmsTxtMonitoringService(CrawlService crawlService,
                                    CrawlSnapshotRepository snapshotRepository,
                                    PageMetaRepository pageMetaRepository,
                                    LlmsTxtGeneratorService llmsTxtGeneratorService) {
        this.crawlService = crawlService;
        this.snapshotRepository = snapshotRepository;
        this.pageMetaRepository = pageMetaRepository;
        this.llmsTxtGeneratorService = llmsTxtGeneratorService;
    }

    @Transactional
    public MonitoringResult crawlAndUpdate(String baseUrl) {
        log.info("Starting crawl for baseUrl={}", baseUrl);

        log.debug("Retrieving previous snapshot and page metadata for baseUrl={}", baseUrl);
        Map<String, String> oldHashesByUrl = snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl)
                .map(prev -> pageMetaRepository.findBySnapshotId(prev.getId()))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(PageMeta::getUrl, PageMeta::getContentHash, (a, b) -> a));
        log.debug("Retrieved {} previous page hashes for baseUrl={}", oldHashesByUrl.size(), baseUrl);

        log.info("Initiating crawl operation for baseUrl={}", baseUrl);
        CrawlService.CrawlResult crawlResult = crawlService.crawl(baseUrl);
        log.info("Crawl operation completed for baseUrl={}, found {} pages", baseUrl, crawlResult.getPages().size());

        log.debug("Creating new crawl snapshot for baseUrl={}", baseUrl);
        CrawlSnapshot snapshot = snapshotRepository.save(
                new CrawlSnapshot(baseUrl, LocalDateTime.now())
        );
        log.debug("Crawl snapshot created with id={} for baseUrl={}", snapshot.getId(), baseUrl);

        log.debug("Processing {} pages for snapshot id={}", crawlResult.getPages().size(), snapshot.getId());
        List<PageMeta> newPages = new ArrayList<>();
        for (CrawlService.PageInfo p : crawlResult.getPages()) {
            newPages.add(new PageMeta(
                    snapshot.getId(),
                    p.getUrl(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getContentHash(),
                    p.getPageType()
            ));
        }
        log.debug("Created {} PageMeta objects for snapshot id={}", newPages.size(), snapshot.getId());

        log.debug("Saving {} page metadata records to database", newPages.size());
        pageMetaRepository.saveAll(newPages);
        log.debug("Successfully saved {} page metadata records", newPages.size());

        log.debug("Building hash map from new pages for comparison");
        Map<String, String> newHashesByUrl = newPages.stream()
                .collect(Collectors.toMap(PageMeta::getUrl, PageMeta::getContentHash, (a, b) -> a));
        log.debug("Built hash map with {} entries from new pages", newHashesByUrl.size());

        log.debug("Calculating added pages (present in new, absent in old)");
        Set<String> added = new HashSet<>(newHashesByUrl.keySet());
        added.removeAll(oldHashesByUrl.keySet());
        log.debug("Found {} added pages", added.size());

        log.debug("Calculating removed pages (present in old, absent in new)");
        Set<String> removed = new HashSet<>(oldHashesByUrl.keySet());
        removed.removeAll(newHashesByUrl.keySet());
        log.debug("Found {} removed pages", removed.size());

        log.debug("Calculating modified pages (present in both but with different hashes)");
        Set<String> modified = oldHashesByUrl.entrySet().stream()
                .filter(e -> newHashesByUrl.containsKey(e.getKey())
                        && !Objects.equals(e.getValue(), newHashesByUrl.get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        log.debug("Found {} modified pages", modified.size());

        log.info("Crawl finished for {} -> added={}, removed={}, modified={}",
                baseUrl, added.size(), removed.size(), modified.size());

        return new MonitoringResult(added, removed, modified);
    }

    @Transactional(readOnly = true)
    public String getLatestLlmsTxt(String baseUrl) {
        log.info("Retrieving latest LLMS txt for baseUrl={}", baseUrl);

        log.debug("Looking up latest snapshot for baseUrl={}", baseUrl);
        CrawlSnapshot snapshot = snapshotRepository
                .findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl)
                .orElseThrow(() -> {
                    log.error("No snapshot found for baseUrl={}", baseUrl);
                    return new IllegalStateException("No snapshot found for baseUrl=" + baseUrl);
                });
        log.debug("Found snapshot with id={} for baseUrl={}, created at {}", snapshot.getId(), baseUrl, snapshot.getCreatedAt());

        log.debug("Retrieving page metadata for snapshot id={}", snapshot.getId());
        List<PageMeta> pages = pageMetaRepository.findBySnapshotId(snapshot.getId());
        return llmsTxtGeneratorService.generate(pages, baseUrl);
    }
}

