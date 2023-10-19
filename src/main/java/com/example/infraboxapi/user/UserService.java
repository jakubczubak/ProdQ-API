package com.example.infraboxapi.user;


import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    password(passwordEncoder.encode(ROOT_PASSWORD)).role(Role.ADMIN).email(ROOT_EMAIL)
                    .build();

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
}
