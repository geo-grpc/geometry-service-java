#!/usr/bin/env bash

echo $1/geometry-service-java/src/main/proto/epl/protobuf/ \
     $1/geometry-client-cpp/proto/epl/protobuf/ \
     $1/geometry-client-python/proto/epl/protobuf/ \
     $GOPATH/src/geo-grpc/geometry-client-go/proto/epl/protobuf/ | xargs -n 1 cp $1/protobuf/src/epl/protobuf/geometry.proto

echo $1/geometry-service-java/src/main/proto/epl/grpc/ \
     $1/geometry-client-cpp/proto/epl/grpc/ \
     $1/geometry-client-python/proto/epl/grpc/ \
     $GOPATH/src/geo-grpc/geometry-client-go/proto/epl/grpc/ | xargs -n 1 cp $1/protobuf/src/epl/protobuf/geometry_operators.proto

#cp $1/protobuf/src/epl/protobuf/geometry.proto $1/geometry-service-java/src/main/proto/epl/protobuf/
#cp $1/protobuf/src/epl/protobuf/geometry_operators.proto $1/geometry-service-java/src/main/proto/epl/grpc/
#./gradlew clean
#./gradlew build install
docker rm -f temp-cc
#docker build -t echoparklabs/geometry-service-java:11-jdk-slim .
#docker run -d --name=temp-cc echoparklabs/geometry-service-java:11-jdk-slim

#cd ${PWD}/geometry-client-cpp
#rm ../geometry-client-cpp/geometry/geometry_*
#rm ../geometry-client-cpp/protos/*.proto
echo $1
rm -rf $1/geometry-client-cpp/build
mkdir $1/geometry-client-cpp/build

#cp $1/geometry-service-java/src/main/proto/epl/grpc/geometry_operators.proto $1/geometry-client-cpp/proto/epl/grpc
#cp $1/geometry-service-java/src/main/proto/epl/protobuf/geometry.proto $1/geometry-client-cpp/proto/epl/protobuf
ls $1/geometry-client-cpp/proto/epl/grpc
ls $1/geometry-client-cpp/proto/epl/protobuf
set -e

cd $1/geometry-client-cpp/build
cmake ..
make
cd ./geometry-test
pwd
./unitTest


echo test Python
echo $1

#cp $1/geometry-service-java/src/main/proto/epl/grpc/*.proto $1/geometry-client-python/proto/epl/grpc/
#cp $1/geometry-service-java/src/main/proto/epl/protobuf/*.proto $1/geometry-client-python/proto/epl/protobuf/
echo $1
echo cd $1/geometry-client-python
cd $1/geometry-client-python
python3 -mgrpc_tools.protoc -I=./proto/ --python_out=./ ./proto/epl/protobuf/geometry.proto
python3 -mgrpc_tools.protoc -I=./proto/ --python_out=./ --grpc_python_out=./ ./proto/epl/grpc/geometry_operators.proto

ls
source "./venv/bin/activate"
pytest ./test/test_client.py
deactivate

#echo test JavaScript
#cp $1/geometry-service-java/src/main/proto/epl/grpc/geometry_operators.proto $1/geometry-client-js/proto/
#cd $1/geometry-client-js/proto
#grpc_tools_node_protoc --js_out=import_style=commonjs,binary:../static_codegen/ --grpc_out=../static_codegen/ --plugin=protoc-gen-grpc=`which grpc_tools_node_protoc_plugin` ./geometry_operators.proto
#cd $1/geometry-client-js
#/usr/local/lib/node_modules/nodeunit/bin/nodeunit test/index.js

echo test Go
#cp $1/geometry-service-java/src/main/proto/epl/grpc/*.proto $GOPATH/src/geo-grpc/geometry-client-go/proto/geometry_operators.proto
cd $GOPATH/src/geo-grpc/geometry-client-go
protoc -I $GOPATH/src/geo-grpc/geometry-client-go/proto/ \
    $GOPATH/src/geo-grpc/geometry-client-go/proto/epl/protobuf/geometry.proto \
    --go_out=$GOPATH/src

protoc -I $GOPATH/src/geo-grpc/geometry-client-go/proto/ \
    $GOPATH/src/geo-grpc/geometry-client-go/proto/geometry_operators.proto \
    --go_out=plugins=grpc:$GOPATH/src

go test test/geometry_test.go -v

cd $1/geometry-service-java
