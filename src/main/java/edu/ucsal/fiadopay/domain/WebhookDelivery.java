package edu.ucsal.fiadopay.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private String eventType;
    private String paymentId;
    private String targetUrl;
    private String signature;
    private int attempts;
    private boolean delivered;
    private Instant lastAttemptAt;

    @Lob
    private String payload;
}
