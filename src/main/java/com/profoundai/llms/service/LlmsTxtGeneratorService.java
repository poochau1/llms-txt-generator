package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LlmsTxtGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(LlmsTxtGeneratorService.class);

    public String generate(List<PageMeta> pages, String baseUrl) {
        log.info("Starting LLMS txt generation for baseUrl={} with {} pages", baseUrl, pages != null ? pages.size() : 0);
        
        if (pages == null) {
            log.warn("Pages list is null for baseUrl={}, returning empty result", baseUrl);
            return "";
        }
        
        if (pages.isEmpty()) {
            log.debug("Pages list is empty for baseUrl={}", baseUrl);
        }
        
        log.debug("Initializing StringBuilder for LLMS txt generation");
        StringBuilder sb = new StringBuilder();
        
        LocalDateTime generationTime = LocalDateTime.now();
        log.debug("Setting generation timestamp: {}", generationTime);
        
        sb.append("# llms.txt generated for ").append(baseUrl).append("\n");
        sb.append("# Generated at ").append(generationTime).append("\n\n");
        log.debug("Added header to LLMS txt for baseUrl={}", baseUrl);

        int pagesWithTitle = 0;
        int pagesWithDescription = 0;
        int pagesProcessed = 0;
        
        log.debug("Processing {} pages for baseUrl={}", pages.size(), baseUrl);
        for (PageMeta page : pages) {
            pagesProcessed++;
            log.trace("Processing page {} of {}: url={}", pagesProcessed, pages.size(), page.getUrl());
            
            sb.append("URL: ").append(page.getUrl()).append("\n");
            
            if (page.getTitle() != null) {
                sb.append("TITLE: ").append(page.getTitle()).append("\n");
                pagesWithTitle++;
                log.trace("Added title for page: {}", page.getUrl());
            } else {
                log.trace("No title found for page: {}", page.getUrl());
            }
            
            if (page.getDescription() != null) {
                sb.append("DESCRIPTION: ").append(page.getDescription()).append("\n");
                pagesWithDescription++;
                log.trace("Added description for page: {}", page.getUrl());
            } else {
                log.trace("No description found for page: {}", page.getUrl());
            }
            
            sb.append("\n");
        }
        
        String result = sb.toString();
        int resultLength = result.length();
        
        log.debug("LLMS txt generation completed for baseUrl={}: processed {} pages, {} with title, {} with description, result length={}",
                baseUrl, pagesProcessed, pagesWithTitle, pagesWithDescription, resultLength);
        log.info("Successfully generated LLMS txt for baseUrl={}, result length={} characters", baseUrl, resultLength);

        return result;
    }
}
