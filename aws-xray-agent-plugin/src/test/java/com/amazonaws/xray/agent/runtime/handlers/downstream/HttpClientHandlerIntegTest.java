package com.amazonaws.xray.agent.runtime.handlers.downstream;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.entities.TraceHeader;
import com.amazonaws.xray.entities.TraceHeader.SampleDecision;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HttpClientHandlerIntegTest {
    private static final String TRACE_HEADER_KEY = TraceHeader.HEADER_KEY;
    private static final int PORT = 8089;
    private static final String ENDPOINT = "http://127.0.0.1:" + PORT;
    private Segment currentSegment;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    @Before
    public void setup() {
        // Initializing WireMock tricks the agent into thinking an HTTP call is being made, which creates subsegments,
        // so this segment is needed to avoid CMEs. It will be overridden with a fresh segment.
        AWSXRay.beginSegment("ignore");

        stubFor(get(anyUrl()).willReturn(ok()));
        stubFor(post(anyUrl())
                .willReturn(ok().withHeader("content-length", "42")));

        // Generate the segment that would be normally made by the upstream instrumentor
        currentSegment = AWSXRay.beginSegment("parentSegment");
    }

    @After
    public void cleanup() {
        AWSXRay.clearTraceEntity();
    }

    @Test
    public void testBasicGetCall() throws Exception {
        URI uri = new URI(ENDPOINT);
        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(uri);

        HttpResponse httpResponse = httpClient.execute(request);

        assertThat(currentSegment.getSubsegments()).hasSize(1);
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Check Subsegment properties
        assertThat(currentSubsegment.getName()).isEqualTo(uri.getHost());
        assertThat(currentSubsegment.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(currentSubsegment.isInProgress()).isFalse();

        // Check for http-specific request info
        Map<String, Object> requestMap = (Map<String, Object>) currentSubsegment.getHttp().get("request");
        assertThat(requestMap).hasSize(2);
        assertThat(requestMap).containsEntry("method", "GET");
        assertThat(requestMap).containsEntry("url", uri.toString());

        // Check for http-specific response info
        Map<String, Object> responseMap = (Map<String, Object>) currentSubsegment.getHttp().get("response");
        assertThat(responseMap).hasSize(1);
        assertThat(responseMap).containsEntry("status", httpResponse.getStatusLine().getStatusCode());
    }

    @Test
    public void testTraceHeaderPropagation() throws Exception {
        URI uri = new URI(ENDPOINT);
        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(uri);

        httpClient.execute(request);

        assertThat(currentSegment.getSubsegments()).hasSize(1);
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Check for Trace propagation
        Header httpTraceHeader = request.getAllHeaders()[0];
        assertThat(httpTraceHeader.getName()).isEqualTo(TRACE_HEADER_KEY);
        TraceHeader injectedTH = TraceHeader.fromString(httpTraceHeader.getValue());
        SampleDecision sampleDecision = currentSegment.isSampled() ? SampleDecision.SAMPLED : SampleDecision.NOT_SAMPLED;
        assertThat(injectedTH.toString())
                .isEqualTo(new TraceHeader(currentSegment.getTraceId(), currentSubsegment.getId(), sampleDecision).toString()); // Trace header should be added
    }

    @Test
    public void testBasicPostCall() throws Exception {
        URI uri = new URI(ENDPOINT);
        HttpClient httpClient = HttpClients.createMinimal();
        HttpPost request = new HttpPost(uri);
        HttpResponse httpResponse = httpClient.execute(request);

        assertThat(currentSegment.getSubsegments()).hasSize(1);
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        // Check Subsegment properties
        assertThat(currentSubsegment.getName()).isEqualTo(uri.getHost());
        assertThat(currentSubsegment.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(currentSubsegment.isInProgress()).isFalse();

        // Check for http-specific request info
        Map<String, Object> requestMap = (Map<String, Object>) currentSubsegment.getHttp().get("request");
        assertThat(requestMap).hasSize(2);
        assertThat(requestMap).containsEntry("method", "POST");
        assertThat(requestMap).containsEntry("url", uri.toString());

        // Check for http-specific response info
        Map<String, Object> responseMap = (Map<String, Object>) currentSubsegment.getHttp().get("response");
        assertThat(responseMap).hasSize(2);
        assertThat(responseMap).containsEntry("status", httpResponse.getStatusLine().getStatusCode());
        assertThat(responseMap).containsEntry("content_length", 42L);
    }

    @Test
    public void testChainedCalls() throws Exception {
        URI uri = new URI(ENDPOINT);
        HttpClient httpClient = HttpClients.createMinimal();
        HttpPost request = new HttpPost(uri);

        assertThat(currentSegment.getSubsegments()).isEmpty();
        httpClient.execute(request);
        assertThat(currentSegment.getSubsegments()).hasSize(1);
        httpClient.execute(request);
        assertThat(currentSegment.getSubsegments()).hasSize(2);
    }

    @Test
    public void testInvalidTargetHost() throws Exception {
        URI uri = new URI("sdfkljdfs");
        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(uri);

        assertThatThrownBy(() -> httpClient.execute(request)).isInstanceOf(IllegalArgumentException.class);

        assertThat(currentSegment.getSubsegments()).hasSize(1);
        Subsegment currentSubsegment = currentSegment.getSubsegments().get(0);

        assertThat(currentSubsegment.getName()).isEqualTo(uri.toString());
        assertThat(currentSubsegment.getCause().getExceptions()).hasSize(1);
        assertThat(currentSubsegment.getCause().getExceptions().get(0).getThrowable()).isInstanceOf(IllegalArgumentException.class);

        assertThat(currentSubsegment.getNamespace()).isEqualTo(Namespace.REMOTE.toString());
        assertThat(currentSubsegment.isFault()).isTrue();
        assertThat(currentSubsegment.isError()).isFalse();
        assertThat(currentSubsegment.isInProgress()).isFalse();

        // Even in failures, we should at least see the requested information.
        Map<String, String> requestMap = (Map<String, String>) currentSubsegment.getHttp().get("request");
        assertThat(requestMap).hasSize(2);
        assertThat(requestMap).containsEntry("method", "GET");
        assertThat(requestMap).containsEntry("url", uri.toString());

        // No response because we passed in an invalid request.
        assertThat(currentSubsegment.getHttp()).doesNotContainKey("response");
    }

    @Test
    public void testIgnoreSamplingCalls() throws Exception {
        URI targetsUri = new URI(ENDPOINT + "/SamplingTargets");
        URI rulesUri = new URI(ENDPOINT + "/GetSamplingRules");

        HttpClient httpClient = HttpClients.createMinimal();
        HttpGet request = new HttpGet(targetsUri);
        httpClient.execute(request);

        assertThat(currentSegment.getSubsegments()).isEmpty();

        request = new HttpGet(rulesUri);
        httpClient.execute(request);

        assertThat(currentSegment.getSubsegments()).isEmpty();
    }
}
