package edu.ucsal.fiadopay.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CardInstallmentInterestCalculator implements InterestCalculator {
    private static final double MONTHLY_INTEREST_RATE = 1.0;
    private static final BigDecimal INTEREST_FACTOR = new BigDecimal("1.01");

    @Override
    public InterestCalculation calculate(BigDecimal baseAmount, int installments) {
        if (installments <= 1) {
            return new InterestCalculation(null, baseAmount);
        }

        BigDecimal factor = INTEREST_FACTOR.pow(installments);
        BigDecimal totalWithInterest = baseAmount.multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);

        return new InterestCalculation(MONTHLY_INTEREST_RATE, totalWithInterest);
    }

    @Override
    public boolean appliesTo(String paymentMethod, int installments) {
        return "CARD".equalsIgnoreCase(paymentMethod) && installments > 1;
    }
}
