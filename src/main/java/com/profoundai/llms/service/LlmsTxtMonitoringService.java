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
        log.info("crawlAndUpdate called for baseUrl={}", baseUrl);

        // Get latest previous snapshot (if any)
        Optional<CrawlSnapshot> previousOpt =
                snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl);

        // =========================================================
        // FIRST EVER CRAWL — no previous snapshot
        // =========================================================
        if (previousOpt.isEmpty()) {
                // Do crawl but DO NOT hide results
                CrawlService.CrawlResult result = crawlService.crawl(baseUrl);

                // Convert to PageMeta and store
                CrawlSnapshot snapshot = new CrawlSnapshot(baseUrl);
                snapshotRepository.save(snapshot);

                List<PageMeta> newPages = result.getPages().stream()
                        .map(p -> new PageMeta(
                                snapshot.getId(),
                                p.getUrl(),
                                p.getTitle(),
                                p.getDescription(),
                                p.getContentHash(),
                                p.getPageType()))
                        .toList();

                pageMetaRepository.saveAll(newPages);

                // Treat ALL as "added"
                Set<String> added = result.getPages().stream()
                        .map(CrawlService.PageInfo::getUrl)
                        .collect(Collectors.toSet());

                return new MonitoringResult(added, Set.of(), Set.of());
            }


        // =========================================================
        // NORMAL DIFF CRAWL
        // =========================================================
        CrawlSnapshot previous = previousOpt.get();
        log.info("Previous snapshot exists (id={}). Performing diff crawl.", previous.getId());

        // Perform full crawl
        CrawlService.CrawlResult result = crawlService.crawl(baseUrl);

        // Compute diffs against previous snapshot
        List<PageMeta> oldPages = pageMetaRepository.findBySnapshotId(previous.getId());
        Map<String, String> oldHashes = oldPages.stream()
                .collect(Collectors.toMap(PageMeta::getUrl, PageMeta::getContentHash));

        Map<String, String> newHashes = result.getPages().stream()
                .collect(Collectors.toMap(
                        CrawlService.PageInfo::getUrl,
                        CrawlService.PageInfo::getContentHash
                ));

        // Identify additions, removals, modifications
        Set<String> added = newHashes.keySet().stream()
                .filter(url -> !oldHashes.containsKey(url))
                .collect(Collectors.toSet());

        Set<String> removed = oldHashes.keySet().stream()
                .filter(url -> !newHashes.containsKey(url))
                .collect(Collectors.toSet());

        Set<String> modified = newHashes.entrySet().stream()
                .filter(e -> oldHashes.containsKey(e.getKey())
                        && !Objects.equals(e.getValue(), oldHashes.get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Save new snapshot
        saveSnapshotWithPages(baseUrl, result);

        log.info("Diff crawl complete: added={}, removed={}, modified={}",
                added.size(), removed.size(), modified.size());

        return new MonitoringResult(added, removed, modified);
    }

    @Transactional
    protected CrawlSnapshot saveSnapshotWithPages(String baseUrl, CrawlService.CrawlResult result) {
        log.debug("Creating new crawl snapshot for baseUrl={}", baseUrl);
        CrawlSnapshot snapshot = snapshotRepository.save(
                new CrawlSnapshot(baseUrl, LocalDateTime.now())
        );
        log.debug("Crawl snapshot created with id={} for baseUrl={}", snapshot.getId(), baseUrl);

        log.debug("Processing {} pages for snapshot id={}", result.getPages().size(), snapshot.getId());
        List<PageMeta> newPages = result.getPages().stream()
                .map(p -> new PageMeta(
                        snapshot.getId(),
                        p.getUrl(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getContentHash(),
                        p.getPageType()
                ))
                .toList();

        log.debug("Saving {} page metadata records to database", newPages.size());
        pageMetaRepository.saveAll(newPages);
        log.debug("Successfully saved {} page metadata records", newPages.size());

        return snapshot;
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

    @Transactional
    public CrawlSnapshot crawlAndStore(String baseUrl) {
        log.info("Starting manual fresh crawl for baseUrl={}", baseUrl);

        // Run the crawl
        CrawlService.CrawlResult result = crawlService.crawl(baseUrl);
        log.debug("Crawl completed for baseUrl={}, pages={}", baseUrl, result.getPages().size());

        // Create and save new snapshot
        CrawlSnapshot snapshot = snapshotRepository.save(
                new CrawlSnapshot(baseUrl, LocalDateTime.now())
        );
        log.debug("Saved snapshot id={} for baseUrl={}", snapshot.getId(), baseUrl);

        // Map and save PageMeta
        List<PageMeta> pages = result.getPages().stream()
                .map(p -> new PageMeta(
                        snapshot.getId(),
                        p.getUrl(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getContentHash(),
                        p.getPageType()
                ))
                .toList();

        pageMetaRepository.saveAll(pages);
        log.info("Saved {} PageMeta rows for snapshot id={}", pages.size(), snapshot.getId());

        return snapshot;
    }

    @Transactional
    public CrawlSnapshot recrawlFresh(String baseUrl) {
        log.info("Hard recrawl requested for baseUrl={}", baseUrl);

        List<CrawlSnapshot> oldSnapshots = snapshotRepository.findByBaseUrl(baseUrl);
        if (!oldSnapshots.isEmpty()) {
            List<Long> snapshotIds = oldSnapshots.stream()
                    .map(CrawlSnapshot::getId)
                    .toList();

            log.debug("Deleting {} old snapshots and their pages for baseUrl={}", oldSnapshots.size(), baseUrl);
            pageMetaRepository.deleteBySnapshotIdIn(snapshotIds);
            snapshotRepository.deleteAll(oldSnapshots);
        }

        return crawlAndStore(baseUrl);
    }

//    @Transactional
//    public void crawlIfMissing(String baseUrl) {
//        log.info("AUTO crawl requested for baseUrl={}", baseUrl);
//
//        boolean exists = snapshotRepository
//                .findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl)
//                .isPresent();
//
//        if (exists) {
//            log.info("Snapshot already exists for baseUrl={}, skipping crawl", baseUrl);
//            return;
//        }
//
//        log.info("No snapshot found for baseUrl={}, performing initial crawl", baseUrl);
//        crawlAndStore(baseUrl);
//    }
}

