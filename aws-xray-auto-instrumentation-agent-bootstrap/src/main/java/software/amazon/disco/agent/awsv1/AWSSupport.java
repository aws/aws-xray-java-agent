package software.amazon.disco.agent.awsv1;

import software.amazon.disco.agent.interception.Installable;
import software.amazon.disco.agent.interception.Package;

import java.util.Arrays;
import java.util.Collection;

/**
 * Bundle of AWS Support interceptors, for Agents to install as a whole.
 */
public class AWSSupport implements Package {
    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Installable> get() {
        return Arrays.asList(
            new AWSClientInvokeRecordInterceptor()
        );
    }
}
