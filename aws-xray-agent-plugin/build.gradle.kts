import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    `java`
    `maven-publish`
    id("com.github.johnrengelman.shadow")
}

description = "AWS X-Ray Java Agent as a DiSCo Plugin"

dependencies {
    // Runtime dependencies are those that we will pull in to create the X-Ray Agent Plugin jar
    // We pull in the latest version of the X-Ray SDK, its transitives, and other runtime deps for use by the agent.
    runtimeOnly("com.amazonaws:aws-xray-recorder-sdk-core")
    runtimeOnly("com.blogspot.mydailyjava:weak-lock-free:0.18")

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
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.27.0")

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

    // Copies Disco agent into our lib for convenience
    register<Copy>("copyAgent") {
        val discoVer = rootProject.extra["discoVersion"]

        dependsOn(configurations.testRuntimeClasspath)
        from(configurations.testRuntimeClasspath.get())
        include("disco-java-agent-$discoVer.jar")

        into("$buildDir/libs/disco")
        rename("disco-java-agent-$discoVer.jar", "disco-java-agent.jar")
    }

    // JARs are just archives, so we can unzip it to a tmp folder
    register<Copy>("unzipAgent") {
        from(zipTree("$buildDir/libs/disco/disco-java-agent.jar"))
        into("$buildDir/libs/tmp")

        dependsOn("copyAgent")
    }

    // Inspect and rewrite the manifest in the tmp folder
    register("rewriteManifest") {
        val discoVer = rootProject.extra["discoVersion"]

        // Reads the old manifest with versioned jar, then rewrites the manifest with replaced jar name
        doFirst {
            val oldManifest: List<String> = File("$buildDir/libs/tmp/META-INF/MANIFEST.MF").readLines()
            File("$buildDir/libs/tmp/META-INF/MANIFEST.MF").printWriter().use { out ->
                oldManifest.map {
                    it.replace("disco-java-agent-$discoVer.jar", "disco-java-agent.jar")
                }.forEach {
                    out.println(it)
                }
            }
        }

        dependsOn("unzipAgent")
    }

    // Re-zip the tmp folder into the new agent JAR and overwrite the agent JAR with the old manifest
    register<Zip>("rezipAgent") {
        archiveFileName.set("disco-java-agent.jar")
        destinationDirectory.set(file("$buildDir/libs/disco"))
        from("$buildDir/libs/tmp")

        dependsOn("rewriteManifest")
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
        dependsOn("shadowJar")
        from("$buildDir/libs")

        val regexSafeVersion: String = version.toString().replace("+", "\\+")
        include("aws-xray-agent-plugin-$version.jar")
        rename("(.+)-$regexSafeVersion(.+)", "$1$2")

        into("$buildDir/libs/disco/disco-plugins")
    }

    // The only tests that run in this module are integration tests, so configure them as the standard test task
    test {
        // Explicitly remove all disco plugins from the classpath since a customer's
        // application (which the integ tests simulate) should not be aware of any of those JARs
        classpath = classpath.filter {
                    !(it.name.contains("disco-java-agent")
                            || it.name.contains("aws-xray-recorder-sdk-aws-sdk")
                            || it.name.contains("aws-xray-agent"))
                }

        // Integration tests run on Windows and Unix in GitHub actions
        jvmArgs("-javaagent:$buildDir/libs/disco/disco-java-agent.jar=pluginPath=$buildDir/libs/disco/disco-plugins:loggerfactory=software.amazon.disco.agent.reflect.logging.StandardOutputLoggerFactory",
                "-Dcom.amazonaws.xray.strategy.tracingName=IntegTest")

        // Cannot run tests until agent and all plugins are available
        dependsOn(withType<Copy>())
        dependsOn(named("rezipAgent"))
        finalizedBy("createAgentZip")
    }

    build {
        // Cannot run tests until agent and all plugins are available
        dependsOn(withType<Copy>())
        dependsOn(named("rezipAgent"))
        finalizedBy("createAgentZip")
    }

    register<Zip>("createAgentZip") {
        archiveFileName.set("xray-agent.zip")
        destinationDirectory.set(file("$buildDir/dist"))
        from("$buildDir/libs")
        include("disco/**")

        dependsOn(withType<Copy>())
        dependsOn(named("rezipAgent"))
    }
}
