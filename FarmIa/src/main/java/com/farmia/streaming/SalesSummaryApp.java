package com.farmia.streaming;

import com.farmia.sales.SalesSummary;
import com.farmia.sales.SalesTransaction;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;

public class SalesSummaryApp {
    public static void main(String[] args) throws IOException {
        Properties props = new Properties();
        try (InputStream fis = SalesSummaryApp.class.getClassLoader().getResourceAsStream("streams.properties")) {
            props.load(fis);
        }
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sales-summary");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        final String inputTopic = "sales-transactions";
        final String outputTopic = "sales-summary";

        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", "http://localhost:8081");

        Serde<SalesTransaction> transactionSerde = new SpecificAvroSerde<>();
        transactionSerde.configure(serdeConfig, false);

        Serde<SalesSummary> summarySerde = new SpecificAvroSerde<>();
        summarySerde.configure(serdeConfig, false);

        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(inputTopic, Consumed.with(Serdes.String(), transactionSerde))
                .groupBy((key, transaction) -> transaction.getCategory().toString())
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)))
                .aggregate(
                        () -> new SalesSummary("", 0, 0.0f, 0L, 0L),
                        (category, transaction, summary) -> {
                            summary.setCategory(category);
                            summary.setTotalQuantity(summary.getTotalQuantity() + transaction.getQuantity());
                            summary.setTotalRevenue(summary.getTotalRevenue() +
                                    (float)(transaction.getPrice() * transaction.getQuantity()));
                            return summary;
                        },
                        Materialized.with(Serdes.String(), summarySerde)
                )
                .toStream()
                .map((windowedKey, summary) -> {
                    // Asignación de timestamps de ventana
                    summary.setWindowStart(windowedKey.window().start());
                    summary.setWindowEnd(windowedKey.window().end());
                    return new KeyValue<>(windowedKey.key(), summary);
                })
                .peek((key, summary) -> System.out.println(
                        "Resumen de ventas: " +
                                "Categoría: " + key +
                                ", Ventana Inicio: " + summary.getWindowStart() + " - Ventana Fin: " + summary.getWindowEnd() +
                                ", Cantidad: " + summary.getTotalQuantity() +
                                ", Revenue: " + summary.getTotalRevenue()
                ))
                .to(outputTopic, Produced.with(Serdes.String(), summarySerde));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}