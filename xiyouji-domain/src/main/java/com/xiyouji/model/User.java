package com.xiyouji.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户实体 - 用于认证体系
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一登录账号。旧数据由 Flyway 使用原 username 回填。 */
    @Column(nullable = false, unique = true, length = 50)
    private String account;

    /** 玩家在界面和房间中显示的名称。 */
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "PLAYER";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public User() {}

    // ===== Getters/Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
