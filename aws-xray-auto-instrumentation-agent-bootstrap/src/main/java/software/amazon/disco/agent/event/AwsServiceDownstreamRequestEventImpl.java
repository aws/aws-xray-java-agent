/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package software.amazon.disco.agent.event;

import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest.SdkHttpRequestAccessor;
import software.amazon.disco.agent.awsv2.executioninterceptor.accessors.sdkrequest.SdkRequestAccessor;

import java.util.List;
import java.util.Map;

import static software.amazon.disco.agent.event.AwsServiceDownstreamEvent.DataKey.HEADER_MAP;

/**
 * Concrete implementation of the AwsServiceDownstreamRequestEvent.
 */
public class AwsServiceDownstreamRequestEventImpl extends AwsServiceDownstreamRequestEvent {
    private SdkRequestAccessor sdkRequestAccessor;
    private SdkHttpRequestAccessor sdkHttpRequestAccessor;

    /**
     * Construct a new AwsServiceDownstreamRequestEventImpl
     * @param origin the origin of the downstream call e.g. 'AWSv2', 'AWS'
     * @param service the service name e.g. 'DynamoDb'
     * @param operation the operation name e.g. 'ListTables'
     */
    public AwsServiceDownstreamRequestEventImpl(String origin, String service, String operation) {
        super(origin, service, operation);
    }

    /**
     * Set the region for this event
     * @param region the region
     * @return 'this' for method chaining
     */
    public AwsServiceDownstreamRequestEvent withRegion(String region) {
        withData(DataKey.REGION.name(), region);
        return this;
    }

    /**
     * Set the underlying SdkRequestAccessor used for retrieving Aws-specific metadata.
     * @param accessor The SdkRequestAccessor that is used to retrieve metadata
     * @return 'this' for method chaining
     */
    public AwsServiceDownstreamRequestEventImpl withSdkRequestAccessor(SdkRequestAccessor accessor) {
        this.sdkRequestAccessor = accessor;
        return this;
    }

    /**
     * Set the underlying SdkHttpRequestAccessor used for retrieving Http-specific metadata.
     * @param accessor The SdkHttpRequestAccessor that is used to retrieve metadata
     * @return 'this' for method chaining
     */
    public AwsServiceDownstreamRequestEventImpl withSdkHttpRequestAccessor(SdkHttpRequestAccessor accessor) {
        this.sdkHttpRequestAccessor = accessor;
        return this;
    }

    /**
     * Set the underlying Header Map which contains the underlying Http Header that's going out to the service.
     * @param headerMap The header map to store into the event.
     * @return 'this' for method chaining
     */
    public AwsServiceDownstreamRequestEventImpl withHeaderMap(Map<String, List<String>> headerMap) {
        this.withData(HEADER_MAP.name(), headerMap);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueForField(String fieldName, Class clazz) {
        if (sdkRequestAccessor == null) return null;

        return this.sdkRequestAccessor.getValueForField(fieldName, clazz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean replaceHeader(String name, String value) {
        if (sdkHttpRequestAccessor == null) return false;

        Map<String, List<String>> headerMap = sdkHttpRequestAccessor.replaceHeader(name, value);
        if (headerMap != null) {
            this.withHeaderMap(headerMap);
        }
        return headerMap != null;
    }
}
