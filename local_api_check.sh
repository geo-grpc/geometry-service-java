#!/usr/bin/env bash


#cp ../protobuf/src/epl/protobuf/geometry_operators.proto ./src/main/proto/epl/grpc/
#./gradlew clean
#./gradlew build install
#docker rm -f temp-cc
#docker build -t echoparklabs/geometry-service-java:11-jdk-slim .
#docker run -d --name=temp-cc echoparklabs/geometry-service-java:11-jdk-slim

#cd ${PWD}/geometry-client-cpp
#rm ../geometry-client-cpp/geometry/geometry_*
#rm ../geometry-client-cpp/protos/*.proto
echo $1
rm -rf $1/geometry-client-cpp/build
mkdir $1/geometry-client-cpp/build

cp $1/geometry-service-java/src/main/proto/epl/grpc/geometry_operators.proto $1/geometry-client-cpp/protos
set -e

cd $1/geometry-client-cpp/build
cmake ..
make
cd ./geometry-test
pwd
./unitTest


echo test Python
echo $1

cd "$1"/geometry-service-java/src/main/proto/epl/grpc
cp geometry_operators.proto $1/geometry-client-python/proto/epl/protobuf/
echo $1
echo cd $1/geometry-client-python
cd $1/geometry-client-python
python3 -mgrpc_tools.protoc -I=./proto/ --python_out=./ --grpc_python_out=./ ./proto/epl/protobuf/geometry_operators.proto

ls
source "./venv/bin/activate"
pytest ./test/sample.py
deactivate

cd $1/geometry-service-java

#echo test JavaScript
#cp $1/geometry-service-java/src/main/proto/epl/grpc/geometry_operators.proto $1/geometry-client-js/proto/
#cd $1/geometry-client-js/proto
#grpc_tools_node_protoc --js_out=import_style=commonjs,binary:../static_codegen/ --grpc_out=../static_codegen/ --plugin=protoc-gen-grpc=`which grpc_tools_node_protoc_plugin` ./geometry_operators.proto
#cd $1/geometry-client-js
#/usr/local/lib/node_modules/nodeunit/bin/nodeunit test/index.js

echo test Go
cp $1/geometry-service-java/src/main/proto/epl/grpc/geometry_operators.proto $GOPATH/src/geo-grpc/geometry-client-go/proto/geometry_operators.proto
cd $GOPATH/src/geo-grpc/geometry-client-go
protoc -I proto/ proto/geometry_operators.proto --go_out=plugins=grpc:epl/protobuf
go test test/geometry_test.go -v

cd $1/geometry-service-java
