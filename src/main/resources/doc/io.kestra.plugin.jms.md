# How to use the JMS plugin

Produce and consume messages on any JMS-compatible broker from Kestra flows.

## Authentication

Configure `connectionFactoryConfig` with one of two subtypes:

**Direct** (load the ConnectionFactory class directly): set `connectionFactoryClass` (required, fully-qualified class name) and `providerJarPaths` (list of paths to the broker's JAR files). Optionally set `username`, `password`, and `connectionProperties`.

**JNDI** (look up via JNDI): set `jndiInitialContextFactory`, `jndiProviderUrl`, and `jndiConnectionFactoryName` (all required). Optionally set `jndiPrincipal` and `jndiCredentials` for authenticated JNDI lookups.

Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

All tasks require a `destination` — set `destinationName` and `destinationType` (`QUEUE` by default).

`Produce` publishes messages — set `from` (a `kestra://` URI or inline data, required). Control serialization with `serdeType` (`STRING` by default, also `JSON` or `BYTES`).

`Consume` reads messages from a `destination` — bound the batch with `maxMessages` (default 1). Filter with `messageSelector` (JMS selector syntax). Control deserialization with `serdeType`.

`RealtimeTrigger` starts one execution per message as it arrives. Set `destination`, `messageSelector`, and `serdeType` the same way as `Consume`.
