package com.amazonaws.xray.agent.config;

import com.amazonaws.xray.AWSXRay;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.contexts.SegmentContextResolverChain;
import com.amazonaws.xray.emitters.Emitter;
import com.amazonaws.xray.strategy.ContextMissingStrategy;
import com.amazonaws.xray.strategy.DefaultThrowableSerializationStrategy;
import com.amazonaws.xray.strategy.StreamingStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.SamplingStrategy;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Basic configuration for the global recorder used by all listener to generate segments.
 */
public class XRaySDKConfiguration {
    private static XRaySDKConfiguration instance = new XRaySDKConfiguration();
    private static final Logger log = LogManager.getLogger(XRaySDKConfiguration.class);
    private static final int DEFAULT_MAX_STACK_TRACE_LENGTH = 10;

    /**
     * X-Ray-specific configuration
     **/
    private int maxStackTraceLength = DEFAULT_MAX_STACK_TRACE_LENGTH;
    private SamplingStrategy samplingStrategy = null;
    private String samplingRuleFilePath = null;
    private ContextMissingStrategy contextMissingStrategy = null;
    private SegmentContextResolverChain segmentContextResolverChain = null;
    private StreamingStrategy streamingStrategy = null;
    private Emitter emitter = null;

    /**
     * @return XRaySDKConfiguration - The global instance of this agent recorder configuration.
     */
    public static XRaySDKConfiguration getInstance() {
        return instance;
    }

    /**
     * Initialize the Agent Recorder with the set attributes.
     * @throws MalformedURLException Thrown if no sampling strategy was passed in and the sampling rule file path is invalid.
     */
    public void init() throws MalformedURLException {
        log.info("Initializing the X-Ray Agent Recorder");
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        builder.withThrowableSerializationStrategy(new DefaultThrowableSerializationStrategy(maxStackTraceLength));
        if (segmentContextResolverChain != null) {
            builder.withSegmentContextResolverChain(segmentContextResolverChain);
        }
        if (contextMissingStrategy != null) {
            builder.withContextMissingStrategy(contextMissingStrategy);
        }
        if (streamingStrategy != null) {
            builder.withStreamingStrategy(streamingStrategy);
        }
        if (emitter != null) {
            builder.withEmitter(emitter);
        }
        if (samplingStrategy != null) {
            builder.withSamplingStrategy(samplingStrategy);
        } else if (samplingRuleFilePath != null) {
            URL ruleFile = new URL("file:" + samplingRuleFilePath);
            log.info("Using the Centralized Sampling Strategy with the sampling rule file: " + samplingRuleFilePath);
            builder.withSamplingStrategy(new CentralizedSamplingStrategy(ruleFile));
        }
        AWSXRay.setGlobalRecorder(builder.build());
    }

    /*
     * Setter functions for configuring this global recorder.
     */

    public void setMaxStackTraceLength(int maxStackTraceLength) {
        this.maxStackTraceLength = maxStackTraceLength;
    }

    public void setSamplingStrategy(SamplingStrategy samplingStrategy) {
        this.samplingStrategy = samplingStrategy;
    }

    public void setEmitter(Emitter emitter) {
        this.emitter = emitter;
    }

    public void setContextMissingStrategy(ContextMissingStrategy contextMissingStrategy) {
        this.contextMissingStrategy = contextMissingStrategy;
    }

    public void setSamplingRuleFilePath(String samplingRuleFilePath) {
        if (samplingRuleFilePath.isEmpty()) {
            throw new IllegalArgumentException("SamplingRuleFilePath cannot be empty.");
        }
        this.samplingRuleFilePath = samplingRuleFilePath;
    }

    public void setSegmentContextResolverChain(SegmentContextResolverChain segmentContextResolverChain) {
        this.segmentContextResolverChain = segmentContextResolverChain;
    }
}
