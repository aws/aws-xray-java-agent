plugins {
    `java`
}

dependencies {
    implementation("com.amazonaws:aws-xray-recorder-sdk-core")
    implementation("com.amazonaws:aws-xray-recorder-sdk-sql")
    implementation("com.fasterxml.jackson.core:jackson-core")

    implementation("software.amazon.disco:disco-java-agent-core")
    implementation("software.amazon.disco:disco-java-agent-web")
    implementation("software.amazon.disco:disco-java-agent-aws-api")

    testImplementation("org.powermock:powermock-api-mockito2:2.0.7")
    testImplementation("org.powermock:powermock-module-junit4:2.0.7")
    testImplementation("com.github.stefanbirkner:system-rules:1.16.0")
    testImplementation("javax.servlet:javax.servlet-api:3.1.0")
    testImplementation("commons-io:commons-io:2.7")

    // For reflective Trace ID injection tests
    testImplementation("com.amazonaws:aws-xray-recorder-sdk-log4j")
    testImplementation("com.amazonaws:aws-xray-recorder-sdk-slf4j")
}

description = "AWS X-Ray Runtime Java Agent"
