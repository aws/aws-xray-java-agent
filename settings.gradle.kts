rootProject.name = "com.amazonaws.xray.agent"

pluginManagement {
    plugins {
        id("com.github.johnrengelman.shadow") version "8.1.1"
        id("nebula.release") version "18.0.6"
        id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
        id("com.github.ben-manes.versions") version "0.38.0"
    }
}

include("aws-xray-agent")
include("aws-xray-agent-plugin")
include("aws-xray-agent-benchmark")
