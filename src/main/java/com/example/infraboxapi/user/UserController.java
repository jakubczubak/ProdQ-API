package com.example.infraboxapi.user;
import com.example.infraboxapi.notification.Notification;
import com.example.infraboxapi.notification.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class UserController {

    private final UserService userService;




    @PostMapping("/register")
    public String register(@RequestBody UserDTO userDTO){
        userService.createUser(userDTO);

        return "User created";
    }

    @GetMapping("/userData")
    public ResponseEntity<UserDTO> getUserInfo() {
        return userService.getUserInfo();
    }


}
