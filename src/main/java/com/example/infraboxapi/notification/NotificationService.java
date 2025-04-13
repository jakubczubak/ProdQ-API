package com.example.infraboxapi.notification;

import com.example.infraboxapi.user.User;
import com.example.infraboxapi.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private static final String ROOT_EMAIL = "root@gmail.com";
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void deleteNotification(Long id) {
        User user = userRepository.findById(getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (isRootUser(user)) {
            return;
        }

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        user.getNotifications().remove(notification);
        userRepository.save(user);
        notificationRepository.delete(notification);
    }

    public void updateNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (isRootUser(notification.getAuthor())) {
            return;
        }

        notification.setRead(!notification.isRead());
        notificationRepository.save(notification);
    }

    public void createAndSendNotification(String description, NotificationDescription notificationDescription) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        if (isRootUser(currentUser)) {
            return;
        }

        List<User> allUsersExceptAuthor = findAllUsersExceptUserWithId(currentUser.getId());

        for (User user : allUsersExceptAuthor) {
            Notification notification = new Notification();
            notification.setDescription(description);
            notification.setTitle(notificationDescription.getDescription());
            notification.setRead(false);
            notification.setAuthor(currentUser.getFirstName() + " " + currentUser.getLastName());
            notification.setUser(user);

            user.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(user);
        }
    }

    public void     createAndSendSystemNotification(String description, NotificationDescription notificationDescription) {
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            Notification notification = new Notification();
            notification.setDescription(description);
            notification.setTitle(notificationDescription.getDescription());
            notification.setRead(false);
            notification.setAuthor("Infrabox");
            notification.setUser(user);

            user.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(user);
        }
    }

    public void createAndSendQuantityNotification(String description, NotificationDescription notificationDescription) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        if (isRootUser(currentUser)) {
            return;
        }

        List<User> allUsers = userRepository.findAll();

        for (User u : allUsers) {
            Notification notification = new Notification();
            notification.setDescription(description);
            notification.setTitle(notificationDescription.getDescription());
            notification.setRead(false);
            notification.setAuthor(currentUser.getFirstName() + " " + currentUser.getLastName());
            notification.setUser(u);

            u.getNotifications().add(notification);
            notificationRepository.save(notification);
            userRepository.save(u);
        }
    }

    @Transactional
    public int deleteUnreadNotifications() {
        Integer userId = getUserId();
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Usuwamy powiadomienia niezależnie od tego, czy użytkownik jest rootem
        return notificationRepository.deleteByReadFalseAndUserId(userId);
    }

    private boolean isRootUser(User user) {
        return user != null && ROOT_EMAIL.equals(user.getEmail());
    }

    private boolean isRootUser(String author) {
        return "root".equalsIgnoreCase(author);
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
}