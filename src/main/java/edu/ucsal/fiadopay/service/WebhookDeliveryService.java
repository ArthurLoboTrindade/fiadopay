package edu.ucsal.fiadopay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.domain.WebhookDelivery;
import edu.ucsal.fiadopay.repo.MerchantRepository;
import edu.ucsal.fiadopay.repo.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class WebhookDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_MS = 1000L;

    private final MerchantRepository merchantRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookDeliveryService(
            MerchantRepository merchantRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookSignatureService signatureService,
            ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.deliveryRepository = deliveryRepository;
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    public void sendPaymentWebhook(Payment payment) {
        Merchant merchant = merchantRepository.findById(payment.getMerchantId()).orElse(null);

        if (merchant == null || !hasValidWebhookUrl(merchant)) {
            log.debug("Merchant {} has no webhook URL configured", payment.getMerchantId());
            return;
        }

        try {
            String payload = buildWebhookPayload(payment);
            String signature = signatureService.generateSignature(payload);

            WebhookDelivery delivery = createDeliveryRecord(payment, merchant, payload, signature);
            deliveryRepository.save(delivery);

            attemptDelivery(delivery.getId());
        } catch (Exception e) {
            log.error("Failed to prepare webhook for payment {}", payment.getId(), e);
        }
    }

    private boolean hasValidWebhookUrl(Merchant merchant) {
        return merchant.getWebhookUrl() != null && !merchant.getWebhookUrl().isBlank();
    }

    private String buildWebhookPayload(Payment payment) throws Exception {
        Map<String, Object> data = Map.of(
                "paymentId", payment.getId(),
                "status", payment.getStatus().name(),
                "occurredAt", Instant.now().toString()
        );

        Map<String, Object> event = Map.of(
                "id", "evt_" + UUID.randomUUID().toString().substring(0, 8),
                "type", "payment.updated",
                "data", data
        );

        return objectMapper.writeValueAsString(event);
    }

    private WebhookDelivery createDeliveryRecord(
            Payment payment,
            Merchant merchant,
            String payload,
            String signature) {

        return WebhookDelivery.builder()
                .eventId("evt_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType("payment.updated")
                .paymentId(payment.getId())
                .targetUrl(merchant.getWebhookUrl())
                .signature(signature)
                .payload(payload)
                .attempts(0)
                .delivered(false)
                .lastAttemptAt(null)
                .build();
    }

    @Async
    public void attemptDelivery(Long deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            log.warn("Delivery record {} not found", deliveryId);
            return;
        }

        try {
            boolean success = executeHttpDelivery(delivery);

            if (success) {
                log.info("Webhook delivered successfully to {} (payment: {})",
                        delivery.getTargetUrl(), delivery.getPaymentId());
            } else if (delivery.getAttempts() < MAX_RETRY_ATTEMPTS) {
                scheduleRetry(deliveryId, delivery.getAttempts());
            } else {
                log.error("Webhook delivery failed after {} attempts (payment: {})",
                        MAX_RETRY_ATTEMPTS, delivery.getPaymentId());
            }
        } catch (Exception e) {
            log.error("Error delivering webhook to {} (payment: {})",
                    delivery.getTargetUrl(), delivery.getPaymentId(), e);

            if (delivery.getAttempts() < MAX_RETRY_ATTEMPTS) {
                scheduleRetry(deliveryId, delivery.getAttempts());
            }
        }
    }

    private boolean executeHttpDelivery(WebhookDelivery delivery) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.getTargetUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Event-Type", delivery.getEventType())
                    .header("X-Signature", delivery.getSignature())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            updateDeliveryRecord(delivery, success);

            return success;
        } catch (Exception e) {
            updateDeliveryRecord(delivery, false);
            throw new RuntimeException("HTTP delivery failed", e);
        }
    }

    private void updateDeliveryRecord(WebhookDelivery delivery, boolean success) {
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(Instant.now());
        delivery.setDelivered(success);
        deliveryRepository.save(delivery);
    }

    private void scheduleRetry(Long deliveryId, int currentAttempts) {
        long delayMs = BASE_RETRY_DELAY_MS * currentAttempts;
        log.info("Scheduling retry for delivery {} in {}ms", deliveryId, delayMs);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delayMs);
                attemptDelivery(deliveryId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Retry scheduling interrupted for delivery {}", deliveryId);
            }
        });
    }
}
