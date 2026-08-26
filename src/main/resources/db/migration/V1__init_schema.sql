-- 初始表结构
CREATE TABLE IF NOT EXISTS device (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      code VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    longitude DOUBLE,
    latitude DOUBLE,
    status VARCHAR(255) NOT NULL,
    latest_lux DOUBLE,
    last_seen BIGINT,
    light_on TINYINT(1) DEFAULT 0,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_code (code)
    );

CREATE TABLE IF NOT EXISTS light_point (
                                           id BIGINT NOT NULL AUTO_INCREMENT,
                                           device_code VARCHAR(255) NOT NULL,
    lux DOUBLE NOT NULL,
    ts BIGINT NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_ts (device_code, ts)
    );

CREATE TABLE IF NOT EXISTS alarm (
                                     id BIGINT NOT NULL AUTO_INCREMENT,
                                     device_id VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    level VARCHAR(255) NOT NULL,
    message VARCHAR(255) NOT NULL,
    ts BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    first_occurred_at DATETIME(6),
    last_occurred_at DATETIME(6),
    occurrence_count INT DEFAULT 1,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_alarm_ts (ts),
    KEY idx_alarm_status (status)
    );

CREATE TABLE IF NOT EXISTS sys_user (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
    );

CREATE TABLE IF NOT EXISTS system_config (
                                             id BIGINT NOT NULL AUTO_INCREMENT,
                                             auto_control TINYINT(1),
    lux_threshold INT,
    hysteresis INT,
    heartbeat_timeout_ms BIGINT,
    PRIMARY KEY (id)
    );

CREATE TABLE IF NOT EXISTS mqtt_dead_letter (
                                                id BIGINT NOT NULL AUTO_INCREMENT,
                                                topic VARCHAR(255),
    payload TEXT,
    error_msg TEXT,
    received_at DATETIME(6),
    PRIMARY KEY (id)
    );

CREATE TABLE IF NOT EXISTS device_command (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              command_id VARCHAR(255) NOT NULL,
    device_code VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_command_id (command_id),
    KEY idx_command_device (device_code, created_at)
    );