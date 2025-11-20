package edu.ucsal.fiadopay.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AntiFraud {

    String name();

    double threshold() default 0.0;

    RiskLevel riskLevel() default RiskLevel.MEDIUM;

    boolean enabled() default true;

    enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
}
