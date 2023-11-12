package com.example.infraboxapi.departmentCost;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentCostRepository extends JpaRepository<DepartmentCost, Integer> {
}
