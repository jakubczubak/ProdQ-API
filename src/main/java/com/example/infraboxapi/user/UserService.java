package com.example.infraboxapi.user;


import com.example.infraboxapi.notification.Notification;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private static final String ROOT_EMAIL = "root@gmail.com";
    private static final String ROOT_PASSWORD = "root";

    @Transactional
    public void createRootUser(){

        if(checkIfEmailExist(ROOT_EMAIL)){
            logger.info("Root user already exists!");
        }else{

            User user = User.builder().
                    password(passwordEncoder.encode(ROOT_PASSWORD)).role(Role.ADMIN).email(ROOT_EMAIL).firstName("root").lastName("root").notifications(new ArrayList<>()).build();
            Notification notification = Notification.builder().user(user).author("Jakub Czubak").title("Dodawanie materiałów").isRead(false).description("Uzytkownik XYZ dodał nowy materiał...").build();
            Notification notification2 = Notification.builder().user(user).author("Jakub Czubak").title("Dodawanie narzędzi").isRead(false).description("Użytkownik xYZ dodał nowe narzędzie...").build();
            Notification notification3 = Notification.builder().user(user).author("Jakub Czubak").title("Usuwanie materiałów").isRead(true).description("Użytkownik XYZ usunął następujący material...").build();

            user.getNotifications().add(notification);
            user.getNotifications().add(notification2);
            user.getNotifications().add(notification3);
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
                .role(role)
                .password(passwordEncoder.encode(userDTO.getPassword()))
                .build();
        userRepository.save(user);
    }



    public void updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id).orElseThrow();
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        userRepository.save(user);
    }


    public ResponseEntity<UserDTO> getUserInfo(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();



        if (authentication.getPrincipal() instanceof User user) {

            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            List<Notification> notifications = user.getNotifications();
            Role role = user.getRole();


            UserDTO userDTO = UserDTO.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .role(role)
                    .notifications(notifications)
                    .build();

            return ResponseEntity.ok(userDTO);
        } else {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    public Integer getUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof User user) {
            return user.getId();
        } else {
            return null;
        }
    }


}
