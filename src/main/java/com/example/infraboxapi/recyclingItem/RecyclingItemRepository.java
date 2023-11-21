package com.example.infraboxapi.recyclingItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecyclingItemRepository extends JpaRepository<RecyclingItem, Integer> {
}
