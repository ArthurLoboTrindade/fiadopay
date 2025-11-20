package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.controller.PaymentRequest;
import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Merchant;
import edu.ucsal.fiadopay.domain.Payment;
import edu.ucsal.fiadopay.repo.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final AuthenticationService authenticationService;
    private final InterestCalculationService interestCalculationService;
    private final PaymentProcessor paymentProcessor;
    private final WebhookDeliveryService webhookDeliveryService;
    private final PaymentMapper paymentMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            AuthenticationService authenticationService,
            InterestCalculationService interestCalculationService,
            PaymentProcessor paymentProcessor,
            WebhookDeliveryService webhookDeliveryService,
            PaymentMapper paymentMapper) {
        this.paymentRepository = paymentRepository;
        this.authenticationService = authenticationService;
        this.interestCalculationService = interestCalculationService;
        this.paymentProcessor = paymentProcessor;
        this.webhookDeliveryService = webhookDeliveryService;
        this.paymentMapper = paymentMapper;
    }

    @Transactional
    public PaymentResponse createPayment(String authHeader, String idempotencyKey, PaymentRequest request) {
        Merchant merchant = authenticationService.authenticateMerchant(authHeader);

        if (idempotencyKey != null) {
            Payment existingPayment = paymentRepository
                    .findByIdempotencyKeyAndMerchantId(idempotencyKey, merchant.getId())
                    .orElse(null);
            if (existingPayment != null) {
                return paymentMapper.toResponse(existingPayment);
            }
        }

        int installments = request.installments() != null ? request.installments() : 1;
        InterestCalculator.InterestCalculation interest = interestCalculationService.calculate(
                request.method(),
                request.amount(),
                installments
        );

        Payment payment = Payment.builder()
                .id("pay_" + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(merchant.getId())
                .method(request.method().toUpperCase())
                .amount(request.amount())
                .currency(request.currency())
                .installments(installments)
                .monthlyInterest(interest.monthlyInterestRate())
                .totalWithInterest(interest.totalWithInterest())
                .status(Payment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .metadataOrderId(request.metadataOrderId())
                .build();

        paymentRepository.save(payment);

        paymentProcessor.process(payment);

        return paymentMapper.toResponse(payment);
    }

    public PaymentResponse getPayment(String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public Map<String, Object> refund(String authHeader, String paymentId) {
        Merchant merchant = authenticationService.authenticateMerchant(authHeader);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (!merchant.getId().equals(payment.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment does not belong to this merchant");
        }

        payment.setStatus(Payment.Status.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);

        webhookDeliveryService.sendPaymentWebhook(payment);

        return Map.of(
                "id", "ref_" + UUID.randomUUID(),
                "status", "PENDING"
        );
    }
}
