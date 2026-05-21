CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_notifications_user_read (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
