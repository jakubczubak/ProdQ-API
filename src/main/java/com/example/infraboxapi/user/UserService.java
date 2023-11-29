package com.example.infraboxapi.user;


import com.example.infraboxapi.notification.Notification;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private static final String ROOT_EMAIL = "root@gmail.com";
    private static final String ROOT_PASSWORD = "root";




    @Transactional
    public void createRootUser() {

        if (checkIfEmailExist(ROOT_EMAIL)) {
            logger.info("Root user already exists!");
        } else {

            User user = User.builder().
                    password(passwordEncoder.encode(ROOT_PASSWORD)).role(Role.ADMIN).email(ROOT_EMAIL).firstName("root").lastName("root").blocked(false).notifications(new ArrayList<>()).build();
            userRepository.save(user);
            logger.info("Root user created successfully :)");
        }
    }

    public boolean checkIfEmailExist(String email) {

        return userRepository.findByEmail(email).isPresent();
    }

    public void createUser(UserDTO userDTO) {
        createUserWithRole(userDTO, Role.USER);
    }

    public void createAdmin(UserDTO userDTO) {
        createUserWithRole(userDTO, Role.ADMIN);
    }

    private void createUserWithRole(UserDTO userDTO, Role role) {
        User user = User.builder()
                .firstName(userDTO.getFirstName())
                .lastName(userDTO.getLastName())
                .email(userDTO.getEmail())
                .blocked(false)
                .role(role)
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .build();
        userRepository.save(user);
    }


    public void updateUser(UserDTO userDTO) {
        User user = userRepository.findById(userDTO.getId()).orElseThrow();

        if(userDTO.getActualPassword() != null && !userDTO.getActualPassword().isEmpty()) {
            if(!passwordEncoder.matches(userDTO.getActualPassword(), user.getPassword())) {
                throw new RuntimeException("Wrong password!");
            }
        }

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if(userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(user);
        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been updated.", NotificationDescription.UserUpdated);
    }


    public void updateUserListAccount(UserDTO userDTO) {
        User user = userRepository.findById(userDTO.getId()).orElseThrow();

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if(userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(user);
        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been updated.", NotificationDescription.UserUpdated);
    }

    public ResponseEntity<UserDTO> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();


        if (authentication.getPrincipal() instanceof User user) {

            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            String email = user.getEmail();
            Integer id = user.getId();
            List<Notification> notifications = user.getNotifications();
            Role role = user.getRole();


            UserDTO userDTO = UserDTO.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .notifications(notifications)
                    .email(email)
                    .id(id)
                    .build();

            return ResponseEntity.ok(userDTO);
        } else {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    public List<User> getUserListWithoutLoggedUserAndRootUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User loggedInUser = (User) authentication.getPrincipal();
            Integer loggedInUserId = loggedInUser.getId();

            User rootUser = userRepository.findByEmail(ROOT_EMAIL).orElseThrow();

            List<User> users = userRepository.findAll();
            users.removeIf(user -> user.getId().equals(loggedInUserId));
            users.removeIf(user -> user.getId().equals(rootUser.getId()));

            return users;
        } else {
            // Handle the case when authentication is not available or the principal is not a User
            return Collections.emptyList(); // or throw an exception, log an error, etc.
        }
    }
    public void blockUser(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setBlocked(true);
        userRepository.save(user);

        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been blocked.", NotificationDescription.BlockUser);

    }

    public void unblockUser(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setBlocked(false);
        userRepository.save(user);

        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been unblocked.", NotificationDescription.UnblockUser);

    }

    public void grantAdminPermission(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been granted admin permission.", NotificationDescription.GrantAdminPermission);
    }

    public void revokeAdminPermission(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setRole(Role.USER);
        userRepository.save(user);

        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been revoked admin permission.", NotificationDescription.RevokeAdminPermission);
    }

    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow();
        userRepository.deleteById(userId);

        notificationService.createAndSendNotification(user.getFirstName() + " " + user.getLastName() + " has been deleted.", NotificationDescription.RevokeAdminPermission);
    }

    public Boolean checkEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
