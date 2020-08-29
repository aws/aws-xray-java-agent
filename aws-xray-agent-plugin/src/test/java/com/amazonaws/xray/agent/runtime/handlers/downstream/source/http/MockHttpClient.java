package com.amazonaws.xray.agent.runtime.handlers.downstream.source.http;

import com.amazonaws.http.apache.client.impl.ConnectionManagerAwareHttpClient;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.io.EmptyInputStream;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * TODO: When Disco removes its data accessor pattern in a future version, delete this & use a mock instead
 */
public class MockHttpClient implements ConnectionManagerAwareHttpClient {
    private String responseContent;
    private HttpUriRequest lastRequest; // Hacky way of spying on the last made request

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
    }

    public HttpUriRequest getLastRequest() {
        return lastRequest;
    }

    @Override
    public HttpClientConnectionManager getHttpClientConnectionManager() {
        return null;
    }

    @Override
    public HttpParams getParams() {
        return null;
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return null;
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        this.lastRequest = httpUriRequest;
        HttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        BasicHttpEntity responseBody = new BasicHttpEntity();
        InputStream in = EmptyInputStream.INSTANCE;
        if(null != responseContent && !responseContent.isEmpty()) {
            in = new ByteArrayInputStream(responseContent.getBytes(StandardCharsets.UTF_8));
        }
        responseBody.setContent(in);
        httpResponse.setEntity(responseBody);
        return httpResponse;
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
        return null;
    }

    @Override
    public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
        return (T) execute((HttpUriRequest) httpRequest, httpContext);
    }
}
