package com.example.infraboxapi.config;


import com.example.infraboxapi.departmentCost.DepartmentCostService;
import com.example.infraboxapi.user.UserService;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnStartUP {

    public OnStartUP(UserService userService, DepartmentCostService departmentCostService) {


        userService.createRootUser();
        departmentCostService.createDefaultDepartmentCost();

    }
}
