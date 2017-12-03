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

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.esri.core.geometry.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import java.io.FileReader;
import java.util.stream.Collectors;


/**
 * Unit tests for {@link GeometryOperatorsServer}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 */
@RunWith(JUnit4.class)
public class GeometryOperatorsServerTest {
  private GeometryOperatorsServer server;
  private ManagedChannel inProcessChannel;
  private Collection<Feature> features;

  @Before
  public void setUp() throws Exception {
    String uniqueServerName = "in-process server for " + getClass();
    features = new ArrayList<Feature>();
    // use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
    // usage timeouts and latches in test. But we still add timeout and latches where they would be
    // needed if no directExecutor were used, just for demo purpose.
    server = new GeometryOperatorsServer(
        InProcessServerBuilder.forName(uniqueServerName).directExecutor(), 0, features);
    server.start();
    inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
  }

  @After
  public void tearDown() throws Exception {
    inProcessChannel.shutdownNow();
    server.stop();
  }

  @Test
  public void getFeature() {
    ReplacePoint point = ReplacePoint.newBuilder().setLongitude(1).setLatitude(1).build();
    Feature unnamedFeature = Feature.newBuilder()
        .setName("").setLocation(point).build();
    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);

    // feature not found in the server
    Feature feature = stub.getFeature(point);

    assertEquals(unnamedFeature, feature);

    // feature found in the server
    Feature namedFeature = Feature.newBuilder()
        .setName("name").setLocation(point).build();
    features.add(namedFeature);

    feature = stub.getFeature(point);

    assertEquals(namedFeature, feature);
  }

  @Test
  public void getWKTGeometry() {
    Polyline polyline = new Polyline();
    polyline.startPath(0,0);
    polyline.lineTo(2, 3);
    polyline.lineTo(3, 3);
    OperatorExportToWkt op = OperatorExportToWkt.local();
    String geom = op.execute(0, polyline, null);
    ServiceGeometry serviceGeom = ServiceGeometry.newBuilder().addGeometryString(geom).setGeometryEncodingType(GeometryEncodingType.wkt).build();
    OperatorRequest requestOp = OperatorRequest.newBuilder()
            .setLeftGeometry(serviceGeom)
            .setOperatorType(ServiceOperatorType.ExportToWkt)
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(requestOp);

    assertEquals(operatorResult.getGeometry().getGeometryString(0), serviceGeom.getGeometryString(0));
  }

  @Test
  public void getWKTGeometryFromWKB() {
    Polyline polyline = new Polyline();
    polyline.startPath(0,0);
    polyline.lineTo(2, 3);
    polyline.lineTo(3, 3);
    OperatorExportToWkb op = OperatorExportToWkb.local();


    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType(GeometryEncodingType.wkb).addGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest requestOp = OperatorRequest.newBuilder()
            .setLeftGeometry(serviceGeometry )
            .setOperatorType(ServiceOperatorType.ExportToWkt)
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(requestOp);

    OperatorExportToWkt op2 = OperatorExportToWkt.local();
    String geom = op2.execute(0, polyline, null);
    assertEquals(operatorResult.getGeometry().getGeometryString(0), geom);
  }

  @Test
  public void getConvexHullGeometryFromWKB() {
    Polyline polyline = new Polyline();
    polyline.startPath(-200, -90);
    polyline.lineTo(-180, -85);
    polyline.lineTo(-90, -70);
    polyline.lineTo(0, 0);
    polyline.lineTo(100, 25);
    polyline.lineTo(170, 45);
    polyline.lineTo(225, 65);
    OperatorExportToWkb op = OperatorExportToWkb.local();
    //TODO why does esri shape fail
//    OperatorExportToESRIShape op = OperatorExportToESRIShape.local();
//    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType("esrishape").setGeometryBinary(ByteString.copyFrom(op.execute(0, polyline))).build();
    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType(GeometryEncodingType.wkb).addGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest serviceOp = OperatorRequest
            .newBuilder()
            .setLeftGeometry(serviceGeometry)
            .setOperatorType(ServiceOperatorType.ConvexHull)
            .build();

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(serviceOp);

    OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
    Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryBinary(0).asReadOnlyByteBuffer(), null);

    boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

    assertTrue(bContains);
  }

  @Test
  public void testProjection() {
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

    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(serviceProjectOp);

    OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
    Polyline result = (Polyline)op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryBinary(0).asReadOnlyByteBuffer(), null);
    TestCase.assertNotNull(result);

    TestCase.assertFalse(polyline.equals(result));
    assertEquals(polyline.getPointCount(), result.getPointCount());
//    projectionTransformation = new ProjectionTransformation(SpatialReference.create(4326), SpatialReference.create(32632));
//    Polyline originalPolyline = (Polyline)OperatorProject.local().execute(polylineOut, projectionTransformation, null);
//
//    for (int i = 0; i < polyline.getPointCount(); i++) {
//      assertEquals(polyline.getPoint(i).getX(), originalPolyline.getPoint(i).getX(), 1e-10);
//      assertEquals(polyline.getPoint(i).getY(), originalPolyline.getPoint(i).getY(), 1e-10);
//    }
  }

  @Test
  public void testChainingBufferConvexHull() {
    Polyline polyline = new Polyline();
    polyline.startPath(0, 0);
    polyline.lineTo(2, 3);
    polyline.lineTo(3, 3);
    // TODO inspect bug where it crosses dateline
//    polyline.startPath(-200, -90);
//    polyline.lineTo(-180, -85);
//    polyline.lineTo(-90, -70);
//    polyline.lineTo(0, 0);
//    polyline.lineTo(100, 25);
//    polyline.lineTo(170, 45);
//    polyline.lineTo(225, 64);
    OperatorExportToWkb op = OperatorExportToWkb.local();
    //TODO why does esri shape fail
    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType(GeometryEncodingType.wkb).addGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
    OperatorRequest serviceConvexOp = OperatorRequest
            .newBuilder()
            .setLeftGeometry(serviceGeometry)
            .setOperatorType(ServiceOperatorType.ConvexHull)
            .build();

    OperatorRequest serviceOp = OperatorRequest.newBuilder()
            .setLeftCursor(serviceConvexOp)
            .addBufferDistances(1)
            .setOperatorType(ServiceOperatorType.Buffer)
            .build();


    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(serviceOp);

    OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
    Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryBinary(0).asReadOnlyByteBuffer(), null);

    boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

    assertTrue(bContains);
  }

  static double randomWithRange(double min, double max)
  {
    double range = Math.abs(max - min);
    return (Math.random() * range) + (min <= max ? min : max);
  }

  @Test
  public void testUnion() {
    int size = 1000;
    List<String> points = new ArrayList<>(size);
    List<Point> pointList = new ArrayList<>(size);
    for (int i = 0; i < size; i++){
      double x = randomWithRange(-20, 20);
      double y = randomWithRange(-20, 20);
      points.add(String.format("Point(%f %f)", x, y));
      pointList.add(new Point(x, y));
    }
    ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().addAllGeometryString(points).setGeometryEncodingType(GeometryEncodingType.wkt).build();
    OperatorRequest serviceBufferOp = OperatorRequest.newBuilder().setLeftGeometry(serviceGeometry).setOperatorType(ServiceOperatorType.Buffer).addBufferDistances(2.5).setBufferUnionResult(true).build();
    GeometryOperatorsGrpc.GeometryOperatorsBlockingStub stub = GeometryOperatorsGrpc.newBlockingStub(inProcessChannel);
    OperatorResult operatorResult = stub.executeOperation(serviceBufferOp);

    List<ByteBuffer> byteBufferList = operatorResult.getGeometry().getGeometryBinaryList().stream().map(com.google.protobuf.ByteString::asReadOnlyByteBuffer).collect(Collectors.toList());
    SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferList);
    OperatorImportFromWkbCursor operatorImportFromWkbCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
    Geometry result = OperatorImportFromWkb.local().execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryBinary(0).asReadOnlyByteBuffer(), null);
    assertTrue(result.calculateArea2D() > (Math.PI * 2.5 * 2.5 * 2));

//    assertEquals(result.calculateArea2D(), Math.PI * 2.5 * 2.5, 0.1);
//    shape_start = datetime.datetime.now()
//    spots = [p.buffer(2.5) for p in points]
//    patches = cascaded_union(spots)
//    shape_end = datetime.datetime.now()
//    shape_delta = shape_end - shape_start
//    shape_microseconds = int(shape_delta.total_seconds() * 1000)
//
//    stub = geometry_grpc.GeometryOperatorsStub(self.channel)
//    serviceGeom = ServiceGeometry()
//
//    epl_start = datetime.datetime.now()
//    serviceGeom.geometry_binary.extend([s.wkb for s in spots])
//    serviceGeom.geometry_encoding_type = GeometryEncodingType.Value('wkb')
//
//        # opRequestBuffer = OperatorRequest(left_geometry=serviceGeom,
//            #                                   operator_type=ServiceOperatorType.Value('Buffer'),
//            #                                   buffer_distances=[2.5])
//
//    opRequestUnion = OperatorRequest(left_geometry=serviceGeom,
//            operator_type=ServiceOperatorType.Value('Union'))
//
//    response = stub.ExecuteOperation(opRequestUnion)
//    unioned_result = wkbloads(response.geometry.geometry_binary[0])
//    epl_end = datetime.datetime.now()
//    epl_delta = epl_end - epl_start
//    epl_microseconds = int(epl_delta.total_seconds() * 1000)
//    self.assertGreater(shape_microseconds, epl_microseconds)
//    self.assertGreater(shape_microseconds / 8, epl_microseconds)
//
//    self.assertAlmostEqual(patches.area, unioned_result.area, 4)
  }



    @Ignore @Test
    public void testRicksSA() {
      try {
        OperatorImportFromGeoJson op = (OperatorImportFromGeoJson) OperatorFactoryLocal.getInstance().getOperator(Operator.Type.ImportFromGeoJson);

        InputStreamReader isr = new FileReader("/Users/davidraleigh/data/descartes/crops/shapes_v1c.json");
        JSONObject geoJsonObject = new JSONObject(isr);
        Iterator<String> iter = geoJsonObject.keys();
        List<Geometry> geometryList = new ArrayList<Geometry>();
        OperatorSimplify operatorSimplify = (OperatorSimplify.local());
        SpatialReference sr = SpatialReference.create(4326);
        while (iter.hasNext())
        {
          JSONObject jsonObject = geoJsonObject.getJSONObject(iter.next());
          MapGeometry mg = op.execute(0, Geometry.Type.Unknown, jsonObject.toString(), null);
          Geometry mgSimple = operatorSimplify.execute(mg.getGeometry(), sr, true, null);
          geometryList.add(mgSimple);
        }
        SimpleGeometryCursor sgc = new SimpleGeometryCursor(geometryList);
        OperatorUnion union = (OperatorUnion) OperatorFactoryLocal.getInstance().getOperator(Operator.Type.Union);

        GeometryCursor outputCursor = union.execute(sgc, sr, null);
        Geometry result = outputCursor.next();
        OperatorExportToGeoJson operatorExportToGeoJson = OperatorExportToGeoJson.local();

        Geometry resSimple = operatorSimplify.execute(result, sr, true, null);

        String s = operatorExportToGeoJson.execute(resSimple);
        int a = 0;
      } catch (Exception e) {
        assertNull(e);
      }
  }

  @Test
  public void listFeatures() throws Exception {
    // setup
    Rectangle rect = Rectangle.newBuilder()
        .setLo(ReplacePoint.newBuilder().setLongitude(0).setLatitude(0).build())
        .setHi(ReplacePoint.newBuilder().setLongitude(10).setLatitude(10).build())
        .build();
    Feature f1 = Feature.newBuilder()
        .setLocation(ReplacePoint.newBuilder().setLongitude(-1).setLatitude(-1).build())
        .setName("f1")
        .build(); // not inside rect
    Feature f2 = Feature.newBuilder()
        .setLocation(ReplacePoint.newBuilder().setLongitude(2).setLatitude(2).build())
        .setName("f2")
        .build();
    Feature f3 = Feature.newBuilder()
        .setLocation(ReplacePoint.newBuilder().setLongitude(3).setLatitude(3).build())
        .setName("f3")
        .build();
    Feature f4 = Feature.newBuilder()
        .setLocation(ReplacePoint.newBuilder().setLongitude(4).setLatitude(4).build())
        .build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);
    final Collection<Feature> result = new HashSet<Feature>();
    final CountDownLatch latch = new CountDownLatch(1);
    StreamObserver<Feature> responseObserver =
        new StreamObserver<Feature>() {
          @Override
          public void onNext(Feature value) {
            result.add(value);
          }

          @Override
          public void onError(Throwable t) {
            fail();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        };
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);

    // run
    stub.listFeatures(rect, responseObserver);
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // verify
    assertEquals(new HashSet<Feature>(Arrays.asList(f2, f3)), result);
  }

  @Test
  public void recordRoute() {
    ReplacePoint p1 = ReplacePoint.newBuilder().setLongitude(1000).setLatitude(1000).build();
    ReplacePoint p2 = ReplacePoint.newBuilder().setLongitude(2000).setLatitude(2000).build();
    ReplacePoint p3 = ReplacePoint.newBuilder().setLongitude(3000).setLatitude(3000).build();
    ReplacePoint p4 = ReplacePoint.newBuilder().setLongitude(4000).setLatitude(4000).build();
    Feature f1 = Feature.newBuilder().setLocation(p1).build(); // unamed
    Feature f2 = Feature.newBuilder().setLocation(p2).setName("f2").build();
    Feature f3 = Feature.newBuilder().setLocation(p3).setName("f3").build();
    Feature f4 = Feature.newBuilder().setLocation(p4).build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);

    @SuppressWarnings("unchecked")
    StreamObserver<RouteSummary> responseObserver =
        (StreamObserver<RouteSummary>) mock(StreamObserver.class);
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);
    ArgumentCaptor<RouteSummary> routeSummaryCaptor = ArgumentCaptor.forClass(RouteSummary.class);

    StreamObserver<ReplacePoint> requestObserver = stub.recordRoute(responseObserver);

    requestObserver.onNext(p1);
    requestObserver.onNext(p2);
    requestObserver.onNext(p3);
    requestObserver.onNext(p4);

    verify(responseObserver, never()).onNext(any(RouteSummary.class));

    requestObserver.onCompleted();

    // allow some ms to let client receive the response. Similar usage later on.
    verify(responseObserver, timeout(100)).onNext(routeSummaryCaptor.capture());
    RouteSummary summary = routeSummaryCaptor.getValue();
    assertEquals(45, summary.getDistance()); // 45 is the hard coded distance from p1 to p4.
    assertEquals(2, summary.getFeatureCount());
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }

  @Test
  public void routeChat() {
    ReplacePoint p1 = ReplacePoint.newBuilder().setLongitude(1).setLatitude(1).build();
    ReplacePoint p2 = ReplacePoint.newBuilder().setLongitude(2).setLatitude(2).build();
    RouteNote n1 = RouteNote.newBuilder().setLocation(p1).setMessage("m1").build();
    RouteNote n2 = RouteNote.newBuilder().setLocation(p2).setMessage("m2").build();
    RouteNote n3 = RouteNote.newBuilder().setLocation(p1).setMessage("m3").build();
    RouteNote n4 = RouteNote.newBuilder().setLocation(p2).setMessage("m4").build();
    RouteNote n5 = RouteNote.newBuilder().setLocation(p1).setMessage("m5").build();
    RouteNote n6 = RouteNote.newBuilder().setLocation(p1).setMessage("m6").build();
    int timesOnNext = 0;

    @SuppressWarnings("unchecked")
    StreamObserver<RouteNote> responseObserver =
        (StreamObserver<RouteNote>) mock(StreamObserver.class);
    GeometryOperatorsGrpc.GeometryOperatorsStub stub = GeometryOperatorsGrpc.newStub(inProcessChannel);

    StreamObserver<RouteNote> requestObserver = stub.routeChat(responseObserver);
    verify(responseObserver, never()).onNext(any(RouteNote.class));

    requestObserver.onNext(n1);
    verify(responseObserver, never()).onNext(any(RouteNote.class));

    requestObserver.onNext(n2);
    verify(responseObserver, never()).onNext(any(RouteNote.class));

    requestObserver.onNext(n3);
    ArgumentCaptor<RouteNote> routeNoteCaptor = ArgumentCaptor.forClass(RouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    RouteNote result = routeNoteCaptor.getValue();
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());

    requestObserver.onNext(n4);
    routeNoteCaptor = ArgumentCaptor.forClass(RouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    Assert.assertEquals(p2, result.getLocation());
    assertEquals("m2", result.getMessage());

    requestObserver.onNext(n5);
    routeNoteCaptor = ArgumentCaptor.forClass(RouteNote.class);
    timesOnNext += 2;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());

    requestObserver.onNext(n6);
    routeNoteCaptor = ArgumentCaptor.forClass(RouteNote.class);
    timesOnNext += 3;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 3);
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    Assert.assertEquals(p1, result.getLocation());
    assertEquals("m5", result.getMessage());

    requestObserver.onCompleted();
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }
}
