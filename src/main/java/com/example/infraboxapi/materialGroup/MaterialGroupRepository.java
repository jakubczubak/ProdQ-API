package com.example.infraboxapi.materialGroup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, Integer> {
}
