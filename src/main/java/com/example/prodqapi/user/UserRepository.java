package com.example.prodqapi.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);


    @Query("SELECT u FROM User u WHERE u.id <> :userId")
    List<User> findAllUsersExceptUserWithId(@Param("userId") Integer userId);

}
