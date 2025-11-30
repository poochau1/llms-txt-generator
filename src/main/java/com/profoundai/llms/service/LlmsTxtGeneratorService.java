package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageMeta;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LlmsTxtGeneratorService {

    public String generate(List<PageMeta> pages, String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("# llms.txt generated for ").append(baseUrl).append("\n");
        sb.append("# Generated at ").append(LocalDateTime.now()).append("\n\n");

        for (PageMeta page : pages) {
            sb.append("URL: ").append(page.getUrl()).append("\n");
            if (page.getTitle() != null) {
                sb.append("TITLE: ").append(page.getTitle()).append("\n");
            }
            if (page.getDescription() != null) {
                sb.append("DESCRIPTION: ").append(page.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
