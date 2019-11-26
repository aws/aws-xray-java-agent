package software.amazon.disco.agent.awsv2;

import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Used to reflectively access the Client Override Configuration Builder primarily for adding execution interceptors.
 */
public class ClientOverrideConfigurationBuilderAccessor {
    private static final String CLIENT_OVERRIDE_CONFIGURATION_NAME = "software.amazon.awssdk.core.client.config.ClientOverrideConfiguration";
    private static final String CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME = "software.amazon.awssdk.core.client.config.ClientOverrideConfiguration$Builder";
    private static final String ADD_EXECUTION_INTERCEPTOR_METHOD_NAME = "addExecutionInterceptor";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static final MethodHandleWrapper builderMethod = new MethodHandleWrapper(CLIENT_OVERRIDE_CONFIGURATION_NAME, classLoader, "builder", Object.class);
    private static final MethodHandleWrapper configurationBuildMethod = new MethodHandleWrapper(CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME, classLoader, "build", Object.class);
    private static final MethodHandleWrapper addExecutionInterceptorMethod = new MethodHandleWrapper(CLIENT_OVERRIDE_CONFIGURATION_BUILDER_NAME, classLoader, "addExecutionInterceptor", Object.class);
    private Object clientOverrideConfigurationBuilder;

    private final static Logger log = LogManager.getLogger(ClientOverrideConfigurationBuilderAccessor.class);

    /**
     * Obtain the override configuration method reflectively from the Client Builder instance.
     * @return The override configuration method. Null if it can't find it.
     */
    private Method getAddExecutionInterceptorMethod() {
        try {
            Class executionInterceptorClass = Class.forName("software.amazon.awssdk.core.interceptor.ExecutionInterceptor", false, classLoader);
            return Class.forName("software.amazon.awssdk.core.client.config.ClientOverrideConfiguration$Builder", false, classLoader).getMethod(ADD_EXECUTION_INTERCEPTOR_METHOD_NAME, executionInterceptorClass);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            log.error("Unable to find the " + ADD_EXECUTION_INTERCEPTOR_METHOD_NAME + " method from the execution interceptor class");
            return null;
        }
    }

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
     * @return Return
     */
    public Object addExecutionInterceptor(Object executionInterceptor) {
//        return addExecutionInterceptorMethod.invoke(this.clientOverrideConfigurationBuilder, executionInterceptor);
        Method addExecutionInterceptorMethodRefl = getAddExecutionInterceptorMethod();
        if (addExecutionInterceptorMethodRefl != null) {
            try {
                return addExecutionInterceptorMethodRefl.invoke(this.clientOverrideConfigurationBuilder, executionInterceptor);
            } catch (IllegalAccessException | InvocationTargetException e) {
                ;
                log.error("Had trouble executing the method.");
            }
        }
        return null;
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
