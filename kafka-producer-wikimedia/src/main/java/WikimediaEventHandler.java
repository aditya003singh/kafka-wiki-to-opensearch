import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WikimediaEventHandler implements EventHandler {

    private final Logger log = LoggerFactory.getLogger(WikimediaEventHandler.class.getSimpleName());

    private KafkaProducer<String, String> kafkaProducer;
    private String topic;

    public WikimediaEventHandler(KafkaProducer<String, String> kafkaProducer, String topic) {
        this.kafkaProducer = kafkaProducer;
        this.topic = topic;
    }

    @Override
    public void onOpen() {
        // do nothing
    }

    @Override
    public void onClosed() {
        // close the producer
        kafkaProducer.close();
    }

    @Override
    public void onMessage(String event, MessageEvent messageEvent) {
        // send the message
        log.info(messageEvent.getData());
        kafkaProducer.send(new ProducerRecord<>(topic, messageEvent.getData()));
    }

    @Override
    public void onComment(String comment) {
        // do nothing
    }

    @Override
    public void onError(Throwable t) {
        // log the error
        log.error("Error in Stream Reading", t);
    }
}
