package com.amazonaws.xray.agent;

import software.amazon.disco.agent.inject.Injector;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A wrapper class for using the X-Ray agent in Lambda environments. Normally a Java program would use the -javaagent
 * JVM argument to know to begin in the agent's premain method. This is not currently possible in Lambda, and so we
 * use this class to do a workaround that starts the agent after JVM startup.
 *
 * Since classes loaded here will be loaded before the agent, and therefore possibly impact instrumentation, we must
 * minimize dependencies here. This means also not using a logger.
 *
 * This class should not be used outside Lambda functions.
 */
public class XRayAgentInstaller {
    private static final String LAMBDA_TASK_ROOT_KEY = "LAMBDA_TASK_ROOT";

    static class ProxiedInstrumentation implements InvocationHandler {
        // Proxied instrumentation to backhandedly pass the executing classloader to the agent
        private final Instrumentation original;

        ProxiedInstrumentation(Instrumentation original) {
            this.original = original;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                //https://amitstechblog.wordpress.com/2011/07/24/java-proxies-and-undeclaredthrowableexception/
                throw e.getCause();
            }
        }
    }

    /**
     * Helper method to add a new URL to an existing URLClassloader
     * @param classLoader a ClassLoader assumed to be a URLClassloader
     * @param url the URL to add
     */
    private static void addURL(Object classLoader, URL url) throws Exception {
        Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);
        addURL.invoke(classLoader, url);
    }

    /**
     * Install the X-Ray agent by specifying the bootstrap and runtime JAR paths.
     * Caveat: If classes that are to be intercepted by loading this agent are already loaded,
     * interception will not work.
     * @param bootstrapJarPath
     * @param runtimeJarPath
     * @param agentArgs
     */
    public static void install(String bootstrapJarPath, String runtimeJarPath, String agentArgs) {
        Instrumentation instrumentation = Injector.createInstrumentation();
        Instrumentation instrumentationProxy = (Instrumentation) Proxy.newProxyInstance(
                ProxiedInstrumentation.class.getClassLoader(),
                new Class[] {Instrumentation.class},
                new ProxiedInstrumentation(instrumentation)
        );

        File runtimeJar = new File(runtimeJarPath);
        try {
            addURL(Injector.class.getClassLoader(), runtimeJar.toURI().toURL());
            Injector.loadAgent(instrumentationProxy, bootstrapJarPath, agentArgs);
        } catch (Throwable e) {
            System.out.println("Failed to add runtime agent loader to classpath, aborting instrumentation");
        }
    }

    /**
     * Convenience method to install directly into lambda. Requires Linux version of Tools.jar, the Boostrap JAR
     * and the Runtime JAR.
     * @param agentArgs
     */
    public static void installInLambda(String agentArgs) {
        if (System.getenv(LAMBDA_TASK_ROOT_KEY) == null) {
            System.out.println("The X-Ray Agent Installer can only be used in AWS Lambda functions");
            return;
        }

        System.setProperty("software.amazon.disco.agent.jar.bytebuddy.agent.toolsjar", "/opt/java/lib/tools.jar");

        // TODO Dynamic Versioning based on maven properties.
        final String bootstrapJarPath = "/opt/java/lib/aws-xray-auto-instrumentation-agent-bootstrap-2.4.0-beta.1.jar";
        final String runtimeJarPath = "/opt/java/lib/aws-xray-auto-instrumentation-agent-runtime-2.4.0-beta.1.jar";

        install(bootstrapJarPath, runtimeJarPath, agentArgs);
    }
}
