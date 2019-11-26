package software.amazon.disco.agent.awsv2.executioninterceptor.accessors;

import software.amazon.disco.agent.logging.LogManager;
import software.amazon.disco.agent.logging.Logger;
import software.amazon.disco.agent.reflect.MethodHandleWrapper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Accessor for execution attributes usually passed in from the execution interceptor.
 */
public class ExecutionAttributesAccessor {
    private final static Logger log = LogManager.getLogger(ExecutionAttributesAccessor.class);

    private static final String AWS_EXECUTION_ATTRIBUTES_CLASSPATH = "software.amazon.awssdk.awscore.AwsExecutionAttribute";
    private static final String EXECUTION_INTERCEPTOR_CLASSPATH = "software.amazon.awssdk.core.interceptor.ExecutionAttributes";
    private static final String EXECUTION_ATTRIBUTE_PARAM_CLASSPATH = "software.amazon.awssdk.core.interceptor.ExecutionAttribute";
    private static final String SDK_EXECUTION_ATTRIBUTES_CLASSPATH = "software.amazon.awssdk.core.interceptor.SdkExecutionAttribute";
    private static final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

    private static final Class executionAttributeClass = getClassOrNull(EXECUTION_ATTRIBUTE_PARAM_CLASSPATH, classLoader);

    private static MethodHandleWrapper getAttributeMethod = new MethodHandleWrapper(EXECUTION_INTERCEPTOR_CLASSPATH, classLoader, "getAttribute", Object.class, executionAttributeClass);
    private static Map<String, Object> executionAttributeNameToAttribute = new HashMap<>();

    /**
     * These attribute names must correspond to the field name:
     * https://github.com/aws/aws-sdk-java-v2/blob/master/core/sdk-core/src/main/java/software/amazon/awssdk/core/interceptor/SdkExecutionAttribute.java
     */
    public static final String SERVICE_NAME_ATTRIBUTE_NAME = "SERVICE_NAME";
    public static final String OPERATION_NAME_ATTRIBUTE_NAME = "OPERATION_NAME";
    public static final String AWS_REGION_ATTRIBUTE_NAME = "AWS_REGION";

    private Object executionAttributesOrig;

    public ExecutionAttributesAccessor(Object executionAttributes) {
        this.executionAttributesOrig = executionAttributes;
        initExecutionAttributeMap();
    }

    /**
     * Retrieve the an attribute.
     * @param attribute The attribute name to retrieve
     * @return The attribute value
     */
    public Object getAttribute(String attribute) {
        Object attributeValue = executionAttributeNameToAttribute.get(attribute);
        if (attributeValue != null) {
            return getAttributeMethod.invoke(this.executionAttributesOrig, attributeValue);
        }
        return null;
    }

    /**
     * Initialize the execution attribute map by finding all the fields in the static SDK and AWS execution fields.
     * attributes object and creating a key-value pairing of the field name to the execution attribute object.
     *
     * This map is used to actually get the execution attributes from the object passed in by the execution interceptor.
     */
    private void initExecutionAttributeMap() {
        if (executionAttributeNameToAttribute.size() == 0) {

            // Iterate through the SDK Execution attributes first; these could be found here:
            // https://github.com/aws/aws-sdk-java-v2/blob/master/core/sdk-core/src/main/java/software/amazon/awssdk/core/interceptor/SdkExecutionAttribute.java
            for (Field f : getDeclaredFieldsFromClass(SDK_EXECUTION_ATTRIBUTES_CLASSPATH)) {
                try {
                    executionAttributeNameToAttribute.put(f.getName(), f.get(null));
                } catch (IllegalAccessException e) {
                    log.error("DiSCo(AWSv2) Unable to retrieve execution attribute: " + f.getName());
                }
            }

            // Iterate through the Aws Execution attributes:
            // https://github.com/aws/aws-sdk-java-v2/blob/master/core/aws-core/src/main/java/software/amazon/awssdk/awscore/AwsExecutionAttribute.java
            for (Field f : getDeclaredFieldsFromClass(AWS_EXECUTION_ATTRIBUTES_CLASSPATH)) {
                try {
                    executionAttributeNameToAttribute.put(f.getName(), f.get(null));
                } catch (IllegalAccessException e) {
                    log.error("DiSCo(AWSv2) Unable to retrieve execution attribute: " + f.getName());
                }
            }
        }
    }

    /**
     * Retrieves the fields from a given classpath.
     * @param classPath String classpath whose fields we are trying to obtain.
     * @return an array of the classpath's fields. An empty array if it fails to find the class.
     */
    private Field[] getDeclaredFieldsFromClass (String classPath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Class theClass = Class.forName(classPath, false, classLoader);
            return theClass.getDeclaredFields();
        } catch (ClassNotFoundException e) {
            log.error("DiSCo(AWSv2) Unable to capture execution attributes from classpath: " + classPath);
            return new Field[0];
        }
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
