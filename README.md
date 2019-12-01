## AWS X-Ray Java Agent

The AWS X-Ray Java Agent is a drop-in solution to enable X-ray traces on a web application, including automatic tracing of X-Ray SDK supported frameworks and libraries. The Java Agent provides use of the X-Ray SDK out of box, but requires no code changes to enable basic propagated traces. Please see the compatibility chart below for current feature parity between the X-Ray SDK and this Java Agent.

The Java Agent is implemented using the new [DiSCo library](https://github.com/awslabs/disco), an all purpose toolkit for building Java Agents.

## Versioning
Each version of the agent corresponds to the supported version of the SDK in order to ensure version compatibility between the SDK dependency brought in by the agent consumers and the version of the agent itself. This agent is released under beta as there are a few known issues that are being ironed out. You may track these issues through the issues tab of this repository. 

The agent provides auto instrumentation support for customers using version **2.4.0** of the [X-Ray SDK](https://github.com/aws/aws-xray-sdk-java/), with an artifact version number of **2.4.0-beta**.

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

## Installing

The AWS X-Ray Java Agent is compatible with Java 8.

The first step is to bring in the agent JAR files into your environment. Insert the following dependencies into your project’s pom.xml file:
```
  <dependencies>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-auto-instrumentation-agent-bootstrap</artifactId>
          <version>2.4.0</version>
          <scope>runtime</scope>
      </dependency>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-auto-instrumentation-agent-runtime</artifactId>
          <version>2.4.0</version>
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
The agent will be built in your project’s target/xray-agent folder. There will be two JAR files, a JAR file that has a “runtime” classifier on it and one without a classifier. 

Prior to running your application, add the following Java arguments, making sure to modify the service name with your service’s name:
```
-javaagent:/path-to-project/target/xray-agent/aws-xray-auto-instrumentation-agent-bootstrap-2.4.0.jar=servicename=TheServiceName

-cp:/path-to-project/target/xray-agent/aws-xray-auto-instrumentation-agent-runtime-2.4.0.jar
```
Please make sure to have the following dependencies in your target application before adding the Agent into your environment.
```
  <dependencies>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-recorder-sdk-core</artifactId>
          <version>2.4.0</version>
      </dependency>
      <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-recorder-sdk-aws-sdk</artifactId>
          <version>2.4.0</version>
      </dependency>
        <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-xray-recorder-sdk-aws-sdk-v2</artifactId>
          <version>2.4.0</version>
      </dependency>
  </dependencies>
```
## Lambda Layers

Using Lambda Layers, you can configure a reusable Agent to include in your Lambda functions without needing to upload the source every time. To configure a Lambda Layer, see https://docs.aws.amazon.com/lambda/latest/dg/configuration-layers.html#configuration-layers-manage.

Build the agent as you would in the *Installing* section but add an additional artifact dependency:

```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-xray-auto-instrumentation-agent-runtime</artifactId>
      <version>2.4.0</version>
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
This artifact is used for installing the agent during runtime. This is necessary for consumption in a Lambda environment. We do not recommend using this installer in a regular runtime environment as running the agent using the java agent argument is more reliable in ensuring all the frameworks are properly instrumented. 

Build the agent and it should contain three additional jars in the ./target/xray-agent directory. Next, we need to obtain the **tools.jar** file from the JDK. Lambda executes the Java code in a Linux environment. In order to get this jar file, we recommend downloading the latest [Correto JDK 1.8](https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/downloads-list.html) version from the website and extracting it from the distribution. Download the **tar.gz** bundled version of the Correto JDK under Linux x64 and unzip it into a directory. The tools.jar should be located in `lib/tools.jar`. With all the jar files ready, upload them as a Lambda Layer.

The next step is to add all the transitive dependencies of the agent as a layer. The dependencies it requires are the runtime agent (which is built in the step above), the X-Ray SDK Core package, and the X-Ray AWS SDK instrumentor package. You may use the following plugin to do so:
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
With the transitive dependencies and agent built, add them as a lambda layer.

To use the agent, import the following class, making sure that the `aws-xray-auto-instrumentation-agent-installer` artifact is consumed as a dependency:
```
import com.amazonaws.xray.agent.XRayAgentInstaller;
```
Add the following line to the top most section within the class definition:
```
static {
    XRayAgentInstaller.installInLambda("servicename=YourServiceNameHere");
}
```
Your lambda function should now be instrumented.


## Getting Help

Please use these community resources for getting help.

* If you think you may have found a bug or need assistance, please open an [issue](https://github.com/aws/aws-xray-java-agent/issues/new).
* Open a support ticket with [AWS Support](http://docs.aws.amazon.com/awssupport/latest/user/getting-started.html).
* Ask a question in the [AWS X-Ray Forum](https://forums.aws.amazon.com/forum.jspa?forumID=241&start=0).
* For contributing guidelines refer to [CONTRIBUTING.md](https://github.com/aws/aws-xray-java-agent/blob/master/CONTRIBUTING.md).

## Documentation

The [developer guide](https://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java.html) provides guidance on using the AWS X-Ray Java Agent. Please refer to the [Sample App](https://github.com/aws/aws-xray-java-agent/tree/master/sample) for an example.

## Building from Source
Once you check out the code from GitHub, you can build it using Maven. As a prerequisite, you may need to build DiSCo into Maven Local first. Follow the instructions [here](https://github.com/awslabs/disco#including-disco-as-a-dependency-in-your-product).

Once DiSCo has been built, you can build the package locally by running the following command:
```
mvn clean package -Dgpg.skip=true
```

## License

The AWS X-Ray SDK Java Agent is licensed under the Apache 2.0 License. See LICENSE and NOTICE.txt for more information.
