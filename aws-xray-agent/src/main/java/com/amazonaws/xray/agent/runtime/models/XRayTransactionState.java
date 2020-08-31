package com.amazonaws.xray.agent.runtime.models;

/**
 * Contains state information for each logical request/response transaction event.
 *
 * Events that are captured should capture the relevant information and store it here.
 */
public class XRayTransactionState {
    private String host;
    private String url;
    private String userAgent;
    private String clientIP;
    private String method;
    private String serviceType; // Type of AWS resource running application.
    private String traceHeader;
    private String origin;

    private static String serviceName;

    public XRayTransactionState withHost(String host) {
        this.host = host;
        return this;
    }

    public XRayTransactionState withUrl(String url) {
        this.url = url;
        return this;
    }

    public XRayTransactionState withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public XRayTransactionState withClientIP(String clientIP) {
        this.clientIP = clientIP;
        return this;
    }

    public XRayTransactionState withMethod(String method) {
        this.method = method;
        return this;
    }

    public XRayTransactionState withServiceType(String serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public XRayTransactionState withTraceheaderString(String traceHeaderString) {
        // can be null
        this.traceHeader = traceHeaderString;
        return this;
    }

    public XRayTransactionState withOrigin(String origin) {
        // can be null
        this.origin = origin;
        return this;
    }

    public String getHost() {
        return this.host;
    }

    public String getURL() {
        return this.url;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public String getClientIP() {
        return this.clientIP;
    }

    public String getMethod() {
        return this.method;
    }

    public String getServiceType() {
        return this.serviceType;
    }

    public String getTraceHeader() {
        // can be null
        return this.traceHeader;
    }

    public String getOrigin() {
        // can be null
        return this.origin;
    }

    public static void setServiceName(String inServiceName) {
        serviceName = inServiceName;
    }

    public static String getServiceName() {
        return serviceName;
    }
}
