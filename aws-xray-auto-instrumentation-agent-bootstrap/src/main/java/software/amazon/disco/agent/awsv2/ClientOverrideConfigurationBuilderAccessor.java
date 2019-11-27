package software.amazon.disco.agent.awsv2;

import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.reflect.MethodHandleWrapper;

/**
 * Used to reflectively access the Client Override Configuration Builder primarily for adding execution interceptors.
 */
public class ClientOverrideConfigurationBuilderAccessor {
    private static final String EXECUTION_INTERCEPTOR_INTERFACE_NAME = "software.amazon.awssdk.core.interceptor.ExecutionInterceptor";
    private static final String CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME = "software.amazon.awssdk.core.client.config.ClientOverrideConfiguration$Builder";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static Class CLIENT_OVERRIDE_CONFIGURATION_BUILDER_CLASS = getClassOrNull(CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME, classLoader);
    private static Class EXECUTION_INTERCEPTOR_CLASS = getClassOrNull(EXECUTION_INTERCEPTOR_INTERFACE_NAME, classLoader);

    private static final MethodHandleWrapper addExecutionInterceptorMethod = new MethodHandleWrapper(CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME, classLoader, "addExecutionInterceptor", CLIENT_OVERRIDE_CONFIGURATION_BUILDER_CLASS, EXECUTION_INTERCEPTOR_CLASS);

    private final static Logger log = LogManager.getLogger(ClientOverrideConfigurationBuilderAccessor.class);

    private Object clientOverrideConfigurationBuilder;

    /**
     * Construct a new AWSClientBuilderAccessor with a concrete client builder object.
     * @param configBuilder The underlying client override configuration builder that we will be accessing
     */
    public ClientOverrideConfigurationBuilderAccessor(Object configBuilder) {
        this.clientOverrideConfigurationBuilder = configBuilder;
    }

    /**
     * Add the execution interceptor into the current client override configuration instance.
     * @param executionInterceptor The execution interceptor object to be added into the execution interceptor chain.
     * @return Return the updated client override configuration. Null otherwise.
     */
    public Object addExecutionInterceptor(Object executionInterceptor) {
        return addExecutionInterceptorMethod.invoke(this.clientOverrideConfigurationBuilder, executionInterceptor);
    }

    /**
     * Helper method to retrieve a class given the class path and classLoader. returns null if it fails.
     * @param classPath The class path string to retrieve.
     * @param classLoader The classloader to use
     * @return The class object if it can be found. Null otherwise.
     */
    private static Class getClassOrNull(String classPath, ClassLoader classLoader) {
        try {
            return Class.forName(classPath, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
