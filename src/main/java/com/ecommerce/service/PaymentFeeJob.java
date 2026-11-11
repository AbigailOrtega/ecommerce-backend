package com.ecommerce.service;

import com.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills in what Stripe and PayPal charged for each payment, for the accounting report. The
 * commission is only known a little after the payment (Stripe's balance transaction), so instead
 * of asking at checkout this asks every few minutes for the paid orders that still lack it,
 * a batch at a time, and stops asking about an order after a few empty answers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFeeJob {

    static final int MAX_ATTEMPTS = 5;
    static final int BATCH = 50;

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;

    @Value("${app.payments.fees.enabled:true}")
    private boolean enabled = true;

    @Scheduled(initialDelayString = "${app.payments.fees.initial-delay:PT3M}",
               fixedDelayString = "${app.payments.fees.interval:PT15M}")
    public void scheduledRun() {
        run();
    }

    /** @return how many orders were looked up */
    public int run() {
        if (!enabled) return 0;
        List<Long> ids = orderRepository.findIdsAwaitingPaymentFee(MAX_ATTEMPTS, PageRequest.of(0, BATCH));
        for (Long id : ids) {
            try {
                paymentService.recordPaymentFee(id);
            } catch (RuntimeException e) {
                log.error("Could not record the commission of order id={}", id, e);
            }
        }
        if (!ids.isEmpty()) log.info("Payment commissions looked up for {} orders", ids.size());
        return ids.size();
    }
}
