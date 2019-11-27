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

package software.amazon.disco.agent.awsv2;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import software.amazon.disco.agent.interception.Installable;

/**
 * Base class for the Aws Client Interceptors to modify intercept all newly generated Aws SDK v2 clients.
 */
public abstract class AWSClientInterceptor implements Installable {

    /**
     * Build a ElementMatcher which defines the kind of class which will be intercepted. Package-private for tests.
     *
     * @return A ElementMatcher suitable to pass to the type() method of an AgentBuilder
     */
    ElementMatcher<? super TypeDescription> buildClassMatcher() {
        return ElementMatchers.hasSuperType(ElementMatchers.named("software.amazon.awssdk.core.client.builder.SdkClientBuilder"));
    }

    /**
     * Build an ElementMatcher which will match against the build() method of an Sdk Client Builder.
     * Package-private for tests
     *
     * @return An ElementMatcher suitable for passing to the method() method of a DynamicType.Builder
     */
    ElementMatcher<? super MethodDescription> buildMethodMatcher() {
        ElementMatcher.Junction<? super MethodDescription> methodMatches = ElementMatchers.named("build")
                .and(ElementMatchers.takesArguments(0))
                .and(ElementMatchers.isFinal())
                .and(ElementMatchers.not(ElementMatchers.isAbstract()));
        return methodMatches;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AgentBuilder install(AgentBuilder agentBuilder) {
        return agentBuilder
                .type(buildClassMatcher())
                .transform((builder, typeDescription, classLoader, module) -> builder
                        .method(buildMethodMatcher())
                        .intercept(MethodDelegation.to(this.getClass())));
    }
}
