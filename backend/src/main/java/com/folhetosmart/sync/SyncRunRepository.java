package com.folhetosmart.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    Optional<SyncRun> findTopByStatusOrderByFinishedAtDesc(String status);

    Optional<SyncRun> findTopByOrderByStartedAtDesc();
}
