package com.profoundai.llms.service;

import com.profoundai.llms.entity.PageType;

import java.util.List;

public interface CrawlService {

    CrawlResult crawl(String baseUrl);

    class PageInfo {
        private final String url;
        private final String title;
        private final String description;
        private final String contentHash;
        private final PageType pageType;

        public PageInfo(String url, String title, String description, String contentHash, PageType pageType) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.contentHash = contentHash;
            this.pageType = pageType;
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

        public PageType getPageType() {
            return pageType;
        }
    }

    class CrawlResult {
        private final String baseUrl;
        private final List<PageInfo> pages;

        public CrawlResult(String baseUrl, List<PageInfo> pages) {
            this.baseUrl = baseUrl;
            this.pages = pages;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public List<PageInfo> getPages() {
            return pages;
        }
    }
}
