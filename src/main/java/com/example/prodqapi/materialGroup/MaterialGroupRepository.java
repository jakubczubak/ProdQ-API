package com.example.prodqapi.materialGroup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, Integer> {
}
