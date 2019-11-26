package software.amazon.disco.agent.awsv2.executioninterceptor.accessors;

/**
 * Generic interface for Sdk Contexts. Resembles the following API interface
 * https://github.com/aws/aws-sdk-java-v2/blob/master/core/sdk-core/src/main/java/software/amazon/awssdk/core/interceptor/Context.java
 */
public interface ContextAccessor {

    /**
     * Retrieve the underlying Sdk Request object from the context
     * @return the Sdk Request Object
     */
    Object getRequest();

    /**
     * Retrieve the underlying Http Object from the context
     * @return the Context's Http Request object
     */
    Object getHttpRequest();

    /**
     * Retrieve the underlying Sdk Response object from the context.
     * @return the Context's Sdk Response object
     */
    Object getResponse();

    /**
     * Retrieve the underlying Http Response object from the context.
     * @return the Context's Http Response object
     */
    Object getHttpResponse();

    /**
     * Retrieve the underlying Sdk Exception from the context
     * @return The Context's Sdk Exception object.
     */
    Throwable getException();
}
