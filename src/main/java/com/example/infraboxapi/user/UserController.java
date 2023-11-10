package com.example.infraboxapi.user;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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

        try{
            return userService.getUserInfo();
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


}
