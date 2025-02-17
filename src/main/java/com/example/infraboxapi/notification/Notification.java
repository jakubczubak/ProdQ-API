package com.example.infraboxapi.notification;

import com.example.infraboxapi.user.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "_notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)  // Ustalamy maksymalną długość na 1000 znaków
    private String description;

    private String title;
    private boolean isRead;
    private String createdOn;
    private String author;

    @ManyToOne
    @JsonBackReference
    private User user;

    @PrePersist
    public void prePersist() {
        ZonedDateTime currentDateTime = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        createdOn = currentDateTime.format(formatter);
    }

}