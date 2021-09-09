package org.hazelcast.retaildemo.stockservice;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.map.IMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.hazelcast.retaildemo.StockEntry;
import org.hazelcast.retaildemo.sharedmodels.OrderModel;
import org.hazelcast.retaildemo.sharedmodels.PaymentFinishedModel;
import org.hazelcast.retaildemo.sharedmodels.PaymentRequestModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Properties;

@SpringBootApplication
@Slf4j
public class StockService {

    public static void main(String[] args) {
        SpringApplication.run(StockService.class, args);
    }

    @Autowired
    private KafkaTemplate<String, PaymentRequestModel> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    private final HazelcastInstance hzClient = HazelcastClient.newHazelcastClient();

    @Bean
    public ApplicationRunner appRunner() {
        return args -> {
            Properties properties = new Properties();
            properties.put("key.deserializer", StringDeserializer.class.getName());
            properties.put("value.deserializer", JsonDeserializer.class.getName());
            properties.put("bootstrap.servers", "kafka:9092");
            properties.put("auto.offset.reset", "earliest");
            StreamSource<Map.Entry<String, PaymentFinishedModel>> source = KafkaSources.kafka(properties, "payment-finished");

            Pipeline pl = Pipeline.create();
            pl.readFrom(source).withoutTimestamps()
//                    .writeTo(Sinks.files("/tmp"))
                    .writeTo(Sinks.map("shippable_orders"))
            ;
            hzClient.getJet().newJob(pl);

            for (;;) {
                Thread.sleep(2000);
                System.out.println("shippable order count: " + hzClient.getMap("shippable_orders").size());
            }
        };
    }

    @KafkaListener(topics = "new-orders", groupId = "test")
    public void newOrder(OrderModel order) {
//        log.info("received new-orders: {}", order);
        IMap<String, StockEntry> stockMap = hzClient.getMap("stock");
        order.getOrderLines().forEach(line -> {
            int requestedQuantity = line.getQuantity();
            stockMap.executeOnKey(line.getProductId(), new ReservationEntryProcessor(requestedQuantity));
        });
        var orderId = orderRepository.save(order);
        kafkaTemplate.send("payment-request", PaymentRequestModel.builder()
                .orderId(orderId)
                .orderLines(order.getOrderLines())
                .build());
    }

//    @KafkaListener(topics = "payment-finished", groupId = "test")
//    public void paymentFinished(PaymentFinishedModel paymentFinished) {
//        log.info("received paymentFinished: {}", paymentFinished);
//        Long orderId = paymentFinished.getOrderId();
//        var orderLines = orderRepository.findOrderLinesByOrderId(orderId);
//        orderLines.forEach(line -> {
//                    IMap<String, StockEntry> stockMap = hzClient.getMap("stock");
//                    stockMap.executeOnKey(line.getProductId(), new PaymentFinishedEntryProcessor(
//                            paymentFinished.isSuccess(),
//                            line.getQuantity())
//                    );
//                }
//        );
//    }
}
