package com.stockmanagement.domain.product.repository;

import com.stockmanagement.domain.product.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** 상품 Elasticsearch 리포지토리 — 기본 CRUD 및 색인/삭제 제공. */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}
