package edu.ucsal.fiadopay.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class NoInterestCalculator implements InterestCalculator {

    @Override
    public InterestCalculation calculate(BigDecimal baseAmount, int installments) {
        return new InterestCalculation(null, baseAmount);
    }

    @Override
    public boolean appliesTo(String paymentMethod, int installments) {
        return true;
    }
}
