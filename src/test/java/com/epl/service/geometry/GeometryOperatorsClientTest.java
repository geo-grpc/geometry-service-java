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

import com.epl.service.geometry.GeometryOperatorsClient.TestHelper;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Random;

import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GeometryOperatorsClient}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 */
@RunWith(JUnit4.class)
public class GeometryOperatorsClientTest {
    private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
    private final TestHelper testHelper = mock(TestHelper.class);
    private final Random noRandomness =
            new Random() {
                int index;
                boolean isForSleep;

                /**
                 * Returns a number deterministically. If the random number is for sleep time, then return
                 * -500 so that {@code Thread.sleep(random.nextInt(1000) + 500)} sleeps 0 ms. Otherwise, it
                 * is for list index, then return incrementally (and cyclically).
                 */
                @Override
                public int nextInt(int bound) {
                    int retVal = isForSleep ? -500 : (index++ % bound);
                    isForSleep = !isForSleep;
                    return retVal;
                }
            };
    private Server fakeServer;
    private GeometryOperatorsClient client;

    @Before
    public void setUp() throws Exception {
        String uniqueServerName = "fake server for " + getClass();

        // use a mutable service registry for later registering the service impl for each test case.
        fakeServer = InProcessServerBuilder.forName(uniqueServerName)
                .fallbackHandlerRegistry(serviceRegistry).directExecutor().build().start();
        client =
                new GeometryOperatorsClient(InProcessChannelBuilder.forName(uniqueServerName).directExecutor());
        client.setTestHelper(testHelper);
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        fakeServer.shutdownNow();
    }

    /**
     * Example for testing blocking unary call.
     */
    @Test
    public void getFeature() {
//    ReplacePoint requestPoint =  ReplacePoint.newBuilder().setLatitude(-1).setLongitude(-1).build();
//    ReplacePoint responsePoint = ReplacePoint.newBuilder().setLatitude(-123).setLongitude(-123).build();
//    final AtomicReference<ReplacePoint> pointDelivered = new AtomicReference<ReplacePoint>();
//    final Feature responseFeature =
//        Feature.newBuilder().setName("dummyFeature").setLocation(responsePoint).build();
//
//    // implement the fake service
//    GeometryOperatorsImplBase getFeatureImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public void getFeature(ReplacePoint point, StreamObserver<Feature> responseObserver) {
//            pointDelivered.set(point);
//            responseObserver.onNext(responseFeature);
//            responseObserver.onCompleted();
//          }
//        };
//    serviceRegistry.addService(getFeatureImpl);
//
//    client.getFeature(-1, -1);
//
//    assertEquals(requestPoint, pointDelivered.get());
//    verify(testHelper).onMessage(responseFeature);
//    verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing blocking unary call.
     */
    @Test
    public void getFeature_error() {
//    ReplacePoint requestPoint =  ReplacePoint.newBuilder().setLatitude(-1).setLongitude(-1).build();
//    final AtomicReference<ReplacePoint> pointDelivered = new AtomicReference<ReplacePoint>();
//    final StatusRuntimeException fakeError = new StatusRuntimeException(Status.DATA_LOSS);
//
//    // implement the fake service
//    GeometryOperatorsImplBase getFeatureImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public void getFeature(ReplacePoint point, StreamObserver<Feature> responseObserver) {
//            pointDelivered.set(point);
//            responseObserver.onError(fakeError);
//          }
//        };
//    serviceRegistry.addService(getFeatureImpl);
//
//    client.getFeature(-1, -1);
//
//    assertEquals(requestPoint, pointDelivered.get());
//    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
//    verify(testHelper).onRpcError(errorCaptor.capture());
//    assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing blocking server-streaming.
     */
    @Test
    public void listFeatures() {
//    final Feature responseFeature1 = Feature.newBuilder().setName("feature 1").build();
//    final Feature responseFeature2 = Feature.newBuilder().setName("feature 2").build();
//    final AtomicReference<Rectangle> rectangleDelivered = new AtomicReference<Rectangle>();
//
//    // implement the fake service
//    GeometryOperatorsImplBase listFeaturesImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public void listFeatures(Rectangle rectangle, StreamObserver<Feature> responseObserver) {
//            rectangleDelivered.set(rectangle);
//
//            // send two response messages
//            responseObserver.onNext(responseFeature1);
//            responseObserver.onNext(responseFeature2);
//
//            // complete the response
//            responseObserver.onCompleted();
//          }
//        };
//    serviceRegistry.addService(listFeaturesImpl);
//
//    client.listFeatures(1, 2, 3, 4);
//
//    Assert.assertEquals(Rectangle.newBuilder()
//                     .setLo(ReplacePoint.newBuilder().setLatitude(1).setLongitude(2).build())
//                     .setHi(ReplacePoint.newBuilder().setLatitude(3).setLongitude(4).build())
//                     .build(),
//                 rectangleDelivered.get());
//    verify(testHelper).onMessage(responseFeature1);
//    verify(testHelper).onMessage(responseFeature2);
//    verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing blocking server-streaming.
     */
    @Test
    public void listFeatures_error() {
//    final Feature responseFeature1 =
//        Feature.newBuilder().setName("feature 1").build();
//    final AtomicReference<Rectangle> rectangleDelivered = new AtomicReference<Rectangle>();
//    final StatusRuntimeException fakeError = new StatusRuntimeException(Status.INVALID_ARGUMENT);
//
//    // implement the fake service
//    GeometryOperatorsImplBase listFeaturesImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public void listFeatures(Rectangle rectangle, StreamObserver<Feature> responseObserver) {
//            rectangleDelivered.set(rectangle);
//
//            // send one response message
//            responseObserver.onNext(responseFeature1);
//
//            // let the rpc fail
//            responseObserver.onError(fakeError);
//          }
//        };
//    serviceRegistry.addService(listFeaturesImpl);
//
//    client.listFeatures(1, 2, 3, 4);
//
//    Assert.assertEquals(Rectangle.newBuilder()
//                     .setLo(ReplacePoint.newBuilder().setLatitude(1).setLongitude(2).build())
//                     .setHi(ReplacePoint.newBuilder().setLatitude(3).setLongitude(4).build())
//                     .build(),
//                 rectangleDelivered.get());
//    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
//    verify(testHelper).onMessage(responseFeature1);
//    verify(testHelper).onRpcError(errorCaptor.capture());
//    assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing async client-streaming.
     */
    @Test
    public void recordRoute() throws Exception {
//    client.setRandom(noRandomness);
//    ReplacePoint point1 = ReplacePoint.newBuilder().setLatitude(1).setLongitude(1).build();
//    ReplacePoint point2 = ReplacePoint.newBuilder().setLatitude(2).setLongitude(2).build();
//    ReplacePoint point3 = ReplacePoint.newBuilder().setLatitude(3).setLongitude(3).build();
//    Feature requestFeature1 =
//        Feature.newBuilder().setLocation(point1).build();
//    Feature requestFeature2 =
//        Feature.newBuilder().setLocation(point2).build();
//    Feature requestFeature3 =
//        Feature.newBuilder().setLocation(point3).build();
//    final List<Feature> features = Arrays.asList(
//        requestFeature1, requestFeature2, requestFeature3);
//    final List<ReplacePoint> pointsDelivered = new ArrayList<ReplacePoint>();
//    final RouteSummary fakeResponse = RouteSummary
//        .newBuilder()
//        .setPointCount(7)
//        .setFeatureCount(8)
//        .setDistance(9)
//        .setElapsedTime(10)
//        .build();
//
//    // implement the fake service
//    GeometryOperatorsImplBase recordRouteImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<ReplacePoint> recordRoute(
//              final StreamObserver<RouteSummary> responseObserver) {
//            StreamObserver<ReplacePoint> requestObserver = new StreamObserver<ReplacePoint>() {
//              @Override
//              public void onNext(ReplacePoint value) {
//                pointsDelivered.add(value);
//              }
//
//              @Override
//              public void onError(Throwable t) {
//              }
//
//              @Override
//              public void onCompleted() {
//                responseObserver.onNext(fakeResponse);
//                responseObserver.onCompleted();
//              }
//            };
//
//            return requestObserver;
//          }
//        };
//    serviceRegistry.addService(recordRouteImpl);
//
//    // send requestFeature1, requestFeature2, requestFeature3, and then requestFeature1 again
//    client.recordRoute(features, 4);
//
//    assertEquals(
//        Arrays.asList(
//            requestFeature1.getLocation(),
//            requestFeature2.getLocation(),
//            requestFeature3.getLocation(),
//            requestFeature1.getLocation()),
//        pointsDelivered);
//    verify(testHelper).onMessage(fakeResponse);
//    verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing async client-streaming.
     */
    @Test
    @Ignore
    public void recordRoute_wrongResponse() throws Exception {
//    client.setRandom(noRandomness);
//    ReplacePoint point1 = ReplacePoint.newBuilder().setLatitude(1).setLongitude(1).build();
//    final Feature requestFeature1 =
//        Feature.newBuilder().setLocation(point1).build();
//    final List<Feature> features = Arrays.asList(requestFeature1);
//
//    // implement the fake service
//    GeometryOperatorsImplBase recordRouteImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<ReplacePoint> recordRoute(StreamObserver<RouteSummary> responseObserver) {
//            RouteSummary response = RouteSummary.getDefaultInstance();
//            // sending more than one responses is not rightSR for client-streaming call.
//            responseObserver.onNext(response);
//            responseObserver.onNext(response);
//            responseObserver.onCompleted();
//
//            return new StreamObserver<ReplacePoint>() {
//              @Override
//              public void onNext(ReplacePoint value) {
//              }
//
//              @Override
//              public void onError(Throwable t) {
//              }
//
//              @Override
//              public void onCompleted() {
//              }
//            };
//          }
//        };
//    serviceRegistry.addService(recordRouteImpl);
//
//    client.recordRoute(features, 4);
//
//    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
//    verify(testHelper).onRpcError(errorCaptor.capture());
//    assertEquals(Status.Code.CANCELLED, Status.fromThrowable(errorCaptor.getValue()).getCode());
    }

    /**
     * Example for testing async client-streaming.
     */
    @Test
    public void recordRoute_serverError() throws Exception {
//    client.setRandom(noRandomness);
//    ReplacePoint point1 = ReplacePoint.newBuilder().setLatitude(1).setLongitude(1).build();
//    final Feature requestFeature1 =
//        Feature.newBuilder().setLocation(point1).build();
//    final List<Feature> features = Arrays.asList(requestFeature1);
//    final StatusRuntimeException fakeError = new StatusRuntimeException(Status.INVALID_ARGUMENT);
//
//    // implement the fake service
//    GeometryOperatorsImplBase recordRouteImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<ReplacePoint> recordRoute(StreamObserver<RouteSummary> responseObserver) {
//            // send an error immediately
//            responseObserver.onError(fakeError);
//
//            StreamObserver<ReplacePoint> requestObserver = new StreamObserver<ReplacePoint>() {
//              @Override
//              public void onNext(ReplacePoint value) {
//              }
//
//              @Override
//              public void onError(Throwable t) {
//              }
//
//              @Override
//              public void onCompleted() {
//              }
//            };
//            return requestObserver;
//          }
//        };
//    serviceRegistry.addService(recordRouteImpl);
//
//    client.recordRoute(features, 4);
//
//    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
//    verify(testHelper).onRpcError(errorCaptor.capture());
//    assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_simpleResponse() throws Exception {
//    RouteNote fakeResponse1 = RouteNote.newBuilder().setMessage("dummy msg1").build();
//    RouteNote fakeResponse2 = RouteNote.newBuilder().setMessage("dummy msg2").build();
//    final List<String> messagesDelivered = new ArrayList<String>();
//    final List<ReplacePoint> locationsDelivered = new ArrayList<ReplacePoint>();
//    final AtomicReference<StreamObserver<RouteNote>> responseObserverRef =
//        new AtomicReference<StreamObserver<RouteNote>>();
//    final CountDownLatch allRequestsDelivered = new CountDownLatch(1);
//    // implement the fake service
//    GeometryOperatorsImplBase routeChatImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<RouteNote> routeChat(StreamObserver<RouteNote> responseObserver) {
//            responseObserverRef.set(responseObserver);
//
//            StreamObserver<RouteNote> requestObserver = new StreamObserver<RouteNote>() {
//              @Override
//              public void onNext(RouteNote value) {
//                messagesDelivered.add(value.getMessage());
//                locationsDelivered.add(value.getLocation());
//              }
//
//              @Override
//              public void onError(Throwable t) {
//              }
//
//              @Override
//              public void onCompleted() {
//                allRequestsDelivered.countDown();
//              }
//            };
//
//            return requestObserver;
//          }
//        };
//    serviceRegistry.addService(routeChatImpl);
//
//    // start routeChat
//    CountDownLatch latch = client.routeChat();
//
//    // request message sent and delivered for four times
//    assertTrue(allRequestsDelivered.await(1, TimeUnit.SECONDS));
//    assertEquals(
//        Arrays.asList("First message", "Second message", "Third message", "Fourth message"),
//        messagesDelivered);
//    assertEquals(
//        Arrays.asList(
//            ReplacePoint.newBuilder().setLatitude(0).setLongitude(0).build(),
//            ReplacePoint.newBuilder().setLatitude(0).setLongitude(1).build(),
//            ReplacePoint.newBuilder().setLatitude(1).setLongitude(0).build(),
//            ReplacePoint.newBuilder().setLatitude(1).setLongitude(1).build()
//        ),
//        locationsDelivered);
//
//    // Let the server send out two simple response messages
//    // and verify that the client receives them.
//    // Allow some timeout for verify() if not using directExecutor
//    responseObserverRef.get().onNext(fakeResponse1);
//    verify(testHelper).onMessage(fakeResponse1);
//    responseObserverRef.get().onNext(fakeResponse2);
//    verify(testHelper).onMessage(fakeResponse2);
//
//    // let server complete.
//    responseObserverRef.get().onCompleted();
//
//    assertTrue(latch.await(1, TimeUnit.SECONDS));
//    verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_echoResponse() throws Exception {
//    final List<RouteNote> notesDelivered = new ArrayList<RouteNote>();
//
//    // implement the fake service
//    GeometryOperatorsImplBase routeChatImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<RouteNote> routeChat(
//              final StreamObserver<RouteNote> responseObserver) {
//            StreamObserver<RouteNote> requestObserver = new StreamObserver<RouteNote>() {
//              @Override
//              public void onNext(RouteNote value) {
//                notesDelivered.add(value);
//                responseObserver.onNext(value);
//              }
//
//              @Override
//              public void onError(Throwable t) {
//                responseObserver.onError(t);
//              }
//
//              @Override
//              public void onCompleted() {
//                responseObserver.onCompleted();
//              }
//            };
//
//            return requestObserver;
//          }
//        };
//    serviceRegistry.addService(routeChatImpl);
//
//    client.routeChat().await(1, TimeUnit.SECONDS);
//
//    String[] messages =
//        {"First message", "Second message", "Third message", "Fourth message"};
//    for (int i = 0; i < 4; i++) {
//      verify(testHelper).onMessage(notesDelivered.get(i));
//      assertEquals(messages[i], notesDelivered.get(i).getMessage());
//    }
//
//    verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_errorResponse() throws Exception {
//    final List<RouteNote> notesDelivered = new ArrayList<RouteNote>();
//    final StatusRuntimeException fakeError = new StatusRuntimeException(Status.PERMISSION_DENIED);
//
//    // implement the fake service
//    GeometryOperatorsImplBase routeChatImpl =
//        new GeometryOperatorsImplBase() {
//          @Override
//          public StreamObserver<RouteNote> routeChat(
//              final StreamObserver<RouteNote> responseObserver) {
//            StreamObserver<RouteNote> requestObserver = new StreamObserver<RouteNote>() {
//              @Override
//              public void onNext(RouteNote value) {
//                notesDelivered.add(value);
//                responseObserver.onError(fakeError);
//              }
//
//              @Override
//              public void onError(Throwable t) {
//              }
//
//              @Override
//              public void onCompleted() {
//                responseObserver.onCompleted();
//              }
//            };
//
//            return requestObserver;
//          }
//        };
//    serviceRegistry.addService(routeChatImpl);
//
//    client.routeChat().await(1, TimeUnit.SECONDS);
//
//    assertEquals("First message", notesDelivered.get(0).getMessage());
//    verify(testHelper, never()).onMessage(any(Message.class));
//    ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
//    verify(testHelper).onRpcError(errorCaptor.capture());
//    assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }
}
