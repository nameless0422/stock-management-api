package com.stockmanagement.domain.inventory.scheduler;

import com.stockmanagement.domain.inventory.entity.DailyInventorySnapshot;
import com.stockmanagement.domain.inventory.entity.Inventory;
import com.stockmanagement.domain.inventory.repository.DailyInventorySnapshotRepository;
import com.stockmanagement.domain.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventorySnapshotScheduler 단위 테스트")
class InventorySnapshotSchedulerTest {

    @Mock InventoryRepository inventoryRepository;
    @Mock DailyInventorySnapshotRepository snapshotRepository;
    @InjectMocks InventorySnapshotScheduler scheduler;

    @Test
    @DisplayName("스냅샷 미존재 시 저장")
    void savesSnapshotWhenNotExists() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        given(inventoryRepository.findAll()).willReturn(List.of(inv));
        given(snapshotRepository.existsByInventoryIdAndSnapshotDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        given(snapshotRepository.save(any(DailyInventorySnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        scheduler.takeSnapshot();

        verify(snapshotRepository).save(any(DailyInventorySnapshot.class));
    }

    @Test
    @DisplayName("스냅샷 이미 존재 시 저장 스킵")
    void skipsWhenSnapshotAlreadyExists() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        given(inventoryRepository.findAll()).willReturn(List.of(inv));
        given(snapshotRepository.existsByInventoryIdAndSnapshotDate(eq(1L), any(LocalDate.class)))
                .willReturn(true);

        scheduler.takeSnapshot();

        verify(snapshotRepository, never()).save(any());
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
