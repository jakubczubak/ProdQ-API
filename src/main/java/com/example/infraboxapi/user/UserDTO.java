package com.example.infraboxapi.user;

import com.example.infraboxapi.notification.Notification;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;

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
    @UniqueElements(message = "Email already exists")
    private String email;

    private String password;

    @Size(min = 4, message = "Field 'confirm password' must be the same as password and must have at least 4 characters")
    private String confirmPassword;

    private String actualPassword;


    private Role role;
    private List<Notification> notifications;

    private boolean blocked;
}
