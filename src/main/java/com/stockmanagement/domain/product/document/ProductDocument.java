package com.stockmanagement.domain.product.document;

import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.entity.ProductStatus;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Elasticsearch 상품 문서.
 *
 * <p>인덱스명: {@code products}
 *
 * <p>필드 매핑:
 * <ul>
 *   <li>{@code name}, {@code description} — text 타입, standard analyzer (전문 검색)
 *   <li>{@code sku}, {@code category}, {@code status} — keyword 타입 (정확 일치 필터)
 *   <li>{@code price} — double 타입 (범위 쿼리)
 * </ul>
 */
@Document(indexName = "products")
@Getter
@Builder
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Double)
    private double price;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS||uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /** Product JPA 엔티티로부터 ES 문서를 생성한다. */
    public static ProductDocument from(Product product) {
        return ProductDocument.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice() != null ? product.getPrice().doubleValue() : 0)
                .sku(product.getSku())
                .category(product.getCategoryName())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .createdAt(product.getCreatedAt())
                .build();
    }

    public BigDecimal getPriceAsBigDecimal() {
        return BigDecimal.valueOf(price);
    }

    /** ES 문서를 ProductResponse DTO로 변환한다. */
    public ProductResponse toProductResponse() {
        return ProductResponse.builder()
                .id(Long.parseLong(id))
                .name(name)
                .description(description)
                .price(getPriceAsBigDecimal())
                .sku(sku)
                .category(category)
                .status(status != null ? ProductStatus.valueOf(status) : null)
                .createdAt(createdAt)
                .build();
    }
}
