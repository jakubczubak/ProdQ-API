package com.example.infraboxapi.tool;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_tool")
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private String type;
    private float dc;
    private float cfl;
    private float oal;
    private float quantity;
    private float minQuantity;
    private BigDecimal price;
    private String toolID;
    private String link;
    private String additionalInfo;
    private float quantityInTransit;

    @Column(name = "updated_on")
    private String updatedOn;


    @PreUpdate
    public void preUpdate() {
        // Pobranie aktualnego użytkownika
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String currentUsername = userDetails.getUsername();  // Zakładając, że username to email użytkownika

        // Jeśli użytkownik to root, nie aktualizuj pola updatedOn
        if (!"root@gmail.com".equals(currentUsername)) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            updatedOn = now.format(formatter);
        }
    }
}
