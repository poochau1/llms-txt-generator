package com.profoundai.llms.repository;

import com.profoundai.llms.entity.CrawlSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CrawlSnapshotRepository extends JpaRepository<CrawlSnapshot, Long> {

    Optional<CrawlSnapshot> findFirstByBaseUrlOrderByCreatedAtDesc(String baseUrl);

    //For generic if from website we want to crawl and not test

    @Query("select distinct cs.baseUrl from CrawlSnapshot cs")
    List<String> findAllBaseUrls();
}
