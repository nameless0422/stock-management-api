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
import java.util.Set;

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
    @DisplayName("스냅샷 미존재 시 saveAll()로 배치 저장")
    void savesSnapshotWhenNotExists() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        given(inventoryRepository.findAll()).willReturn(List.of(inv));
        // 오늘 스냅샷이 없는 상태 — 빈 Set 반환
        given(snapshotRepository.findInventoryIdsBySnapshotDate(any(LocalDate.class)))
                .willReturn(Set.of());
        given(snapshotRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        scheduler.takeSnapshot();

        verify(snapshotRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("스냅샷 이미 존재 시 saveAll()에 빈 목록 전달 (저장 없음)")
    void skipsWhenSnapshotAlreadyExists() {
        Inventory inv = buildInventory(1L, 100, 10, 5);
        given(inventoryRepository.findAll()).willReturn(List.of(inv));
        // inventoryId=1L이 이미 존재하는 상태
        given(snapshotRepository.findInventoryIdsBySnapshotDate(any(LocalDate.class)))
                .willReturn(Set.of(1L));
        given(snapshotRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        scheduler.takeSnapshot();

        verify(snapshotRepository).saveAll(List.of());
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
