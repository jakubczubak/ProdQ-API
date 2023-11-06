package com.example.infraboxapi.toolGroup;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolGroupRepository extends JpaRepository<ToolGroup, Integer> {
}
