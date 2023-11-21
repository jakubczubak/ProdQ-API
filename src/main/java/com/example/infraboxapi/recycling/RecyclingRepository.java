package com.example.infraboxapi.recycling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecyclingRepository extends JpaRepository<Recycling, Integer> {
}
