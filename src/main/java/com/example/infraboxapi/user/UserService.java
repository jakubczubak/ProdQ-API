package com.example.infraboxapi.user;


import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    password(passwordEncoder.encode(ROOT_PASSWORD)).role(Role.ADMIN).email(ROOT_EMAIL).firstName("root").lastName("root").build();


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

    public void deleteUser(Integer id) {
        userRepository.deleteById(id);
    }

    public void updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id).orElseThrow();
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        userRepository.save(user);
    }

    public User getUser(Integer id) {
        return userRepository.findById(id).orElseThrow();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }


}
