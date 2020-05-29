[![Build Status](https://travis-ci.com/aws/aws-xray-java-agent.svg?branch=master)](https://travis-ci.com/aws/aws-xray-java-agent)

![Screenshot of the AWS X-Ray console](/images/xray-agent-service-graph-example.png?raw=true)

## AWS X-Ray Java Agent

The AWS X-Ray Java Agent is a drop-in solution that enables the propogation of X-Ray traces within your web applications. This includes automatic tracing for AWS X-Ray SDK supported frameworks and libraries. The agent enables you to use the X-Ray SDK out of box, and requires no code changes to enable the basic propogation of traces. See the compatibility chart below for the current feature parity between the AWS X-Ray SDK and the AWS X-Ray Java Agent.

See the [Sample App](https://github.com/aws/aws-xray-java-agent/tree/master/sample) for a demonstration on how to use the agent.

The X-Ray Java Agent is implemented using the [DiSCo library](https://github.com/awslabs/disco), an all purpose AWS toolkit for building Java Agents.

## Versioning

Each version of the agent corresponds to the supported version of the SDK in order to ensure version compatibility. This agent is a beta release. You may track issues and fixes through the issues tab of the repository. 

The agent provides auto-instrumentation support for customers using version **2.4.0** of the [X-Ray SDK](https://github.com/aws/aws-xray-sdk-java/), with an artifact version number of **2.4.0-beta.1**.

## Compatibility Chart

| *Feature*	| *X-Ray SDK*	| *X-Ray Agent* |
| ----------- | ----------- | ----------- |
| AWS SDK V1 Instrumentation (Confirmed on 1.11.x) | ✔ | ✔ | 
| AWS SDK V2 Instrumentation | ✔ | ✔ | 
| [Centralized Sampling](https://docs.aws.amazon.com/xray/latest/devguide/xray-console-sampling.html) | ✔ | ✔ | 
| Automatic Multi-threaded Support | ❌ | ✔ | 
| Generate Custom Subsegments | ✔ | ✔ | 
| [SQL Queries](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-sqlclients.html) | ✔ | ❌ | 
| [Plugins](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-configuration.html#xray-sdk-java-configuration-plugins) | ✔ | ❌ | 
| [Apache HTTP Client](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-httpclients.html) | ✔ | ✔ | 
| [HttpServlet](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-filters.html) | ✔ | ✔ | 
| [Lambda Layers](https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html) | ✔ | ✔ | 
| Log Injection | ✔ | ❌ | 
| Spring Framework | ✔ | ❌ | 

## Prerequisites

The AWS X-Ray Java Agent is compatible with Java 8 and 11.

## Installation

The first step is to bring in the agent JAR files into your environment. Insert the following dependencies into your project’s pom.xml file:
```
  <dependencies>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-auto-instrumentation-agent-bootstrap</artifactId>
          <version>2.4.0-beta.1</version>
          <scope>runtime</scope>
      </dependency>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-auto-instrumentation-agent-runtime</artifactId>
          <version>2.4.0-beta.1</version>
      </dependency>
  </dependencies>
```
Add the following plugin to your project:
```
  <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-dependency-plugin</artifactId>
      <version>3.1.1</version>
      <executions>
          <execution>
              <id>unpack-xray-agent</id>
              <phase>package</phase>
              <goals>
                  <goal>copy</goal>
              </goals>
              <configuration>
                  <overWriteReleases>false</overWriteReleases>
                  <overWriteSnapshots>false</overWriteSnapshots>
                  <overWriteIfNewer>true</overWriteIfNewer>
                  <artifactItems>
                      <artifactItem>
                          <!--Obtain the runtime agent artifact-->
                          <groupId>com.amazonaws</groupId>
                          <artifactId>aws-xray-auto-instrumentation-agent-runtime</artifactId>
                          <outputDirectory>${project.build.directory}/xray-agent</outputDirectory>
                      </artifactItem>
                      <artifactItem>
                          <!--Obtain the bootstrap agent artifact-->
                          <groupId>com.amazonaws</groupId>
                          <artifactId>aws-xray-auto-instrumentation-agent-bootstrap</artifactId>
                          <outputDirectory>${project.build.directory}/xray-agent</outputDirectory>
                      </artifactItem>
                  </artifactItems>
              </configuration>
          </execution>
      </executions>
  </plugin>
```
The agent will be built in your project’s target/xray-agent folder. There will be two JAR files. A JAR file that has a “runtime” classifier, and one without. 

Prior to running your application, add the following Java arguments. Make sure to modify the service name with your service’s name:
```
-javaagent:/path-to-project/target/xray-agent/aws-xray-auto-instrumentation-agent-bootstrap-2.4.0-beta.1.jar=servicename=TheServiceName

-cp:/path-to-project/target/xray-agent/aws-xray-auto-instrumentation-agent-runtime-2.4.0-beta.1.jar
```
Make sure to have the following dependencies in your target application before adding the agent into your environment.
```
  <dependencies>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-auto-instrumentation-agent-runtime</artifactId>
          <version>2.4.0-beta.1</version>
      </dependency>
  </dependencies>
```
This runtime agent artifact will include all the necessary transitive dependencies into your environment.

## Lambda Layers

Using AWS Lambda layers, you can configure a reusable agent to be includeed in your Lambda function without needing to upload the source every time. To configure layers in Lambda, see https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html#configuration-layers-manage.

You will build the agent as you did in during the *Installation* section, but you will add an additional artifact dependency:

```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-xray-auto-instrumentation-agent-installer</artifactId>
      <version>2.4.0-beta.1</version>
  </dependency>
```
Add the following artifact item into the unpack-xray-agent execution id of the maven-dependency-plugin:
```
  <artifactItem>
      <!--Obtain the Agent installer artifact-->
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-xray-auto-instrumentation-agent-installer</artifactId>
      <outputDirectory>${project.build.directory}/xray-agent</outputDirectory>
  </artifactItem>
```
This artifact is used for installing the agent during runtime. This is required for the consumption of the Lambda environment. 

We do not recommend using this installer in a regular runtime environment. Running the X-Ray agent using the Java agent argument is more reliable when ensuring all frameworks are properly instrumented. 

After building the agent, it should add three additional JAR files to the ./target/xray-agent directory. 

Then, we need to obtain the **tools.jar** file from the JDK. Lambda executes the Java code in a Linux environment. In order to use this JAR file, we recommend downloading the latest [Correto JDK 1.8](https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/downloads-list.html) version from this website and extracting it from the distribution. Download the **tar.gz** bundled version of the Correto JDK, under Linux x64, and unzip it into a directory. The **tools.jar** file should be located in `lib/tools.jar`. With all the JAR files decomperssed, upload them as a layer in Lambda. For convenience, you may also retrieve the `tools.jar` file from the sample app resource [here](https://github.com/aws/aws-xray-java-agent/tree/master/sample/src/main/resources).

The next step is to add all the transitive dependencies of the agent as a layer. The dependencies required are the runtime agent (which is built in the step above), the X-Ray SDK Core package, and the X-Ray AWS SDK instrumentor package. Use the following plugin:
```
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.0.0</version>
    <executions>
      <execution>
        <id>copy-dependencies</id>
        <phase>package</phase>
        <goals>
          <goal>copy-dependencies</goal>
        </goals>
        <configuration>
          <outputDirectory>${project.build.directory}/alternateLocation</outputDirectory>
          <overWriteReleases>false</overWriteReleases>
          <overWriteSnapshots>false</overWriteSnapshots>
          <overWriteIfNewer>true</overWriteIfNewer>
        </configuration>
      </execution>
    </executions>
  </plugin>
```
With the transitive dependencies and the agent built, add them as a layer in Lambda.

To use the agent, import the following class. Make sure that the `aws-xray-auto-instrumentation-agent-installer` artifact is consumed as a dependency:
```
import com.amazonaws.xray.agent.XRayAgentInstaller;
```
Add the following line to the top of the class definition:
```
static {
    XRayAgentInstaller.installInLambda("servicename=YourServiceNameHere");
}
```
Your Lambda function should now be instrumented.

## Configuration

The X-Ray Agent is configured by an external, user-provided JSON file. By default this file is expected to be located at 
the root of the user's resources directory and titled `xray-agent.json`. You can configure a custom location for the config
file by setting the `com.amazonaws.xray.configFile` system property to the absolute path of your configuration file.

An example configuration file is as follows:
```json
{
    "serviceName": "XRayInstrumentedService",
    "contextMissingStrategy": "LOG_ERROR",
    "daemonAddress": "127.0.0.1:2000",
    "tracingEnabled": true,
    "samplingStrategy": "CENTRAL",
    "samplingRulesManifest": "/path/to/manifest",
    "maxStackTraceLength": 50,
    "streamingThreshold": 100,
    "awsSDKVersion": 2,
    "awsServiceHandlerManifest": "/path/to/manifest"
}
```

### Configuration Specification
Refer to the below table for valid values and descriptions of each property. Note that property names
are case sensitive, but their keys are not. For properties that can be overridden by environment variables and
system properties, the order of priority is always Environment Variable, then System Property, then configuration
file. See the [X-Ray Docs](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-configuration.html#xray-sdk-java-configuration-envvars) 
for more context on some overrideable properties. All fields are optional.

| ﻿**Property Name**        | **Type** | **Valid Values**                                                  | **Description**                                                                                                                                                                                                                                  | **Environment Variable**     | **System Property**                                    | **Default**                                                                                                                                                                                                                     |
|---------------------------|---------|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|----------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| serviceName               | String  | Any string                                                    | The name of your instrumented service as it will appear in the X-Ray console                                                                                                                                                                 | AWS_XRAY_TRACING_NAME    | com.amazonaws.xray.strategy.tracingName            | XRayInstrumentedService                                                                                                                                                                                                     |
| contextMissingStrategy    | String  | LOG_ERROR, IGNORE_ERROR                                       | The action taken by the agent when it attempts to use the X-Ray Segment context but none is present                                                                                                                                          | AWS_XRAY_CONTEXT_MISSING | com.amazonaws.xray.strategy.contextMissingStrategy | LOG_ERROR                                                                                                                                                                                                                   |
| daemonAddress             | String  | Formatted IP address and port, or list of TCP and UDP address | The address the agent uses to communicate with the X-Ray Daemon                                                                                                                                                                              | AWS_XRAY_DAEMON_ADDRESS  | com.amazonaws.xray.emitter.daemonAddress           | 127.0.0.1:2000                                                                                                                                                                                                              |
| tracingEnabled            | Boolean | True, False                                                   | Enables instrumentation by the X-Ray Agent                                                                                                                                                                                                   | AWS_XRAY_TRACING_ENABLED | com.amazonaws.xray.tracingEnabled                  | TRUE                                                                                                                                                                                                                        |
| samplingStrategy          | String  | CENTRAL, LOCAL, NONE, ALL                                     | The sampling strategy used by the Agent. ALL captures all requests, NONE captures no requests. See [X-Ray Sampling](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java-configuration.html#xray-sdk-java-configuration-sampling). | N/A                      | N/A                                                | CENTRAL                                                                                                                                                                                                                     |
| samplingRulesManifest     | String  | An absolute file path                                         | The path to a custom sampling rules file to be used as the source of sampling rules for the local sampling strategy, or the fallback rules for the central strategy.                                                                         | N/A                      | N/A                                                | [DefaultSamplingRules.json](https://github.com/aws/aws-xray-sdk-java/blob/master/aws-xray-recorder-sdk-core/src/main/resources/com/amazonaws/xray/strategy/sampling/DefaultSamplingRules.json)                              |
| maxStackTraceLength       | Integer | >= 0                                                          | The maximum lines of a stack trace to record in a trace                                                                                                                                                                                      | N/A                      | N/A                                                | 50                                                                                                                                                                                                                          |
| streamingThreshold        | Integer | >= 0                                                          | Once at least this many subsegments have been closed, they will be streamed to the daemon out-of-band to avoid chunks being too large                                                                                                        | N/A                      | N/A                                                | 100                                                                                                                                                                                                                         |
| awsSDKVersion             | Integer | 1, 2                                                          | Version of the [AWS SDK for Java](https://docs.aws.amazon.com/sdk-for-java/index.html) you're using. Ignored if `awsServiceHandlerManifest` is not also set.                                                                                 | N/A                      | N/A                                                | 2                                                                                                                                                                                                                           |
| awsServiceHandlerManifest | String  | An absolute file path                                         | The path to a custom parameter whitelist to capture additional information from AWS SDK clients.                                                                                                                                             | N/A                      | N/A                                                | [DefaultOperationParameterWhitelist.json](https://github.com/aws/aws-xray-sdk-java/blob/master/aws-xray-recorder-sdk-aws-sdk-v2/src/main/resources/com/amazonaws/xray/interceptors/DefaultOperationParameterWhitelist.json) |

Note that the `serviceName` property was configurable in the agent's JVM argument, but that behavior is deprecated
and will be removed in a future version.

## Getting Help

Please use these community resources for getting help.

* If you think you may have found a bug or need assistance, please open an [issue](https://github.com/aws/aws-xray-java-agent/issues/new).
* Open a support ticket with [AWS Support](http://docs.aws.amazon.com/awssupport/latest/user/getting-started.html).
* Ask a question in the [AWS X-Ray Forum](https://forums.aws.amazon.com/forum.jspa?forumID=241&start=0).
* For contributing guidelines refer to [CONTRIBUTING.md](https://github.com/aws/aws-xray-java-agent/blob/master/CONTRIBUTING.md).

## Troubleshooting
When troubleshooting the agent, one of the first steps is to enable logging in the agent. In this case, that means publishing the internal log outputs to Standard Out. To enable logging, please append the following line to your JVM arguments, with an equal sign as a delimiter:
`loggerfactory=software.amazon.disco.agent.reflect.logging.StandardOutputLoggerFactory:verbose`

For example, if your JFM args looks like `servicename=YourServiceNameHere`, then the log-enabled argument would look like
`servicename=YourServiceNameHere=loggerfactory=software.amazon.disco.agent.reflect.logging.StandardOutputLoggerFactory:verbose`

Following is a list of the most common issues, and how to resolve them.

**I've built the agent and published it as a Lambda layer but it's still not instrumenting any of my downstream calls.**

One of the most common reasons this might be happening is your Lambda function does not contain all the necessary transitive dependencies that the agent requires. We recommend downloading the Lambda layer and manually verifying your dependencies. You may cross reference it with the `compile` scoped dependencies seen [here](/aws-xray-auto-instrumentation-agent-runtime/pom.xml)

## Documentation

The [developer guide](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java.html) provides guidance on using the AWS X-Ray Java Agent. Please refer to the [Sample App](https://github.com/aws/aws-xray-java-agent/tree/master/sample) for an example.

## Building from Source
Once you check out the code from GitHub, you can build it using Maven. You can build the package locally by running the following command from the repository root:

```
./mvnw clean package -Dgpg.skip=true
```

## License

The AWS X-Ray SDK Java Agent is licensed under the Apache 2.0 License. See LICENSE and NOTICE.txt for more information.
