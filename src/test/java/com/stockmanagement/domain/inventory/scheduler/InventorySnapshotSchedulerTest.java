package com.stockmanagement.domain.inventory.scheduler;

import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventorySnapshotScheduler 단위 테스트")
class InventorySnapshotSchedulerTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock DailyInventorySnapshotRepository snapshotRepository;
    @Mock InventorySnapshotProcessor snapshotProcessor;
    @InjectMocks InventorySnapshotScheduler scheduler;

    @Test
    @DisplayName("스냅샷 미존재 시 processBatch() 호출")
    void savesSnapshotWhenNotExists() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        Page<Inventory> page = new PageImpl<>(List.of(inv));
        given(inventoryRepository.findAll(any(Pageable.class))).willReturn(page);
        given(snapshotRepository.findInventoryIdsBySnapshotDate(any(LocalDate.class)))
                .willReturn(Set.of());
        given(snapshotProcessor.processBatch(anyList(), anySet(), any(LocalDate.class)))
                .willReturn(1);

        scheduler.takeSnapshot();

        verify(snapshotProcessor).processBatch(anyList(), anySet(), any(LocalDate.class));
    }

    @Test
    @DisplayName("스냅샷 이미 존재 시 alreadySnapped에 inventoryId 포함하여 processor에 전달")
    void passesAlreadySnappedToProcessor() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        Page<Inventory> page = new PageImpl<>(List.of(inv));
        given(inventoryRepository.findAll(any(Pageable.class))).willReturn(page);
        given(snapshotRepository.findInventoryIdsBySnapshotDate(any(LocalDate.class)))
                .willReturn(Set.of(1L));
        given(snapshotProcessor.processBatch(anyList(), anySet(), any(LocalDate.class)))
                .willReturn(0);

        scheduler.takeSnapshot();

        verify(snapshotProcessor).processBatch(anyList(), eq(Set.of(1L)), any(LocalDate.class));
    }

    // ===== 헬퍼 =====

    private Inventory buildInventory(Long id, int onHand, int reserved, int allocated) {
        Inventory inv = Inventory.builder().product(null).build();
        ReflectionTestUtils.setField(inv, "id", id);
        ReflectionTestUtils.setField(inv, "onHand", onHand);
        ReflectionTestUtils.setField(inv, "reserved", reserved);
        ReflectionTestUtils.setField(inv, "allocated", allocated);
        return inv;
    }
}
