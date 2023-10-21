package com.example.infraboxapi.user;
import com.example.infraboxapi.notification.Notification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:3000")
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

    @GetMapping("/userData")
    public ResponseEntity<UserDTO> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();

           String firstName = user.getFirstName();
            String lastName = user.getLastName();
            List<Notification> notifications = user.getNotifications();
            Role role = user.getRole();



            Notification notification = Notification.builder().
                    description("asdasd").title("asdasd").id(1L).isRead(false).author("Jakub Czubak").build();
            notifications.add(notification);
            notifications.add(notification);

            UserDTO userDTO = UserDTO.builder()
                    .firstName(firstName)
                            .lastName(lastName).notifications(notifications).role(role).notifications(notifications).build();

                 return ResponseEntity.ok(userDTO);
        } else {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }


}
