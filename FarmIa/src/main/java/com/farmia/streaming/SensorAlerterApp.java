package com.farmia.streaming;

import com.farmia.iot.SensorTelemetry;
import com.farmia.streaming.data.SensorAlert;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class SensorAlerterApp {

    public static void main(String[] args) throws IOException {
        Properties props = new Properties();
        String config = "streams.properties";
        try (InputStream fis = SensorAlerterApp.class.getClassLoader().getResourceAsStream(config)) {
            props.load(fis);
        }
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sensor-alerter-app");

        final String inputTopic = "sensor-telemetry";
        final String outputTopic = "sensor-alerts";

        final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", "http://localhost:8081");
        Serde<SensorTelemetry> sensorTelemetrySerde = new SpecificAvroSerde<>();
        sensorTelemetrySerde.configure(serdeConfig, false);
        Serde<SensorAlert> sensorAlertSerde = new SpecificAvroSerde<>();
        sensorAlertSerde.configure(serdeConfig, false);

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, SensorTelemetry> sensorStream = builder.stream(inputTopic, Consumed.with(Serdes.String(), sensorTelemetrySerde));

        sensorStream
                .filter((key, value) -> value.getTemperature() > 35 || value.getHumidity() < 20)
                .mapValues(value -> {
                    SensorAlert alert = new SensorAlert();
                    alert.setSensorId(value.getSensorId());
                    alert.setTimestamp(value.getTimestamp());
                    alert.setAlertType(value.getTemperature() > 35 ? "HIGH_TEMPERATURE" : "LOW_HUMIDITY");
                    alert.setDetails(value.getTemperature() > 35 ? "Temperature exceeded 35°C" : "Humidity below 20%");
                    return alert;
                })
                .to(outputTopic, Produced.with(Serdes.String(), sensorAlertSerde));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}