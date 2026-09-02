package com.xiyouji.port;

import com.xiyouji.model.User;

import java.util.Optional;

/** Application-facing user persistence port. */
public interface UserRepositoryPort {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    User save(User user);
}
