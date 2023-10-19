package com.example.infraboxapi.config;


import com.example.infraboxapi.user.UserService;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnStartUP {

    private final UserService userService;

    public OnStartUP(UserService userService){
        this.userService=userService;


        userService.createRootUser();
    }
}
