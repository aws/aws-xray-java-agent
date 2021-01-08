package com.amazonaws.xray.agent.runtime.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionContextResolver;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import com.amazonaws.xray.config.DaemonConfiguration;
import com.amazonaws.xray.contexts.LambdaSegmentContextResolver;
import com.amazonaws.xray.contexts.SegmentContextResolverChain;
import com.amazonaws.xray.contexts.ThreadLocalSegmentContextResolver;
import com.amazonaws.xray.emitters.UDPEmitter;
import com.amazonaws.xray.entities.StringValidator;
import com.amazonaws.xray.listeners.SegmentListener;
import com.amazonaws.xray.strategy.DefaultStreamingStrategy;
import com.amazonaws.xray.strategy.DefaultThrowableSerializationStrategy;
import com.amazonaws.xray.strategy.IgnoreErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.SegmentNamingStrategy;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Singleton class that represents the X-Ray Agent's configuration programmatically. This class is responsible for
 * parsing and validating the contents of the agent's configuration file. It also reads the environment variables
 * and system properties for relevant configurations. Priority for settings is as follows:
 *
 * 1. Environment variables
 * 2. System properties
 * 3. Configuration file values
 * 4. Default value
 *
 * For now, environment variable and system property overrides are handled in various locations of the SDK.
 * Note that configuring these values programmatically is still possible, and will override the configuration file and
 * default value set here, but that is not recommended.
 */
public class XRaySDKConfiguration {
    // Visible for testing
    static final String LAMBDA_TASK_ROOT_KEY = "LAMBDA_TASK_ROOT";
    static final String ENABLED_ENVIRONMENT_VARIABLE_KEY = "AWS_XRAY_TRACING_ENABLED";
    static final String ENABLED_SYSTEM_PROPERTY_KEY = "com.amazonaws.xray.tracingEnabled";

    private static final String[] TRACE_ID_INJECTION_CLASSES = {
            "com.amazonaws.xray.log4j.Log4JSegmentListener",
            "com.amazonaws.xray.slf4j.SLF4JSegmentListener"
    };

    private static final Log log = LogFactory.getLog(XRaySDKConfiguration.class);

    /* JSON factory used instead of mapper for performance */
    private static final JsonFactory factory = new JsonFactory();

    /* Singleton instance */
    private static final XRaySDKConfiguration instance = new XRaySDKConfiguration();

    /* Configuration storage */
    private AgentConfiguration agentConfiguration;

    /* Indicator for whether trace ID injection has been configured */
    private boolean traceIdInjectionConfigured;

    /* AWS Manifest whitelist, for runtime loader access */
    @Nullable
    private URL awsServiceHandlerManifest = null;
    private int awsSdkVersion;

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

    public int getAwsSdkVersion() {
        return awsSdkVersion;
    }

    @Nullable
    public URL getAwsServiceHandlerManifest() {
        return awsServiceHandlerManifest;
    }

    // Visible for testing
    AgentConfiguration getAgentConfiguration() {
        return agentConfiguration;
    }

    // Visible for testing
    void setAgentConfiguration(AgentConfiguration agentConfiguration) {
        this.agentConfiguration = agentConfiguration;
    }

    public boolean isEnabled() {
        return agentConfiguration.isTracingEnabled();
    }

    public boolean shouldCollectSqlQueries() { return agentConfiguration.shouldCollectSqlQueries(); }

    public boolean isTraceIncomingRequests() {
        return agentConfiguration.isTraceIncomingRequests();
    }

    // Visible for testing
    XRaySDKConfiguration() {
    }

    /**
     * @return XRaySDKConfiguration - The global instance of this agent recorder configuration.
     */
    public static XRaySDKConfiguration getInstance() {
        return instance;
    }

    /**
     * Parses the given agent configuration file and stores its properties. If file is missing or incorrectly formatted,
     * throw an {@code InvalidAgentConfigException}. If {@code null} is passed in, configures the agent with default
     * properties.
     * @param configFile - Location of configuration file
     */
    public void init(@Nullable URL configFile) {
        if (configFile == null) {
            this.agentConfiguration = new AgentConfiguration();
        } else {
            try {
                log.info("Reading X-Ray Agent config file at: " + configFile.getPath());
                this.agentConfiguration = parseConfig(configFile);
            } catch (IOException e) {
                throw new InvalidAgentConfigException("Failed to read X-Ray Agent configuration file " + configFile.getPath(), e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Starting the X-Ray Agent with the following properties:\n" + agentConfiguration.toString());
        }
        init(AWSXRayRecorderBuilder.standard());
    }

    /**
     * Initialize the X-Ray SDK's Recorder used by the agent with default settings.
     * Clears out any previously stored configuration.
     */
    public void init() {
        this.agentConfiguration = null;
        init(AWSXRayRecorderBuilder.standard());
    }

    // Visible for testing
    void init(AWSXRayRecorderBuilder builder) {
        log.info("Initializing the X-Ray Agent Recorder");

        // Reset to defaults
        if (agentConfiguration == null) {
            agentConfiguration = new AgentConfiguration();
        }

        this.awsServiceHandlerManifest = null;
        this.awsSdkVersion = 0;

        // X-Ray Enabled
        if ("false".equalsIgnoreCase(System.getenv(ENABLED_ENVIRONMENT_VARIABLE_KEY)) ||
                "false".equalsIgnoreCase(System.getProperty(ENABLED_SYSTEM_PROPERTY_KEY)) ||
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

        // Plugins
        // Do not add plugins in Lambda environment to improve performance & avoid irrelevant metadata
        if (StringValidator.isNullOrBlank(System.getenv(LAMBDA_TASK_ROOT_KEY)) &&
                agentConfiguration.arePluginsEnabled())
        {
            builder.withDefaultPlugins();
        }

        // Context missing
        final ContextMissingStrategy contextMissing;
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
        final SamplingStrategy samplingStrategy;
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

        // Trace ID Injection
        if (agentConfiguration.isTraceIdInjection()) {
            // TODO: Include the trace ID injection libraries in the agent JAR and use this reflective approach to
            // only enable them if their corresponding logging libs are in the context classloader
            List<SegmentListener> listeners = getTraceIdInjectorsReflectively(ClassLoader.getSystemClassLoader());

            if (!listeners.isEmpty()) {
                traceIdInjectionConfigured = true;
                for (SegmentListener listener : listeners) {
                    builder.withSegmentListener(listener);
                }
            }
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
            int version = agentConfiguration.getAwsSdkVersion();
            if (version != 1 && version != 2) {
                throw new InvalidAgentConfigException("Invalid AWS SDK version given in X-Ray Agent configuration file: " + version);
            } else {
                this.awsSdkVersion = version;
                try {
                    this.awsServiceHandlerManifest = new File(agentConfiguration.getAwsServiceHandlerManifest()).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new InvalidAgentConfigException("Invalid AWS Service Handler Manifest location given in X-Ray Agent " +
                            "configuration file: " + agentConfiguration.getAwsServiceHandlerManifest(), e);
                }
            }
        }

        SegmentContextResolverChain segmentContextResolverChain = new SegmentContextResolverChain();
        segmentContextResolverChain.addResolver(new LambdaSegmentContextResolver());

        // Context resolution - use TransactionContext by default, or ThreadLocal if contextPropagation is disabled
        if (agentConfiguration.isContextPropagation()) {
            segmentContextResolverChain.addResolver(new XRayTransactionContextResolver());
        } else {
            segmentContextResolverChain.addResolver(new ThreadLocalSegmentContextResolver());
        }

        builder.withSegmentContextResolverChain(segmentContextResolverChain);

        log.debug("Successfully configured the X-Ray Agent's recorder.");

        AWSXRay.setGlobalRecorder(builder.build());
    }

    /**
     * Attempts to enable trace ID injection via reflection. This can be called during runtime as opposed to agent
     * startup in case required trace ID injection classes aren't available on the classpath during premain.
     *
     * @param recorder - the X-Ray Recorder to configure
     */
    public void lazyLoadTraceIdInjection(AWSXRayRecorder recorder) {
        // Fail fast if injection disabled or we've already tried to lazy load
        if (!agentConfiguration.isTraceIdInjection() || traceIdInjectionConfigured) {
            return;
        }
        traceIdInjectionConfigured = true;

        // We must use the context class loader because the whole reason we're lazy loading the injection libraries
        // is that they're only visible to the classloader used by the customer app
        recorder.addAllSegmentListeners(getTraceIdInjectorsReflectively(Thread.currentThread().getContextClassLoader()));
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
                return new AgentConfiguration(propertyMap);  // Hitting a null field implies end of JSON object
            }

            parser.nextToken();
            propertyMap.put(field, parser.getValueAsString());
        }
        return new AgentConfiguration(propertyMap);
    }

    private List<SegmentListener> getTraceIdInjectorsReflectively(ClassLoader classLoader) {
        final List<SegmentListener> listeners = new ArrayList<>();
        final String prefix = agentConfiguration.getTraceIdInjectionPrefix();
        log.debug("Prefix is: " + prefix);
        for (String className : TRACE_ID_INJECTION_CLASSES) {
            try {
                Class<?> listenerClass = Class.forName(className, true, classLoader);
                SegmentListener listener = (SegmentListener) listenerClass.getConstructor(String.class).newInstance(prefix);
                listeners.add(listener);
                log.debug("Enabled AWS X-Ray trace ID injection into logs using " + className);
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
                log.debug("Could not find trace ID injection class " + className + " with class loader " + classLoader.getClass().getSimpleName());
            }
        }

        return listeners;
    }
}
