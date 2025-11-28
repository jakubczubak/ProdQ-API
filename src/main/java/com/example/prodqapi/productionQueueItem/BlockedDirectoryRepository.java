package com.example.prodqapi.productionQueueItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repozytorium dla encji BlockedDirectory.
 */
@Repository
public interface BlockedDirectoryRepository extends JpaRepository<BlockedDirectory, Long> {
    List<BlockedDirectory> findAll();
}