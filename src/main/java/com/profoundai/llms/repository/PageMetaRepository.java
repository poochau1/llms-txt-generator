package com.profoundai.llms.repository;

import com.profoundai.llms.entity.PageMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageMetaRepository extends JpaRepository<PageMeta, Long> {

    List<PageMeta> findBySnapshotId(Long snapshotId);
}

