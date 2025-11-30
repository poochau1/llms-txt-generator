package com.profoundai.llms.service;

import java.util.Set;

public class MonitoringResult {

    private final Set<String> addedUrls;
    private final Set<String> removedUrls;
    private final Set<String> modifiedUrls;

    public MonitoringResult(Set<String> addedUrls,
                            Set<String> removedUrls,
                            Set<String> modifiedUrls) {
        this.addedUrls = addedUrls;
        this.removedUrls = removedUrls;
        this.modifiedUrls = modifiedUrls;
    }

    public Set<String> getAddedUrls() {
        return addedUrls;
    }

    public Set<String> getRemovedUrls() {
        return removedUrls;
    }

    public Set<String> getModifiedUrls() {
        return modifiedUrls;
    }
}

