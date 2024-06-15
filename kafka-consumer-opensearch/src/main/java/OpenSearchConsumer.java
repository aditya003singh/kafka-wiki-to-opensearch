import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class OpenSearchConsumer {

    public static RestHighLevelClient createOpenSearchClient() {
//        String connString = "http://localhost:9200";
        String connString = "https://92g4vy94cx:afj5eorr43@cera-search-5510226121.eu-west-1.bonsaisearch.net:443";

        // we build a URI from the connection string
        RestHighLevelClient restHighLevelClient;
        URI connUri = URI.create(connString);
        // extract login information if it exists
        String userInfo = connUri.getUserInfo();

        if (userInfo == null) {
            // REST client without security
            restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), "http")));

        } else {
            // REST client with security
            String[] auth = userInfo.split(":");

            CredentialsProvider cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));

            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme()))
                            .setHttpClientConfigCallback(
                                    httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(cp)
                                            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())));


        }

        return restHighLevelClient;
    }

    // creating the Kafka Consumer
    private static KafkaConsumer<String, String> createKafkaConsumer(){

        String bootstrapServers = "[::1]:9092";
        String groupId = "consumer-opensearch-demo";

        // set consumer properties and config
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // for manual offset commit
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // create and return consumer
        return new KafkaConsumer<>(properties);
    }

    // function to extract id
    private static String extractId(String json) {
        // using gson library
        return JsonParser.parseString(json)
                .getAsJsonObject()
                .get("meta")
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }

    public static void main(String[] args) throws IOException {

        Logger log = LoggerFactory.getLogger(OpenSearchConsumer.class.getSimpleName());

        // first create an OpenSearch Client
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        // create our Kafka Client
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        // get a reference to the mai thread
        final Thread mainThread = Thread.currentThread();

        // adding a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run(){
                log.info("A shutdown has been detected, let us exit by calling consumer.wakeup()....");
                consumer.wakeup();

                // join the main thread to allow execution in the main thread
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        // create the index on OpenSearch if it does not exist
        try {

            boolean indexExists = openSearchClient.indices().exists(new GetIndexRequest("wikimedia"), RequestOptions.DEFAULT);

            if (!indexExists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                log.info("The wikimedia index has been created!");
            } else {
                log.info("The wikimedia index already exists");
            }

            // we subscribe the consumer
            consumer.subscribe(Collections.singleton("wikimedia.recentchange"));

            while (true) {

                // main code logic to send data to OpenSearch
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                int recordCount = records.count();
                log.info("Received: " + recordCount + " record(s)");

                // creating Bulk Request
//                BulkRequest bulkRequest = new BulkRequest();

                for (ConsumerRecord<String, String> record : records) {

                    // send data to OpenSearch
                    // Making sure we do not enter duplicates to OpnSearch Index
                    // Strategy 1 - define id using Kafka Record coordinates
//                    String id = record.topic() + "_" + record.partition() + "_" + record.offset();

                    try {
                        // Strategy 2 - we extract the id from the json value
                        String id = extractId(record.value());

                        IndexRequest indexRequest = new IndexRequest("wikimedia")
                                .source(record.value(), XContentType.JSON)
                                .id(id);

                        IndexResponse response = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);
//                        bulkRequest.add(indexRequest);

//                        log.info(response.getId());
                    } catch (Exception e) {

                    }
                }

                // Part to bulk insert data
//                if (bulkRequest.numberOfActions() > 0) {
//                    // execute the bulk request
//                    BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
//                    log.info("Inserted " + bulkResponse.getItems().length + " record(s)");
//
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//
//                    // commit offsets after the batch has been consumed
//                    consumer.commitSync();
//                    log.info("Offsets have been committed");
//                }

                // commit offsets after the batch has been consumed
                 consumer.commitSync();
                 log.info("Offsets have been committed");
            }

        } catch (WakeupException e) {
            log.info("Consumer is starting to shut down");
        } catch (IOException e) {
            log.error("Unexpected exception in the consumer", e);
        } finally {
            // close things
            openSearchClient.close();
            consumer.close();
            log.info("The consumer is now gracefully shut down");
        }
    }
}
