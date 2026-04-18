# Kestra JMS Plugin

## What

- Provides plugin components under `io.kestra.plugin.jms`.
- Includes classes such as `Consume`, `JMSMessage`, `Produce`, `JMSDestination`.

## Why

- This plugin integrates Kestra with JMS.
- It provides tasks that produce, consume, and trigger workflows from JMS queues or topics.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `jms`

Infrastructure dependencies (Docker Compose services):

- `activemq`
- `app`

### Key Plugin Classes

- `io.kestra.plugin.jms.Consume`
- `io.kestra.plugin.jms.Produce`
- `io.kestra.plugin.jms.RealtimeTrigger`

### Project Structure

```
plugin-jms/
├── src/main/java/io/kestra/plugin/jms/serde/
├── src/test/java/io/kestra/plugin/jms/serde/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
