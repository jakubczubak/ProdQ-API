package com.example.prodqapi.config;


import com.example.prodqapi.user.UserService;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnStartUP {

    public OnStartUP(UserService userService) {


        userService.createRootUser();

    }
}
