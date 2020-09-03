package com.amazonaws.xray.agent.utils;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.proxies.apache.http.HttpClientBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

    public static HttpServlet normalHttpServlet() {
        return new NormalServlet();
    }

    public static HttpServlet instrumentedHttpServlet() {
        return new InstrumentedServlet();
    }

    /**
     * Simple servlet class that just waits 2 milliseconds to service a request then responds. Will be invoked by the "service"
     * method, which is instrumented by the Agent. No point in manipulating response since it's a mock.
     */
    private static class NormalServlet extends HttpServlet {
        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            try {
                Thread.sleep(2);
            } catch (Exception e) {
            }
        }
    }

    /**
     * "Instrumented" servlet class that simulates the X-Ray SDK's filter by calling the pre-filter and post-filter
     * methods around the activity of the servlet.
     */
    private static class InstrumentedServlet extends HttpServlet {
        private AWSXRayServletFilter filter = new AWSXRayServletFilter("Benchmark");

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) {
            filter.preFilter(request, response);

            try {
                Thread.sleep(2);
            } catch (Exception e) {
            }

            filter.postFilter(request, response);
        }
    }
}
