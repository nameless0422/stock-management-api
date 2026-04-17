package com.stockmanagement.domain.product.image.entity;

import com.stockmanagement.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 공개 접근 URL (브라우저에서 직접 표시) */
    @Column(nullable = false, length = 500)
    private String imageUrl;

    /** 스토리지 내 오브젝트 경로 (삭제 시 사용) */
    @Column(nullable = false, length = 500)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageType imageType;

    /** 상세 이미지 정렬 순서 — THUMBNAIL은 관습적으로 0 */
    @Column(nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ProductImage(Product product, String imageUrl, String objectKey,
                         ImageType imageType, int displayOrder) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.objectKey = objectKey;
        this.imageType = imageType;
        this.displayOrder = displayOrder;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
