package com.stockmanagement.domain.product.category.repository;

import com.stockmanagement.domain.product.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByName(String name);

    /** 이름 변경 시 자기 자신을 제외한 중복 확인 */
    boolean existsByNameAndIdNot(String name, Long id);
}
