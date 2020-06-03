package com.amazonaws.xray.agent;

import com.amazonaws.xray.agent.handlers.XRayHandlerInterface;
import software.amazon.disco.agent.event.EventBus;
import com.amazonaws.xray.agent.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.dispatcher.EventDispatcher;
import com.amazonaws.xray.agent.handlers.downstream.AWSHandler;
import com.amazonaws.xray.agent.handlers.downstream.AWSV2Handler;
import com.amazonaws.xray.agent.handlers.downstream.HttpClientHandler;
import com.amazonaws.xray.agent.handlers.upstream.ServletHandler;
import com.amazonaws.xray.agent.listeners.XRayListener;
import com.amazonaws.xray.agent.models.XRayTransactionState;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Bridge between classes residing in the bootstrap classloader or application classloader;
 * basically, modules that need to be invoked during application runtime--not the Agent's premain.
 */
public class AgentRuntimeLoader implements AgentRuntimeLoaderInterface {
    private static final String AWS_ORIGIN = "AWSv1";
    private static final String AWS_V2_ORIGIN = "AWSv2";
    private static final String APACHE_HTTP_CLIENT_ORIGIN = "ApacheHttpClient";
    private static final String HTTP_SERVLET_ORIGIN = "httpServlet";

    private static final String CONFIG_FILE_SYS_PROPERTY = "com.amazonaws.xray.configFile";
    private static final String CONFIG_FILE_DEFAULT_LOCATION = "/xray-agent.json";

    private static final Log log = LogFactory.getLog(AgentRuntimeLoader.class);

    /**
     * Initialize the classes belonging in the runtime.
     * @param serviceName - The service name that this agent represents passed from the command line.
     */
    public void init(@Nullable String serviceName) {
        if (serviceName != null) {
            log.warn("Setting the X-Ray service name via JVM arguments is deprecated. Use the agent's " +
                    "configuration file instead.");
            XRayTransactionState.setServiceName(serviceName);
        }

        // Configuration needs to be done before we initialize the listener because its handlers
        // rely on X-Ray configurations upon init.
        boolean enabled = configureXRay();

        if (!enabled) {
            return;
        }

        XRayListener listener = generateXRayListener();
        EventBus.addListener(listener);
    }

    // Visible for testing
    XRayListener generateXRayListener() {
        EventDispatcher upstreamEventDispatcher = new EventDispatcher();
        upstreamEventDispatcher.addHandler(HTTP_SERVLET_ORIGIN, new ServletHandler());

        EventDispatcher downstreamEventDispatcher = new EventDispatcher();
        downstreamEventDispatcher.addHandler(AWS_ORIGIN, getAwsHandlerByVersion(1));
        downstreamEventDispatcher.addHandler(AWS_V2_ORIGIN, getAwsHandlerByVersion(2));
        downstreamEventDispatcher.addHandler(APACHE_HTTP_CLIENT_ORIGIN, new HttpClientHandler());

        // We generate the dispatchers in this runtime loader so that when we later add
        // configurations, we can configure which handlers should be enabled.
        return new XRayListener(upstreamEventDispatcher, downstreamEventDispatcher);
    }

    /**
     * Helper method to configure the internal global recorder. It can throw exceptions to interrupt customer's
     * code at startup to notify them of invalid configuration rather than assuming defaults which might be unexpected.
     */
    private static boolean configureXRay() {
        XRaySDKConfiguration configuration = XRaySDKConfiguration.getInstance();

        URL configFile = getConfigFile();
        if (configFile != null) {
            configuration.init(configFile);
        } else {
            configuration.init();
        }

        return configuration.isEnabled();
    }

    /**
     * Helper method to retrieve the xray-agent config file's URL
     * @return the URL of config file or null if it wasn't found
     *
     * Visible for testing
     */
    @Nullable
    static URL getConfigFile() {
        String customLocation = System.getProperty(CONFIG_FILE_SYS_PROPERTY);
        if (customLocation != null && !customLocation.isEmpty()) {
            try {
                return new File(customLocation).toURI().toURL();
            } catch (MalformedURLException e) {
                log.error("X-Ray agent config file's custom location was malformed. " +
                        "Falling back to default configuration.");
                return null;
            }
        }

        // Will return null if default file is absent
        return AgentRuntimeLoader.class.getResource(CONFIG_FILE_DEFAULT_LOCATION);
    }

    /**
     * Helper method to construct AWS SDK handlers differently based on the version of the AWS SDK and
     * the presence of a user-provided AWS Service Manifest file
     *
     * @param version - version of AWS SDK for Java, 1 or 2
     * @return AWSHandler for appropriate Java SDK version with manifest file if present
     */
    private XRayHandlerInterface getAwsHandlerByVersion(int version) {
        URL manifest = XRaySDKConfiguration.getInstance().getAwsServiceHandlerManifest();
        int configVersion = XRaySDKConfiguration.getInstance().getAwsSdkVersion();
        if (manifest != null && version == configVersion) {
            if (version == 1) {
                return new AWSHandler(manifest);
            } else {
                return new AWSV2Handler(manifest);
            }
        }

        return version == 1 ? new AWSHandler() : new AWSV2Handler();
    }
}
