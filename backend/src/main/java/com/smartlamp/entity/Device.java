package com.smartlamp.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String name;
    private String location;
    private String binding;
    private Boolean bound = true;
    private String lampStatus = "OFF";
    private String status;
    private Double latestLux;
    private Long lastSeen;
    private LocalDateTime createdAt;
}
