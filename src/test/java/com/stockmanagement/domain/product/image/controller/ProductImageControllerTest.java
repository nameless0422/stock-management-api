package com.stockmanagement.domain.product.image.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmanagement.common.config.SecurityConfig;
import com.stockmanagement.common.security.JwtBlacklist;
import com.stockmanagement.domain.product.image.dto.*;
import com.stockmanagement.domain.product.image.entity.ImageType;
import com.stockmanagement.domain.product.image.service.ProductImageService;
import com.stockmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductImageController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductImageController 단위 테스트")
class ProductImageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ProductImageService productImageService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtBlacklist jwtBlacklist;

    private static final String BASE_URL = "/api/products/1/images";

    @Nested
    @DisplayName("POST /api/products/{productId}/images/presigned")
    class GeneratePresignedUrl {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 200 + presigned URL 반환")
        void admin_success() throws Exception {
            PresignedUrlResponse res = new PresignedUrlResponse(
                    "https://minio/presigned", "https://minio/img.jpg", "products/1/uuid.jpg");
            given(productImageService.generatePresignedUrl(eq(1L), any())).willReturn(res);

            String body = objectMapper.writeValueAsString(
                    Map.of("fileExtension", "jpg", "contentType", "image/jpeg", "imageType", "THUMBNAIL"));

            mockMvc.perform(post(BASE_URL + "/presigned")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.presignedUrl").value("https://minio/presigned"))
                    .andExpect(jsonPath("$.data.objectKey").value("products/1/uuid.jpg"));
        }

        @Test
        @DisplayName("비인증 — 403")
        void unauthenticated_403() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("fileExtension", "jpg", "contentType", "image/jpeg", "imageType", "THUMBNAIL"));

            mockMvc.perform(post(BASE_URL + "/presigned")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/products/{productId}/images")
    class SaveImage {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 201 + 저장된 이미지 응답")
        void admin_success() throws Exception {
            ProductImageResponse res = new ProductImageResponse(
                    1L, 1L, "https://minio/img.jpg", ImageType.THUMBNAIL, 0, LocalDateTime.now());
            given(productImageService.saveImage(eq(1L), any())).willReturn(res);

            String body = objectMapper.writeValueAsString(Map.of(
                    "imageUrl", "https://minio/img.jpg",
                    "objectKey", "products/1/uuid.jpg",
                    "imageType", "THUMBNAIL",
                    "displayOrder", 0));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.imageUrl").value("https://minio/img.jpg"))
                    .andExpect(jsonPath("$.data.imageType").value("THUMBNAIL"));
        }
    }

    @Nested
    @DisplayName("GET /api/products/{productId}/images")
    class GetImages {

        @Test
        @DisplayName("비인증 — 200 (공개 엔드포인트)")
        void public_success() throws Exception {
            ProductImageResponse res = new ProductImageResponse(
                    1L, 1L, "https://minio/img.jpg", ImageType.THUMBNAIL, 0, LocalDateTime.now());
            given(productImageService.getImages(1L)).willReturn(List.of(res));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].imageUrl").value("https://minio/img.jpg"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{productId}/images/{imageId}")
    class DeleteImage {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN — 204")
        void admin_success() throws Exception {
            doNothing().when(productImageService).deleteImage(1L, 1L);

            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("USER — 403")
        void user_forbidden() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
