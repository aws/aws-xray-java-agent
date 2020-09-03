#!/bin/bash

if [ "$1" = "" ]; then
  echo "Must provide a version number argument, e.g. 2.7.x"
  exit 0
fi

echo "**************************************"
echo "Starting Baseline Benchmarks"
echo "**************************************"

./gradlew clean jmh --stacktrace

echo "**************************************"
echo "Starting SDK Benchmarks"
echo "**************************************"

./gradlew jmh -Psdk --stacktrace

echo "**************************************"
echo "Starting SDK Benchmarks"
echo "**************************************"

./gradlew jmh -Pagent -x test --stacktrace  # We don't care about agent integ tests here

echo "**************************************"
echo "Finished all benchmarks"
echo "**************************************"

mkdir -p "aws-xray-agent-benchmark/results/$1"
cp -Rf aws-xray-agent-benchmark/build/reports/jmh/ "aws-xray-agent-benchmark/results/$1"
git add aws-xray-agent-benchmark/results

echo "Inspect the results in aws-xray-agent-benchmark/results/$1 then commit!"
