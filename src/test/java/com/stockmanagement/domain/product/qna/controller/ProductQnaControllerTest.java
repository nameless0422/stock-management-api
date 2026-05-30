package com.stockmanagement.domain.product.qna.controller;

import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.EmailVerificationTokenStore;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.common.security.JwtTokenProvider;
import com.stockmanagement.domain.product.qna.dto.QnaResponse;
import com.stockmanagement.domain.product.qna.service.ProductQnaService;
import com.stockmanagement.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductQnaController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductQnaController 단위 테스트")
class ProductQnaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProductQnaService qnaService;
    @MockBean private UserService userService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;
    @MockBean private EmailVerificationTokenStore emailVerificationTokenStore;

    private static final UsernamePasswordAuthenticationToken USER_AUTH =
            new UsernamePasswordAuthenticationToken("user1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

    private static final UsernamePasswordAuthenticationToken ADMIN_AUTH =
            new UsernamePasswordAuthenticationToken("admin1", null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @BeforeEach
    void setUp() {
        given(userService.resolveUserId(anyString())).willReturn(1L);
    }

    private QnaResponse sampleResponse() {
        return QnaResponse.builder()
                .id(1L)
                .productId(1L)
                .username("bu***")
                .content("배송 문의")
                .secret(false)
                .answered(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ===== GET /api/products/{productId}/qna =====

    @Nested
    @DisplayName("GET /api/products/{productId}/qna")
    class GetList {

        @Test
        @DisplayName("비로그인 조회 → 200")
        void getListWithoutAuth() throws Exception {
            given(qnaService.getList(anyLong(), any(Pageable.class), isNull(), eq(false)))
                    .willReturn(new PageImpl<>(List.of(sampleResponse())));

            mockMvc.perform(get("/api/v1/products/1/qna"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].content").value("배송 문의"));
        }
    }

    // ===== POST /api/products/{productId}/qna =====

    @Nested
    @DisplayName("POST /api/products/{productId}/qna")
    class Create {

        @Test
        @DisplayName("인증된 사용자 → 201")
        void createSuccess() throws Exception {
            given(qnaService.create(anyLong(), anyLong(), any())).willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/products/1/qna")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"배송 문의\",\"secret\":false}")
                            .with(authentication(USER_AUTH)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("미인증 → 401")
        void createUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/products/1/qna")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"배송 문의\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== POST /api/products/{productId}/qna/{qnaId}/answer =====

    @Nested
    @DisplayName("POST /api/products/{productId}/qna/{qnaId}/answer")
    class Answer {

        @Test
        @DisplayName("ADMIN → 200")
        void answerByAdmin() throws Exception {
            QnaResponse response = QnaResponse.builder()
                    .id(1L).productId(1L).username("bu***")
                    .content("문의").secret(false).answered(true)
                    .answer("답변 드립니다").answeredAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now()).build();

            given(userService.resolveUserId("admin1")).willReturn(100L);
            given(qnaService.answer(anyLong(), anyLong(), anyLong(), any())).willReturn(response);

            mockMvc.perform(post("/api/v1/products/1/qna/1/answer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answer\":\"답변 드립니다\"}")
                            .with(authentication(ADMIN_AUTH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.answered").value(true));
        }

        @Test
        @DisplayName("일반 사용자 → 403")
        void answerByUserForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/products/1/qna/1/answer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answer\":\"답변 시도\"}")
                            .with(authentication(USER_AUTH)))
                    .andExpect(status().isForbidden());
        }
    }

    // ===== DELETE /api/products/{productId}/qna/{qnaId} =====

    @Nested
    @DisplayName("DELETE /api/products/{productId}/qna/{qnaId}")
    class Delete {

        @Test
        @DisplayName("인증된 사용자 → 204")
        void deleteSuccess() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1/qna/1")
                            .with(authentication(USER_AUTH)))
                    .andExpect(status().isNoContent());

            verify(qnaService).delete(eq(1L), eq(1L), anyLong(), eq(false));
        }

        @Test
        @DisplayName("미인증 → 401")
        void deleteUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1/qna/1"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
