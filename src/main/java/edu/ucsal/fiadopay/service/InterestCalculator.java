package edu.ucsal.fiadopay.service;

import java.math.BigDecimal;

public interface InterestCalculator {

    record InterestCalculation(Double monthlyInterestRate, BigDecimal totalWithInterest) {}

    InterestCalculation calculate(BigDecimal baseAmount, int installments);

    boolean appliesTo(String paymentMethod, int installments);
}
