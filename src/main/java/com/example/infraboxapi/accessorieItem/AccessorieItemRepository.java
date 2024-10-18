package com.example.infraboxapi.accessorieItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AccessorieItemRepository extends JpaRepository<AccessorieItem, Integer> {
}
