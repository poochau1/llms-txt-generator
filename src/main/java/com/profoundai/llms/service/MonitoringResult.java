package com.profoundai.llms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class MonitoringResult {

    private static final Logger log = LoggerFactory.getLogger(MonitoringResult.class);

    private final Set<String> addedUrls;
    private final Set<String> removedUrls;
    private final Set<String> modifiedUrls;

    public MonitoringResult(Set<String> addedUrls,
                            Set<String> removedUrls,
                            Set<String> modifiedUrls) {
        log.debug("Creating MonitoringResult: added={}, removed={}, modified={}",
                addedUrls != null ? addedUrls.size() : 0,
                removedUrls != null ? removedUrls.size() : 0,
                modifiedUrls != null ? modifiedUrls.size() : 0);
        
        this.addedUrls = addedUrls;
        this.removedUrls = removedUrls;
        this.modifiedUrls = modifiedUrls;
        
        log.trace("MonitoringResult created with addedUrls={}, removedUrls={}, modifiedUrls={}",
                addedUrls, removedUrls, modifiedUrls);
    }

    public Set<String> getAddedUrls() {
        log.trace("Accessing getAddedUrls, returning {} URLs", addedUrls != null ? addedUrls.size() : 0);
        return addedUrls;
    }

    public Set<String> getRemovedUrls() {
        log.trace("Accessing getRemovedUrls, returning {} URLs", removedUrls != null ? removedUrls.size() : 0);
        return removedUrls;
    }

    public Set<String> getModifiedUrls() {
        log.trace("Accessing getModifiedUrls, returning {} URLs", modifiedUrls != null ? modifiedUrls.size() : 0);
        return modifiedUrls;
    }
}

