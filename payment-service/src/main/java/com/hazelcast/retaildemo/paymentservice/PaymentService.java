package com.hazelcast.retaildemo.paymentservice;

import com.hazelcast.retaildemo.sharedmodels.OrderModel;
import com.hazelcast.retaildemo.sharedmodels.PaymentFinishedModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootApplication
@Slf4j
public class PaymentService {

    public static final String PAYMENT_FINISHED = "payment-finished";

    public static void main(String[] args) {
        SpringApplication.run(PaymentService.class);
    }

    @Autowired
    private KafkaTemplate<String, PaymentFinishedModel> kafkaTemplate;

    @Bean
    public ApplicationRunner applicationRunner() {
        return args -> {

        };
    }

    @KafkaListener(topics = {"new-orders"}, groupId = "test")
    void newOrderArrived(OrderModel order) {
        log.info("received order {}", order);
        kafkaTemplate.send(PAYMENT_FINISHED, PaymentFinishedModel.builder()
                .order(order)
                .isSuccess(randomSuccessOrFailure())
                .build());
    }

    private boolean randomSuccessOrFailure() {
        return Math.random() > 0.15d;
    }

}
