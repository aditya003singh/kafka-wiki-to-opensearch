# kafka-wiki-to-opensearch
This project demonstrates the integration of Kafka for streaming real-time data from Wikimedia, processing it with custom transformations, and writing it to OpenSearch. The project is divided into three main modules: `kafka-producer-wikimedia`, `kafka-consumer-opensearch`, and `kafka-streams-wikimedia`.

## Table of Contents

1. [Kafka Producer Wikimedia](#kafka-producer-wikimedia)
2. [Kafka Consumer OpenSearch](#kafka-consumer-opensearch)
3. [Kafka Streams Wikimedia](#kafka-streams-wikimedia)

## Kafka Producer Wikimedia

This producer sources data from the Wikimedia RecentChange Event Stream. The following topics have been explored and implemented:

### Producer Acknowledgement Deep Dive
- **acks=0**: Producer won’t wait for acknowledgement (possible data loss).
- **acks=1**: Producer will wait for leader acknowledgement (limited data loss).
- **acks=all/-1**: Leader + replicas acknowledgement (no data loss).

### Producer Retries
- Defaults to 0 for Kafka <= 2.0.
- Defaults to 2147483647 for Kafka >= 2.1.
- `retry.backoff.ms` default setting is 100ms.
- Producer timeout default is 2 minutes.

### Idempotent Producer

### Safe Kafka Producer Summary
- **Kafka Version 2.8 or lower**
  - `acks=all (-1)`: Ensures data is properly replicated before an ack is received.
  - `min.insync.replicas=2` (broker/topic level): Ensures at least 2 brokers in ISR have the data after an ack.
  - `enable.idempotence=true`: Prevents duplicates due to network retries.
  - `retries=MAX_INT` (producer level): Retry until `delivery.timeout.ms` is reached.
  - `delivery.ms.timeout=120000`: Fail after retrying for 2 minutes.
  - `max.in.flight.requests.per.connection=5`: Ensures maximum performance while maintaining message ordering.
- **Kafka Version 3.0 or higher**
  - The producer is safe by default.
  
### Kafka Message Compression
- Tested compression methods like snappy and lz4 for optimal speed/compression ratio.
- Used `linger.ms` and `batch.size` to create bigger batches, resulting in more compression and higher throughput.

### Advanced Producer
- `max.block.ms`: Default value is 60000ms.
- `buffer.memory`: Default value is 32 MB.

## Kafka Consumer OpenSearch

This module acts as a sink for Wikimedia RecentChange data. OpenSearch can be set up locally using Docker or via a cloud service like Bonsai.io. The following topics have been explored and implemented:

### Consumer Delivery Semantics
- At most once.
- At least once.
- Exactly once.

### Idempotent Consumer

### Consumer Offsets Commit Strategies
- `enable.auto.commit = true`
- `enable.auto.commit = false` (manual commit)

### Batching Data
- Implemented bulk requests.

### Consumer Offset Reset Behavior
- `auto.offset.reset=latest`: Read from the end of the log.
- `auto.offset.reset=earliest`: Read from the start of the log.
- `auto.offset.reset=none`: Throws an exception if no offset is found.

## Kafka Streams Wikimedia

Reads from the topic storing data from Wikipedia and performs real-time analytics. The following stream builders have been implemented:

### BotCountStreamBuilder
- Counts the number of times a change was created by a bot versus a human.

### EventCountTimeseriesBuilder
- Analyzes the number of changes per website.

### WebsiteCountStreamBuilder
- Tracks the number of edits in a time window as a time series.
