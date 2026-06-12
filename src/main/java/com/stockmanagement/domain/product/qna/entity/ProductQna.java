package com.stockmanagement.domain.product.qna.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_qna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductQna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean secret;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_by")
    private Long answeredBy;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ProductQna(Long productId, Long userId, String content, boolean secret) {
        this.productId = productId;
        this.userId = userId;
        this.content = content;
        this.secret = secret;
    }

    /** ADMIN이 답변을 작성(또는 수정)한다. */
    public void answer(String answer, Long adminId) {
        this.answer = answer;
        this.answeredBy = adminId;
        this.answeredAt = LocalDateTime.now();
    }
}
