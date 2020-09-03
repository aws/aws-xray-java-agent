package com.amazonaws.xray.agent.runtime.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Class represents the agent configuration's JSON file. It is immutable and thus completely thread-safe.
 */
public final class AgentConfiguration {
    static final String DEFAULT_SERVICE_NAME = "XRayInstrumentedService";
    private static final Log log = LogFactory.getLog(AgentConfiguration.class);

    private final String serviceName;
    private final String contextMissingStrategy;
    private final String daemonAddress;
    private final String samplingStrategy;
    private final String traceIdInjectionPrefix;
    private final int maxStackTraceLength;
    private final int streamingThreshold;
    private final int awsSdkVersion;
    private final boolean pluginsEnabled;
    private final boolean tracingEnabled;
    private final boolean collectSqlQueries;
    private final boolean traceIdInjection;
    private final boolean contextPropagation;
    private final boolean traceIncomingRequests;

    @Nullable
    private final String samplingRulesManifest;

    @Nullable
    private final String awsServiceHandlerManifest;

    /**
     * Sets default values
     */
    public AgentConfiguration() {
        serviceName = DEFAULT_SERVICE_NAME;
        contextMissingStrategy = "LOG_ERROR";
        daemonAddress = "127.0.0.1:2000";
        samplingStrategy = "CENTRAL";
        traceIdInjectionPrefix = "";
        maxStackTraceLength = 50;
        streamingThreshold = 100;
        samplingRulesManifest = null; // Manifests are null by default since the default location file will be found later
        awsSdkVersion = 2;
        awsServiceHandlerManifest = null;
        pluginsEnabled = true;
        tracingEnabled = true;
        collectSqlQueries = false;
        traceIdInjection = true;
        contextPropagation = true;
        traceIncomingRequests = true;
    }

    /**
     * Constructs agent configuration from provided map. Does not do any validation since that is taken care of in
     * {@link XRaySDKConfiguration}. Can throw {@code InvalidAgentConfigException} if improper type is assigned to any
     * property. Assigns all non-configured properties to default values.
     * @param properties - Map of property names to their values in string representation, which will be cast to proper types.
     */
    public AgentConfiguration(@Nullable Map<String, String> properties) {
        String serviceName = DEFAULT_SERVICE_NAME,
                contextMissingStrategy = "LOG_ERROR",
                daemonAddress = "127.0.0.1:2000",
                samplingStrategy = "CENTRAL",
                traceIdInjectionPrefix = "",
                samplingRulesManifest = null,
                awsServiceHandlerManifest = null;
        int maxStackTraceLength = 50,
                streamingThreshold = 100,
                awsSdkVersion = 2;
        boolean pluginsEnabled = true,
                tracingEnabled = true,
                collectSqlQueries = false,
                traceIdInjection = true,
                contextPropagation = true,
                traceIncomingRequests = true;

        if (properties != null) {
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
                        case "traceIdInjectionPrefix":
                            traceIdInjectionPrefix = entry.getValue();
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
                        case "awsSdkVersion":
                            awsSdkVersion = Integer.parseInt(entry.getValue());
                            break;
                        case "awsServiceHandlerManifest":
                            awsServiceHandlerManifest = entry.getValue();
                            break;
                        case "pluginsEnabled":
                            pluginsEnabled = Boolean.parseBoolean(entry.getValue());
                            break;
                        case "tracingEnabled":
                            tracingEnabled = Boolean.parseBoolean(entry.getValue());
                            break;
                        case "collectSqlQueries":
                            collectSqlQueries = Boolean.parseBoolean(entry.getValue());
                            break;
                        case "traceIdInjection":
                            traceIdInjection = Boolean.parseBoolean(entry.getValue());
                            break;
                        case "contextPropagation":
                            contextPropagation = Boolean.parseBoolean(entry.getValue());
                            break;
                        case "traceIncomingRequests":
                            traceIncomingRequests = Boolean.parseBoolean(entry.getValue());
                            break;
                        default:
                            log.warn("Encountered unknown property " + entry.getKey() + " in X-Ray agent configuration. Ignoring.");
                            break;
                    }
                }
            } catch (Exception e) {
                throw new InvalidAgentConfigException("Invalid type given in X-Ray Agent configuration file", e);
            }
        }

        this.serviceName = serviceName;
        this.contextMissingStrategy = contextMissingStrategy;
        this.daemonAddress = daemonAddress;
        this.samplingStrategy = samplingStrategy;
        this.traceIdInjectionPrefix = traceIdInjectionPrefix;
        this.maxStackTraceLength = maxStackTraceLength;
        this.streamingThreshold = streamingThreshold;
        this.samplingRulesManifest = samplingRulesManifest;
        this.awsSdkVersion = awsSdkVersion;
        this.awsServiceHandlerManifest = awsServiceHandlerManifest;
        this.pluginsEnabled = pluginsEnabled;
        this.tracingEnabled = tracingEnabled;
        this.collectSqlQueries = collectSqlQueries;
        this.traceIdInjection = traceIdInjection;
        this.contextPropagation = contextPropagation;
        this.traceIncomingRequests = traceIncomingRequests;
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

    public String getTraceIdInjectionPrefix() { return traceIdInjectionPrefix; }

    public int getMaxStackTraceLength() {
        return maxStackTraceLength;
    }

    public int getStreamingThreshold() {
        return streamingThreshold;
    }

    @Nullable
    public String getSamplingRulesManifest() {
        return samplingRulesManifest;
    }

    public int getAwsSdkVersion() {
        return awsSdkVersion;
    }

    @Nullable
    public String getAwsServiceHandlerManifest() {
        return awsServiceHandlerManifest;
    }

    public boolean arePluginsEnabled() { return pluginsEnabled; }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public boolean shouldCollectSqlQueries() { return collectSqlQueries; }

    public boolean isTraceIdInjection() { return traceIdInjection; }

    public boolean isContextPropagation() { return contextPropagation; }

    public boolean isTraceIncomingRequests() { return traceIncomingRequests; }

    @Override
    public String toString() {
        return "AgentConfiguration{" +
                "serviceName='" + serviceName + '\'' +
                ", contextMissingStrategy='" + contextMissingStrategy + '\'' +
                ", daemonAddress='" + daemonAddress + '\'' +
                ", samplingStrategy='" + samplingStrategy + '\'' +
                ", traceIdInjectionPrefix='" + traceIdInjectionPrefix + '\'' +
                ", maxStackTraceLength=" + maxStackTraceLength +
                ", streamingThreshold=" + streamingThreshold +
                ", samplingRulesManifest='" + samplingRulesManifest + '\'' +
                ", awsSdkVersion='" + awsSdkVersion + '\'' +
                ", awsServiceHandlerManifest='" + awsServiceHandlerManifest + '\'' +
                ", pluginsEnabled=" + pluginsEnabled +
                ", tracingEnabled=" + tracingEnabled +
                ", collectSqlQueries=" + collectSqlQueries +
                ", traceIdInjection=" + traceIdInjection +
                ", contextPropagation=" + contextPropagation +
                ", traceIncomingRequests=" + traceIncomingRequests +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentConfiguration that = (AgentConfiguration) o;
        return maxStackTraceLength == that.maxStackTraceLength &&
                streamingThreshold == that.streamingThreshold &&
                awsSdkVersion == that.awsSdkVersion &&
                pluginsEnabled == that.pluginsEnabled &&
                tracingEnabled == that.tracingEnabled &&
                collectSqlQueries == that.collectSqlQueries &&
                traceIdInjection == that.traceIdInjection &&
                contextPropagation == that.contextPropagation &&
                traceIncomingRequests == that.traceIncomingRequests &&
                serviceName.equals(that.serviceName) &&
                contextMissingStrategy.equals(that.contextMissingStrategy) &&
                daemonAddress.equals(that.daemonAddress) &&
                samplingStrategy.equals(that.samplingStrategy) &&
                traceIdInjectionPrefix.equals(that.traceIdInjectionPrefix) &&
                Objects.equals(samplingRulesManifest, that.samplingRulesManifest) &&
                Objects.equals(awsServiceHandlerManifest, that.awsServiceHandlerManifest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, contextMissingStrategy, daemonAddress, samplingStrategy, traceIdInjection, traceIdInjectionPrefix, maxStackTraceLength, streamingThreshold, awsSdkVersion, pluginsEnabled, tracingEnabled, collectSqlQueries, contextPropagation, traceIncomingRequests, samplingRulesManifest, awsServiceHandlerManifest);
    }
}
