package software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkresponse;

import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.util.Optional;

public class SdkResponseAccessor {
    private static final String SDK_RESPONSE_CLASSPATH = "software.amazon.awssdk.core.SdkResponse";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static MethodHandleWrapper getValueForFieldMethod = new MethodHandleWrapper(SDK_RESPONSE_CLASSPATH, classLoader, "getValueForField", Optional.class, String.class, Class.class);

    private Object sdkResponse;

    public SdkResponseAccessor(Object response) {
        sdkResponse = response;
    }

    /**
     * Retrieve the field value from the underlying sdk response object.
     * @param field The field name to search for
     * @param clazz The class to cast the object into
     * @return The value for the sdk request object's field.
     */
    public Optional getValueForField(String field, Class clazz) {
        return (Optional) getValueForFieldMethod.invoke(this.sdkResponse, field, clazz);
    }
}
