package com.amazonaws.xray.agent.utils;

import com.amazonaws.xray.proxies.apache.http.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Helper class to create various types of instrumented and non-instrumented clients for use in benchmark tests,
 * so that we can ensure the tests are exactly the same and the only variable is the type (or lack) of instrumentation
 * used.
 */
public final class ClientProvider {

    public static CloseableHttpClient normalApacheHttpClient() {
        return HttpClients.createDefault();
    }

    public static CloseableHttpClient instrumentedApacheHttpClient() {
        return HttpClientBuilder.create().build();
    }
}
