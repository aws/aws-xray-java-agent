# AWS X-Ray Java Agent Sample App

This directory contains a few items to show off the capabilities of the AWS X-Ray Java Agent for auto-instrumenting your Lambda function.
The CloudformationStack and source code included will setup a small distributed service using DynamoDB, S3, Lambda, S3 and SQS.

The Java Agent is implemented using the new [DiSCo library](https://github.com/awslabs/disco), an all purpose toolkit for building Java Agents.

## Getting Started
### Prerequisites
* An AWS account with
* The [AWS CLI tool](https://aws.amazon.com/cli/) for your development environment 
* [Gradle](https://gradle.org/install/) installed

### Running the Agent on AWS Lambda [WIP]

Build the Lambda code here with Gradle to create a ZIP containing all the project dependencies.
```
   cd AWSXRayJavaAgentSample
   ./gradlew build
```
This will create two ZIP files in build/distributions. Upload thse into a new S3 bucket, "xray-java-agent-lambda-source".
Build the Agent, and upload this ZIP as well.
```
   bucket='MY_BUCKET_NAME'
   aws s3api create-bucket --bucket $bucket --region us-west-2 --create-bucket-configuration LocationConstraint=us-west-2
   aws s3api put-object --bucket $bucket --key XRayJavaAgentLambda1Source.zip --body build/distributions/XRayJavaAgentLambda1Source.zip
   aws s3api put-object --bucket $bucket --key XRayJavaAgentLambda1Dep.zip --body build/distributions/XRayJavaAgentLambda1Dep.zip
   aws s3api put-object --bucket $bucket --key XRayJavaAgent.zip --body XRayJavaAgent.zip

   aws cloudformation create-stack --stack-name "XRayJavaAgentSample" --template-body file://XRayJavaAgentCFNTemplate.json --capabilities CAPABILITY_NAMED_IAM --parameters  ParameterKey=SourceBucket,ParameterValue=$bucket
```
You can view the stack in creation on the CloudFormation console. When done, you can test your Lambda function named "XRayJavaAgentLambda1". It should send segments automatically through to the X-Ray console.

Once set up, you can modify the source code and run "./gradlew build" again, and upload the XRayJavaAgentLambda1Source.zip straight into the Lambda function.

## FAQ

**Question**: Why is my Lambda function's cold start initialization slower than expected?

**Answer**: This is expected behavior and a current issue with DiSCo and the X-Ray Java Agent. 
All subsequent invokes should see minimal latency overhead. See this [issue](https://github.com/aws/aws-xray-java-agent/issues/6) for tracking our progress to optimize it.

**Question**: I have a Spring web service. Can I use the Agent?

**Answer**: Due to the way Spring/Spring Boot load resources, this is creating a race condition on the Agent/Spring dependency chain. 
We plan to support Spring-based web applications as soon as possible. See this [issue](https://github.com/aws/aws-xray-java-agent/issues/7) for tracking our progress to support them. 


