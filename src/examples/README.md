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
- Automatically copies the RabbitMQ JMS client to `build/test-libs/` (needed for examples)
- Skips running tests

### 2. Start Docker Compose

The Docker Compose setup mounts both directories into the Kestra container.

## Quick Start with RabbitMQ

The fastest way to get started is using RabbitMQ, which requires no external installations.

### 1. Build and Start Services

```bash
# Build plugin (if not done yet)
./gradlew build -x test

# Start services
docker-compose -f src/examples/docker/docker-compose.yml up
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

### 4. Run Examples

1. Execute **jms_send** to send a test message
2. Execute **jms_consume** to read messages
3. Enable **jms_trigger** and send more messages to see it trigger automatically

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

### RabbitMQ JMS libs not found

**Error**: Cannot find RabbitMQ JMS client libraries

**Solution**: Make sure you ran `./gradlew build -x test` before starting docker-compose. This copies the required dependencies to `build/test-libs/`.

### Plugin not loaded in Kestra

**Error**: JMS plugin tasks not available in Kestra UI

**Solution**:
1. Ensure you built the plugin: `./gradlew build -x test`
2. Check that `build/libs/` contains the plugin JAR
3. Restart the docker-compose services

### Running Tests

If you want to run the integration tests after starting the examples:

```bash
./gradlew test
```

The tests will use the RabbitMQ instance already running via docker-compose.

### Connection refused to RabbitMQ

**Error**: Cannot connect to RabbitMQ

**Solution**:
1. Wait for RabbitMQ to fully start (check logs: `docker-compose logs rabbitmq`)
2. Verify RabbitMQ is healthy: `docker-compose ps`
3. If using `localhost` in flows, change it to `rabbitmq` (the service name)

## Directory Structure

```
src/examples/
├── README.md                           # This file
├── docker/
│   ├── docker-compose.yml             # Default: RabbitMQ quick-start
│   └── docker-compose-sonicmq.yml     # SonicMQ/Aurea Messenger setup
└── flows/
    ├── rabbitmq/                      # RabbitMQ examples (quick-start)
    │   ├── jms_send.yaml
    │   ├── jms_consume.yaml
    │   └── jms_trigger.yaml
    └── sonicmq/                       # SonicMQ/Aurea Messenger examples
        ├── direct/                    # Direct connection examples
        │   ├── jms_send.yaml
        │   ├── jms_consume.yaml
        │   ├── jms_trigger.yaml
        │   └── jms_trigger_with_reply.yaml
        └── jndi/                      # JNDI connection examples
            ├── jms_send_jndi.yaml
            ├── jms_consume_jndi.yaml
            └── jms_trigger_jndi.yaml
```

## Notes

- **RabbitMQ** is recommended for quick testing and development
- **SonicMQ/Aurea Messenger** examples demonstrate advanced JMS features and JNDI lookup
- Both setups mount the plugin from `build/libs/` so you can rebuild and restart to test changes
- Debug port 7999 is available if you uncomment the JAVA_OPTS environment variable
