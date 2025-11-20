package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SimulatedPaymentProcessor implements PaymentProcessor {
    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentProcessor.class);

    private final PaymentRepository paymentRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final long processingDelayMs;
    private final double failureRate;

    public SimulatedPaymentProcessor(
            PaymentRepository paymentRepository,
            WebhookDeliveryService webhookDeliveryService,
            @Value("${fiadopay.processing-delay-ms}") long processingDelayMs,
            @Value("${fiadopay.failure-rate}") double failureRate) {
        this.paymentRepository = paymentRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.processingDelayMs = processingDelayMs;
        this.failureRate = failureRate;
    }

    @Override
    @Async
    public void process(Payment payment) {
        log.info("Starting payment processing for {}", payment.getId());

        simulateProcessingDelay();

        Payment persistedPayment = paymentRepository.findById(payment.getId()).orElse(null);
        if (persistedPayment == null) {
            log.warn("Payment {} not found during processing", payment.getId());
            return;
        }

        boolean approved = determineApproval();
        Payment.Status newStatus = approved ? Payment.Status.APPROVED : Payment.Status.DECLINED;

        persistedPayment.setStatus(newStatus);
        persistedPayment.setUpdatedAt(Instant.now());
        paymentRepository.save(persistedPayment);

        log.info("Payment {} processed with status {}", persistedPayment.getId(), newStatus);

        webhookDeliveryService.sendPaymentWebhook(persistedPayment);
    }

    private void simulateProcessingDelay() {
        try {
            Thread.sleep(processingDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Processing delay interrupted");
        }
    }

    private boolean determineApproval() {
        return Math.random() > failureRate;
    }
}
