package com.profoundai.llms.entity;

import jakarta.persistence.*;

@Entity
public class PageMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long snapshotId;

    @Column(length = 1000)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 64)
    private String contentHash;

    protected PageMeta() {
    }

    public PageMeta(Long snapshotId, String url, String title, String description, String contentHash) {
        this.snapshotId = snapshotId;
        this.url = url;
        this.title = title;
        this.description = description;
        this.contentHash = contentHash;
    }

    public Long getId() {
        return id;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getContentHash() {
        return contentHash;
    }
}
