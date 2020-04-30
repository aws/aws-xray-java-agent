package com.amazonaws.xray.agent;

import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import com.amazonaws.xray.agent.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.dispatcher.EventDispatcher;
import com.amazonaws.xray.agent.handlers.downstream.AWSHandler;
import com.amazonaws.xray.agent.handlers.downstream.AWSV2Handler;
import com.amazonaws.xray.agent.handlers.downstream.HttpClientHandler;
import com.amazonaws.xray.agent.handlers.upstream.ServletHandler;
import com.amazonaws.xray.agent.listeners.XRayListener;
import com.amazonaws.xray.agent.models.XRayTransactionContextResolver;
import com.amazonaws.xray.agent.models.XRayTransactionState;
import com.amazonaws.xray.contexts.LambdaSegmentContextResolver;
import com.amazonaws.xray.contexts.SegmentContextResolverChain;
import com.amazonaws.xray.strategy.DefaultContextMissingStrategy;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;

/**
 * Bridge between classes residing in the bootstrap classloader or application classloader;
 * basically, modules that need to be invoked during application runtime--not the Agent's premain.
 */
public class AgentRuntimeLoader implements AgentRuntimeLoaderInterface {
    private static final String AWS_ORIGIN = "AWSv1";
    private static final String AWS_V2_ORIGIN = "AWSv2";
    private static final String APACHE_HTTP_CLIENT_ORIGIN = "ApacheHttpClient";
    private static final String HTTP_SERVLET_ORIGIN = "httpServlet";
    private static final Logger LOG = LogManager.getLogger(AgentRuntimeLoader.class);

    /**
     * Initialize the classes belonging in the runtime.
     * @param serviceName - The service name that this agent represents.
     */
    public void init(String serviceName) {
        XRayTransactionState.setServiceName(serviceName);

        // Configuration needs to be done before we initialize the listener because its handlers
        // rely on X-Ray configurations upon init.
        configureXRay();

        XRayListener xRayListener = xrayListenerGenerator();

        EventBus.addListener(xRayListener);
    }

    protected XRayListener xrayListenerGenerator() {
        EventDispatcher upstreamEventDispatcher = new EventDispatcher();
        upstreamEventDispatcher.addHandler(HTTP_SERVLET_ORIGIN, new ServletHandler());

        EventDispatcher downstreamEventDispatcher = new EventDispatcher();
        downstreamEventDispatcher.addHandler(AWS_ORIGIN, new AWSHandler());
        downstreamEventDispatcher.addHandler(AWS_V2_ORIGIN, new AWSV2Handler());
        downstreamEventDispatcher.addHandler(APACHE_HTTP_CLIENT_ORIGIN, new HttpClientHandler());

        // We generate the dispatchers in this runtime loader so that when we later add
        // configurations, we can configure which handlers should be enabled.
        return new XRayListener(upstreamEventDispatcher, downstreamEventDispatcher);
    }

    /**
     * Helper method to configure the internal global recorder. It takes care to make sure
     * no exceptions are thrown so that customer code is not impacted.
     */
    private static void configureXRay() {
        LOG.info("Using the Centralized Sampling Strategy");
        XRaySDKConfiguration configuration = XRaySDKConfiguration.getInstance();

        // Configure X-Ray
        SegmentContextResolverChain segmentContextResolverChain = new SegmentContextResolverChain();
        segmentContextResolverChain.addResolver(new LambdaSegmentContextResolver());
        segmentContextResolverChain.addResolver(new XRayTransactionContextResolver());
        configuration.setSamplingStrategy(new CentralizedSamplingStrategy());
        configuration.setSegmentContextResolverChain(segmentContextResolverChain);
        configuration.setContextMissingStrategy(new DefaultContextMissingStrategy());

        try {
            configuration.init();
        } catch(Throwable t) {
            // If there's an exception, we just use some default
            LOG.error("Unable to configure global recorder: " + t.getMessage());
        }
    }
}
