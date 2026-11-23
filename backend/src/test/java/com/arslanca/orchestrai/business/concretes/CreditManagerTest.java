package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.mapper.EntityMapper;
import com.arslanca.orchestrai.core.exception.type.InsufficientCreditException;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditRepository;
import com.arslanca.orchestrai.entity.concretes.CreditEntity;

@ExtendWith(MockitoExtension.class)
class CreditManagerTest {

    @Mock
    private CreditRepository creditRepository;

    @Mock
    private EntityMapper entityMapper;

    private CreditManager creditManager;

    @BeforeEach
    void setUp() {
        creditManager = new CreditManager(creditRepository, entityMapper);
    }

    @Test
    @DisplayName("getCreditBalance: kredi kaydi varsa CreditResponse doner")
    void shouldReturnCreditResponseWhenCreditExists() {
        final UUID userId = UUID.randomUUID();
        final CreditEntity entity = org.mockito.Mockito.mock(CreditEntity.class);
        final CreditResponse expected = new CreditResponse(3, 2);

        when(creditRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(entityMapper.toCreditResponse(entity)).thenReturn(expected);

        final CreditResponse result = creditManager.getCreditBalance(userId);

        assertThat(result).isEqualTo(expected);
        verify(creditRepository).findByUserId(userId);
        verify(entityMapper).toCreditResponse(entity);
    }

    @Test
    @DisplayName("reserveCredit: yeterli kredi varsa reserve eder ve kaydeder")
    void shouldReserveCreditSuccessfully() {
        final UUID userId = UUID.randomUUID();
        final CreditEntity creditEntity = org.mockito.Mockito.mock(CreditEntity.class);

        when(creditRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(creditEntity));
        doNothing().when(creditEntity).reserveCredit();

        creditManager.reserveCredit(userId);

        verify(creditRepository).findByUserIdForUpdate(userId);
        verify(creditEntity).reserveCredit();
        verify(creditRepository).save(creditEntity);
    }

    @Test
    @DisplayName("captureReservedCredit: reserve edilmis kredi varsa capture eder")
    void shouldCaptureReservedCreditSuccessfully() {
        final UUID userId = UUID.randomUUID();
        final CreditEntity creditEntity = org.mockito.Mockito.mock(CreditEntity.class);

        when(creditRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(creditEntity));
        doNothing().when(creditEntity).captureReservedCredit();

        creditManager.captureReservedCredit(userId);

        verify(creditEntity).captureReservedCredit();
        verify(creditRepository).save(creditEntity);
    }

    @Test
    @DisplayName("releaseReservedCredit: reserve edilmis kredi varsa serbest birakir")
    void shouldReleaseReservedCreditSuccessfully() {
        final UUID userId = UUID.randomUUID();
        final CreditEntity creditEntity = org.mockito.Mockito.mock(CreditEntity.class);

        when(creditRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(creditEntity));
        doNothing().when(creditEntity).releaseReservedCredit();

        creditManager.releaseReservedCredit(userId);

        verify(creditEntity).releaseReservedCredit();
        verify(creditRepository).save(creditEntity);
    }

    @Test
    @DisplayName("deductCredit: yetersiz kredi durumunda InsufficientCreditException firlatir")
    void shouldThrowInsufficientCreditExceptionWhenBalanceIsZero() {
        final UUID userId = UUID.randomUUID();
        final CreditEntity creditEntity = org.mockito.Mockito.mock(CreditEntity.class);

        when(creditRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(creditEntity));
        doThrow(InsufficientCreditException.forUser(userId)).when(creditEntity).deductCredit();

        assertThatThrownBy(() -> creditManager.deductCredit(userId))
                .isInstanceOf(InsufficientCreditException.class)
                .hasMessageContaining("Yetersiz kredi");

        verify(creditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("getCreditBalance: kredi kaydi yoksa IllegalStateException firlatir")
    void shouldThrowIllegalStateExceptionWhenCreditNotFound() {
        final UUID userId = UUID.randomUUID();
        when(creditRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditManager.getCreditBalance(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kredi kaydı bulunamadı");

        verifyNoInteractions(entityMapper);
    }
}
