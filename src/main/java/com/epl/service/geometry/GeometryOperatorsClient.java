/*
Copyright 2017 Echo Park Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

For additional information, contact:

email: info@echoparklabs.io
*/

package com.epl.service.geometry;


import com.esri.core.geometry.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsBlockingStub;
import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsStub;
import io.grpc.*;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import io.grpc.util.RoundRobinLoadBalancerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Sample client code that makes gRPC calls to the server.
 */
public class GeometryOperatorsClient {
    private static final Logger logger = Logger.getLogger(GeometryOperatorsClient.class.getName());


    private final ManagedChannel channel;
    private final GeometryOperatorsBlockingStub blockingStub;
    private final GeometryOperatorsStub asyncStub;



    private Random random = new Random();
    private TestHelper testHelper;


    /** Construct client for accessing GeometryOperators server at {@code host:port}. */
    public GeometryOperatorsClient(String host, int port) {
        this(ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext(true));
    }

    public GeometryOperatorsClient(String serviceTarget) {
        this(ManagedChannelBuilder
                .forTarget(serviceTarget)
                .nameResolverFactory(new KubernetesNameResolverProvider())  // this is on by default
                .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
                .usePlaintext(true));
    }

    /** Construct client for accessing GeometryOperators server using the existing channel. */
    public GeometryOperatorsClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = GeometryOperatorsGrpc.newBlockingStub(channel);
        asyncStub = GeometryOperatorsGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void shapefileThrottled(File inFile) throws IOException, InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        String prfFile = inFile.getAbsolutePath().substring(0, inFile.getAbsolutePath().lastIndexOf('.')) + ".prj";
        String projectionWKT = new String(Files.readAllBytes(Paths.get(prfFile)));
        ShapefileByteReader shapefileByteReader = new ShapefileByteReader(inFile);

        ServiceSpatialReference serviceSpatialReference = ServiceSpatialReference.newBuilder()
                .setEsriWkt(projectionWKT).build();

        ServiceSpatialReference wgs84SpatiralReference = ServiceSpatialReference.newBuilder()
                .setWkid(4326).build();

        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder()
                .addGeometryBinary(ByteString.copyFromUtf8(""))
                .addGeometryId(0)
                .setGeometryEncodingType(GeometryEncodingType.esri)
                .setSpatialReference(serviceSpatialReference);

        OperatorRequest.Builder operatorRequestBuilder = OperatorRequest.newBuilder()
                .setOperatorType(ServiceOperatorType.Buffer)
                .addBufferDistances(2.5)
                .setResultsEncodingType("wkt")
                .setResultSpatialReference(wgs84SpatiralReference);

        GeometryOperatorsStub geometryOperatorsStub = asyncStub
                .withMaxInboundMessageSize(2147483647)
                .withMaxOutboundMessageSize(2147483647);

        // When using manual flow-control and back-pressure on the client, the ClientResponseObserver handles both
        // request and response streams.
        ClientResponseObserver<OperatorRequest, OperatorResult> clientResponseObserver =
                new ClientResponseObserver<OperatorRequest, OperatorResult>() {
                    ClientCallStreamObserver<OperatorRequest> requestStream;

                    @Override
                    public void beforeStart(ClientCallStreamObserver<OperatorRequest> requestStream) {
                        this.requestStream = requestStream;
                        // Set up manual flow control for the response stream. It feels backwards to configure the response
                        // stream's flow control using the request stream's observer, but this is the way it is.
                        requestStream.disableAutoInboundFlowControl();

                        // Set up a back-pressure-aware producer for the request stream. The onReadyHandler will be invoked
                        // when the consuming side has enough buffer space to receive more messages.
                        //
                        // Messages are serialized into a transport-specific transmit buffer. Depending on the size of this buffer,
                        // MANY messages may be buffered, however, they haven't yet been sent to the server. The server must call
                        // request() to pull a buffered message from the client.
                        //
                        // Note: the onReadyHandler's invocation is serialized on the same thread pool as the incoming
                        // StreamObserver'sonNext(), onError(), and onComplete() handlers. Blocking the onReadyHandler will prevent
                        // additional messages from being processed by the incoming StreamObserver. The onReadyHandler must return
                        // in a timely manor or else message processing throughput will suffer.
                        requestStream.setOnReadyHandler(new Runnable() {
                            @Override
                            public void run() {
                                while(requestStream.isReady()) {
                                    if (shapefileByteReader.hasNext()) {
                                        byte[] data = shapefileByteReader.next();
                                        int id = shapefileByteReader.getGeometryID();
                                        ByteString byteString = ByteString.copyFrom(data);
                                        logger.info("bytes length -->" + data.length);

                                        ServiceGeometry serviceGeometry= serviceGeometryBuilder
                                                .setGeometryBinary(0, byteString)
                                                .setGeometryId(0, id)
                                                .build();
                                        OperatorRequest operatorRequest = operatorRequestBuilder
                                                .setLeftGeometry(serviceGeometry).build();
                                        requestStream.onNext(operatorRequest);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onNext(OperatorResult operatorResult) {
                        String results = operatorResult.getGeometry().getGeometryString(0);
                        logger.info("<-- " + results );
                        // Signal the sender to send one message.
                        requestStream.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                        done.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.info("All Done");
                        done.countDown();
                    }
                };
        // Note: clientResponseObserver is handling both request and response stream processing.
        asyncStub.streamOperations(clientResponseObserver);

        done.await();

        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    public CountDownLatch shapefile(File inFile) throws IOException {
        ///Users/davidraleigh/Downloads/landsat_8_c1/landsat_8_c1.shp
        //StreamOperations
        info("*** RouteChat");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        // https://github.com/grpc/grpc-java/issues/2563
        // https://github.com/grpc/grpc-java/blob/c90f27f454f59f15fcd1030be5af8c69b0aad42c/stub/src/main/java/io/grpc/stub/AbstractStub.java#L212
        // https://github.com/grpc/grpc-java/blob/c90f27f454f59f15fcd1030be5af8c69b0aad42c/stub/src/main/java/io/grpc/stub/AbstractStub.java#L222
        StreamObserver<OperatorRequest> requestStreamObserver = asyncStub
                .withMaxInboundMessageSize(2147483647)
                .withMaxOutboundMessageSize(2147483647)
                .streamOperations(new StreamObserver<OperatorResult>() {
            @Override
            public void onNext(OperatorResult operatorResult) {
                String resultString = operatorResult.getGeometry().getGeometryString(0);
                info("Got geometry \"{0}\"", resultString);
            }

            @Override
            public void onError(Throwable t) {
                warning("Geometry Operator Failed: {0}", Status.fromThrowable(t));
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished RouteChat");
                finishLatch.countDown();
            }
        });

        // Get projection
        String prfFile = inFile.getAbsolutePath().substring(0, inFile.getAbsolutePath().lastIndexOf('.')) + ".prj";
        String projectionWKT = new String(Files.readAllBytes(Paths.get(prfFile)));

        ServiceSpatialReference serviceSpatialReference = ServiceSpatialReference.newBuilder()
                .setEsriWkt(projectionWKT).build();

        ServiceSpatialReference wgs84SpatiralReference = ServiceSpatialReference.newBuilder()
                .setWkid(4326).build();

        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder()
                .setGeometryEncodingType(GeometryEncodingType.esri)
                .setSpatialReference(serviceSpatialReference);

        OperatorRequest.Builder operatorRequestBuilder = OperatorRequest.newBuilder()
                .setOperatorType(ServiceOperatorType.Buffer)
                .addBufferDistances(2.5)
                .setResultsEncodingType("wkt")
                .setResultSpatialReference(wgs84SpatiralReference);


        ShapefileByteReader shapefileByteReader = new ShapefileByteReader(inFile);
        try {
            while (shapefileByteReader.hasNext()) {
                byte[] data = shapefileByteReader.next();
                ByteString byteString = ByteString.copyFrom(data);
                serviceGeometryBuilder.addGeometryBinary(byteString);
                OperatorRequest operatorRequest = operatorRequestBuilder
                        .setLeftGeometry(serviceGeometryBuilder
                                .setGeometryBinary(0, byteString)
                                .build()).build();

                requestStreamObserver.onNext(operatorRequest);
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestStreamObserver.onError(e);
            throw e;
        }

        // Mark the end of requests
        requestStreamObserver.onCompleted();

        // return the latch while receiving happens asynchronously
        return finishLatch;
    }

    public void getProjected() {
        Polyline polyline = new Polyline();
        polyline.startPath( 500000,       0);
        polyline.lineTo(400000,  100000);
        polyline.lineTo(600000, -100000);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        ServiceSpatialReference inputSpatialReference = ServiceSpatialReference.newBuilder()
                .setWkid(32632)
                .build();

        ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder()
                .setGeometryEncodingType(GeometryEncodingType.wkb)
                .setSpatialReference(inputSpatialReference)
                .addGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        ServiceSpatialReference outputSpatialReference = ServiceSpatialReference.newBuilder()
                .setWkid(4326)
                .build();


        OperatorRequest serviceProjectOp = OperatorRequest
                .newBuilder()
                .setLeftGeometry(serviceGeometry)
                .setOperatorType(ServiceOperatorType.Project)
                .setOperationSpatialReference(outputSpatialReference)
                .build();

        System.out.println("executing request");
        OperatorResult operatorResult = blockingStub.executeOperation(serviceProjectOp);
        System.out.println("finished request");

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();

        Polyline result = (Polyline)op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryBinary(0).asReadOnlyByteBuffer(), null);
        System.out.println(GeometryEngine.geometryToWkt(result, 0));
    }


    /** Issues several different requests and then exits. */
    public static void main(String[] args) throws InterruptedException {
        GeometryOperatorsClient geometryOperatorsClient = null;
        String target = System.getenv("GEOMETRY_SERVICE_TARGET");
        if (target != null)
            geometryOperatorsClient = new GeometryOperatorsClient(target);
        else
            geometryOperatorsClient = new GeometryOperatorsClient(args[0], 8980);

        System.out.println("Starting main");
        try {
            // File file = new File("/data/Parcels/PARCELS.shp");
            File file = new File("/Users/davidraleigh/Downloads/Parcels/PARCELS.shp");

            long startTime = System.nanoTime();
            geometryOperatorsClient.shapefileThrottled(file);
//            CountDownLatch countDownLatch = geometryOperatorsClient.shapefile(file);
//            if (!countDownLatch.await(20, TimeUnit.MINUTES)) {
//                geometryOperatorsClient.warning("routeChat can not finish within 20 minutes");
//            }
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000;
            System.out.println("Test duration");
            System.out.println(duration);

//            geometryOperatorsClient.getProjected();
//            geometryOperatorsClient.getProjected();
//            geometryOperatorsClient.getProjected();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            geometryOperatorsClient.shutdown();
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }


    /**
     * Only used for unit test, as we do not want to introduce randomness in unit test.
     */
    @VisibleForTesting
    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Only used for helping unit test.
     */
    @VisibleForTesting
    interface TestHelper {
        /**
         * Used for verify/inspect message received from server.
         */
        void onMessage(Message message);

        /**
         * Used for verify/inspect error received from server.
         */
        void onRpcError(Throwable exception);
    }

    @VisibleForTesting
    void setTestHelper(TestHelper testHelper) {
        this.testHelper = testHelper;
    }
}
