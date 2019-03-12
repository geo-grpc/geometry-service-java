#!/usr/bin/env bash

PROTOPATH="/Users/davidraleigh/go/src/github.com/geo-grpc/api/proto"
echo $2
#https://stackoverflow.com/a/195972/445372
echo $1/geometry-service-java/src/main/proto/epl/protobuf/ \
     $1/geometry-client-cpp/proto/epl/protobuf/ \
     $1/geometry-client-python/proto/epl/protobuf/ \
     $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/epl/protobuf/ | xargs -n 1 cp $GOPATH/src/github.com/geo-grpc/api/proto/epl/protobuf/geometry.proto

echo $1/geometry-service-java/src/main/proto/epl/grpc/ \
     $1/geometry-client-cpp/proto/epl/grpc/ \
     $1/geometry-client-python/proto/epl/grpc/ \
     $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/epl/grpc/ | xargs -n 1 cp $GOPATH/src/github.com/geo-grpc/api/proto/epl/grpc/geometry_operators.proto

./gradlew clean
./gradlew build install
#docker rm -f temp-cc
#docker build -t echoparklabs/geometry-service-java:11-jdk-slim .
#docker run -d --name=temp-cc echoparklabs/geometry-service-java:11-jdk-slim

echo test C++
echo $1
rm -rf $1/geometry-client-cpp/build
mkdir $1/geometry-client-cpp/build

set -e
cmake -B $1/geometry-client-cpp/build $1/geometry-client-cpp/build/..
make -C $1/geometry-client-cpp/build
$1/geometry-client-cpp/build/geometry-test/unitTest
echo end test C++

echo build Python
python3 -mgrpc_tools.protoc -I=$1/geometry-client-python/proto/ --python_out=$1/geometry-client-python/ $1/geometry-client-python/proto/epl/protobuf/geometry.proto
python3 -mgrpc_tools.protoc -I=$1/geometry-client-python/proto/ --python_out=$1/geometry-client-python/ --grpc_python_out=$1/geometry-client-python/ $1/geometry-client-python/proto/epl/grpc/geometry_operators.proto
echo test Python
source "$1/geometry-client-python/venv/bin/activate"
pytest $1/geometry-client-python/test/test_client.py
deactivate
echo end test Python

echo build Go
protoc -I $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/ \
    $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/epl/protobuf/geometry.proto \
    --go_out=$GOPATH/src

protoc -I $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/ \
    $GOPATH/src/github.com/geo-grpc/geometry-client-go/proto/epl/grpc/geometry_operators.proto \
    --go_out=plugins=grpc:$GOPATH/src
echo test Go
cd ${GOPATH}/src/github.com/geo-grpc/geometry-client-go
export GO111MODULE=on
go test ${GOPATH}/src/github.com/geo-grpc/geometry-client-go/test/geometry_test.go -v
echo end test Go

docker rm -f temp-cc
