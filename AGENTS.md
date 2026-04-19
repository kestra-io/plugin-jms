# Kestra JMS Plugin

## What

- Provides plugin components under `io.kestra.plugin.jms`.
- Includes classes such as `Consume`, `JMSMessage`, `Produce`, `JMSDestination`.

## Why

- What user problem does this solve? Teams need to produce, consume, and trigger workflows from JMS queues or topics from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps JMS steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on JMS.

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
