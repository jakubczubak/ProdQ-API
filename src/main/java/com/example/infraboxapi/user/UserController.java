package com.example.infraboxapi.user;
import com.example.infraboxapi.notification.Notification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/userData")
    public ResponseEntity<User> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();

            // Teraz masz dostęp do wszystkich pól w obiekcie użytkownika
            // Możesz użyć ich do stworzenia odpowiedzi JSON
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            List<Notification> notifications = user.getNotifications();

            // Tworzenie obiektu User z potrzebnymi danymi
            User userInfo = new User();
            userInfo.setFirstName(firstName);
            userInfo.setLastName(lastName);
            userInfo.setNotifications(notifications);

            // Zwracanie obiektu User jako JSON w odpowiedzi z odpowiednim statusem HTTP
            return ResponseEntity.ok(userInfo);
        } else {
            // Możesz również obsłużyć sytuację, gdy nie można uzyskać informacji o użytkowniku
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

}
