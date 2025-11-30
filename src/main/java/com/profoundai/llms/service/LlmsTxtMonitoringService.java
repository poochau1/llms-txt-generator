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

        Map<String, String> oldHashesByUrl = snapshotRepository.findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl)
                .map(prev -> pageMetaRepository.findBySnapshotId(prev.getId()))
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.toMap(PageMeta::getUrl, PageMeta::getContentHash, (a, b) -> a));


        CrawlService.CrawlResult crawlResult = crawlService.crawl(baseUrl);

        CrawlSnapshot snapshot = snapshotRepository.save(
                new CrawlSnapshot(baseUrl, LocalDateTime.now())
        );

        List<PageMeta> newPages = new ArrayList<>();
        for (CrawlService.PageInfo p : crawlResult.getPages()) {
            newPages.add(new PageMeta(
                    snapshot.getId(),
                    p.getUrl(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getContentHash()
            ));
        }
        pageMetaRepository.saveAll(newPages);

        Map<String, String> newHashesByUrl = newPages.stream()
                .collect(Collectors.toMap(PageMeta::getUrl, PageMeta::getContentHash, (a, b) -> a));

        Set<String> added = new HashSet<>(newHashesByUrl.keySet());
        added.removeAll(oldHashesByUrl.keySet());

        Set<String> removed = new HashSet<>(oldHashesByUrl.keySet());
        removed.removeAll(newHashesByUrl.keySet());

        Set<String> modified = oldHashesByUrl.entrySet().stream()
                .filter(e -> newHashesByUrl.containsKey(e.getKey())
                        && !Objects.equals(e.getValue(), newHashesByUrl.get(e.getKey())))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        log.info("Crawl finished for {} -> added={}, removed={}, modified={}",
                baseUrl, added.size(), removed.size(), modified.size());

        return new MonitoringResult(added, removed, modified);
    }

    @Transactional(readOnly = true)
    public String getLatestLlmsTxt(String baseUrl) {
        CrawlSnapshot snapshot = snapshotRepository
                .findFirstByBaseUrlOrderByCreatedAtDesc(baseUrl)
                .orElseThrow(() -> new IllegalStateException("No snapshot found for baseUrl=" + baseUrl));

        List<PageMeta> pages = pageMetaRepository.findBySnapshotId(snapshot.getId());
        return llmsTxtGeneratorService.generate(pages, baseUrl);
    }
}

