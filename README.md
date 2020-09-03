[![Build Status](https://travis-ci.com/aws/aws-xray-java-agent.svg?branch=master)](https://travis-ci.com/aws/aws-xray-java-agent)

![Screenshot of the AWS X-Ray console](/images/xray-agent-sample.png?raw=true)

# AWS X-Ray Java Agent

The AWS X-Ray Java Agent is a drop-in solution that enables the propagation of X-Ray traces within your web applications. 
This includes automatic tracing for AWS X-Ray SDK supported frameworks and libraries. 
The agent enables you to use the X-Ray SDK out of box, and requires no code changes to enable the basic propagation of traces. 
See the compatibility chart below for the current feature parity between the AWS X-Ray SDK and the AWS X-Ray Java Agent.

The X-Ray Java Agent is implemented using the [DiSCo library](https://github.com/awslabs/disco), a toolkit for building Java Agents in distributed environments.

## Compatibility Chart

| *Feature*	| *X-Ray SDK*	| *X-Ray Agent* |
| ----------- | ----------- | ----------- |
| AWS SDK V1 Instrumentation (Confirmed on 1.11.x) | ✔ | ✔ | 
| AWS SDK V2 Instrumentation | ✔ | ✔ | 
| [Centralized Sampling](https://docs.aws.amazon.com/xray/latest/devguide/xray-console-sampling.html) | ✔ | ✔ | 
| Automatic Multi-threaded Support | ❌ | ✔ | 
| Generate Custom Subsegments | ✔ | ✔ | 
| [SQL Queries](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-sqlclients.html) | ✔ | ✔ | 
| [Plugins](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-configuration.html#xray-sdk-java-configuration-plugins) | ✔ | ✔ | 
| [Apache HTTP Client](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-httpclients.html) | ✔ | ✔ | 
| [HttpServlet](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-filters.html) | ✔ | ✔ | 
| [Lambda Layers](https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html) | ✔ | ✔ | 
| Log Injection | ✔ | ✔ | 
| Spring Framework | ✔ | ✔ | 

## Prerequisites

The AWS X-Ray Java Agent is compatible with Java 8 and 11.

## Installation

You can download the latest version of the X-Ray Agent [here](https://github.com/aws/aws-xray-java-agent/releases/latest/download/xray-agent.zip),
or you can browse the [releases](https://github.com/aws/aws-xray-java-agent/releases) to download earlier versions.

Alternatively, you can download the agent from Maven Central by adding a dependency on it. To depend on the agent
from your project, just add this dependency:

```xml
<dependencies>
  <dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-xray-agent-plugin</artifactId>
    <version>2.7.1</version>
  </dependency>
</dependencies>
```

Note that you will also need the DiSCo Java Agent and DiSCo Web, AWS, and SQL plugins to properly run the agent.
To depend on those, please see the [DiSCo repository](https://github.com/awslabs/disco).

To get started with the agent, see the [official documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-GettingStarted).

## Sample App

To see the Agent in action first, checkout the `xray-agent` branch of the [eb-java-scorekeep](https://github.com/aws-samples/eb-java-scorekeep/tree/xray-agent).
The README has steps to set up a fully-functioning Spring Boot application instrumented with the X-Ray
Agent and complemented with manual instrumentation by the X-Ray SDK.

## Configuration

The X-Ray Agent is configured by an external, user-provided JSON file. By default this file is expected to be located at 
the root of the user's classpath and titled `xray-agent.json`. You can configure a custom location for the config
file by setting the `com.amazonaws.xray.configFile` system property to the absolute filesystem path OR
absolute location on the classpath of your configuration file.

For more details about configuring the agent, see the [official documentation](https://docs.aws.amazon.com/xray/latest/devguide/aws-x-ray-auto-instrumentation-agent-for-java.html#XRayAutoInstrumentationAgent-Configuration). 

## Getting Help

Please use these community resources for getting help.

* If you think you may have found a bug or need assistance, please open an [issue](https://github.com/aws/aws-xray-java-agent/issues/new).
* Open a support ticket with [AWS Support](http://docs.aws.amazon.com/awssupport/latest/user/getting-started.html).
* Ask a question in the [AWS X-Ray Forum](https://forums.aws.amazon.com/forum.jspa?forumID=241&start=0).
* For contributing guidelines refer to [CONTRIBUTING.md](https://github.com/aws/aws-xray-java-agent/blob/master/CONTRIBUTING.md).

## Troubleshooting
When troubleshooting the agent, one of the first steps is to enable logging in the agent. In this case, that means publishing the internal log outputs to Standard Out. To enable logging, please append the following line to your JVM arguments:
`:loggerfactory=software.amazon.disco.agent.reflect.logging.StandardOutputLoggerFactory:verbose`

## Building from Source
Once you check out the code from GitHub, you can build it using Gradle. You can build the package locally by running the following command from the repository root:

```
./gradlew publishToMavenLocal -x sign
```

## License

The AWS X-Ray SDK Java Agent is licensed under the Apache 2.0 License. See LICENSE and NOTICE.txt for more information.
