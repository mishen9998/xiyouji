package com.xiyouji.repository;

import com.xiyouji.model.User;
import com.xiyouji.port.UserRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Spring Data adapter for the application user persistence port. */
@Repository
public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepositoryPort {
    Optional<User> findByAccount(String account);
    Optional<User> findByUsername(String username);
    boolean existsByAccount(String account);
    boolean existsByUsername(String username);
}
