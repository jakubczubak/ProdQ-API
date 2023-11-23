package com.example.infraboxapi.user;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/user/")
@CrossOrigin(origins = "http://localhost:3000")
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final CommonService commonService;


    @PostMapping("/register")
    public String register(@RequestBody UserDTO userDTO) {
        userService.createUser(userDTO);

        return "User created";
    }

    @GetMapping("/userData")
    public ResponseEntity<UserDTO> getUserInfo() {

        try {
            return userService.getUserInfo();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @PutMapping("/update")
    public ResponseEntity<String> updateUser(@Valid @RequestBody UserDTO userDTO, BindingResult bindingResult) {



        if (bindingResult.hasErrors()) {
            return commonService.handleBindingResult(bindingResult);
        }

        try {
            userService.updateUser(userDTO);
            return ResponseEntity.ok("User updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }




}
