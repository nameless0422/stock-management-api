package com.stockmanagement.domain.user.address.service;

import com.stockmanagement.common.exception.BusinessException;
import com.stockmanagement.common.exception.ErrorCode;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressRequest;
import com.stockmanagement.domain.user.address.dto.DeliveryAddressResponse;
import com.stockmanagement.domain.user.address.entity.DeliveryAddress;
import com.stockmanagement.domain.user.address.repository.DeliveryAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryAddressService 단위 테스트")
class DeliveryAddressServiceTest {

    @Mock DeliveryAddressRepository repository;
    @InjectMocks DeliveryAddressService service;

    private DeliveryAddress address;
    private DeliveryAddressRequest request;

    @BeforeEach
    void setUp() {
        address = buildAddress(1L, 1L, false);
        request = buildRequest("집", "홍길동", "01012345678");
    }

    @Nested
    @DisplayName("배송지 등록 (create)")
    class Create {

        @Test
        @DisplayName("첫 번째 배송지 → isDefault 자동 설정")
        void firstAddressBecomesDefault() {
            given(repository.countByUserId(1L)).willReturn(0L);
            given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

            DeliveryAddressResponse result = service.create(1L, request);

            assertThat(result.isDefault()).isTrue();
        }

        @Test
        @DisplayName("두 번째 배송지 → isDefault = false")
        void secondAddressNotDefault() {
            given(repository.countByUserId(1L)).willReturn(1L);
            given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

            DeliveryAddressResponse result = service.create(1L, request);

            assertThat(result.isDefault()).isFalse();
        }
    }

    @Nested
    @DisplayName("배송지 조회 (getById)")
    class GetById {

        @Test
        @DisplayName("본인 배송지 조회 성공")
        void successForOwner() {
            given(repository.findById(1L)).willReturn(Optional.of(address));

            DeliveryAddressResponse result = service.getById(1L, 1L);

            assertThat(result.getAlias()).isEqualTo("집");
        }

        @Test
        @DisplayName("타인 배송지 → DELIVERY_ADDRESS_ACCESS_DENIED 예외")
        void deniedForOtherUser() {
            given(repository.findById(1L)).willReturn(Optional.of(address));

            assertThatThrownBy(() -> service.getById(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("없는 배송지 → DELIVERY_ADDRESS_NOT_FOUND 예외")
        void throwsWhenNotFound() {
            given(repository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("기본 배송지 설정 (setDefault)")
    class SetDefault {

        @Test
        @DisplayName("기존 기본 배송지 해제 후 새 기본 배송지 설정")
        void switchesDefault() {
            DeliveryAddress oldDefault = buildAddress(2L, 1L, true);
            given(repository.findByUserIdAndIsDefaultTrueForUpdate(1L)).willReturn(Optional.of(oldDefault));
            given(repository.findById(1L)).willReturn(Optional.of(address));

            service.setDefault(1L, 1L);

            assertThat(oldDefault.isDefault()).isFalse();
            assertThat(address.isDefault()).isTrue();
        }
    }

    @Nested
    @DisplayName("배송지 삭제 (delete)")
    class Delete {

        @Test
        @DisplayName("기본 배송지 삭제 시 다른 배송지 자동 승격")
        void deletingDefaultPromotesAnother() {
            DeliveryAddress defaultAddr = buildAddress(1L, 1L, true);
            DeliveryAddress otherAddr  = buildAddress(2L, 1L, false);
            given(repository.findById(1L)).willReturn(Optional.of(defaultAddr));
            given(repository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                    .willReturn(Optional.of(otherAddr));

            service.delete(1L, 1L);

            verify(repository).delete(defaultAddr);
            assertThat(otherAddr.isDefault()).isTrue();
        }

        @Test
        @DisplayName("일반 배송지 삭제 시 다른 배송지 승격 없음")
        void deletingNonDefaultNoPromotion() {
            given(repository.findById(1L)).willReturn(Optional.of(address)); // isDefault=false

            service.delete(1L, 1L);

            verify(repository).delete(address);
            verify(repository, never()).findFirstByUserIdOrderByCreatedAtDesc(anyLong());
        }
    }

    // ===== 헬퍼 =====

    private DeliveryAddress buildAddress(Long id, Long userId, boolean isDefault) {
        DeliveryAddress addr = DeliveryAddress.builder()
                .userId(userId).alias("집").recipient("홍길동")
                .phone("01012345678").zipCode("12345")
                .address1("서울시 강남구").address2(null)
                .build();
        ReflectionTestUtils.setField(addr, "id", id);
        if (isDefault) addr.setAsDefault();
        return addr;
    }

    private DeliveryAddressRequest buildRequest(String alias, String recipient, String phone) {
        DeliveryAddressRequest req = new DeliveryAddressRequest();
        ReflectionTestUtils.setField(req, "alias", alias);
        ReflectionTestUtils.setField(req, "recipient", recipient);
        ReflectionTestUtils.setField(req, "phone", phone);
        ReflectionTestUtils.setField(req, "zipCode", "12345");
        ReflectionTestUtils.setField(req, "address1", "서울시 강남구");
        ReflectionTestUtils.setField(req, "address2", null);
        return req;
    }
}
