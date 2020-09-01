plugins {
    `java`
    `maven-publish`
    id("com.github.johnrengelman.shadow")
}

description = "AWS X-Ray Java Agent as a DiSCo Plugin"

dependencies {
    // Runtime dependencies are those that we will pull in to create the X-Ray Agent Plugin jar
    // We pull in the latest version of the X-Ray SDK and its transitives for use by the agent.
    runtimeOnly("com.amazonaws:aws-xray-recorder-sdk-core")

    // Setting isTransitive to false ensures we do not pull in any transitive dependencies of these modules
    // and pollute our JAR with them
    val nonTransitiveDeps = listOf(
            "com.amazonaws:aws-xray-recorder-sdk-aws-sdk",
            "com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2",
            "com.amazonaws:aws-xray-recorder-sdk-aws-sdk-core",
            "com.amazonaws:aws-xray-recorder-sdk-sql"
    )

    nonTransitiveDeps.forEach {
        runtimeOnly(it) {
            isTransitive = false
        }
    }

    // Project dependency that contains the actual source code of the X-Ray agent plugin
    runtimeOnly(project(":aws-xray-agent")) {
        isTransitive = false
    }

    testImplementation("org.powermock:powermock-api-mockito2:2.0.7")
    testImplementation("org.powermock:powermock-module-junit4:2.0.7")

    testImplementation("com.amazonaws:aws-xray-recorder-sdk-core")
    testImplementation("com.amazonaws:aws-xray-recorder-sdk-sql")

    // These Disco artifacts will be packaged into the final X-Ray Agent distribution
    testImplementation("software.amazon.disco:disco-java-agent")
    testImplementation("software.amazon.disco:disco-java-agent-aws-plugin")
    testImplementation("software.amazon.disco:disco-java-agent-web-plugin")
    testImplementation("software.amazon.disco:disco-java-agent-sql-plugin")
    testImplementation("software.amazon.disco:disco-java-agent-api")

    testImplementation("com.amazonaws:aws-java-sdk-dynamodb")
    testImplementation("com.amazonaws:aws-java-sdk-lambda")
    testImplementation("com.amazonaws:aws-java-sdk-s3")
    testImplementation("com.amazonaws:aws-java-sdk-sqs")
    testImplementation("com.amazonaws:aws-java-sdk-sns")
    testImplementation("software.amazon.awssdk:dynamodb")
    testImplementation("software.amazon.awssdk:lambda")
    testImplementation("software.amazon.awssdk:s3")
    testImplementation("javax.servlet:javax.servlet-api:3.1.0")
}

tasks {
    shadowJar {
        // Decorate this artifact to indicate it is a Disco plugin
        manifest {
            attributes(mapOf(
                    "Disco-Init-Class" to "com.amazonaws.xray.agent.runtime.AgentRuntimeLoader"
            ))
        }
    }

    // Copies Disco agent and plugin JARs into our lib for convenience
    register<Copy>("copyAgent") {
        val discoVer = rootProject.extra["discoVersion"]

        dependsOn(configurations.testRuntimeClasspath)
        from(configurations.testRuntimeClasspath.get())
        include("disco-java-agent-$discoVer.jar")

        into("$buildDir/libs/disco")
        rename("disco-java-agent-$discoVer.jar", "disco-java-agent.jar")
    }

    register<Copy>("copyPlugins") {
        val discoVer = rootProject.extra["discoVersion"]

        dependsOn(configurations.testRuntimeClasspath)
        from(configurations.testRuntimeClasspath.get())
        include("disco-java-agent-*-plugin-$discoVer.jar")

        rename("(.+)-$discoVer(.+)", "$1$2")
        into("$buildDir/libs/disco/disco-plugins")
    }

    register<Copy>("copyXRay") {
        from("$buildDir/libs")

        include("aws-xray-agent-plugin-$version.jar")
        rename("(.+)-$version(.+)", "$1$2")

        into("$buildDir/libs/disco/disco-plugins")
    }

    // The only tests that run in this module are integration tests, so configure them as the standard test task
    test {
        // Explicitly remove all runtime dependencies and disco plugins from the classpath since a customer's
        // application (which the integ tests simulate) should not be aware of any of those JARs
        classpath = classpath.minus(configurations.runtimeClasspath.get())

        jvmArgs("-javaagent:$buildDir/libs/disco/disco-java-agent.jar=pluginPath=$buildDir/libs/disco/disco-plugins",
                "-Dcom.amazonaws.xray.strategy.tracingName=IntegTest")

        // Cannot run tests until agent and all plugins are available
        dependsOn(withType<Copy>())
        finalizedBy("createAgentZip")
    }

    register<Zip>("createAgentZip") {
        archiveFileName.set("xray-agent.zip")
        destinationDirectory.set(file("$buildDir/dist"))
        from("$buildDir/libs")
        include("disco/**")

        dependsOn(withType<Copy>())
    }
}
