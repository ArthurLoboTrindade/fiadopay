package edu.ucsal.fiadopay.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InterestCalculationService {
    private final List<InterestCalculator> calculators;

    public InterestCalculationService(List<InterestCalculator> calculators) {
        this.calculators = calculators;
    }

    public InterestCalculator.InterestCalculation calculate(
            String paymentMethod,
            BigDecimal baseAmount,
            int installments) {

        return calculators.stream()
                .filter(calc -> calc.appliesTo(paymentMethod, installments))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No interest calculator found"))
                .calculate(baseAmount, installments);
    }
}
