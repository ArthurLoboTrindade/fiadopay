package edu.ucsal.fiadopay.service;

import edu.ucsal.fiadopay.domain.Payment;

public interface PaymentProcessor {

    void process(Payment payment);
}
