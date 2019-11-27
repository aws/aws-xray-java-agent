package software.amazon.disco.agent.awsv2;

import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.function.Consumer;

/**
 * Used to reflectively access the client builder to make calls.
 */
public class AWSClientBuilderAccessor {
    private final static Logger log = LogManager.getLogger(AWSClientBuilderAccessor.class);

    private static final String SDK_CLIENT_BUILDER_NAME = "software.amazon.awssdk.core.client.builder.SdkClientBuilder";
    private static final String OVERRIDE_CONFIGURATION_METHOD = "overrideConfiguration";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static final Class SDK_CLIENT_BUILDER_CLASS = getClassOrNull(SDK_CLIENT_BUILDER_NAME, classLoader);

    private static final MethodHandleWrapper overrideConfigurationMethod = new MethodHandleWrapper(SDK_CLIENT_BUILDER_NAME, classLoader, OVERRIDE_CONFIGURATION_METHOD, SDK_CLIENT_BUILDER_CLASS, Consumer.class);

    private Object clientBuilder;

    /**
     * Construct a new AWSClientBuilderAccessor with a concrete client builder object.
     * @param clientBuilder - The client builder object to access
     */
    public AWSClientBuilderAccessor(Object clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    /**
     * Call overrideConfiguration on the
     * @param executionInterceptor - The execution interceptor instance we want to add to the client builder.
     * @return the builder object if success. null otherwise.
     */
    public Object withExecutionInterceptor(Object executionInterceptor) {
        Consumer<Object> consumerConfig = builder -> {
            software.amazon.disco.agent.awsv2.ClientOverrideConfigurationBuilderAccessor builderAccessor = new ClientOverrideConfigurationBuilderAccessor(builder);
            builderAccessor.addExecutionInterceptor(executionInterceptor);
        };

        return overrideConfigurationMethod.invoke(clientBuilder, consumerConfig);
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
