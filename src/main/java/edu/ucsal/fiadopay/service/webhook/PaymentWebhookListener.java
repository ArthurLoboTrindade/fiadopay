package edu.ucsal.fiadopay.service.webhook;

import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentWebhookListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookListener.class);

    @WebhookSink(eventType = "payment.updated", async = true, order = 1)
    public void onPaymentUpdated(Payment payment) {
        log.info("Webhook listener triggered for payment.updated: {} - Status: {}",
                payment.getId(), payment.getStatus());

    }

    @WebhookSink(eventType = "payment.updated", async = true, order = 2)
    public void onPaymentUpdatedAnalytics(Payment payment) {
        log.info("Recording analytics for payment: {} with amount: {}",
                payment.getId(), payment.getAmount());
    }
}
