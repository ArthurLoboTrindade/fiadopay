package edu.ucsal.fiadopay.service.antifraud;

import edu.ucsal.fiadopay.annotation.AntiFraud;
import edu.ucsal.fiadopay.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AntiFraudService {
    private static final Logger log = LoggerFactory.getLogger(AntiFraudService.class);
    private final List<ValidatorInfo> validators;

    public AntiFraudService(ApplicationContext context) {
        this.validators = discoverValidators(context);
        log.info("Discovered {} anti-fraud validators via reflection", validators.size());
    }

    private List<ValidatorInfo> discoverValidators(ApplicationContext context) {
        List<ValidatorInfo> result = new ArrayList<>();

        String[] beanNames = context.getBeanNamesForType(FraudValidator.class);
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> clazz = bean.getClass();

            if (clazz.isAnnotationPresent(AntiFraud.class)) {
                AntiFraud annotation = clazz.getAnnotation(AntiFraud.class);
                if (annotation.enabled()) {
                    result.add(new ValidatorInfo(
                            (FraudValidator) bean,
                            annotation.name(),
                            annotation.threshold(),
                            annotation.riskLevel()
                    ));
                    log.info("Registered validator: {} (threshold: {}, risk: {})",
                            annotation.name(), annotation.threshold(), annotation.riskLevel());
                }
            }
        }

        result.sort(Comparator.comparing((ValidatorInfo v) -> v.riskLevel).reversed());
        return result;
    }

    public List<FraudValidator.ValidationResult> validatePayment(Payment payment) {
        List<FraudValidator.ValidationResult> results = new ArrayList<>();

        for (ValidatorInfo info : validators) {
            log.debug("Applying validator: {}", info.name);
            FraudValidator.ValidationResult result = info.validator.validate(payment);
            results.add(result);
        }

        return results;
    }

    private record ValidatorInfo(
            FraudValidator validator,
            String name,
            double threshold,
            AntiFraud.RiskLevel riskLevel
    ) {}
}
