package com.stockmanagement.domain.notification.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.notification.dto.NotificationResponse;
import com.stockmanagement.domain.notification.entity.NotificationType;
import com.stockmanagement.domain.notification.service.NotificationService;
import com.stockmanagement.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@DisplayName("NotificationController 단위 테스트")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private NotificationService notificationService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private UserService userService;

    @Nested
    @DisplayName("GET /api/notifications")
    class GetNotifications {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 알림 목록 조회 → 200")
        void returnsList() throws Exception {
            var response = NotificationResponse.builder()
                    .id(1L)
                    .type(NotificationType.ORDER_CREATED)
                    .title("주문 접수")
                    .message("주문이 접수되었습니다.")
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            given(notificationService.getNotifications(any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(response)));

            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].title").value("주문 접수"));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/notifications/{id}/read")
    class MarkAsRead {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 읽음 처리 → 204")
        void marksRead() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/1/read"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/notifications/read-all")
    class MarkAllAsRead {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 전체 읽음 처리 → 204")
        void marksAllRead() throws Exception {
            mockMvc.perform(post("/api/v1/notifications/read-all"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/unread-count")
    class UnreadCount {

        @Test
        @WithMockUser
        @DisplayName("인증된 사용자 — 미읽음 개수 → 200")
        void returnsCount() throws Exception {
            given(notificationService.getUnreadCount(any())).willReturn(3L);

            mockMvc.perform(get("/api/v1/notifications/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(3));
        }

        @Test
        @DisplayName("인증 없음 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/notifications/unread-count"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
