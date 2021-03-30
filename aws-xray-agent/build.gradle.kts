plugins {
    `java`
    `jacoco`
}

description = "AWS X-Ray Runtime Java Agent"

dependencies {
    implementation("com.amazonaws:aws-xray-recorder-sdk-core")
    implementation("com.amazonaws:aws-xray-recorder-sdk-sql")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-core")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2")
    implementation("software.amazon.disco:disco-java-agent-aws-api")
    implementation("com.fasterxml.jackson.core:jackson-core")

    implementation("software.amazon.disco:disco-java-agent-core")
    implementation("software.amazon.disco:disco-java-agent-web")
    implementation("software.amazon.disco:disco-java-agent-aws-api")
    implementation("com.blogspot.mydailyjava:weak-lock-free:0.18")

    testImplementation("org.powermock:powermock-api-mockito2:2.0.7")
    testImplementation("org.powermock:powermock-module-junit4:2.0.7")
    testImplementation("com.github.stefanbirkner:system-rules:1.16.0")
    testImplementation("com.amazonaws:aws-java-sdk-dynamodb")
    testImplementation("javax.servlet:javax.servlet-api:3.1.0")
    testImplementation("commons-io:commons-io:2.7")

    // For reflective Trace ID injection tests
    testImplementation("com.amazonaws:aws-xray-recorder-sdk-log4j")
    testImplementation("com.amazonaws:aws-xray-recorder-sdk-slf4j")
    testImplementation("org.apache.logging.log4j:log4j-api:2.13.3")
    testImplementation("ch.qos.logback:logback-classic:1.3.0-alpha5")
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
}

jacoco {
    toolVersion = "0.8.6"
}
tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}
