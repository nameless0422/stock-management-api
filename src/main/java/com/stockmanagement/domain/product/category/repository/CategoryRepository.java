package com.stockmanagement.domain.product.category.repository;

import com.stockmanagement.domain.product.category.entity.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

    /** 이름 변경 시 자기 자신을 제외한 중복 확인 */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * 전체 카테고리 조회 — parent LEFT JOIN FETCH로 N+1 방지.
     * getList(flat 목록)·getTree(트리 조립) 모두 parent를 참조하므로 함께 로딩.
     */
    @Override
    @EntityGraph(attributePaths = "parent")
    List<Category> findAll();

    /**
     * 단건 조회 — children LEFT JOIN FETCH로 N+1 방지.
     * getById()·delete() 시 children 컬렉션을 즉시 로딩.
     */
    @EntityGraph(attributePaths = "children")
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdWithChildren(@Param("id") Long id);
}
