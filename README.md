<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
  <a href="https://twitter.com/kestra_io" style="margin: 0 10px;">
        <img src="https://kestra.io/twitter.svg" alt="twitter" width="35" height="25" /></a>
  <a href="https://www.linkedin.com/company/kestra/" style="margin: 0 10px;">
        <img src="https://kestra.io/linkedin.svg" alt="linkedin" width="35" height="25" /></a>
  <a href="https://www.youtube.com/@kestra-io" style="margin: 0 10px;">
        <img src="https://kestra.io/youtube.svg" alt="youtube" width="35" height="25" /></a>
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 4 minutes.</i></p>


# Kestra JMS Plugin

> Java Message Service support for Kestra

This repository contains a set of plugins to use JMS with your Kestra flows.
The goal if this plugin is to remain as generic as possible and stick with the JMS standard APIs.
The plugin should work with any JMS 1.x compliant implementation.

Even older client versions using javax.jms instead of jakarta.jms are supported.
The compatibility is ensured using the [CONAPI JMS-adapter](https://github.com/conapi-oss/jms-adapter).

The generic approach requires you to provide the JMS client libraries with your deployment.
The most convenient (default) way is to put the JAR files in a sub folder of your Kestra plugins folder called 'jms-libs'.
Alternatively you can specify the jar file location as part of the trigger/task configuration.

Configuration examples are provided in the test/resources [flows](https://github.com/kestra-io/plugin-jms/tree/main/src/test/resources/flows) location.

For simple testing of the plugin, we highly recommend [Message Manager] (https://conapi.at/message-manager/)

**_NOTE:_** As a Kestra user you get a free 12 months Professional license of Message Manager. Use the contact form on the conapi website.

There are also some blogs with instructions and a video demo available:

- https://conapi.at/gravitee-kestra-integration-apis-workflows/
- https://conapi.at/kestra-jms-integration-trigger-send-receive/

This repository contains three different plugins:

## Real Time Trigger

Can be used to start a new flow execution whenever a JMS message is received.

## JMS Consumer

Can be used at task level inside a flow to consume/receive JMS messages.

## JMS Producer

Can be used at task level inside a flow to send a JMS messages to a queue or a topic.

## Running the project in local
### Prerequisites
- Java 21
- Docker

### Running tests
Ensure you have ActiveMQ Artemis running. You can use the provided docker-compose-ci.yml:

```bash
docker-compose -f docker-compose-ci.yml up -d
./gradlew check --parallel
```

### Development

`VSCode`:

Follow the README.md within the `.devcontainer` folder for a quick and easy way to get up and running with developing plugins if you are using VSCode.

`Other IDEs`:

```
./gradlew shadowJar && docker-compose up
```
> [!NOTE]
> You need to relaunch this whole command everytime you make a change to your plugin

go to http://localhost:8080, your plugin will be available to use

## Documentation
* Full documentation can be found under: [kestra.io/docs](https://kestra.io/docs)
* Documentation for developing a plugin is included in the [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/)


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)


## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
