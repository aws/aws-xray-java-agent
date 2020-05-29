package com.amazonaws.xray.agent.config;

import java.util.Objects;

/**
 * Class represents the agent configuration's JSON file
 */
public class AgentConfiguration {
    private String serviceName;
    private String contextMissingStrategy;
    private String daemonAddress;
    private String samplingStrategy;
    private int maxStackTraceLength;
    private int streamingThreshold;
    private String samplingRulesManifest;
    private int awsSDKVersion;
    private String awsServiceHandlerManifest;
    private boolean tracingEnabled;

    /**
     * Sets default or placeholders values
     */
    public AgentConfiguration() {
        tracingEnabled = true;
        contextMissingStrategy = "LOG_ERROR";
        awsSDKVersion = 2;

        // Integers are initialized to invalid value
        maxStackTraceLength = -1;
        streamingThreshold = -1;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getContextMissingStrategy() {
        return contextMissingStrategy;
    }

    public void setContextMissingStrategy(String contextMissingStrategy) {
        this.contextMissingStrategy = contextMissingStrategy;
    }

    public String getDaemonAddress() {
        return daemonAddress;
    }

    public void setDaemonAddress(String daemonAddress) {
        this.daemonAddress = daemonAddress;
    }

    public String getSamplingStrategy() {
        return samplingStrategy;
    }

    public void setSamplingStrategy(String samplingStrategy) {
        this.samplingStrategy = samplingStrategy;
    }

    public int getMaxStackTraceLength() {
        return maxStackTraceLength;
    }

    public void setMaxStackTraceLength(int maxStackTraceLength) {
        this.maxStackTraceLength = maxStackTraceLength;
    }

    public int getStreamingThreshold() {
        return streamingThreshold;
    }

    public void setStreamingThreshold(int streamingThreshold) {
        this.streamingThreshold = streamingThreshold;
    }

    public String getSamplingRulesManifest() {
        return samplingRulesManifest;
    }

    public int getAwsSDKVersion() {
        return awsSDKVersion;
    }

    public void setAwsSDKVersion(int awsSDKVersion) {
        this.awsSDKVersion = awsSDKVersion;
    }

    public void setSamplingRulesManifest(String samplingRulesManifest) {
        this.samplingRulesManifest = samplingRulesManifest;
    }

    public String getAwsServiceHandlerManifest() {
        return awsServiceHandlerManifest;
    }

    public void setAwsServiceHandlerManifest(String awsServiceHandlerManifest) {
        this.awsServiceHandlerManifest = awsServiceHandlerManifest;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }

    @Override
    public String toString() {
        return "AgentConfiguration{" +
                "serviceName='" + serviceName + '\'' +
                ", contextMissingStrategy='" + contextMissingStrategy + '\'' +
                ", daemonAddress='" + daemonAddress + '\'' +
                ", samplingStrategy='" + samplingStrategy + '\'' +
                ", maxStackTraceLength=" + maxStackTraceLength +
                ", streamingThreshold=" + streamingThreshold +
                ", samplingRulesManifest='" + samplingRulesManifest + '\'' +
                ", awsSDKVersion='" + awsSDKVersion + '\'' +
                ", awsServiceHandlerManifest='" + awsServiceHandlerManifest + '\'' +
                ", tracingEnabled=" + tracingEnabled +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentConfiguration that = (AgentConfiguration) o;
        return maxStackTraceLength == that.maxStackTraceLength &&
                streamingThreshold == that.streamingThreshold &&
                tracingEnabled == that.tracingEnabled &&
                awsSDKVersion == that.awsSDKVersion &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(contextMissingStrategy, that.contextMissingStrategy) &&
                Objects.equals(daemonAddress, that.daemonAddress) &&
                Objects.equals(samplingStrategy, that.samplingStrategy) &&
                Objects.equals(samplingRulesManifest, that.samplingRulesManifest) &&
                Objects.equals(awsServiceHandlerManifest, that.awsServiceHandlerManifest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, contextMissingStrategy, daemonAddress, samplingStrategy, maxStackTraceLength, streamingThreshold, samplingRulesManifest, awsSDKVersion, awsServiceHandlerManifest, tracingEnabled);
    }
}
