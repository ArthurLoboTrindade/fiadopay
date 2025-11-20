package edu.ucsal.fiadopay.service.webhook;

import edu.ucsal.fiadopay.annotation.WebhookSink;
import edu.ucsal.fiadopay.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WebhookEventDispatcher implements SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(WebhookEventDispatcher.class);
    private final ApplicationContext context;
    private final List<WebhookSinkInfo> sinks;
    private final ExecutorService executorService;

    public WebhookEventDispatcher(ApplicationContext context) {
        this.context = context;
        this.sinks = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(3);
    }

    @Override
    public void afterSingletonsInstantiated() {
        sinks.addAll(discoverWebhookSinks(context));
        log.info("Discovered {} webhook sinks via reflection", sinks.size());
    }

    private List<WebhookSinkInfo> discoverWebhookSinks(ApplicationContext context) {
        List<WebhookSinkInfo> result = new ArrayList<>();

        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> clazz = bean.getClass();

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(WebhookSink.class)) {
                    WebhookSink annotation = method.getAnnotation(WebhookSink.class);
                    result.add(new WebhookSinkInfo(
                            bean,
                            method,
                            annotation.eventType(),
                            annotation.async(),
                            annotation.order()
                    ));
                    log.info("Registered webhook sink: {}.{} for event '{}'",
                            clazz.getSimpleName(), method.getName(), annotation.eventType());
                }
            }
        }

        result.sort(Comparator.comparingInt(WebhookSinkInfo::order));
        return result;
    }

    public void dispatch(String eventType, Payment payment) {
        log.debug("Dispatching webhook event '{}' for payment {}", eventType, payment.getId());

        for (WebhookSinkInfo sink : sinks) {
            if (sink.eventType.equals(eventType)) {
                if (sink.async) {
                    executorService.submit(() -> invokeSink(sink, payment));
                } else {
                    invokeSink(sink, payment);
                }
            }
        }
    }

    private void invokeSink(WebhookSinkInfo sink, Payment payment) {
        try {
            sink.method.setAccessible(true);
            sink.method.invoke(sink.bean, payment);
            log.debug("Invoked webhook sink: {}.{}",
                    sink.bean.getClass().getSimpleName(), sink.method.getName());
        } catch (Exception e) {
            log.error("Failed to invoke webhook sink: {}.{}",
                    sink.bean.getClass().getSimpleName(), sink.method.getName(), e);
        }
    }

    private record WebhookSinkInfo(
            Object bean,
            Method method,
            String eventType,
            boolean async,
            int order
    ) {}
}
