package com.example.infraboxapi.notification;

import com.example.infraboxapi.user.User;
import com.example.infraboxapi.user.UserRepository;
import com.example.infraboxapi.user.UserService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserService userService;




    @Transactional
    public void deleteNotification(Long id){

        User user = userRepository.findById(userService.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found"));
        user.getNotifications().remove(notification);
        userRepository.save(user);
        notificationRepository.delete(notification);


    }

    //This method is used to update the notification to read or unread
    public void updateNotification(Long id){

        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(!notification.isRead());
        notificationRepository.save(notification);

    }
}
