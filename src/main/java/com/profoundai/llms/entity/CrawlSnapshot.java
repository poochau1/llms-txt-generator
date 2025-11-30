package com.profoundai.llms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class CrawlSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String baseUrl;

    private LocalDateTime createdAt;

    protected CrawlSnapshot() {
    }

    public CrawlSnapshot(String baseUrl, LocalDateTime createdAt) {
        this.baseUrl = baseUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
