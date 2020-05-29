# AWSXRayJavaAgentSample

This repo contains a few items to show off the capabilities of the AWS X-Ray Java Agent for auto-instrumenting your Lambda function.

The CloudformationStack and source code included will setup a small distributed service using DynamoDB, Lambda, S3 and SQS. 
It will also create a Lambda layer for the Agent, so it can be easily reused in other projects. Deleting the stack will delete all resources, 
including the Agent Lambda Layer, making for easy cleanup.

Under the hood, the Java Agent utilizes the new DISCO library, an all purpose toolkit for building Java Agents:
https://github.com/awslabs/disco

## Prerequisites

[Install the AWS SDK CLI tool for your development environment](https://aws.amazon.com/cli/)  

## Setup
**Step 1:** Create an S3 bucket for this project.

```
   # bucket must be lowercased
   bucket='MY_BUCKET_NAME'
   aws s3api create-bucket --bucket $bucket --region us-west-2 --create-bucket-configuration LocationConstraint=us-west-2
```

**Step 2:** Build the Sample App with Gradle.

```
   cd aws-xray-java-agent/sample
   ./gradlew build
```

This will create three ZIP files in build/distributions:
1. **XRayJavaAgentLambda1Source.zip** - The compiled Lambda function code
1. **XRayJavaAgentLambda1Dep.zip** - The Lambda Layer with the transitive dependencies of the Lambda function.
1. **XRayJavaAgent.zip** - The Lambda Layer with the X-Ray Agent JAR files and the required `tools.jar` file.


**Step 3:** Upload these to your project S3 bucket.

```
   aws s3api put-object --bucket $bucket --key XRayJavaAgentLambda1Source.zip --body build/distributions/XRayJavaAgentLambda1Source.zip
   aws s3api put-object --bucket $bucket --key XRayJavaAgentLambda1Dep.zip --body build/distributions/XRayJavaAgentLambda1Dep.zip
   aws s3api put-object --bucket $bucket --key XRayJavaAgent.zip --body build/distributions/XRayJavaAgent.zip
```

**Step 4:** Create all required resources with CloudFormation.

```
   aws cloudformation create-stack --stack-name "XRayJavaAgentSample" --template-body file://XRayJavaAgentCFNTemplate.json --capabilities CAPABILITY_NAMED_IAM --parameters  ParameterKey=SourceBucket,ParameterValue=$bucket
```

You can view the stack in creation on the CloudFormation console.

## Running the App

You can test your Lambda function named "XRayJavaAgentLambda1" in the Lambda Console. 
It should send segments automatically through to the X-Ray console.

## Modifying the app

Once set up, you can modify the sample app source code and test your changes by running:

```
./gradlew build
aws lambda update-function-code --function-name XRayJavaAgentLambda1 --zip-file fileb://build/distributions/XRayJavaAgentLambda1Source.zip
```

Then running the app as described above.

## Clean Up

Delete the CloudFormation stack:
```
aws cloudformation delete-stack --stack-name "XRayJavaAgentSample"
```

Delete the S3 bucket:
```
aws s3api delete-bucket --bucket $bucket
```
