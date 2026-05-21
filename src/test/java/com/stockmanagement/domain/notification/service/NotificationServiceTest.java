package com.stockmanagement.domain.notification.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.notification.dto.NotificationResponse;
import com.stockmanagement.domain.notification.entity.Notification;
import com.stockmanagement.domain.notification.entity.NotificationType;
import com.stockmanagement.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification createNotification(Long id, Long userId) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(NotificationType.ORDER_CREATED)
                .title("주문 접수")
                .message("주문이 접수되었습니다.")
                .build();
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Nested
    @DisplayName("getNotifications()")
    class GetNotifications {

        @Test
        @DisplayName("read=null — 전체 알림을 반환한다")
        void returnsAll() {
            Pageable pageable = PageRequest.of(0, 20);
            Notification n = createNotification(1L, 1L);
            given(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                    .willReturn(new PageImpl<>(List.of(n)));

            Page<NotificationResponse> result = notificationService.getNotifications(1L, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("주문 접수");
        }

        @Test
        @DisplayName("read=false — 미읽음 알림만 반환한다")
        void returnsUnread() {
            Pageable pageable = PageRequest.of(0, 20);
            given(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(1L, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            Page<NotificationResponse> result = notificationService.getNotifications(1L, false, pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("본인 알림을 읽음 처리한다")
        void marksRead() {
            Notification n = createNotification(1L, 1L);
            given(notificationRepository.findById(1L)).willReturn(Optional.of(n));

            notificationService.markAsRead(1L, 1L);

            assertThat(n.isRead()).isTrue();
        }

        @Test
        @DisplayName("다른 사용자의 알림이면 NOTIFICATION_ACCESS_DENIED")
        void deniesOtherUser() {
            Notification n = createNotification(1L, 2L);
            given(notificationRepository.findById(1L)).willReturn(Optional.of(n));

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOTIFICATION_ACCESS_DENIED));
        }

        @Test
        @DisplayName("존재하지 않는 알림이면 NOTIFICATION_NOT_FOUND")
        void notFound() {
            given(notificationRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("markAllAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("전체 읽음 처리 호출")
        void callsBulkUpdate() {
            notificationService.markAllAsRead(1L);

            verify(notificationRepository).markAllAsRead(any(), any());
        }
    }

    @Nested
    @DisplayName("getUnreadCount()")
    class GetUnreadCount {

        @Test
        @DisplayName("미읽음 개수를 반환한다")
        void returnsCount() {
            given(notificationRepository.countByUserIdAndReadAtIsNull(1L)).willReturn(5L);

            long count = notificationService.getUnreadCount(1L);

            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("알림을 생성한다")
        void creates() {
            notificationService.create(1L, NotificationType.ORDER_CREATED, "제목", "메시지");

            verify(notificationRepository).save(any(Notification.class));
        }
    }
}
