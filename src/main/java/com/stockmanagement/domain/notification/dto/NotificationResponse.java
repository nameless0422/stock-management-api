package com.stockmanagement.domain.notification.dto;

import com.stockmanagement.domain.notification.entity.Notification;
import com.stockmanagement.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private final Long id;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
