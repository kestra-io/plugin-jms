# JMS Plugin Examples

This directory contains example flows and Docker Compose setups for manually testing the JMS plugin with different brokers.

## Prerequisites

**IMPORTANT:** Before using the examples, follow these steps:

### 1. Build the Plugin (skip tests to avoid port conflicts)

```bash
./gradlew build -x test
```

This command:
- Builds the plugin JAR to `build/libs/` (needed for Kestra)
- Automatically copies RabbitMQ and ActiveMQ JMS clients to `build/test-libs/` (needed for examples)
- Skips running tests

### 2. Start Docker Compose

The Docker Compose setup mounts both directories into the Kestra container.

## Quick Start with ActiveMQ Artemis

The fastest way to get started is using ActiveMQ Artemis, which requires no external installations.

### 1. Build and Start Services

```bash
# Build plugin (if not done yet)
./gradlew build -x test

# Start services
docker-compose -f src/examples/docker/docker-compose.yml up
```

This starts:
- **ActiveMQ Artemis** on ports 61616 (JMS) and 8161 (Web Console)
- **Kestra** on port 8080

### 2. Access UIs

- **Kestra UI**: http://localhost:8080
- **ActiveMQ Console**: http://localhost:8161 (admin/admin)

### 3. Import Example Flows

In the Kestra UI, import the example flows from `src/examples/flows/activemq/`:

- **jms_produce_activemq.yaml** - Send a message to an ActiveMQ queue
- **jms_consume_activemq.yaml** - Consume messages from a queue
- **jms_trigger_activemq.yaml** - Trigger a flow when messages arrive

### 4. Run Examples

1. Execute **jms_produce_activemq** to send a test message
2. Execute **jms_consume_activemq** to read messages
3. Enable **jms_trigger_activemq** and send more messages to see it trigger automatically

## Alternative: Quick Start with ActiveMQ Classic

ActiveMQ Classic (5.x series) is the legacy version of ActiveMQ, different from the modern ActiveMQ Artemis. Use this if you're working with existing ActiveMQ Classic infrastructure.

### 1. Build and Start Services

```bash
# Build plugin (if not done yet)
./gradlew build -x test

# Start services with ActiveMQ Classic
docker-compose -f src/examples/docker/docker-compose-activemq-classic.yml up
```

This starts:
- **ActiveMQ Classic** on ports 61616 (JMS) and 8161 (Web Console)
- **Kestra** on port 8080

### 2. Access UIs

- **Kestra UI**: http://localhost:8080
- **ActiveMQ Console**: http://localhost:8161/admin (admin/admin)

### 3. Import Example Flows

In the Kestra UI, import the example flows from `src/examples/flows/activemq-classic/`:

- **jms_produce_activemq_classic.yaml** - Send a message to an ActiveMQ Classic queue
- **jms_consume_activemq_classic.yaml** - Consume messages from a queue
- **jms_trigger_activemq_classic.yaml** - Trigger a flow when messages arrive

### 4. Run Examples

1. Execute **jms_produce_activemq_classic** to send a test message
2. Execute **jms_consume_activemq_classic** to read messages
3. Enable **jms_trigger_activemq_classic** and send more messages to see it trigger automatically

**Note:** The key difference from Artemis is the connection factory class: `org.apache.activemq.ActiveMQConnectionFactory` (Classic) vs `org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory` (Artemis) and the slightly different connection property names userName vs user.

## Alternative: Quick Start with RabbitMQ

RabbitMQ is also supported as an alternative JMS provider.

### 1. Build and Start Services

```bash
# Build plugin (if not done yet)
./gradlew build -x test

# Start services with RabbitMQ
docker-compose -f src/examples/docker/docker-compose-rabbitmq.yml up
```

This starts:
- **RabbitMQ** on ports 5672 (AMQP) and 15672 (Management UI)
- **Kestra** on port 8080

### 2. Access UIs

- **Kestra UI**: http://localhost:8080
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

### 3. Import Example Flows

In the Kestra UI, import the example flows from `src/examples/flows/rabbitmq/`:

- **jms_send.yaml** - Send a message to a RabbitMQ queue
- **jms_consume.yaml** - Consume messages from a queue
- **jms_trigger.yaml** - Trigger a flow when messages arrive

**Note:** RabbitMQ has limited JMS support (no message selectors).

## Using SonicMQ / Aurea Messenger

If you have access to SonicMQ (now known as Aurea Messenger), you can use the more advanced examples.

### 1. Update Docker Compose

Edit `src/examples/docker/docker-compose-sonicmq.yml` and update the volume mount to point to your local SonicMQ installation:

```yaml
volumes:
  - /path/to/your/sonic/lib/:/app/plugins/jms-libs/
```

### 2. Start Services

```bash
docker-compose -f src/examples/docker/docker-compose-sonicmq.yml up
```

### 3. Import Example Flows

In the Kestra UI, import the example flows from `src/examples/flows/sonicmq/`:

#### Direct Connection Examples (`direct/`)
- **jms_send.yaml** - Send messages using direct connection
- **jms_consume.yaml** - Consume messages
- **jms_trigger.yaml** - Basic trigger
- **jms_trigger_with_reply.yaml** - Advanced trigger with reply

#### JNDI Connection Examples (`jndi/`)
- **jms_send_jndi.yaml** - Send messages using JNDI lookup
- **jms_consume_jndi.yaml** - Consume messages via JNDI
- **jms_trigger_jndi.yaml** - Trigger with JNDI configuration

### 4. Configure Connection Details

Update the connection details in the flows to match your SonicMQ broker:
- Host/port
- Username/password
- Queue/topic names

## Troubleshooting

### JMS client libs not found

**Error**: Cannot find JMS client libraries (ActiveMQ, RabbitMQ, etc.)

**Solution**: Make sure you ran `./gradlew build -x test` before starting docker-compose. This copies the required dependencies to `build/test-libs/`.

### Plugin not loaded in Kestra

**Error**: JMS plugin tasks not available in Kestra UI

**Solution**:
1. Ensure you built the plugin: `./gradlew build -x test`
2. Check that `build/libs/` contains the plugin JAR
3. Restart the docker-compose services

### Running Tests

The integration tests use ActiveMQ Artemis. Start the test infrastructure:

```bash
docker-compose -f ../../docker-compose-ci.yml up -d
./gradlew test
```

### Connection refused to ActiveMQ/RabbitMQ

**Error**: Cannot connect to the message broker

**Solution**:
1. Wait for the broker to fully start (check logs: `docker-compose logs activemq` or `docker-compose logs rabbitmq`)
2. Verify the broker is healthy: `docker-compose ps`
3. If using `localhost` in flows, change it to the service name (`activemq` or `rabbitmq`)

## Directory Structure

```
src/examples/
├── README.md                                # This file
├── docker/
│   ├── docker-compose.yml                  # Default: ActiveMQ Artemis quick-start
│   ├── docker-compose-activemq-classic.yml # Alternative: ActiveMQ Classic (5.x)
│   ├── docker-compose-rabbitmq.yml         # Alternative: RabbitMQ setup
│   └── docker-compose-sonicmq.yml          # SonicMQ/Aurea Messenger setup
└── flows/
    ├── activemq/                           # ActiveMQ Artemis examples (default)
    │   ├── jms_produce_activemq.yaml
    │   ├── jms_consume_activemq.yaml
    │   └── jms_trigger_activemq.yaml
    ├── activemq-classic/                   # ActiveMQ Classic (5.x) examples
    │   ├── jms_produce_activemq_classic.yaml
    │   ├── jms_consume_activemq_classic.yaml
    │   └── jms_trigger_activemq_classic.yaml
    ├── rabbitmq/                           # RabbitMQ examples (alternative)
    │   ├── jms_send.yaml
    │   ├── jms_consume.yaml
    │   └── jms_trigger.yaml
    └── sonicmq/                            # SonicMQ/Aurea Messenger examples
        ├── direct/                         # Direct connection examples
        │   ├── jms_send.yaml
        │   ├── jms_consume.yaml
        │   ├── jms_trigger.yaml
        │   └── jms_trigger_with_reply.yaml
        └── jndi/                           # JNDI connection examples
            ├── jms_send_jndi.yaml
            ├── jms_consume_jndi.yaml
            └── jms_trigger_jndi.yaml
```

## Notes

- **ActiveMQ Artemis** is recommended for quick testing and development (full JMS support, modern architecture)
- **ActiveMQ Classic** (5.x series) is the legacy version, use if working with existing Classic infrastructure
- **RabbitMQ** is supported but has limited JMS features (no message selectors)
- **SonicMQ/Aurea Messenger** examples demonstrate advanced JMS features and JNDI lookup
- All setups mount the plugin from `build/libs/` so you can rebuild and restart to test changes
- Debug port 7999 is available if you uncomment the JAVA_OPTS environment variable
