package com.stockmanagement.domain.notification.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.notification.dto.NotificationResponse;
import com.stockmanagement.domain.notification.entity.Notification;
import com.stockmanagement.domain.notification.entity.NotificationType;
import com.stockmanagement.domain.notification.repository.NotificationRepository;
import com.stockmanagement.common.dto.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 인앱 알림 서비스.
 *
 * <p>알림 목록 조회, 읽음 처리, 미읽음 카운트, 알림 생성을 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 내 알림 목록을 커서 기반으로 조회한다.
     *
     * @param userId 사용자 ID
     * @param read   null이면 전체, true이면 읽음만, false이면 미읽음만
     * @param lastId 커서 (이전 페이지 마지막 ID), null이면 첫 페이지
     * @param size   페이지 크기
     */
    public CursorPage<NotificationResponse> getNotifications(Long userId, Boolean read, Long lastId, int size) {
        PageRequest limit = PageRequest.of(0, size + 1);
        java.util.List<Notification> items;

        if (read == null) {
            items = lastId == null
                    ? notificationRepository.findByUserIdOrderByIdDesc(userId, limit)
                    : notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, lastId, limit);
        } else if (read) {
            items = lastId == null
                    ? notificationRepository.findByUserIdAndReadAtIsNotNullOrderByIdDesc(userId, limit)
                    : notificationRepository.findByUserIdAndReadAtIsNotNullAndIdLessThanOrderByIdDesc(userId, lastId, limit);
        } else {
            items = lastId == null
                    ? notificationRepository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, limit)
                    : notificationRepository.findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(userId, lastId, limit);
        }

        return CursorPage.of(
                items.stream().map(NotificationResponse::from).toList(),
                size,
                NotificationResponse::getId);
    }

    /** 알림 단건 읽음 처리. 소유권 검증 포함. */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        notification.markAsRead();
    }

    /** 내 알림 전체 읽음 처리. */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    /** 미읽음 알림 개수를 반환한다. */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 알림을 생성한다 (이벤트 리스너에서 호출). */
    @Transactional
    public void create(Long userId, NotificationType type, String title, String message) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .build());
    }
}
