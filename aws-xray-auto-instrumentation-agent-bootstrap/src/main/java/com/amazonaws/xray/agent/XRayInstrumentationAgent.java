package com.amazonaws.xray.agent;

import software.amazon.disco.agent.awsv1.AWSClientInvokeInterceptor;
import software.amazon.disco.agent.awsv2.AWSClientBuilderInterceptor;
import software.amazon.disco.agent.DiscoAgentTemplate;
import software.amazon.disco.agent.concurrent.ConcurrencySupport;
import software.amazon.disco.agent.event.EventBus;
import software.amazon.disco.agent.interception.Installable;
import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.web.WebSupport;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

/**
 * The entry point for the DiSCo Authority agent.
 * For more on Java agents see: https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html
 *
 * This class defines the 'premain' method to be executed at the time of class loading, before any loaded
 * classes are actually used. At this time, the classes can be instrumented/transformed. The 'ByteBuddy' library
 * is used to rewrite/rebase the intercepted classes.
 *
 * For more on ByteBuddy see: http://bytebuddy.net
 */
public class XRayInstrumentationAgent {
    public static final String TRACE_HEADER_KEY = "X-Amzn-Trace-Id"; // TODO Merge this into a single file.
    public static final String SERVICE_NAME_ARG = "servicename"; // TODO Merge this into a single file.
    public static final String DEFAULT_SERVICE_NAME = "UnnamedXRayInstrumentedService";

    /**
     * log4j logger for log messages.
     */
    private static final Logger LOG = LogManager.getLogger(XRayInstrumentationAgent.class);

    /**
     * The agent is loaded by a -javaagent command line parameter, which will treat 'premain' as its
     * entrypoint, in the class referenced by the Premain-Class attribute in the manifest - which should be this one.
     *
     * @param agentArgs - any arguments passed as part of the -javaagent argument string
     * @param instrumentation - the Instrumentation object given to every Agent, to transform bytecode
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        LogManager.setMinimumLevel(Logger.Level.ERROR);

        DiscoAgentTemplate discoAgentTemplate = new DiscoAgentTemplate(agentArgs);
        Set<Installable> installables = new HashSet<>();

        installables.addAll(new ConcurrencySupport().get());

        // Required for intercepting HttpClients and Servlets
        installables.addAll(new WebSupport().get());

        // AWS SDK instrumentation
        installables.add(new AWSClientInvokeInterceptor()); // V1
        installables.add(new AWSClientBuilderInterceptor()); // V2

        // Note that we should not load any other classes before we install the interceptors. Many of the interceptors
        // only work if they haven't been loaded by the JVM yet.
        discoAgentTemplate.install(instrumentation, installables);

        // Attempt to get the runtime loader; if this fails, we abort by removing the XRayListener from the event bus.
        // Add XRay Listener to the event bus, parse out the agent name from the argument
        // In a lambda environment, the instrumentation is constructed through a proxy class, so that the
        // classloader would the customerClassloader; in a general case, the instrumentation would be instantiated
        // at the bootstrap classloader, so we by default use the system classloader during those scenarios.
        if (!initializeRuntimeAgent(agentArgs, instrumentation.getClass().getClassLoader())) {
            LOG.error("Unable to initialize the runtime agent. Running without instrumentation.");
            EventBus.removeAllListeners();
            return;
        }
    }

    private static boolean initializeRuntimeAgent(String agentArgs, ClassLoader classLoader) {
        // Reflectively acquire the agent runtime loader and initialize it to configure X-Ray.
        try {
            AgentRuntimeLoaderInterface agentRuntimeLoader = getAgentRuntimeLoader(classLoader);
            String serviceName = getServiceNameFromArgs(agentArgs, DEFAULT_SERVICE_NAME);
            agentRuntimeLoader.init(serviceName);
            return true;
        } catch (ClassNotFoundException e) {
            LOG.error("Unable to locate agent runtime loader. Please make sure it's imported as a dependency.");
        } catch (NoSuchMethodException e) {
            LOG.error("Unable to locate the agent runtime loader constructor.");
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            LOG.error("Unable to initialize the agent runtime loader.");
        }
        return false;
    }

    private static AgentRuntimeLoaderInterface getAgentRuntimeLoader(ClassLoader classLoader) throws ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        // Iterate through its parents until we find it. If we reach the bootstrap, then we know it doesn't exist.

        Class<?> agentRuntimeLoaderClass = null;
        ClassLoader currentClassloader = classLoader;

        // Use the application classloader if the classloader passed in is null (within the bootstrap)
        if (currentClassloader == null) {
            currentClassloader = ClassLoader.getSystemClassLoader();
        }
        agentRuntimeLoaderClass = Class.forName("com.amazonaws.xray.agent.AgentRuntimeLoader", true, currentClassloader);

        Constructor runtimeLoaderConstructor = agentRuntimeLoaderClass.getConstructor();
        return (AgentRuntimeLoaderInterface) runtimeLoaderConstructor.newInstance();
    }

    private static String getServiceNameFromArgs(String agentArgs, String defaultName) {
        // TODO Add a more proficient parser.
        if (agentArgs == null) {
            return defaultName;
        }

        String[] argArray = agentArgs.split("=");
        for(int i = 0; i < argArray.length-1; i++) {
            String argOrValue = argArray[i];
            if (argOrValue.contentEquals(SERVICE_NAME_ARG)) {
                return argArray[i+1];
            }
        }
        return defaultName;
    }


}
