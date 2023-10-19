package com.example.infraboxapi.user;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")

public class UserController {

    private final UserService userService;

    public UserController(UserService userService){
        this.userService=userService;
    }


    @PostMapping("/register")
    public String register(@RequestBody UserDTO userDTO){
        userService.createUser(userDTO);

        return "User created";
    }

    @GetMapping("/userInfo")
    public String getUserInfo(){
        return "userInfo";
    }

}
