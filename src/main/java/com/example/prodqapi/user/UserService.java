package com.example.prodqapi.user;


import com.example.prodqapi.notification.Notification;
import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String ADMIN_EMAIL = "admin@prodq.local";
    private static final String INITIAL_PASSWORD_FILE = "/var/lib/prodq/initial-admin-password.txt";
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    /**
     * Generates a secure random password (32 characters)
     */
    private String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        return random.ints(32, 0, chars.length())
                .mapToObj(i -> String.valueOf(chars.charAt(i)))
                .collect(Collectors.joining());
    }

    /**
     * Saves password to file for first-time retrieval
     */
    private void saveInitialPassword(String password) {
        try {
            Path passwordFile = Paths.get(INITIAL_PASSWORD_FILE);
            Files.createDirectories(passwordFile.getParent());
            Files.write(passwordFile,
                    Arrays.asList(
                            "ProdQ Initial Admin Password",
                            "Generated: " + LocalDateTime.now(),
                            "",
                            "Email: " + ADMIN_EMAIL,
                            "Password: " + password,
                            "",
                            "⚠️ IMPORTANT: Change this password on first login!",
                            "⚠️ This file will be deleted after password change."
                    ),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            // Set file permissions (owner read/write only) - only on Unix/Linux systems
            try {
                Files.setPosixFilePermissions(passwordFile,
                        PosixFilePermissions.fromString("rw-------"));
                logger.info("POSIX file permissions set (rw-------)");
            } catch (UnsupportedOperationException e) {
                // Windows doesn't support POSIX permissions - that's OK for development
                logger.warn("POSIX permissions not supported on this system (Windows). File saved without permissions restriction.");
            }

            logger.info("Initial password saved to: {}", INITIAL_PASSWORD_FILE);
        } catch (IOException e) {
            logger.error("Failed to save initial password to file", e);
        }
    }

    @Transactional
    public void createRootUser() {
        if (checkIfEmailExist(ADMIN_EMAIL)) {
            logger.info("Admin user '{}' already exists", ADMIN_EMAIL);
            return;
        }

        // Generate secure random password
        String initialPassword = generateSecurePassword();

        // Log password (visible in console/logs during setup)
        logger.warn("═══════════════════════════════════════════════════════════");
        logger.warn("⚠️  INITIAL ADMIN PASSWORD GENERATED");
        logger.warn("═══════════════════════════════════════════════════════════");
        logger.warn("Email:    {}", ADMIN_EMAIL);
        logger.warn("Password: {}", initialPassword);
        logger.warn("");
        logger.warn("⚠️  IMPORTANT: Change this password on first login!");
        logger.warn("⚠️  Password saved to: {}", INITIAL_PASSWORD_FILE);
        logger.warn("═══════════════════════════════════════════════════════════");

        // Save to file
        saveInitialPassword(initialPassword);

        // Create admin user
        User admin = User.builder()
                .password(passwordEncoder.encode(initialPassword))
                .role(Role.ADMIN)
                .email(ADMIN_EMAIL)
                .firstName("Admin")
                .lastName("ProdQ")
                .blocked(false)
                .passwordChangeRequired(true)  // Force password change
                .notifications(new ArrayList<>())
                .build();

        userRepository.save(admin);
        logger.info("✅ Admin user created: {}", ADMIN_EMAIL);
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

        if (userDTO.getActualPassword() != null && !userDTO.getActualPassword().isEmpty()) {
            if (!passwordEncoder.matches(userDTO.getActualPassword(), user.getPassword())) {
                throw new RuntimeException("Wrong password!");
            }
        }

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(user);
        notificationService.sendNotification(NotificationDescription.UserUpdated, Map.of("name", user.getFirstName() + " " + user.getLastName()));
    }


    public void updateUserListAccount(UserDTO userDTO) {
        User user = userRepository.findById(userDTO.getId()).orElseThrow();

        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(user);
        notificationService.sendNotification(NotificationDescription.UserUpdated, Map.of("name", user.getFirstName() + " " + user.getLastName()));
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

        if (authentication != null && authentication.getPrincipal() instanceof User loggedInUser) {
            Integer loggedInUserId = loggedInUser.getId();

            List<User> users = userRepository.findAll();
            users.removeIf(user -> user.getId().equals(loggedInUserId));

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

        notificationService.sendNotification(NotificationDescription.BlockUser, Map.of("name", user.getFirstName() + " " + user.getLastName()));

    }

    public void unblockUser(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setBlocked(false);
        userRepository.save(user);

        notificationService.sendNotification(NotificationDescription.UnblockUser, Map.of("name", user.getFirstName() + " " + user.getLastName()));

    }

    public void grantAdminPermission(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        notificationService.sendNotification(NotificationDescription.GrantAdminPermission, Map.of("name", user.getFirstName() + " " + user.getLastName()));
    }

    public void revokeAdminPermission(Integer userId) {

        User user = userRepository.findById(userId).orElseThrow();
        user.setRole(Role.USER);
        userRepository.save(user);

        notificationService.sendNotification(NotificationDescription.RevokeAdminPermission, Map.of("name", user.getFirstName() + " " + user.getLastName()));
    }

    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId).orElseThrow();
        userRepository.deleteById(userId);

        notificationService.sendNotification(NotificationDescription.DeleteUser, Map.of("name", user.getFirstName() + " " + user.getLastName()));
    }

    public Boolean checkEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Transactional
    public void changePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));

        // Clear password change flag
        user.setPasswordChangeRequired(false);

        userRepository.save(user);

        // Delete initial password file if exists
        try {
            Path passwordFile = Paths.get(INITIAL_PASSWORD_FILE);
            if (Files.exists(passwordFile)) {
                Files.delete(passwordFile);
                logger.info("Initial password file deleted after password change");
            }
        } catch (IOException e) {
            logger.error("Failed to delete initial password file", e);
        }

        logger.info("Password changed successfully for user: {}", email);
    }
}
