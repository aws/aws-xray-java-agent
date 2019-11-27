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

import java.util.List;
import java.util.Map;

import static software.amazon.disco.agent.event.AwsServiceDownstreamEvent.DataKey.HEADER_MAP;

/**
 * Specialization of a ServiceDownstreamRequestEvent, to encapsulate data specific to Aws downstream call requests.
 */
public class AwsServiceDownstreamRequestEvent extends ServiceDownstreamRequestEvent implements AwsServiceDownstreamEvent {
    /**
     * Keys to use in the data map
     */
    enum DataKey {
        /**
         * The Request Id of the current sdk request.
         */
        REQUEST_ID,

        /**
         * The region of the request
         */
        REGION,

        /**
         * The operation name of the current request
         */
        OPERATION_NAME,

        /**
         * The service that the request is going to
         */
        SERVICE_NAME,
    }

    /**
     * Construct a new AwsServiceDownstreamRequestEvent
     * @param origin the origin of the downstream call e.g. 'AWSv1' or 'AWSv2'
     * @param service the service name e.g. 'DynamoDb'
     * @param operation the operation name e.g. 'ListTables'
     */
    public AwsServiceDownstreamRequestEvent(String origin, String service, String operation) {
        super(origin, service, operation);
    }

    /**
     * Retrieve the region for this event
     * @return the region the downstream call is making to.
     */
    public String getRegion() {
        return (String)getData(DataKey.REGION.name());
    }


    /**
     * Obtain the Field value for the corresponding event. This obtains the field values from the SdkRequest object.
     * @param fieldName - The field name for the Sdk Request
     * @param clazz - The class the return object should be casted into upon returning
     * @return The object in the Sdk request field value.
     */
    @Override
    public Object getValueForField(String fieldName, Class clazz) {
        return null;
    }

    /**
     * Override this method if you are a publisher of AwsServiceDownstreamRequestEvent which allows header replacement
     * @param name the header name
     * @param value the header value
     * @return true if successful
     */
    public boolean replaceHeader(String name, String value) {
        return false;
    }

    /**
     * Retrieve the underlying Http header map for the outgoing request.
     * @return an immutable header map
     */
    public Map<String, List<String>> getHeaderMap() {
        return (Map<String, List<String>>) getData(HEADER_MAP.name());
    }

}
