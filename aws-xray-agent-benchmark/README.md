## AWS X-Ray Agent Benchmarks

This package contains benchmarking tests to compare the performance of several common scenarios with instrumentation
by the AWS X-Ray Auto-Instrumentation Agent, the AWS X-Ray SDK for Java, and no instrumentation at all as a baseline.

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

TODO: summary of performance of the three modes.

You can  also take a look the [results directory](https://github.com/aws/aws-xray-java-agent/tree/master/aws-xray-agent-benchmark/results)
for detailed benchmarking data from previous versions of the agent.
