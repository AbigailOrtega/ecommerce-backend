package com.ecommerce.service;

import com.ecommerce.tenant.TenantJobRunner;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecommerce.entity.OrderStatus;
import com.ecommerce.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Releases the stock held by orders whose payment never arrived (a closed tab, a card that was never
 * entered). Creating an order reserves its units, so without this they would stay unsellable forever.
 * Each order is checked with Stripe/PayPal first; see {@link PaymentService#resolveAbandonedOrder}.
 */
@Slf4j
@Component
public class AbandonedOrderJob {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    @Value("${app.orders.abandoned.enabled:true}")
    private boolean enabled = true;

    /** Long enough for 3-D Secure, a slow bank or a delayed webhook. */
    @Value("${app.orders.abandoned.after:PT1H}")
    private Duration after = Duration.ofHours(1);

    @Autowired
    public AbandonedOrderJob(OrderRepository orderRepository, PaymentService paymentService) {
        this(orderRepository, paymentService, Clock.systemDefaultZone());
    }

    AbandonedOrderJob(OrderRepository orderRepository, PaymentService paymentService, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.orders.abandoned.initial-delay:PT5M}",
               fixedDelayString = "${app.orders.abandoned.interval:PT10M}")
    public void scheduledRun() {
        TenantJobRunner.forEachActiveTenant(tenantJobRunner, this::run);
    }

    private TenantJobRunner tenantJobRunner;

    @Autowired(required = false)
    void setTenantJobRunner(TenantJobRunner tenantJobRunner) {
        this.tenantJobRunner = tenantJobRunner;
    }

    public Map<PaymentService.AbandonedOutcome, Integer> run() {
        Map<PaymentService.AbandonedOutcome, Integer> counts = new EnumMap<>(PaymentService.AbandonedOutcome.class);
        if (!enabled) return counts;

        // createdAt is written with LocalDateTime.now(), so compare in the same zone
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(after);
        List<Long> ids = orderRepository.findIdsByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
        for (Long id : ids) {
            try {
                counts.merge(paymentService.resolveAbandonedOrder(id), 1, Integer::sum);
            } catch (RuntimeException e) {
                log.error("Could not settle pending order id={}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("Pending orders older than {}: {} checked, {}", after, ids.size(), counts);
        }
        return counts;
    }
}
