package com.amazonaws.xray.agent.runtime.listeners;

import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.dispatcher.EventDispatcher;
import com.amazonaws.xray.agent.runtime.handlers.XRayHandlerInterface;
import com.amazonaws.xray.agent.runtime.handlers.downstream.HttpClientHandler;
import com.amazonaws.xray.agent.runtime.handlers.downstream.SqlHandler;
import com.amazonaws.xray.agent.runtime.handlers.upstream.ServletHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.disco.agent.event.Listener;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Factory class that produces an X-Ray listener with upstream and downstream dispatchers to intercept relevant
 * events on the Disco event bus. It is important to keep this class minimal since we add different handlers
 * to the listener based on the environment it's consumed in.
 */
public class XRayListenerFactory implements ListenerFactory {
    private static final Log log = LogFactory.getLog(XRayListenerFactory.class);

    private static final String AWS_ORIGIN = "AWSv1";
    private static final String AWS_V2_ORIGIN = "AWSv2";
    private static final String APACHE_HTTP_CLIENT_ORIGIN = "ApacheHttpClient";
    private static final String HTTP_SERVLET_ORIGIN = "httpServlet";
    private static final String SQL_ORIGIN = "SQL";

    private static URL manifest;
    private static int configVersion;

    // Visible for testing
    static final String AWS_V1_CLASS_NAME = "com.amazonaws.xray.agent.runtime.handlers.downstream.AWSHandler";
    static final String AWS_V2_CLASS_NAME = "com.amazonaws.xray.agent.runtime.handlers.downstream.AWSV2Handler";

    /**
     * Creates a Disco Event Bus listener with upstream and downstream event dispatchers that have handlers
     * for each event type that the X-Ray Agent supports.
     *
     * @return An X-Ray Agent listener
     */
    @Override
    public Listener generateListener() {
        manifest = XRaySDKConfiguration.getInstance().getAwsServiceHandlerManifest();
        configVersion = XRaySDKConfiguration.getInstance().getAwsSdkVersion();

        EventDispatcher upstreamEventDispatcher = new EventDispatcher();
        upstreamEventDispatcher.addHandler(HTTP_SERVLET_ORIGIN, new ServletHandler());

        EventDispatcher downstreamEventDispatcher = new EventDispatcher();
        downstreamEventDispatcher.addHandler(APACHE_HTTP_CLIENT_ORIGIN, new HttpClientHandler());
        downstreamEventDispatcher.addHandler(SQL_ORIGIN, new SqlHandler());

        // The AWS Handlers must be acquired by reflection, since it is optional for the customer to add them
        // to the classpath. Customers may not want them to avoid an unnecessary dependency on the AWS SDK.
        XRayHandlerInterface awsHandler = tryGetAwsHandler(1, AWS_V1_CLASS_NAME);
        if (awsHandler != null) {
            log.info("AWS SDK V1 handler found on classpath, intercepting its requests");
            downstreamEventDispatcher.addHandler(AWS_ORIGIN, awsHandler);
        }

        XRayHandlerInterface awsV2Handler = tryGetAwsHandler(2, AWS_V2_CLASS_NAME);
        if (awsV2Handler != null) {
            log.info("AWS SDK V2 handler found on classpath, intercepting its requests");
            downstreamEventDispatcher.addHandler(AWS_V2_ORIGIN, awsV2Handler);
        }

        return new XRayListener(upstreamEventDispatcher, downstreamEventDispatcher);
    }

    /**
     * Helper method to attempt to acquire an AWS Handler by the provided class name reflectively.
     * Also constructs the handler with a custom manifest if configured by the customer.
     *
     * @param version - version of AWS SDK for Java, 1 or 2
     * @param className - the fully qualified class name of the handler
     * @return AWSHandler for appropriate AWS Java SDK version, or null if not present
     */
    private static XRayHandlerInterface tryGetAwsHandler(int version, String className) {
        try {
            Class<?> awsHandler = Class.forName(className, true, ClassLoader.getSystemClassLoader());
            if (configVersion == version && manifest != null) {
                return (XRayHandlerInterface) awsHandler.getConstructor(URL.class).newInstance(manifest);
            }
            return (XRayHandlerInterface) awsHandler.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            // Do nothing - customer not required to have either AWS interceptor
            log.debug("Failed to find AWS SDK Version " + version + " on classpath, ignoring requests", e);
            return null;
        }
    }
}
