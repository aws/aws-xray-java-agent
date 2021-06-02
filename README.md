[![Build Status](https://github.com/aws/aws-xray-java-agent/workflows/Continuous%20Build/badge.svg)](https://github.com/aws/aws-xray-java-agent/actions)

![Screenshot of the AWS X-Ray console](/images/xray-agent-sample.png?raw=true)

# AWS X-Ray Java Agent

The AWS X-Ray Java Agent is a drop-in solution that enables the propagation of X-Ray traces within your Java web applications and servlet-based microservices. 
This includes automatic tracing for supported frameworks and libraries, including Spring, the AWS SDK, Apache HTTP clients, and JDBC-based SQL queries. 
The agent enables you to use the X-Ray SDK out of box, and requires no code changes to enable the basic propagation of traces. 
See the chart below for the current feature parity between the AWS X-Ray SDK and the AWS X-Ray Java Agent.

The X-Ray Java Agent is implemented using the [DiSCo library](https://github.com/awslabs/disco), a toolkit for building Java Agents in distributed environments.

## Compatibility Chart

| *Feature*	| *X-Ray SDK*	| *X-Ray Agent* |
| ----------- | ----------- | ----------- |
| AWS SDK V1 Instrumentation (Confirmed on 1.11.x) | ✔ | ✔ | 
| AWS SDK V2 Instrumentation | ✔ | ✔ | 
| [Centralized Sampling](https://docs.aws.amazon.com/xray/latest/devguide/xray-console-sampling.html) | ✔ | ✔ | 
| Automatic Multi-threaded Support | ❌ | ✔ | 
| Custom manual instrumentation | ✔ | ✔ | 
| [SQL Queries](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-sqlclients.html) | ✔ | ✔ | 
| [Plugins](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-configuration.html#xray-sdk-java-configuration-plugins) | ✔ | ✔ | 
| [Apache HTTP Client](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-httpclients.html) | ✔ | ✔ | 
| [HttpServlet](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-filters.html) | ✔ | ✔ | 
| [Lambda Layers](https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html) | ✔ | ❌ | 
| Log Injection | ✔ | ✔ | 
| Spring Framework | ✔ | ✔ | 

## Prerequisites

The AWS X-Ray Java Agent is compatible with Java 8 and 11. You must be able to modify the JVM arguments of your
application to use the agent.

## Installation

You can download the latest version of the X-Ray Agent [here](https://github.com/aws/aws-xray-java-agent/releases/latest/download/xray-agent.zip),
or you can browse the [releases](https://github.com/aws/aws-xray-java-agent/releases) to download earlier versions.

Alternatively, you can download the agent from Maven Central by adding a dependency on it. This approach is
not recommended because you will be responsible for assembling all the JARs into the required file structure. 
To depend on the agent from your project, just add these dependencies:

```xml
<dependencies>
    <dependency>
        <groupId>software.amazon.disco</groupId>
        <artifactId>disco-toolkit-bom</artifactId>
        <version>0.11.0</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
    <dependency>
        <groupId>software.amazon.disco</groupId>
        <artifactId>disco-java-agent</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.disco</groupId>
        <artifactId>disco-java-agent-aws-plugin</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.disco</groupId>
        <artifactId>disco-java-agent-sql-plugin</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.disco</groupId>
        <artifactId>disco-java-agent-web-plugin</artifactId>
    </dependency>
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-xray-agent-plugin</artifactId>
        <version>2.9.1</version>
    </dependency>
</dependencies>
```

To get started with the agent in your application, see the [official documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-GettingStarted).

## Sample App

To see the Agent in action first, checkout the `xray-agent` branch of the [eb-java-scorekeep](https://github.com/aws-samples/eb-java-scorekeep/tree/xray-agent).
The README has steps to set up a fully-functioning Spring Boot application instrumented with the X-Ray
Agent and complemented with manual instrumentation by the X-Ray SDK.

## Performance impacts

To get an idea of how much impact the X-Ray Agent might have on your system, please see the [benchmarking package](https://github.com/aws/aws-xray-java-agent/tree/main/aws-xray-agent-benchmark).

## Customizing the Agent

### Configuration

The X-Ray Agent is configured by an external, user-provided JSON file. By default this file is expected to be located at 
the root of the user's classpath and titled `xray-agent.json`. You can configure a custom location for the config
file by setting the `com.amazonaws.xray.configFile` system property to the absolute filesystem path OR
absolute location on the classpath of your configuration file.

For more details about configuring the agent, see the [official documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-Configuration).

### Toggling Tracing for Different Events

The benefit of the X-Ray Agent operating as a plugin rather than a monolith is that you can add or remove other
compatible DiSCo plugins as you please. All of the plugins that the X-Ray agent will use are in the directory pointed
to by the `pluginPath` parameter in your JVM argument. The plugins are what instrument different events like AWS SDK requests
and SQL queries so they can be traced by X-Ray. Normally, this is the `disco-plugins` directory in the ZIP distribution,
but it can be any directory.

To remove an undesirable plugin, simply remove it from your `pluginPath` directory and restart your app. For example, to
disable tracing of SQL queries, remove the `disco-java-agent-sql-plugin` from your `pluginPath`.

To add new DiSCo plugins, just add them as a JAR to your `pluginPath` directory and restart your app. For example, if a
plugin to intercept HTTP requests made with OkHTTP was developed, you should be able to just build the plugin and add the
JAR to your `pluginPath`. No additional configuration from the agent should be required, but feel free to open an issue if it
doesn't work out of the box.

### Troubleshooting

When troubleshooting the agent, one of the first steps is to enable logging for the agent. The agent uses Apache Commons Logging,
so you may need to add a bridge like `log4j-jcl` or `jcl-over-slf4j` to your classpath to see logs. To configure the log level,
set this system property:

```
logging.level.com.amazonaws.xray = DEBUG
```

If your problem cannot be diagnosed with X-Ray logs, then DiSCo logs could help too.
To enable DiSCo logs, append the classpath of the DiSCo logger to your JVM argument that's enabling the X-Ray agent, like so:

```
-javaagent:/<path-to-disco>/disco-java-agent.jar=pluginPath=/<path-to-disco>/disco-plugins:loggerfactory=software.amazon.disco.agent.reflect.logging.StandardOutputLoggerFactory:verbose
```

For more troubleshooting steps, see the [official documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-Troubleshooting).

## Developing on the Agent

### Structure of this repo

This repository contains the X-Ray Agent as a DiSCo plugin. Note that this is NOT a proper Java agent with 
a premain and bytecode manipulation. Rather it is a *plugin* to extend the functionality of a proper Java agent 
like the one described. To learn more about DiSCo plugins and how they
work with the DiSCo java agent, see the [DiSCo documentation](https://github.com/awslabs/disco/tree/main/disco-java-agent/disco-java-agent).

The layout of this project is:

[`aws-xray-agent`](https://github.com/aws/aws-xray-java-agent/tree/main/aws-xray-agent) - The source code of the AWS
X-Ray agent plugin. This contains the hooks that allow our plugin to communicate with the DiSCo Agent. It is also where
instrumentation using the X-Ray SDK happens.

[`aws-xray-agent-plugin`](https://github.com/aws/aws-xray-java-agent/tree/main/aws-xray-agent-plugin) - This package
contains no source code. It only uses a series of build rules to bundle the above source code into a JAR that represents
a DiSCo plugin, run integration tests against that JAR, then finally bundle that JAR and all needed DiSCo dependencies into
an archive for the end user.

[`aws-xray-agent-benchmark`](https://github.com/aws/aws-xray-java-agent/tree/main/aws-xray-agent-benchmark) - This package
also contains no source code. It runs tests to compare the performance of the X-Ray Agent and the X-Ray SDK.

### Building from Source

If there are unreleased changes on the `main` branch that you'd like to try out early, you can build the agent from its source code. The agent uses Gradle to manage its builds and produce the `xray-agent.zip` artifact that is ultimately distributed with [releases](https://github.com/aws/aws-xray-java-agent/releases). You can build the agent distribution locally by running the following commands:

```bash
git clone https://github.com/aws/aws-xray-java-agent.git
cd aws-xray-java-agent/
./gradlew build
```

Now, the latest changes on `main` will be bundled into a ZIP file located at `aws-xray-agent-plugin/build/dist/xray-agent.zip`. This ZIP file is structured the same as the one described in the [installation documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-GettingStarted), so you can follow those instructions using this artifact. For example, if you'd like to extract the X-Ray Agent JAR and its dependencies to use in your project, you could run the following commands:

```bash
cd aws-xray-agent-plugin/build/dist/
unzip xray-agent.zip                 # Unpackages Agent JAR and disco dependencies into a disco directory
cp -r disco /path/to/your/project    # Copies the disco directory for use in your project with the -javaagent argument
```

## Getting Help

Please use these community resources for getting help.

* If you think you may have found a bug or need assistance, please open an [issue](https://github.com/aws/aws-xray-java-agent/issues/new).
* Open a support ticket with [AWS Support](http://docs.aws.amazon.com/awssupport/latest/user/getting-started.html).
* Ask a question in the [AWS X-Ray Forum](https://forums.aws.amazon.com/forum.jspa?forumID=241&start=0).
* For contributing guidelines refer to [CONTRIBUTING.md](https://github.com/aws/aws-xray-java-agent/blob/main/CONTRIBUTING.md).

## License

The AWS X-Ray SDK Java Agent is licensed under the Apache 2.0 License. See LICENSE and NOTICE.txt for more information.
