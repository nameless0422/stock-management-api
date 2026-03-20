package com.stockmanagement.domain.product.service;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.stockmanagement.domain.product.document.ProductDocument;
import com.stockmanagement.domain.product.dto.ProductSearchRequest;
import com.stockmanagement.domain.product.dto.ProductResponse;
import com.stockmanagement.domain.product.entity.Product;
import com.stockmanagement.domain.product.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 기반 상품 검색 서비스.
 *
 * <p>검색 조건: 키워드(multi_match), 가격 범위(range), 카테고리(term) — 모두 선택적 AND 결합.
 * <p>정렬: relevance(기본), price_asc, price_desc, newest.
 * <p>상품 상태 필터: ACTIVE만 검색 결과에 포함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductSearchRepository productSearchRepository;

    /**
     * 동적 조건으로 상품을 검색한다.
     *
     * @param request 검색 조건 (q, minPrice, maxPrice, category, sort)
     * @param pageable 페이징 정보
     * @return ACTIVE 상태 상품 검색 결과
     */
    public Page<ProductResponse> search(ProductSearchRequest request, Pageable pageable) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 상태 필터: ACTIVE만 검색
        boolQuery.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));

        // 키워드 검색 — name(^3), sku(^2), category(^2), description(^1) 필드 대상
        if (request.getQ() != null && !request.getQ().isBlank()) {
            String keyword = request.getQ();
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .query(keyword)
                    .fields("name^3", "sku^2", "category^2", "description^1")
            ));
        }

        // 가격 범위 필터
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            boolQuery.filter(f -> f.range(r -> {
                var numRange = r.number(n -> {
                    n.field("price");
                    if (request.getMinPrice() != null) n.gte(request.getMinPrice().doubleValue());
                    if (request.getMaxPrice() != null) n.lte(request.getMaxPrice().doubleValue());
                    return n;
                });
                return numRange;
            }));
        }

        // 카테고리 필터 (정확 일치)
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            boolQuery.filter(f -> f.term(t -> t.field("category").value(request.getCategory())));
        }

        // 정렬 옵션
        List<SortOptions> sortOptions = buildSortOptions(request.getSort());

        // ES는 자체 sort(buildSortOptions)로 처리 — pageable의 MySQL 기본 sort("id") 제거
        Pageable esPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        var queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()))
                .withPageable(esPageable);

        if (!sortOptions.isEmpty()) {
            queryBuilder.withSort(sortOptions);
        }

        NativeQuery query = queryBuilder.build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

        List<ProductResponse> content = hits.stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::toProductResponse)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    /**
     * 상품을 Elasticsearch에 색인한다.
     * ProductService의 create/update/changeStatus에서 호출된다.
     */
    public void index(Product product) {
        productSearchRepository.save(ProductDocument.from(product));
        log.debug("ES 색인 완료. productId={}", product.getId());
    }

    /**
     * 상품을 Elasticsearch 색인에서 삭제한다.
     * ProductService의 delete/changeStatus(DISCONTINUED)에서 호출된다.
     */
    public void deleteFromIndex(Long productId) {
        productSearchRepository.deleteById(String.valueOf(productId));
        log.debug("ES 색인 삭제 완료. productId={}", productId);
    }

    // ===== 내부 헬퍼 =====

    private List<SortOptions> buildSortOptions(String sort) {
        List<SortOptions> options = new ArrayList<>();
        if (sort == null) return options; // ES 기본 _score 정렬

        switch (sort) {
            case "price_asc" ->
                options.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc))));
            case "price_desc" ->
                options.add(SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc))));
            case "newest" ->
                options.add(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
            // relevance: 별도 지정 없음 — ES 기본 _score 정렬
            default -> { }
        }
        return options;
    }
}
