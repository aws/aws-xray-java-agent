plugins {
    id("me.champeau.gradle.jmh") version "0.5.0"
}

val JMH_VERSION = "1.23"

sourceSets {
    named("jmh") {
        java {
            srcDir("src/jmh/java")

        }
    }
}

dependencies {
    jmh("com.amazonaws:aws-xray-recorder-sdk-core")
    jmh("com.amazonaws:aws-xray-recorder-sdk-sql")
    jmh("com.amazonaws:aws-xray-recorder-sdk-aws-sdk") {
        isTransitive = false  // Don't bring in all clients
    }
    jmh("com.amazonaws:aws-java-sdk-dynamodb:${rootProject.extra["awsSdkV1Version"]}")
    jmh("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2")
    jmh("software.amazon.awssdk:dynamodb:${rootProject.extra["awsSdkV2Version"]}")
    jmh("com.amazonaws:aws-xray-recorder-sdk-apache-http")
    jmh("com.blogspot.mydailyjava:weak-lock-free:0.18")

    jmh("org.apache.httpcomponents:httpclient:4.5.12")
    jmh("javax.servlet:javax.servlet-api:4.0.1")
    jmh("org.eclipse.jetty:jetty-server:9.4.1.v20170120")
    jmh("org.eclipse.jetty:jetty-servlet:9.4.1.v20170120")

    jmh("org.mockito:mockito-core:2.28.2")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:${JMH_VERSION}")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:${JMH_VERSION}")

    jmh(platform("com.amazonaws:aws-xray-recorder-sdk-bom:${rootProject.extra["xraySdkVersion"]}"))
}

// JMH plugin docs: https://github.com/melix/jmh-gradle-plugin
jmh {
    benchmarkMode = listOf("thrpt", "sample")
    fork = 1
    timeUnit = "ms"

    warmupIterations = 10
    warmup = "1s"

    iterations = 5
    timeOnIteration = "1s"

    if (project.hasProperty("agent")) {
        val discoPath = "${rootProject.projectDir}/aws-xray-agent-plugin/build/libs/disco"
        jvmArgs = listOf("-javaagent:$discoPath/disco-java-agent.jar=pluginPath=$discoPath/disco-plugins",
                "-Dcom.amazonaws.xray.strategy.tracingName=Benchmark")

        resultsFile = project.file("$buildDir/reports/jmh/auto-instrumentation.txt")
    } else if (project.hasProperty("sdk")) {
        jvmArgs = listOf("-Dcom.amazonaws.xray.sdk=true")  // Propagate the system property into testing environment to use appropriate clients
        resultsFile = project.file("$buildDir/reports/jmh/sdk-instrumentation.txt")
    } else {
        resultsFile = project.file("$buildDir/reports/jmh/no-instrumentation.txt")
    }

    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
}

// Need to have the disco directory assembled to run benchmarks with X-Ray agent
tasks.jmh {
    if (project.hasProperty("agent")) {
        dependsOn(":aws-xray-agent-plugin:build")
    }
}
