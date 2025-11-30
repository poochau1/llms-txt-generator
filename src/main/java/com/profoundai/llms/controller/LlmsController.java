package com.profoundai.llms.controller;

import com.profoundai.llms.service.LlmsTxtMonitoringService;
import com.profoundai.llms.service.MonitoringResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LlmsController {

    private final LlmsTxtMonitoringService monitoringService;

    public LlmsController(LlmsTxtMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    // Allow GET + POST
    @RequestMapping(value = "/crawl", method = {RequestMethod.POST, RequestMethod.GET})
    public MonitoringResult crawl(@RequestParam String baseUrl) {
        return monitoringService.crawlAndUpdate(baseUrl);
    }

    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLlmsTxt(@RequestParam String baseUrl) {
        return monitoringService.getLatestLlmsTxt(baseUrl);
    }
}

