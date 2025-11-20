package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.controller.PaymentResponse;
import edu.ucsal.fiadopay.domain.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getInstallments(),
                payment.getMonthlyInterest(),
                payment.getTotalWithInterest()
        );
    }
}
