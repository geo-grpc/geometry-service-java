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

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.grpc.*;

import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by davidraleigh on 4/9/17.
 */
public class GeometryOperatorsServer {
    private static final Logger logger = Logger.getLogger(GeometryOperatorsServer.class.getName());

    private final int port;

    // io.grpc.Server
    private final Server server;

    private final LinkedList<ManagedChannel> fakeOobChannels = new LinkedList<ManagedChannel>();

    public GeometryOperatorsServer(int port) throws IOException {
        this(port, GeometryOperatorsUtil.getDefaultFeaturesFile());
    }

    /** Create a GeometryOperators server listening on {@code port} using {@code featureFile} database. */
    public GeometryOperatorsServer(int port, URL featureFile) throws IOException {
        // changed max message size to match tensorflow
        // https://github.com/tensorflow/serving/issues/288
        // https://github.com/tensorflow/tensorflow/blob/d0d975f8c3330b5402263b2356b038bc8af919a2/tensorflow/core/platform/types.h#L52
        // TODO add a test to check data size can handle 2 gigs
        this(NettyServerBuilder.forPort(port).maxMessageSize(2147483647), port, GeometryOperatorsUtil.parseFeatures(featureFile));
    }

    /** Create a GeometryOperators server using serverBuilder as a base and features as data. */
    public GeometryOperatorsServer(ServerBuilder<?> serverBuilder, int port, Collection<Feature> features) {
        this.port = port;
        server = serverBuilder.addService(new GeometryOperatorsService(features)).build();
    }

    /** Start serving requests. */
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

    /** Stop serving requests and shutdown resources. */
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
     *
     * <p>See route_guide.proto for details of the methods.
     */
    private static class GeometryOperatorsService extends GeometryOperatorsGrpc.GeometryOperatorsImplBase {
        private final Collection<Feature> features;
        private final ConcurrentMap<ReplacePoint, List<RouteNote>> routeNotes =
                new ConcurrentHashMap<ReplacePoint, List<RouteNote>>();

        GeometryOperatorsService(Collection<Feature> features) {
            this.features = features;
        }

        /**
         * Gets the {@link Feature} at the requested {@link ReplacePoint}. If no feature at that location
         * exists, an unnamed feature is returned at the provided location.
         *
         * @param request the requested location for the feature.
         * @param responseObserver the observer that will receive the feature at the requested point.
         */
        @Override
        public void getFeature(ReplacePoint request, StreamObserver<Feature> responseObserver) {
            logger.info("server name" + System.getenv("MY_NODE_NAME"));
            logger.info("server name" + System.getenv("MY_POD_NAME"));
            logger.info("getfeature");
            responseObserver.onNext(checkFeature(request));
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<OperatorRequest> streamOperations(StreamObserver<OperatorResult> responseObserver) {
            return new StreamObserver<OperatorRequest>() {
                @Override
                public void onNext(OperatorRequest value) {
                    try {
                        responseObserver.onNext(__executeOperator(value));
                    } catch (java.io.IOException exception) {
                        logger.log(Level.WARNING, "streamOperations error", exception);
                        responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND.withCause(exception)));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "streamOperations error", t);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public void executeOperation(OperatorRequest request, StreamObserver<OperatorResult> responseObserver) {
            try {
                logger.info("server name" + System.getenv("MY_NODE_NAME"));
                System.out.println("Start process");
                responseObserver.onNext(__executeOperator(request));
                responseObserver.onCompleted();
                System.out.println("End process");
            } catch (IOException exception) {
                logger.log(Level.WARNING, "executeOperation error", exception);
                responseObserver.onError(new StatusRuntimeException(Status.NOT_FOUND.withCause(exception)));
            }
        }

        /**
         * Gets all features contained within the given bounding {@link Rectangle}.
         *
         * @param request the bounding rectangle for the requested features.
         * @param responseObserver the observer that will receive the features.
         */
        @Override
        public void listFeatures(Rectangle request, StreamObserver<Feature> responseObserver) {
            logger.info("server name" + System.getenv("MY_NODE_NAME"));
            logger.info("server name" + System.getenv("MY_POD_NAME"));
            logger.info("listFeatures");
            int left = min(request.getLo().getLongitude(), request.getHi().getLongitude());
            int right = max(request.getLo().getLongitude(), request.getHi().getLongitude());
            int top = max(request.getLo().getLatitude(), request.getHi().getLatitude());
            int bottom = min(request.getLo().getLatitude(), request.getHi().getLatitude());

            for (Feature feature : features) {
                if (!GeometryOperatorsUtil.exists(feature)) {
                    continue;
                }

                int lat = feature.getLocation().getLatitude();
                int lon = feature.getLocation().getLongitude();
                if (lon >= left && lon <= right && lat >= bottom && lat <= top) {
                    responseObserver.onNext(feature);
                }
            }
            responseObserver.onCompleted();
        }


        /**
         * Gets a stream of points, and responds with statistics about the "trip": number of points,
         * number of known features visited, total distance traveled, and total time spent.
         *
         * @param responseObserver an observer to receive the response summary.
         * @return an observer to receive the requested route points.
         */
        @Override
        public StreamObserver<ReplacePoint> recordRoute(final StreamObserver<RouteSummary> responseObserver) {
            return new StreamObserver<ReplacePoint>() {
                int pointCount;
                int featureCount;
                int distance;
                ReplacePoint previous;
                final long startTime = System.nanoTime();

                @Override
                public void onNext(ReplacePoint point) {
                    logger.info("server name" + System.getenv("MY_NODE_NAME"));
                    logger.info("server name" + System.getenv("MY_POD_NAME"));
                    logger.info("recordRoute.onNext");
                    pointCount++;
                    if (GeometryOperatorsUtil.exists(checkFeature(point))) {
                        featureCount++;
                    }
                    // For each point after the first, add the incremental distance from the previous point to
                    // the total distance value.
                    if (previous != null) {
                        distance += calcDistance(previous, point);
                    }
                    previous = point;
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "recordRoute cancelled");
                }

                @Override
                public void onCompleted() {
                    long seconds = NANOSECONDS.toSeconds(System.nanoTime() - startTime);
                    responseObserver.onNext(RouteSummary.newBuilder().setPointCount(pointCount)
                            .setFeatureCount(featureCount).setDistance(distance)
                            .setElapsedTime((int) seconds).build());
                    responseObserver.onCompleted();
                }
            };
        }

        /**
         * Receives a stream of message/location pairs, and responds with a stream of all previous
         * messages at each of those locations.
         *
         * @param responseObserver an observer to receive the stream of previous messages.
         * @return an observer to handle requested message/location pairs.
         */
        @Override
        public StreamObserver<RouteNote> routeChat(final StreamObserver<RouteNote> responseObserver) {
            return new StreamObserver<RouteNote>() {
                @Override
                public void onNext(RouteNote note) {
                    logger.info("server name" + System.getenv("MY_NODE_NAME"));
                    logger.info("server name" + System.getenv("MY_POD_NAME"));
                    logger.info("routeChat.onNext");
                    List<RouteNote> notes = getOrCreateNotes(note.getLocation());

                    // Respond with all previous notes at this location.
                    for (RouteNote prevNote : notes.toArray(new RouteNote[0])) {
                        responseObserver.onNext(prevNote);
                    }

                    // Now add the new note to the list
                    notes.add(note);
                }

                @Override
                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "routeChat cancelled");
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }



        /**
         * Get the notes list for the given location. If missing, create it.
         */
        private List<RouteNote> getOrCreateNotes(ReplacePoint location) {
            List<RouteNote> notes = Collections.synchronizedList(new ArrayList<RouteNote>());
            List<RouteNote> prevNotes = routeNotes.putIfAbsent(location, notes);
            return prevNotes != null ? prevNotes : notes;
        }


        private OperatorResult __executeOperator(OperatorRequest serviceOperator) throws IOException {
            return GeometryOperatorsUtil.executeOperator(serviceOperator);
        }



        /**
         * Gets the feature at the given point.
         *
         * @param location the location to check.
         * @return The feature object at the point. Note that an empty name indicates no feature.
         */
        private Feature checkFeature(ReplacePoint location) {
            for (Feature feature : features) {
                if (feature.getLocation().getLatitude() == location.getLatitude()
                        && feature.getLocation().getLongitude() == location.getLongitude()) {
                    return feature;
                }
            }

            // No feature was found, return an unnamed feature.
            return Feature.newBuilder().setName("").setLocation(location).build();
        }

        /**
         * Calculate the distance between two points using the "haversine" formula.
         * This code was taken from http://www.movable-type.co.uk/scripts/latlong.html.
         *
         * @param start The starting point
         * @param end The end point
         * @return The distance between the points in meters
         */
        private static int calcDistance(ReplacePoint start, ReplacePoint end) {
            double lat1 = GeometryOperatorsUtil.getLatitude(start);
            double lat2 = GeometryOperatorsUtil.getLatitude(end);
            double lon1 = GeometryOperatorsUtil.getLongitude(start);
            double lon2 = GeometryOperatorsUtil.getLongitude(end);



            int r = 6371000; // meters
            double phi1 = toRadians(lat1);
            double phi2 = toRadians(lat2);
            double deltaPhi = toRadians(lat2 - lat1);
            double deltaLambda = toRadians(lon2 - lon1);

            double a = sin(deltaPhi / 2) * sin(deltaPhi / 2)
                    + cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2);
            double c = 2 * atan2(sqrt(a), sqrt(1 - a));

            return (int) (r * c);
        }
    }

}


