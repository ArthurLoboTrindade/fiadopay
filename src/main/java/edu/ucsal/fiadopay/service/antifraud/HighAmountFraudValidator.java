package edu.ucsal.fiadopay.service.antifraud;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@AntiFraud(name = "HighAmountValidator", threshold = 5000.0, riskLevel = AntiFraud.RiskLevel.HIGH)
public class HighAmountFraudValidator implements FraudValidator {
    private static final Logger log = LoggerFactory.getLogger(HighAmountFraudValidator.class);
    private static final BigDecimal THRESHOLD = new BigDecimal("5000.00");

    @Override
    public ValidationResult validate(Payment payment) {
        if (payment.getAmount().compareTo(THRESHOLD) > 0) {
            log.warn("High amount detected for payment {}: {}", payment.getId(), payment.getAmount());
            return new ValidationResult(false, "High amount transaction requires manual review");
        }
        return new ValidationResult(true, "Amount within acceptable range");
    }
}
