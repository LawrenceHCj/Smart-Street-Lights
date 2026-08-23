package com.smartlamp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String role;
    private String status;
    private Long createdAt;   // 毫秒时间戳

    public UserDTO(Long id, String username, String role, String status, Long createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }
}