package com.amazonaws.xray.agent.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.agent.models.XRayTransactionContextResolver;
import com.amazonaws.xray.agent.models.XRayTransactionState;
import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.contexts.LambdaSegmentContextResolver;
import com.amazonaws.xray.emitters.UDPEmitter;
import com.amazonaws.xray.entities.StringValidator;
import com.amazonaws.xray.strategy.DefaultStreamingStrategy;
import com.amazonaws.xray.strategy.IgnoreErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.SegmentNamingStrategy;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.contexts.SegmentContextResolverChain;
import com.amazonaws.xray.strategy.DefaultThrowableSerializationStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton class that represents the X-Ray Agent's configuration programatically. This class is responsible for
 * parsing and validating the contents of the agent's configuration file. It also reads the environment variables
 * and system properties for relevant configurations. Priority for settings is as follows:
 *
 * 1. Environment variables
 * 2. System properties
 * 3. Configuration file values
 * 4. Default value
 *
 * For now, environment variable and system property overrides are handled in various locations of the SDK.
 * Note that configuring these values programatically is still possible, and will override the configuration file and
 * default value set here, but that is not recommended.
 */
public class XRaySDKConfiguration {
    static final String ENABLED_ENVIRONMENT_VARIABLE_KEY = "AWS_XRAY_TRACING_ENABLED";
    static final String ENABLED_SYSTEM_PROPERTY_KEY = "com.amazonaws.xray.tracingEnabled";

    private static final Log log = LogFactory.getLog(XRaySDKConfiguration.class);

    /* JSON factory used instead of mapper for performance */
    JsonFactory factory = new JsonFactory();

    /* Singleton instance */
    private static XRaySDKConfiguration instance;

    /* Configuration storage */
    private AgentConfiguration agentConfiguration;

    /* AWS Manifest whitelist, for runtime loader access */
    private URL awsServiceHandlerManifest = null;
    private int awsSDKVersion;

    /* Context missing enums */
    enum ContextMissingStrategy {
        LOG_ERROR,
        IGNORE_ERROR,
    }

    /* Sampling strategy enums */
    enum SamplingStrategy {
        LOCAL,
        CENTRAL,
        NONE,
        ALL,
    }

    public int getAwsSDKVersion() {
        return awsSDKVersion;
    }

    void setAwsSDKVersion(int version) {
        this.awsSDKVersion = version;
    }

    public URL getAwsServiceHandlerManifest() {
        return awsServiceHandlerManifest;
    }

    // For testing
    void setAwsServiceHandlerManifest(URL awsServiceHandlerManifest) {
        this.awsServiceHandlerManifest = awsServiceHandlerManifest;
    }

    // For testing
    AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    // For testing
    void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    public boolean isEnabled() {
        return agentConfiguration.isTracingEnabled();
    }

    private XRaySDKConfiguration() {
    }

    /**
     * @return XRaySDKConfiguration - The global instance of this agent recorder configuration.
     */
    public static XRaySDKConfiguration getInstance() {
        if (instance == null) {
            instance = new XRaySDKConfiguration();
        }
        return instance;
    }

    /**
     * Parses the given agent configuration file and stores its properties. If file is missing or incorrectly formatted,
     * the agent is configured with the default settings.
     * @param configFile - Location of configuration file
     */
    public void init(URL configFile) {
        try {
            log.info("Reading X-Ray Agent config file at: " + configFile.getPath());
            this.agentConfiguration = parseConfig(configFile);
        } catch (IOException e) {
            throw new InvalidAgentConfigException("Failed to read X-Ray Agent configuration file " + configFile.getPath(), e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Starting the X-Ray Agent with the following properties:\n" + agentConfiguration.toString());
        }
        init();
    }

    /**
     * Initialize the X-Ray SDK's Recorder used by the agent.
     */
    public void init() {
        init(AWSXRayRecorderBuilder.standard());
    }

    // For testing only
    void init(AWSXRayRecorderBuilder builder) {
        log.info("Initializing the X-Ray Agent Recorder");

        // Reset to defaults
        if (agentConfiguration == null) {
            agentConfiguration = new AgentConfiguration();
        }

        this.awsServiceHandlerManifest = null;
        this.awsSDKVersion = 0;

        // X-Ray Enabled
        String envString = System.getenv(ENABLED_ENVIRONMENT_VARIABLE_KEY);
        String sysString = System.getProperty(ENABLED_SYSTEM_PROPERTY_KEY);
        if (envString != null && envString.toLowerCase().equals("false") ||
            sysString != null && sysString.toLowerCase().equals("false") ||
            !agentConfiguration.isTracingEnabled())
        {
            log.info("Instrumentation via the X-Ray Agent has been disabled by user configuration.");
            return;
        }

        /*
        Service name is unique since it can still be set via JVM arg. So we check for that first, then proceed with
        the normal priority of properties. Once the JVM arg option is removed, we can remove the first condition
         */
        if (XRayTransactionState.getServiceName() == null) {
            String environmentNameOverrideValue = System.getenv(SegmentNamingStrategy.NAME_OVERRIDE_ENVIRONMENT_VARIABLE_KEY);
            String systemNameOverrideValue = System.getProperty(SegmentNamingStrategy.NAME_OVERRIDE_SYSTEM_PROPERTY_KEY);

            if (StringValidator.isNotNullOrBlank(environmentNameOverrideValue)) {
                XRayTransactionState.setServiceName(environmentNameOverrideValue);
            } else if (StringValidator.isNotNullOrBlank(systemNameOverrideValue)) {
                XRayTransactionState.setServiceName(systemNameOverrideValue);
            } else {
                XRayTransactionState.setServiceName(agentConfiguration.getServiceName());
            }
        }

        // Context missing
        ContextMissingStrategy contextMissing;
        try {
            contextMissing = ContextMissingStrategy.valueOf(agentConfiguration.getContextMissingStrategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAgentConfigException("Invalid context missing strategy given in X-Ray Agent " +
                    "configuration file: " + agentConfiguration.getContextMissingStrategy());
        }
        switch (contextMissing) {
            case LOG_ERROR:
                builder.withContextMissingStrategy(new LogErrorContextMissingStrategy());
                break;
            case IGNORE_ERROR:
                builder.withContextMissingStrategy(new IgnoreErrorContextMissingStrategy());
                break;
            default:
        }

        // Daemon address
        DaemonConfiguration daemonConfiguration = new DaemonConfiguration();
        try {
            // SDK handles all validation & environment overrides
            daemonConfiguration.setDaemonAddress(agentConfiguration.getDaemonAddress());
            builder.withEmitter(new UDPEmitter(daemonConfiguration));
        } catch (Exception e) {
            throw new InvalidAgentConfigException("Invalid daemon address provided in X-Ray Agent configuration " +
                    "file: " + agentConfiguration.getDaemonAddress(), e);
        }

        // Sampling Rules manifest
        URL samplingManifest = null;
        if (agentConfiguration.getSamplingRulesManifest() != null) {
            try {
                samplingManifest = new File(agentConfiguration.getSamplingRulesManifest()).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new InvalidAgentConfigException("Invalid sampling rules manifest location provided in X-Ray Agent " +
                        "configuration file: " + agentConfiguration.getSamplingRulesManifest(), e);
            }
        }

        // Sampling strategy
        SamplingStrategy samplingStrategy;
        try {
            samplingStrategy = SamplingStrategy.valueOf(agentConfiguration.getSamplingStrategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidAgentConfigException("Invalid sampling strategy given in X-Ray Agent " +
                    "configuration file: " + agentConfiguration.getSamplingStrategy());
        }
        switch (samplingStrategy) {
            case ALL:
                builder.withSamplingStrategy(new AllSamplingStrategy());
                break;
            case NONE:
                builder.withSamplingStrategy(new NoSamplingStrategy());
                break;
            case LOCAL:
                builder.withSamplingStrategy(samplingManifest != null ?
                        new LocalizedSamplingStrategy(samplingManifest) :
                        new LocalizedSamplingStrategy());
                break;
            case CENTRAL:
                builder.withSamplingStrategy(samplingManifest != null ?
                        new CentralizedSamplingStrategy(samplingManifest) :
                        new CentralizedSamplingStrategy());
                break;
            default:
        }

        // Max stack trace length
        if (agentConfiguration.getMaxStackTraceLength() >= 0) {
            builder.withThrowableSerializationStrategy(
                    new DefaultThrowableSerializationStrategy(agentConfiguration.getMaxStackTraceLength()));
        } else {
            throw new InvalidAgentConfigException("Invalid max stack trace length given in X-Ray Agent " +
                    "configuration file: " + agentConfiguration.getMaxStackTraceLength());
        }

        // Streaming threshold
        if (agentConfiguration.getStreamingThreshold() >= 0) {
            builder.withStreamingStrategy(new DefaultStreamingStrategy(agentConfiguration.getStreamingThreshold()));
        } else {
            throw new InvalidAgentConfigException("Invalid streaming threshold given in X-Ray Agent " +
                    "configuration file: " + agentConfiguration.getStreamingThreshold());
        }

        // AWS Service handler manifest
        if (agentConfiguration.getAwsServiceHandlerManifest() != null) {
            int version = agentConfiguration.getAwsSDKVersion();
            if (version != 1 && version != 2) {
                throw new InvalidAgentConfigException("Invalid AWS SDK version given in X-Ray Agent configuration file: " + version);
            } else {
                this.awsSDKVersion = version;
                try {
                    this.awsServiceHandlerManifest = new File(agentConfiguration.getAwsServiceHandlerManifest()).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new InvalidAgentConfigException("Invalid AWS Service Handler Manifest location given in X-Ray Agent " +
                            "configuration file: " + agentConfiguration.getAwsServiceHandlerManifest(), e);
                }
            }
        }

        // Non-configurable properties
        SegmentContextResolverChain segmentContextResolverChain = new SegmentContextResolverChain();
        segmentContextResolverChain.addResolver(new LambdaSegmentContextResolver());
        segmentContextResolverChain.addResolver(new XRayTransactionContextResolver());
        builder.withSegmentContextResolverChain(segmentContextResolverChain);

        log.debug("Successfully configured the X-Ray Agent's recorder.");

        AWSXRay.setGlobalRecorder(builder.build());
    }

    private AgentConfiguration parseConfig(URL configFile) throws IOException {
        Map<String, String> propertyMap = new HashMap<>();
        JsonParser parser = factory.createParser(configFile);
        parser.nextToken();

        if (!parser.isExpectedStartObjectToken()) {
            throw new InvalidAgentConfigException("X-Ray Agent configuration file is not valid JSON");
        }

        while (!parser.isClosed()) {
            String field = parser.nextFieldName();
            if (field == null) {
                continue;  // Ignore trailing null fields
            }

            parser.nextToken();
            propertyMap.put(field, parser.getValueAsString());
        }
        return new AgentConfiguration(propertyMap);
    }
}
