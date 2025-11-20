package edu.ucsal.fiadopay.service.antifraud;

import edu.ucsal.fiadopay.domain.Payment;

public interface FraudValidator {

    ValidationResult validate(Payment payment);

    record ValidationResult(boolean valid, String message) {}
}
