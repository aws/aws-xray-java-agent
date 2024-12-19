rootProject.name = "com.amazonaws.xray.agent"

pluginManagement {
    plugins {
        id("com.github.johnrengelman.shadow") version "5.2.0"
        id("nebula.release") version "15.1.0"
        id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
        id("com.github.ben-manes.versions") version "0.38.0"
    }
}

include("aws-xray-agent")
include("aws-xray-agent-plugin")
include("aws-xray-agent-benchmark")
