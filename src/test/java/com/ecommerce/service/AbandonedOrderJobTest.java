package com.ecommerce.service;

import com.ecommerce.entity.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.PaymentService.AbandonedOutcome;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbandonedOrderJobTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ZoneId zone = ZoneId.of("America/Mexico_City");
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T18:00:00Z"), zone);
    private final AbandonedOrderJob job = new AbandonedOrderJob(orderRepository, paymentService, clock);

    @Test
    void settlesPendingOrdersOlderThanAnHour() {
        LocalDateTime anHourAgo = LocalDateTime.now(clock).minusHours(1);
        when(orderRepository.findIdsByStatusAndCreatedAtBefore(OrderStatus.PENDING, anHourAgo)).thenReturn(List.of(1L, 2L, 3L));
        when(paymentService.resolveAbandonedOrder(1L)).thenReturn(AbandonedOutcome.CANCELLED);
        when(paymentService.resolveAbandonedOrder(2L)).thenReturn(AbandonedOutcome.CONFIRMED);
        when(paymentService.resolveAbandonedOrder(3L)).thenReturn(AbandonedOutcome.CANCELLED);

        Map<AbandonedOutcome, Integer> counts = job.run();

        assertThat(counts).containsEntry(AbandonedOutcome.CANCELLED, 2).containsEntry(AbandonedOutcome.CONFIRMED, 1);
    }

    @Test
    void oneFailingOrderDoesNotStopTheRest() {
        when(orderRepository.findIdsByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(1L, 2L));
        when(paymentService.resolveAbandonedOrder(1L)).thenThrow(new IllegalStateException("boom"));
        when(paymentService.resolveAbandonedOrder(2L)).thenReturn(AbandonedOutcome.CANCELLED);

        assertThat(job.run()).containsEntry(AbandonedOutcome.CANCELLED, 1);
        verify(paymentService).resolveAbandonedOrder(2L);
    }

    @Test
    void doesNothingWhenDisabled() {
        org.springframework.test.util.ReflectionTestUtils.setField(job, "enabled", false);

        assertThat(job.run()).isEmpty();
        verifyNoInteractions(orderRepository, paymentService);
    }
}
