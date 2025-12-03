package com.profoundai.llms.controller;

import com.profoundai.llms.service.LlmsTxtMonitoringService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LlmsController {

    private final LlmsTxtMonitoringService monitoringService;

    public LlmsController(LlmsTxtMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLlmsTxt(@RequestParam String baseUrl,
                             @RequestParam(defaultValue = "false") boolean refresh) {
        // If refresh=true, force a fresh crawl before returning llms.txt
        if (refresh) {
            monitoringService.crawlAndStore(baseUrl);
        }
        return monitoringService.getLatestLlmsTxt(baseUrl);
    }

    @PostMapping("/crawl")
    public void crawl(@RequestParam String baseUrl) {
        // Normal button: only crawl if no snapshot exists yet
        monitoringService.crawlAndUpdate(baseUrl);
    }

    @PostMapping("/crawl/reset")
    public void resetAndCrawl(@RequestParam String baseUrl) {
        // Reset/force button: wipe old snapshots (if your recrawlFresh does that)
        // and perform a fresh crawl
        monitoringService.recrawlFresh(baseUrl);
    }
}

