package com.example.infraboxapi.user;

import com.example.infraboxapi.common.CommonService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/user/")
@AllArgsConstructor
public class UserController {

    private final UserService userService;
    private final CommonService commonService;


    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserDTO userDTO) {

        try{
            userService.createUser(userDTO);
            return ResponseEntity.ok("User registered");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
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

    @PutMapping("/update/user_account")
    public ResponseEntity<String> updateUser(@RequestBody UserDTO userDTO) {

        System.out.println(userDTO);

        try {
            userService.updateUserListAccount(userDTO);
            return ResponseEntity.ok("User updated");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<User>> getUserList(){
        try {
            return ResponseEntity.ok(userService.getUserListWithoutLoggedUserAndRootUser());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @PutMapping("/manageUser/{userId}/{action}")
    public ResponseEntity<String> manageUser(@PathVariable Integer userId, @PathVariable String action) {
        try {
            switch (action) {
                case "block":
                    userService.blockUser(userId);
                    return ResponseEntity.ok("User blocked");
                case "unblock":
                    userService.unblockUser(userId);
                    return ResponseEntity.ok("User unblocked");
                case "grantAdmin":
                    userService.grantAdminPermission(userId);
                    return ResponseEntity.ok("Admin permission granted");
                case "revokeAdmin":
                    userService.revokeAdminPermission(userId);
                    return ResponseEntity.ok("Admin permission revoked");
                default:
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid action");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer userId) {
        try {
            userService.deleteUser(userId);
            return ResponseEntity.ok("User deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/email/check/{email}")
    public ResponseEntity<Boolean> checkEmail(@PathVariable String email) {
        try {
            if(userService.checkEmail(email)) {
                return ResponseEntity.ok(true);
            } else {
                return ResponseEntity.ok(false);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}
