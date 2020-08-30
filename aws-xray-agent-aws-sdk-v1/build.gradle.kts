plugins {
    `java`
    `maven-publish`
}

description = "AWS X-Ray Agent AWS SDK V1 auto-instrumentation library"

dependencies {
    implementation(project(":aws-xray-agent-plugin"))
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk")
    implementation("software.amazon.disco:disco-java-agent-api")

    testImplementation("org.powermock:powermock-api-mockito2:2.0.7")
    testImplementation("org.powermock:powermock-module-junit4:2.0.7")
}
