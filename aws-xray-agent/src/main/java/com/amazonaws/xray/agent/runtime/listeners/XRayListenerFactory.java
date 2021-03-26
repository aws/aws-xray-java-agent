package com.amazonaws.xray.agent.runtime.listeners;

import com.amazonaws.xray.agent.runtime.config.XRaySDKConfiguration;
import com.amazonaws.xray.agent.runtime.dispatcher.EventDispatcher;
import com.amazonaws.xray.agent.runtime.handlers.downstream.AWSHandler;
import com.amazonaws.xray.agent.runtime.handlers.downstream.AWSV2Handler;
import com.amazonaws.xray.agent.runtime.handlers.downstream.HttpClientHandler;
import com.amazonaws.xray.agent.runtime.handlers.downstream.SqlHandler;
import com.amazonaws.xray.agent.runtime.handlers.downstream.SqlPrepareHandler;
import com.amazonaws.xray.agent.runtime.handlers.upstream.ServletHandler;
import software.amazon.disco.agent.event.Listener;

import java.net.URL;

/**
 * Factory class that produces an X-Ray listener with upstream and downstream dispatchers to intercept relevant
 * events on the Disco event bus. It is important to keep this class minimal since we add different handlers
 * to the listener based on the environment it's consumed in.
 */
public class XRayListenerFactory implements ListenerFactory {
    private static final String AWS_ORIGIN = "AWSv1";
    private static final String AWS_V2_ORIGIN = "AWSv2";
    private static final String APACHE_HTTP_CLIENT_ORIGIN = "ApacheHttpClient";
    private static final String HTTP_SERVLET_ORIGIN = "httpServlet";
    private static final String SQL_ORIGIN = "SQL";
    private static final String SQL_PREPARE_ORIGIN = "SqlPrepare";

    private static URL manifest;
    private static int configVersion;

    /**
     * Creates a Disco Event Bus listener with upstream and downstream event dispatchers that have handlers
     * for each event type that the X-Ray Agent supports.
     *
     * @return An X-Ray Agent listener
     */
    @Override
    public Listener generateListener() {
        manifest = XRaySDKConfiguration.getInstance().getAwsServiceHandlerManifest();
        configVersion = XRaySDKConfiguration.getInstance().getAwsSdkVersion();

        EventDispatcher upstreamEventDispatcher = new EventDispatcher();

        if (XRaySDKConfiguration.getInstance().isTraceIncomingRequests()) {
            upstreamEventDispatcher.addHandler(HTTP_SERVLET_ORIGIN, new ServletHandler());
        }

        EventDispatcher downstreamEventDispatcher = new EventDispatcher();
        downstreamEventDispatcher.addHandler(APACHE_HTTP_CLIENT_ORIGIN, new HttpClientHandler());
        downstreamEventDispatcher.addHandler(SQL_ORIGIN, new SqlHandler());
        downstreamEventDispatcher.addHandler(SQL_PREPARE_ORIGIN, new SqlPrepareHandler());

        if (configVersion == 1 && manifest != null) {
            downstreamEventDispatcher.addHandler(AWS_ORIGIN, new AWSHandler(manifest));
        } else {
            downstreamEventDispatcher.addHandler(AWS_ORIGIN, new AWSHandler());
        }

        if (configVersion == 2 && manifest != null) {
            downstreamEventDispatcher.addHandler(AWS_V2_ORIGIN, new AWSV2Handler(manifest));
        } else {
            downstreamEventDispatcher.addHandler(AWS_V2_ORIGIN, new AWSV2Handler());
        }

        return new XRayListener(upstreamEventDispatcher, downstreamEventDispatcher);
    }
}
