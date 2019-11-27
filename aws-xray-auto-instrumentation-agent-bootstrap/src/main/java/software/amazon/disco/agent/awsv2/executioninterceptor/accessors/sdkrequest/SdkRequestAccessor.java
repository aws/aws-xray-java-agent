package software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.Optional;

/**
 * Accessor for Sdk Request objects.
 */
public class SdkRequestAccessor {
    private static final String SDK_REQUEST_CLASSPATH = "software.amazon.awssdk.core.SdkRequest";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static MethodHandleWrapper getValueForFieldMethod = new MethodHandleWrapper(SDK_REQUEST_CLASSPATH, classLoader, "getValueForField", Optional.class, String.class, Class.class);

    private Object sdkRequest;

    /**
     * Construct the SdkRequestAccessor from the SdkRequest object
     * @param request The SdkRequest that will be reflectively accessed.
     */
    public SdkRequestAccessor(Object request) {
        sdkRequest = request;
    }

    /**
     * Retrieve the field value from the underlying sdk request object.
     * @param field The field name to search for
     * @param clazz The class to cast the object into
     * @return The value for the sdk request object's field.
     */
    public Object getValueForField(String field, Class clazz) {
        return clazz.cast(getValueForFieldMethod.invoke(this.sdkRequest, field, clazz));
    }
}
