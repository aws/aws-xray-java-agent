package com.amazonaws.xray.agent.runtime;

import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.listeners.ListenerFactory;
import com.amazonaws.xray.agent.runtime.listeners.XRayListenerFactory;
import com.amazonaws.xray.agent.runtime.models.XRayTransactionState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.event.Listener;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Bridge between classes residing in the bootstrap classloader or application classloader;
 * basically, modules that need to be invoked during application runtime--not the Agent's premain.
 */
public class AgentRuntimeLoader {
    private static final String CONFIG_FILE_SYS_PROPERTY = "com.amazonaws.xray.configFile";
    private static final String CONFIG_FILE_DEFAULT_LOCATION = "/xray-agent.json";
    private static final String CONFIG_FILE_SPRING_BOOT_LOCATION = "/BOOT-INF/classes/xray-agent.json";

    private static final Log log = LogFactory.getLog(AgentRuntimeLoader.class);
    private static ListenerFactory listenerFactory = new XRayListenerFactory();

    // exposed for testing
    static void setListenerFactory(ListenerFactory factory) {
        listenerFactory = factory;
    }

    /**
     * Wrapper for main init method used by DiSCo Plugin model.
     */
    public static void init() {
        init(null);
    }

    /**
     * Initialize the classes belonging in the runtime.
     * @param serviceName - The service name that this agent represents passed from the command line.
     */
    public static void init(@Nullable String serviceName) {
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

        Listener listener = listenerFactory.generateListener();
        EventBus.addListener(listener);
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
     * Helper method to retrieve the xray-agent config file's URL. First checks the provided path on the file system,
     * then falls back to checking the system classpath, before giving up and returning null.
     *
     * @return the URL of config file or null if it wasn't found
     *
     * Visible for testing
     */
    @Nullable
    static URL getConfigFile() {
        String customLocation = System.getProperty(CONFIG_FILE_SYS_PROPERTY);
        if (customLocation != null && !customLocation.isEmpty()) {
            try {
                File file = new File(customLocation);
                if (file.exists()) {
                    return file.toURI().toURL();
                } else {
                    return AgentRuntimeLoader.class.getResource(customLocation);
                }
            } catch (MalformedURLException e) {
                log.error("X-Ray agent config file's custom location was malformed. " +
                        "Falling back to default configuration.");
                return null;
            }
        }

        // Search root of classpath first, then check root of Spring Boot classpath
        URL defaultLocation = AgentRuntimeLoader.class.getResource(CONFIG_FILE_DEFAULT_LOCATION);
        if (defaultLocation == null) {
            defaultLocation = AgentRuntimeLoader.class.getResource(CONFIG_FILE_SPRING_BOOT_LOCATION);
        }
        return defaultLocation;
    }
}
