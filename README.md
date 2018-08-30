# v0 gRPC geometry operator service
This service uses a fork of ESRI's open source computational geometry library to provide computational geometry operators over gRPC.

## Run an the Geometry Service from Docker 
Run a container on a local dev machine:
```bash
docker run -p 8980:8980 -it --name=temp-c echoparklabs/geometry-service-java:8-jre-slim
```

### Python Sample
running a python sample will also test the docker container. To build it you will need to follow the instructions in the [geometry-client-python](https://github.com/geo-grpc/geometry-client-python) directory's README.md:
```bash
python -m pip install --upgrade pip
pip install grpc
pip install shapely
python -m pip install grpcio
python -mgrpc_tools.protoc -I=./proto/ --python_out=./ --grpc_python_out=./ ./proto/epl/grpc/geometry/geometry_operators.proto
```

to run the python sample:
```bash

pytest test/sample.py
```

Right now the local Java client tests are not filled out, but if they were you can run them as follows against the containerized service (after you've run `./gradlew build` on local dev machine), you'll run:
```bash
./build/install/geometry-service/bin/geometry-operators-client
```

## Build Docker Image

The Docker images are based off of the [openjdk](https://hub.docker.com/_/openjdk/) images. You can build a jdk image or a jre image, you can use Java 8 or 10 (maybe 11, haven't tested), and you can use debian or alpine.

### Building Debian
To build the latest debian 8 jdk image:
```bash
docker build -t echoparklabs/geometry-service-java:8-jdk-slim .
```
The latest debian 8 jre image
```bash
docker build --build-arg JRE_TAG=8-jre-slim -t echoparklabs/geometry-service-java:8-jre-slim .
```
To build the latest debian 10 jdk:
```bash
docker build --build-arg JDK_TAG=10-jdk-slim -t echoparklabs/geometry-service-java:10-jdk-slim .
```
To build the latest debian 10 jre:
```bash
docker build --build-arg JDK_TAG=10-jdk-slim --build-arg JRE_TAG=10-jre-slim \
       -t echoparklabs/geometry-service-java:10-jdk-slim .
```


### Building Alpine
At this time, the resulting Alpine docker image is about 50% smaller than the slim debian images. The default Alpine image uses the `8-jdk-apline` image

To build the latest Alpine JDK 8 image:
```bash
docker build -t echoparklabs/geometry-service-java:8-jdk-alpine -f Dockerfile.alpine .
```

To build the latest Alpine JRE image use the jre tag with a `--build-arg` (it will default to the latest JDK 8 alpine image):
```bash
docker build --build-arg JRE_TAG=8-jre-alpine \
       -t echoparklabs/geometry-service-java:8-jre-alpine -f Dockerfile.alpine .
```


### Building with specific jdk docker images:

To build a specific Alpine JDK 8 image use the `--build-arg`. For example if you wanted to build off of the `8u171-jdk-alpine3.8` openjdk image:
```bash
docker build --build-arg JDK_TAG=8u171-jdk-alpine3.8 \
       -t echoparklabs/geometry-service-java:8u171-jdk-alpine3.8 -f Dockerfile.alpine .
```

And to build a specific jre image use the following `--build-args`. For example if you wanted to the `8u171-jre-alpine3.8`  you would need to also specifiy `8u171-jdk-alpine3.8` JDK:
```bash
docker build --build-arg JRE_TAG=8u171-jre-alpine3.8 \
       --build-arg JDK_TAG=8u171-jdk-alpine3.8 \
       -t echoparklabs/geometry-service-java:8u171-jre-alpine3.8 -f Dockerfile.alpine .
```

## Examples
### Chaining in Go:
 ```go
spatialReferenceWGS := pb.SpatialReferenceData{Wkid:4326}
	spatialReferenceNAD := pb.SpatialReferenceData{Wkid:4269}//SpatialReferenceData.newBuilder().setWkid(4269).build();
	spatialReferenceMerc := pb.SpatialReferenceData{Wkid:3857}
	spatialReferenceGall := pb.SpatialReferenceData{Wkid:54016}

	//var polyline = "MULTILINESTRING ((-120 -45, -100 -55, -90 -63, 0 0, 1 1, 100 25, 170 45, 175 65))";
	geometry_string := []string{"MULTILINESTRING ((-120 -45, -100 -55, -90 -63, 0 0, 1 1, 100 25, 170 45, 175 65))"}
	lefGeometryBag := pb.GeometryBagData{
		Wkt: geometry_string,
		GeometryEncodingType:pb.GeometryEncodingType_wkt,
		SpatialReference:&spatialReferenceNAD}

	operatorLeft := pb.OperatorRequest{
		GeometryBag:&lefGeometryBag,
		OperatorType:pb.ServiceOperatorType_Buffer,
		BufferParams:&pb.BufferParams{Distances:[]float64{.5}},
		ResultSpatialReference:&spatialReferenceWGS}

	operatorNestedLeft := pb.OperatorRequest{
		GeometryRequest:&operatorLeft,
		OperatorType:pb.ServiceOperatorType_ConvexHull,
		ResultSpatialReference:&spatialReferenceGall}

	rightGeometryBag := pb.GeometryBagData{
		Wkt: geometry_string,
		GeometryEncodingType:pb.GeometryEncodingType_wkt,
		SpatialReference:&spatialReferenceNAD}

	operatorRight := pb.OperatorRequest{
		GeometryBag:&rightGeometryBag,
		OperatorType:pb.ServiceOperatorType_GeodesicBuffer,
		BufferParams:&pb.BufferParams{
			Distances:[]float64{1000},
			UnionResult:false},
		OperationSpatialReference:&spatialReferenceWGS}

	operatorNestedRight := pb.OperatorRequest{
		GeometryRequest:&operatorRight,
		OperatorType:pb.ServiceOperatorType_ConvexHull,
		ResultSpatialReference:&spatialReferenceGall}

	operatorContains := pb.OperatorRequest{
		LeftGeometryRequest:&operatorNestedLeft,
		RightGeometryRequest:&operatorNestedRight,
		OperatorType:pb.ServiceOperatorType_Contains,
		OperationSpatialReference:&spatialReferenceMerc}
	operatorResultEquals, err := client.ExecuteOperation(context.Background(), &operatorContains)
```



## Why gRPC and protocol buffers
### Protocol Buffers
They're basically like a binary JSON message. It's platform and language neutral (Python, C++, Java, Go, Node.js). Google provides libraries for each language for auto-generating gRPC communication library code to use the service defined in the proto file.

Quote from google docs:
>Protocol buffers are a flexible, efficient, automated mechanism for serializing structured data – think XML, but smaller, faster, and simpler. You define how you want your data to be structured once, then you can use special generated source code to easily write and read your structured data to and from a variety of data streams and using a variety of languages. You can even update your data structure without breaking deployed programs that are compiled against the "old" format.

### HTTP2 vs HTTP1
gRPC uses HTTP2 only. So why use HTTP2? The advantage is to have one TCP connection with true multiplexing. From [HTTP/2 FAQ](https://http2.github.io/faq/#why-just-one-tcp-connection):
> One application opening so many connections simultaneously breaks a lot of the assumptions that TCP was built upon; since each connection will start a flood of data in the response, there’s a real risk that buffers in the intervening network will overflow, causing a congestion event and retransmits.

http://www.http2demo.io/
