package com.example.prodqapi.notification;

import com.example.prodqapi.user.User;
import com.example.prodqapi.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void deleteNotification(Long id) {
        User user = userRepository.findById(getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        user.getNotifications().remove(notification);
        userRepository.save(user);
        notificationRepository.delete(notification);
    }

    public void updateNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setRead(!notification.isRead());
        notificationRepository.save(notification);
    }

    /**
     * Sends notification to all users except the current user.
     * @param type Notification type (used as translation key on frontend)
     * @param data Entity data for interpolation (e.g. {"name": "Steel 10mm"})
     */
    public void sendNotification(NotificationDescription type, Map<String, String> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        List<User> allUsersExceptAuthor = findAllUsersExceptUserWithId(currentUser.getId());
        String entityData = convertToJson(data);

        for (User user : allUsersExceptAuthor) {
            Notification notification = Notification.builder()
                    .notificationType(type)
                    .entityData(entityData)
                    .isRead(false)
                    .author(currentUser.getFirstName() + " " + currentUser.getLastName())
                    .user(user)
                    .build();

            user.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(user);
        }
    }

    /**
     * Sends notification to all users except the current user (without data).
     */
    public void sendNotification(NotificationDescription type) {
        sendNotification(type, Collections.emptyMap());
    }

    /**
     * Sends system notification to ALL users (author = "ProdQ").
     * @param type Notification type (used as translation key on frontend)
     * @param data Entity data for interpolation
     */
    public void sendSystemNotification(NotificationDescription type, Map<String, String> data) {
        List<User> allUsers = userRepository.findAll();
        String entityData = convertToJson(data);

        for (User user : allUsers) {
            Notification notification = Notification.builder()
                    .notificationType(type)
                    .entityData(entityData)
                    .isRead(false)
                    .author("ProdQ")
                    .user(user)
                    .build();

            user.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(user);
        }
    }

    /**
     * Sends system notification to ALL users (without data).
     */
    public void sendSystemNotification(NotificationDescription type) {
        sendSystemNotification(type, Collections.emptyMap());
    }

    /**
     * Sends notification to ALL users including the current user.
     * @param type Notification type (used as translation key on frontend)
     * @param data Entity data for interpolation
     */
    public void sendQuantityNotification(NotificationDescription type, Map<String, String> data) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        List<User> allUsers = userRepository.findAll();
        String entityData = convertToJson(data);

        for (User u : allUsers) {
            Notification notification = Notification.builder()
                    .notificationType(type)
                    .entityData(entityData)
                    .isRead(false)
                    .author(currentUser.getFirstName() + " " + currentUser.getLastName())
                    .user(u)
                    .build();

            u.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(u);
        }
    }

    /**
     * Sends quantity notification to ALL users (without data).
     */
    public void sendQuantityNotification(NotificationDescription type) {
        sendQuantityNotification(type, Collections.emptyMap());
    }

    @Transactional
    public int deleteUnreadNotifications() {
        Integer userId = getUserId();
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return notificationRepository.deleteByReadFalseAndUserId(userId);
    }

    public Integer getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

    public List<User> findAllUsersExceptUserWithId(Integer userId) {
        return userRepository.findAllUsersExceptUserWithId(userId);
    }

    private String convertToJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.error("Failed to convert entity data to JSON: {}", e.getMessage());
            return null;
        }
    }
}