package com.example.infraboxapi.user;

import com.example.infraboxapi.notification.Notification;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UserDTO {
    private Integer id;
    @NotBlank(message = "Field 'firstName' cannot be blank")
    @Size(min = 2, max = 50, message = "Field 'firstName' must have a length between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Field 'lastName' cannot be blank")
    @Size(min = 2, max = 50, message = "Field 'lastName' must have a length between 2 and 50 characters")
    private String lastName;

    @Email(message = "Invalid email address format")
    @Size(max = 100, message = "Field 'email' cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Field 'password' cannot be blank")
    @Size(min = 8, message = "Field 'password' must have at least 8 characters")
    private String password;

    @NotNull(message = "Field 'role' cannot be blank")
    private Role role;
    private List<Notification> notifications;
}
