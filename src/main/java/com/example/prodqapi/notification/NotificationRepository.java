package com.example.prodqapi.notification;

import com.example.prodqapi.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser(User user); // Poprawiona nazwa

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isRead = false AND n.user.id = :userId")
    int deleteByReadFalseAndUserId(Integer userId);
}