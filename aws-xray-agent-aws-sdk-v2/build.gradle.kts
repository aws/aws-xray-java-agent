plugins {
    `java`
}

dependencies {
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-v2")
    implementation("com.amazonaws:aws-xray-recorder-sdk-aws-sdk-core")
    implementation("software.amazon.disco:disco-java-agent-api")
    implementation("software.amazon.disco:disco-java-agent-aws-api")
    implementation(project(":aws-xray-agent"))
}

description = "AWS X-Ray Agent AWS SDK V2 auto-instrumentation library"