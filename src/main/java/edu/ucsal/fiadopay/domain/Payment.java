package edu.ucsal.fiadopay.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Table(
    indexes = { @Index(columnList="merchantId"), @Index(columnList="status") },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_merchant_idempotency", columnNames = {"merchantId", "idempotencyKey"})
    }
)
public class Payment {
    @Id
    private String id;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private Integer installments;

    private Double monthlyInterest;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalWithInterest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 64)
    private String idempotencyKey;
    @Column(length = 255)
    private String metadataOrderId;

    public enum Status { PENDING, APPROVED, DECLINED, EXPIRED, REFUNDED }
}
