package com.example.infraboxapi.notification;

import com.example.infraboxapi.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "_notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    private Long id;
    private String description;
    private String title;
    private boolean isRead;
    private LocalDateTime createdOn;
    private String author;

    @ManyToOne
    private User user;

    @PrePersist
    public void preUpdate() {
        LocalDateTime now = LocalDateTime.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        createdOn = LocalDateTime.parse(now.format(formatter));
    }

}
