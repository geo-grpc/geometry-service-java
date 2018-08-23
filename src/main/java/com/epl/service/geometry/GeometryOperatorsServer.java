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

import io.grpc.*;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Created by davidraleigh on 4/9/17.
 */
public class GeometryOperatorsServer {
    private static final Logger logger = Logger.getLogger(GeometryOperatorsServer.class.getName());

    private final int port;

    // io.grpc.Server
    private final Server server;

    private final LinkedList<ManagedChannel> fakeOobChannels = new LinkedList<ManagedChannel>();

    /**
     * Create a GeometryOperators server listening on {@code port} using {@code featureFile} database.
     */
    public GeometryOperatorsServer(int port) throws IOException {
        // changed max message size to match tensorflow
        // https://github.com/tensorflow/serving/issues/288
        // https://github.com/tensorflow/tensorflow/blob/d0d975f8c3330b5402263b2356b038bc8af919a2/tensorflow/core/platform/types.h#L52
        // TODO add a test to check data size can handle 2 gigs
        // maxInboundMessageSize
        // https://github.com/grpc/grpc-java/blob/master/SECURITY.md
        this(NettyServerBuilder
                .forPort(port)
                .maxMessageSize(2147483647), port);
    }

    /**
     * Create a GeometryOperators server using serverBuilder as a base and features as data.
     */
    public GeometryOperatorsServer(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;

        // try adding security
        String chainPath = System.getenv("GRPC_CHAIN");
        String keyPath = System.getenv("GRPC_KEY");
        if (chainPath != null || keyPath != null) {
            File chainFile = new File(chainPath);
            File keyFile = new File(keyPath);
            if (chainFile.exists() && !chainFile.isDirectory() &&
                    keyFile.exists() && !keyFile.isDirectory()) {
                serverBuilder.useTransportSecurity(chainFile, keyFile);
            }
        }

        server = serverBuilder.addService(new GeometryOperatorsService()).build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        logger.info("server name" + System.getenv("MY_NODE_NAME"));
        logger.info("server name" + System.getenv("MY_POD_NAME"));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                GeometryOperatorsServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        GeometryOperatorsServer server = new GeometryOperatorsServer(8980);
        server.start();
        server.blockUntilShutdown();
    }

    /**
     * Our implementation of GeometryOperators service.
     * <p>
     * <p>See route_guide.proto for details of the methods.
     */
    private static class GeometryOperatorsService extends GeometryOperatorsGrpc.GeometryOperatorsImplBase {


        @Override
        public StreamObserver<OperatorRequest> streamOperationsEx(StreamObserver<OperatorResult> responseObserver) {

            return new StreamObserver<OperatorRequest>() {
                @Override
                public void onNext(OperatorRequest value) {
                    try {
                        __executeOperator(value);
                    } catch (Throwable throwable) {
                        responseObserver.onError(Status.UNKNOWN.withDescription("Error handling request").withCause(throwable).asException());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.info("ERROR");
                    responseObserver.onCompleted();
                }

                @Override
                public void onCompleted() {
                    // Signal the end of work when the client ends the request stream.
                    logger.info("COMPLETED");
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public StreamObserver<OperatorRequest> streamOperations(StreamObserver<OperatorResult> responseObserver) {
            // Set up manual flow control for the request stream. It feels backwards to configure the request
            // stream's flow control using the response stream's observer, but this is the way it is.
            final ServerCallStreamObserver<OperatorResult> serverCallStreamObserver =
                    (ServerCallStreamObserver<OperatorResult>) responseObserver;
            serverCallStreamObserver.disableAutoInboundFlowControl();

            // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
            // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
            // request(1) would be called twice - once by onNext() and once by the onReady() scheduled during onNext()'s
            // execution.
            final AtomicBoolean wasReady = new AtomicBoolean(false);

            serverCallStreamObserver.setOnReadyHandler(() -> {
                if (serverCallStreamObserver.isReady() && wasReady.compareAndSet(false, true)) {
//                    logger.info("READY");
                    // Signal the request sender to send one message. This happens when isReady() turns true, signaling that
                    // the receive buffer has enough free space to receive more messages. Calling request() serves to prime
                    // the message pump.
                    serverCallStreamObserver.request(1);
                }
            });

            return new StreamObserver<OperatorRequest>() {
                @Override
                public void onNext(OperatorRequest value) {
                    // Process the request and send a response or an error.
                    try {
                        // Accept and enqueue the request.
                        responseObserver.onNext(__executeOperator(value));

                        // Check the provided ServerCallStreamObserver to see if it is still ready to accept more messages.
                        if (serverCallStreamObserver.isReady()) {
                            // Signal the sender to send another request. As long as isReady() stays true, the server will keep
                            // cycling through the loop of onNext() -> request()...onNext() -> request()... until either the client
                            // runs out of messages and ends the loop or the server runs out of receive buffer space.
                            //
                            // If the server runs out of buffer space, isReady() will turn false. When the receive buffer has
                            // sufficiently drained, isReady() will turn true, and the serverCallStreamObserver's onReadyHandler
                            // will be called to restart the message pump.
                            serverCallStreamObserver.request(1);
                        } else {
                            // If not, note that back-pressure has begun.
                            wasReady.set(false);
                        }
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                        responseObserver.onError(
                                Status.UNKNOWN.withDescription("Error handling request").withCause(throwable).asException());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // End the response stream if the client presents an error.
                    t.printStackTrace();
                    responseObserver.onCompleted();
                }

                @Override
                public void onCompleted() {
                    // Signal the end of work when the client ends the request stream.
                    logger.info("COMPLETED");
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void executeOperation(OperatorRequest request, StreamObserver<OperatorResult> responseObserver) {
            try {
                // logger.info("server name" + System.getenv("MY_NODE_NAME"));
                // System.out.println("Start process");
                responseObserver.onNext(__executeOperator(request));
                responseObserver.onCompleted();
                // System.out.println("End process");
            } catch (StatusRuntimeException sre) {
                logger.log(Level.WARNING, "executeOperation error : ".concat(sre.getMessage()));
                StatusRuntimeException s = new StatusRuntimeException(Status.fromThrowable(sre));
                responseObserver.onError(s.getStatus().withDescription("executeOperation error : ".concat(sre.getMessage())).asRuntimeException());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "executeOperation error : ".concat(t.toString()));
                StatusRuntimeException s = new StatusRuntimeException(Status.fromThrowable(t));
                responseObserver.onError(s.getStatus().withDescription("executeOperation error : ".concat(t.toString())).asRuntimeException());
//                responseObserver.onError(new StatusRuntimeException(Status.fromThrowable(t)));
            }
        }

        private OperatorResult __executeOperator(OperatorRequest serviceOperator) throws IOException {
            return GeometryOperatorsUtil.initExecuteOperatorEx(serviceOperator);
        }
    }
}


