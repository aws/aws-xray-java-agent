## AWS X-Ray Agent Benchmarks

This package contains benchmarking tests to compare the performance of common scenarios in a distributed application. 
The scenarios can be run with instrumentation by the AWS X-Ray Auto-Instrumentation Agent, the AWS X-Ray SDK for Java, 
and no instrumentation at all as a baseline. A local server is used to mitigate unpredictable network latencies.

## Running the Benchmarks

The officially recorded benchmark results are run on an m5.xlarge Amazon EC2 instance with 4 vCPUs and 16 GB
of memory running Amazon Linux 2. It is recommended you run them in a similar environment for comparable results.

The benchmarks can be run in 3 modes: `agent`, `SDK`, or `normal`. When run in `normal` mode, the benchmark tests are ran without
any instrumentation as a baseline. When run in `SDK` mode, the benchmark tests are ran with instrumentation by the AWS X-Ray SDK.
When run in `agent` mode, the benchmark tests are ran with the X-Ray Agent included on the benchmarking JVM (these can take
quite a while to complete because the agent must re-initialize before each benchmark trial). 

To run in Agent mode, run from the root of this repo:

```shell script
./gradlew clean jmh -Pagent
```

To run in SDK mode, run from the root of this repo:

```shell script
./gradlew clean jmh -Psdk
```

To run in normal mode, run from the root of this repo:

```shell script
./gradlew clean jmh
```

The results will be output into the `build/reports/jmh` directory after the tests are completed.

## Benchmark Results

The below table summarizes the **approximate** latency added using both manual X-Ray SDK and automatic
X-Ray agent-based instrumentation for different activities in microseconds. These numbers should not be
considered precisely accurate in all applications, they are just to give an idea of added latency.
Overall, you can see that in a typical application the SDK or the agent would not add more than a single-digit
amount of milliseconds to a request.

|                          | SDK Instrumentation | Auto Instrumentation |
|--------------------------|---------------------|----------------------|
| Service incoming request | +100 us             | +110 us              |
| SQL Query                | +70 us              | +60 us               |
| AWS SDK V1 Request       | +30 us              | +60 us               |
| AWS SDK V2 Request       | +30 us              | +90 us               |
| Apache HTTP Request      | +20 us              | +40 us               |

You can  also take a look the [results directory](https://github.com/aws/aws-xray-java-agent/tree/main/aws-xray-agent-benchmark/results)
for detailed benchmarking data from previous versions of the agent. Since we use an arbitrary delay to simulate the effect
of a network, the *absolute* values of each individual benchmark are not particularly meaningful. The results are more
useful when analyzed in context, that is comparing the times of the same scenario in all 3 modes, and noticing
the difference between those times.
