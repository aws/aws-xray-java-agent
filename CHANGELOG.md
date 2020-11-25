# Change Log

## 2.8.0 - 2020-11-25
* Fixed NPE in Servlet Response Handler [PR #63](https://github.com/aws/aws-xray-java-agent/pull/63)
* Fixed bug in parsing sampling decision from upstream header [PR #66](https://github.com/aws/aws-xray-java-agent/pull/66)

## 2.7.1 - 2020-09-03
* Added build steps to compile X-Ray agent ZIP [PR #55](https://github.com/aws/aws-xray-java-agent/pull/55)
* Added Benchmarking tests [PR #57](https://github.com/aws/aws-xray-java-agent/pull/57)
* Added support for trace ID injection and plugins [PR #52](https://github.com/aws/aws-xray-java-agent/pull/52)
* Added SQL instrumentation [PR #52](https://github.com/aws/aws-xray-java-agent/pull/52)
* Converted agent architecture to be a DiSCo plugin rather than monolith [PR #52](https://github.com/aws/aws-xray-java-agent/pull/52)
* Converted project to Gradle [PR #52](https://github.com/aws/aws-xray-java-agent/pull/52)
* Added ability to configure agent via external JSON file [PR #33](https://github.com/aws/aws-xray-java-agent/pull/33)
* Switched to use Apache Commons Logging [PR #32](https://github.com/aws/aws-xray-java-agent/pull/32)

## 2.4.0-beta - 2019-12-2
Initial commit