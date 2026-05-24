package com.stockmanagement.domain.product.qna.dto;

import com.stockmanagement.domain.product.qna.entity.ProductQna;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QnaResponse {

    private Long id;
    private Long productId;
    private String username;
    private String content;
    private boolean secret;
    private boolean answered;
    private String answer;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;

    /**
     * 비밀글 가시성 처리.
     * <ul>
     *   <li>공개글 또는 작성자 본인 또는 ADMIN → 원문 노출</li>
     *   <li>타인의 비밀글 → content="비밀글입니다", answer=null</li>
     * </ul>
     */
    public static QnaResponse from(ProductQna qna, String username, Long currentUserId, boolean isAdmin) {
        boolean canView = !qna.isSecret() || isAdmin
                || (currentUserId != null && currentUserId.equals(qna.getUserId()));

        return QnaResponse.builder()
                .id(qna.getId())
                .productId(qna.getProductId())
                .username(maskUsername(username))
                .content(canView ? qna.getContent() : "비밀글입니다")
                .secret(qna.isSecret())
                .answered(qna.getAnswer() != null)
                .answer(canView ? qna.getAnswer() : null)
                .answeredAt(canView ? qna.getAnsweredAt() : null)
                .createdAt(qna.getCreatedAt())
                .build();
    }

    private static String maskUsername(String raw) {
        if (raw == null || raw.isBlank()) return "[탈퇴사용자]";
        if (raw.length() <= 2) return raw.charAt(0) + "***";
        return raw.substring(0, 2) + "***";
    }
}
