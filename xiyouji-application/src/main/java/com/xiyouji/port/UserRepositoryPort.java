package com.xiyouji.port;

import com.xiyouji.model.User;

import java.util.Optional;

/** Application-facing user persistence port. */
public interface UserRepositoryPort {
    Optional<User> findByAccount(String account);
    Optional<User> findByUsername(String username);
    boolean existsByAccount(String account);
    boolean existsByUsername(String username);
    User save(User user);
}
