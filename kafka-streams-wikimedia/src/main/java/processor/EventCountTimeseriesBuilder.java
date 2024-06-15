package processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class EventCountTimeseriesBuilder {

    private static final String TIMESERIES_TOPIC = "wikimedia.stats.timeseries";
    private static final String TIMESERIES_STORE = "event-count-store";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KStream<String, String> inputStream;

    public EventCountTimeseriesBuilder(KStream<String, String> inputStream) {
        this.inputStream = inputStream;
    }

    public void setup() {
        final TimeWindows timeWindows = TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10));
        this.inputStream
                .selectKey((key, value) -> "key-to-group")
                .groupByKey()
                .windowedBy(timeWindows)
                .count(Materialized.as(TIMESERIES_STORE))
                .toStream()
                .mapValues((readOnlyKey, value) -> {
                    final Map<String, Object> kvMap = new HashMap<>();
                    kvMap.put("start_time", readOnlyKey.window().startTime().toString());
                    kvMap.put("end_time", readOnlyKey.window().endTime().toString());
                    kvMap.put("window_size", timeWindows.size());
                    kvMap.put("event_count", value);
                    try {
                        return OBJECT_MAPPER.writeValueAsString(kvMap);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .to(TIMESERIES_TOPIC, Produced.with(
                        WindowedSerdes.timeWindowedSerdeFrom(String.class, timeWindows.size()),
                        Serdes.String()
                ));
    }
}
