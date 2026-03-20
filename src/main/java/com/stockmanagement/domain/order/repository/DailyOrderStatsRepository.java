package com.stockmanagement.domain.order.repository;

import com.stockmanagement.domain.order.entity.DailyOrderStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyOrderStatsRepository extends JpaRepository<DailyOrderStats, Long> {

    Optional<DailyOrderStats> findByStatDate(LocalDate date);

    List<DailyOrderStats> findByStatDateBetweenOrderByStatDateDesc(LocalDate from, LocalDate to);
}
