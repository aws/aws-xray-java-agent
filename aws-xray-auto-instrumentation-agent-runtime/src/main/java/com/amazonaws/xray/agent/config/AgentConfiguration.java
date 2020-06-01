package com.amazonaws.xray.agent.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Class represents the agent configuration's JSON file. It is immutable and thus completely thread-safe.
 */
public final class AgentConfiguration {
    private static final Log log = LogFactory.getLog(AgentConfiguration.class);
    static final String DEFAULT_SERVICE_NAME = "XRayInstrumentedService";

    private final String serviceName;
    private final String contextMissingStrategy;
    private final String daemonAddress;
    private final String samplingStrategy;
    private final int maxStackTraceLength;
    private final int streamingThreshold;
    private final String samplingRulesManifest;
    private final int awsSDKVersion;
    private final String awsServiceHandlerManifest;
    private final boolean tracingEnabled;

    /**
     * Sets default values
     */
    public AgentConfiguration() {
        serviceName = DEFAULT_SERVICE_NAME;
        contextMissingStrategy = "LOG_ERROR";
        daemonAddress = "127.0.0.1:2000";
        samplingStrategy = "CENTRAL";
        maxStackTraceLength = 50;
        streamingThreshold = 100;
        samplingRulesManifest = null; // Manifests are null by default since the default location file will be found later
        awsSDKVersion = 2;
        awsServiceHandlerManifest = null;
        tracingEnabled = true;
    }

    /**
     * Constructs agent configuration from provided map. Does not do any validation since that is taken care of in
     * {@link XRaySDKConfiguration}. Can throw {@code InvalidAgentConfigException} if improper type is assigned to any
     * property. Assigns all non-configured properties to default values.
     * @param properties - Map of property names to their values in string representation, which will be cast to proper types.
     */
    public AgentConfiguration(Map<String, String> properties) {
        String serviceName = DEFAULT_SERVICE_NAME,
                contextMissingStrategy = "LOG_ERROR",
                daemonAddress = "127.0.0.1:2000",
                samplingStrategy = "CENTRAL",
                samplingRulesManifest = null,
                awsServiceHandlerManifest = null;
        int maxStackTraceLength = 50,
                streamingThreshold = 100,
                awsSDKVersion = 2;
        boolean tracingEnabled = true;

        try {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                switch (entry.getKey()) {
                    case "serviceName":
                        serviceName = entry.getValue();
                        break;
                    case "contextMissingStrategy":
                        contextMissingStrategy = entry.getValue();
                        break;
                    case "daemonAddress":
                        daemonAddress = entry.getValue();
                        break;
                    case "samplingStrategy":
                        samplingStrategy = entry.getValue();
                        break;
                    case "maxStackTraceLength":
                        maxStackTraceLength = Integer.parseInt(entry.getValue());
                        break;
                    case "streamingThreshold":
                        streamingThreshold = Integer.parseInt(entry.getValue());
                        break;
                    case "samplingRulesManifest":
                        samplingRulesManifest = entry.getValue();
                        break;
                    case "awsSDKVersion":
                        awsSDKVersion = Integer.parseInt(entry.getValue());
                        break;
                    case "awsServiceHandlerManifest":
                        awsServiceHandlerManifest = entry.getValue();
                        break;
                    case "tracingEnabled":
                        tracingEnabled = Boolean.parseBoolean(entry.getValue());
                        break;
                    default:
                        log.debug("Encountered unknown property " + entry.getKey() + " in X-Ray agent configuration. Ignoring.");
                        break;
                }
            }
        } catch (Exception e) {
            throw new InvalidAgentConfigException("Invalid type given in X-Ray Agent configuration file", e);
        }

        this.serviceName = serviceName;
        this.contextMissingStrategy = contextMissingStrategy;
        this.daemonAddress = daemonAddress;
        this.samplingStrategy = samplingStrategy;
        this.maxStackTraceLength = maxStackTraceLength;
        this.streamingThreshold = streamingThreshold;
        this.samplingRulesManifest = samplingRulesManifest;
        this.awsSDKVersion = awsSDKVersion;
        this.awsServiceHandlerManifest = awsServiceHandlerManifest;
        this.tracingEnabled = tracingEnabled;
    }

    public String getServiceName() {
        return serviceName;
    }


    public String getContextMissingStrategy() {
        return contextMissingStrategy;
    }

    public String getDaemonAddress() {
        return daemonAddress;
    }

    public String getSamplingStrategy() {
        return samplingStrategy;
    }

    public int getMaxStackTraceLength() {
        return maxStackTraceLength;
    }

    public int getStreamingThreshold() {
        return streamingThreshold;
    }

    public String getSamplingRulesManifest() {
        return samplingRulesManifest;
    }

    public int getAwsSDKVersion() {
        return awsSDKVersion;
    }

    public String getAwsServiceHandlerManifest() {
        return awsServiceHandlerManifest;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
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
