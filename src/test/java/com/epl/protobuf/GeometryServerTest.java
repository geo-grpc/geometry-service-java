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

package com.epl.protobuf;

import com.esri.core.geometry.*;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;


/**
 * Unit tests for {@link GeometryServer}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 */
@RunWith(JUnit4.class)
public class GeometryServerTest {
    private GeometryServer server;
    private ManagedChannel inProcessChannel;

    @Before
    public void setUp() throws Exception {
        String uniqueServerName = "in-process server for " + getClass();
        // use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
        // usage timeouts and latches in test. But we still add timeout and latches where they would be
        // needed if no directExecutor were used, just for demo purpose.
        server = new GeometryServer(InProcessServerBuilder.forName(uniqueServerName).directExecutor(), 0);
        server.start();
        inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
    }

    @After
    public void tearDown() throws Exception {
        inProcessChannel.shutdownNow();
        server.stop();
    }


    @Test
    public void getWKTGeometry() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkt op = OperatorExportToWkt.local();
        String geom = op.execute(0, polyline, null);

        GeometryData geometryData = GeometryData.newBuilder().setWkt(geom).setGeometryId(42).build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        assertEquals(operatorResult.getGeometry().getWkt(), geometryData.getWkt());
        assertEquals(operatorResult.getGeometry().getGeometryId(), 42);
    }

    @Test
    public void getWKTGeometryDataLeft() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkt op = OperatorExportToWkt.local();
        String geom = op.execute(0, polyline, null);

        GeometryData geometryData = GeometryData.newBuilder().setWkt(geom).setGeometryId(42).build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        assertEquals(operatorResult.getGeometry().getWkt(), geometryData.getWkt());
        assertEquals(operatorResult.getGeometry().getGeometryId(), 42);
    }

    @Test
    public void getGeoJsonGeometryDataLeft() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorFactoryLocal factory = OperatorFactoryLocal.getInstance();
        SimpleGeometryCursor simpleGeometryCursor = new SimpleGeometryCursor(polyline);
        OperatorExportToGeoJsonCursor exportToGeoJsonCursor = new OperatorExportToGeoJsonCursor(GeoJsonExportFlags.geoJsonExportSkipCRS, null, simpleGeometryCursor);
        String geom = exportToGeoJsonCursor.next();

        GeometryData geometryData = GeometryData.newBuilder().setGeometryId(42).setGeojson(geom).build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        OperatorExportToWkt op2 = OperatorExportToWkt.local();
        String geom2 = op2.execute(0, polyline, null);

        assertEquals(operatorResult.getGeometry().getWkt(), geom2);
        assertEquals(operatorResult.getGeometry().getGeometryId(), 42);
    }


    @Test
    public void getGeoJsonGeometryData() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToGeoJson op = OperatorExportToGeoJson.local();
        String geom = op.execute(0, null, polyline);

        GeometryData geometryData = GeometryData.newBuilder().setGeometryId(42).setGeojson(geom).build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        OperatorExportToWkt op2 = OperatorExportToWkt.local();
        String geom2 = op2.execute(0, polyline, null);

        assertEquals(operatorResult.getGeometry().getWkt(), geom2);
        assertEquals(operatorResult.getGeometry().getGeometryId(), 42);
    }



    @Test
    public void getWKTGeometryFromWKB() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkb op = OperatorExportToWkb.local();


        GeometryData geometryData = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        OperatorExportToWkt op2 = OperatorExportToWkt.local();
        String geom = op2.execute(0, polyline, null);
        assertEquals(operatorResult.getGeometry().getWkt(), geom);
    }

    @Test
    public void getWKTGeometryFromWKBData() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        OperatorExportToWkb op = OperatorExportToWkb.local();


        GeometryData geometryData = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest requestOp = GeometryRequest.newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.EXPORT_TO_WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(requestOp);

        OperatorExportToWkt op2 = OperatorExportToWkt.local();
        String geom = op2.execute(0, polyline, null);
        assertEquals(operatorResult.getGeometry().getWkt(), geom);
    }

    @Test
    public void getCONVEX_HULLGeometryFromWKB() {
        Polyline polyline = new Polyline();
        polyline.startPath(-200, -90);
        polyline.lineTo(-180, -85);
        polyline.lineTo(-90, -70);
        polyline.lineTo(0, 0);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(225, 65);
        OperatorExportToESRIShape op = OperatorExportToESRIShape.local();

        GeometryData geometryData = GeometryData.newBuilder()
                .setEsriShape(ByteString.copyFrom(op.execute(0, polyline)))
                .build();

        GeometryRequest serviceOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }


    @Test
    public void getCONVEX_HULLGeometryFromWKBData() {
        Polyline polyline = new Polyline();
        polyline.startPath(-200, -90);
        polyline.lineTo(-180, -85);
        polyline.lineTo(-90, -70);
        polyline.lineTo(0, 0);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(225, 65);
        OperatorExportToESRIShape op = OperatorExportToESRIShape.local();

        GeometryData geometryData = GeometryData.newBuilder()
                .setEsriShape(ByteString.copyFrom(op.execute(0, polyline)))
                .build();

        GeometryRequest serviceOp = GeometryRequest
                .newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }


    @Test
    public void testProjection() {
        Polyline polyline = new Polyline();
        polyline.startPath(500000, 0);
        polyline.lineTo(400000, 100000);
        polyline.lineTo(600000, -100000);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData inputSpatialReference = SpatialReferenceData.newBuilder()
                .setWkid(32632)
                .build();

        GeometryData geometryData = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setGeometryId(44)
                .setSr(inputSpatialReference)
                .build();

        SpatialReferenceData outputSpatialReference = SpatialReferenceData.newBuilder()
                .setWkid(4326)
                .build();


        GeometryRequest serviceProjectOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.PROJECT)
                .setResultSr(outputSpatialReference)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceProjectOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Polyline result = (Polyline) op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);
        TestCase.assertNotNull(result);

        TestCase.assertFalse(polyline.equals(result));
        assertEquals(polyline.getPointCount(), result.getPointCount());
        assertEquals(operatorResult.getGeometry().getGeometryId(), 44);
        ProjectionTransformation projectionTransformation = new ProjectionTransformation(SpatialReference.create(32632), SpatialReference.create(4326));
        Polyline originalPolyline = (Polyline)OperatorProject.local().execute(polyline, projectionTransformation, null);

        for (int i = 0; i < polyline.getPointCount(); i++) {
            assertEquals(result.getPoint(i).getX(), originalPolyline.getPoint(i).getX(), 1e-10);
            assertEquals(result.getPoint(i).getY(), originalPolyline.getPoint(i).getY(), 1e-10);
        }
    }

    @Test
    public void testSpatialReferenceReturn() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());
        assertEquals(operatorResult.getGeometry().getSr().getWkid(), 4326);
    }

    @Test
    public void testSpatialReferenceReturn_2() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setResultSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setOperator(OperatorType.BUFFER);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());
        assertEquals(operatorResult.getGeometry().getSr().getWkid(), 4326);
    }

    @Test
    public void testSpatialReferenceReturn_3() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperationSr(SpatialReferenceData.newBuilder().setWkid(3857).build())
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());
        assertEquals(operatorResult.getGeometry().getSr().getWkid(), 3857);
    }

    @Test
    public void testSpatialReferenceReturn_5() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperationSr(SpatialReferenceData.newBuilder().setProj4(SpatialReference.create(3857).getProj4()).build())
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());
        assertEquals(operatorResult.getGeometry().getSr().getWkid(), 3857);
    }

    @Test
    public void testSpatialReferenceReturn_6() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setResultSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setOperator(OperatorType.BUFFER)
                .setResultEncoding(Encoding.ENVELOPE);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());
        assertEquals(operatorResult.getEnvelope().getSr().getWkid(), 4326);
    }

    @Test
    public void testEnvelopeReturn() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setSr(SpatialReferenceData.newBuilder().setWkid(4326).build())
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest.Builder serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER);


        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp.build());

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);

        serviceOp.setResultEncoding(Encoding.ENVELOPE);
        GeometryResponse operatorResult2 = stub.operate(serviceOp.build());

        assertEquals(-1, operatorResult2.getEnvelope().getXmin(), 0.0);
        assertEquals(-1, operatorResult2.getEnvelope().getYmin(), 0.0);
        assertEquals(4, operatorResult2.getEnvelope().getXmax(), 0.0);
        assertEquals(4, operatorResult2.getEnvelope().getYmax(), 0.0);

    }

//    @Test
//    public void testEnvelope() {
//        Polyline polyline = new Polyline();
//        polyline.startPath(0, 0);
//        polyline.lineTo(2, 3);
//        polyline.lineTo(3, 3);
//        GeometryRequest geometryRequest = GeometryRequest
//                .newBuilder()
//                .setResultEncoding(Encoding.ENVELOPE)
//                .setGeometry(GeometryData
//                        .newBuilder()
//                        .setWkb(ByteString
//                                .copyFrom(OperatorExportToWkb
//                                        .local()
//                                        .execute(0,polyline, null)))).build();
//
//        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
//        GeometryResponse response = stub.operate(geometryRequest);
//        assertEquals(0, response.getEnvelope().getXmin(), 0.0);
//        assertEquals(0, response.getEnvelope().getYmin(), 0.0);
//        assertEquals(3, response.getEnvelope().getXmax(), 0.0);
//        assertEquals(3, response.getEnvelope().getYmax(), 0.0);
//    }

    @Test
    public void testChainingBufferCONVEX_HULLLeft() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        // TODO inspect bug where it crosses dateline
//        polyline.startPath(-200, -90);
//        polyline.lineTo(-180, -85);
//        polyline.lineTo(-90, -70);
//        polyline.lineTo(0, 0);
//        polyline.lineTo(100, 25);
//        polyline.lineTo(170, 45);
//        polyline.lineTo(225, 64);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest serviceOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER)
                .build();


        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }

    @Test
    public void testChainingBufferCONVEX_HULLData() {
        Polyline polyline = new Polyline();
        polyline.startPath(0, 0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        // TODO inspect bug where it crosses dateline
//        polyline.startPath(-200, -90);
//        polyline.lineTo(-180, -85);
//        polyline.lineTo(-90, -70);
//        polyline.lineTo(0, 0);
//        polyline.lineTo(100, 25);
//        polyline.lineTo(170, 45);
//        polyline.lineTo(225, 64);

        OperatorExportToWkb op = OperatorExportToWkb.local();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .build();

        GeometryRequest serviceConvexOp = GeometryRequest
                .newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.CONVEX_HULL)
                .build();


        GeometryRequest serviceOp = GeometryRequest.newBuilder()
                .setGeometryRequest(serviceConvexOp)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1).build())
                .setOperator(OperatorType.BUFFER)
                .build();


        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceOp);

        OperatorImportFromWkb op2 = OperatorImportFromWkb.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);

        assertTrue(bContains);
    }

    static double randomWithRange(double min, double max) {
        double range = Math.abs(max - min);
        return (Math.random() * range) + (min <= max ? min : max);
    }

//    @Test
//    public void testUnion() {
//        int size = 1000;
//        List<String> points = new ArrayList<>(size);
//        List<Point> pointList = new ArrayList<>(size);
//        for (int i = 0; i < size; i++) {
//            double x = randomWithRange(-20, 20);
//            double y = randomWithRange(-20, 20);
//            points.add(String.format("Point(%f %f)", x, y));
//            pointList.add(new Point(x, y));
//        }
//        GeometryData geometryData = GeometryData.newBuilder()
//                .setAllWkt(points)
//                .build();
//
//        BufferParams bufferParams = BufferParams.newBuilder().setDistance(2.5).setUnionResult(true).build();
//
//        GeometryRequest serviceBufferOp = GeometryRequest.newBuilder()
//                .setLeftGeometry(geometryData)
//                .setOperator(OperatorType.BUFFER)
//                .setBufferParams(GeometryRequest.(bufferParams)
//                .build();
//
//        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
//        GeometryResponse operatorResult = stub.operate(serviceBufferOp);
//
//        List<ByteBuffer> byteBufferList = operatorResult.getGeometry().getWkbList().stream().map(com.google.protobuf.ByteString::asReadOnlyByteBuffer).collect(Collectors.toList());
//        SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferList);
//        OperatorImportFromWkbCursor operatorImportFromWkbCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
//        Geometry result = OperatorImportFromWkb.local().execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer(), null);
//        assertTrue(result.calculateArea2D() > (Math.PI * 2.5 * 2.5 * 2));
//
////    assertEquals(resultSR.calculateArea2D(), Math.PI * 2.5 * 2.5, 0.1);
////    shape_start = datetime.datetime.now()
////    spots = [p.buffer(2.5) for p in points]
////    patches = cascaded_union(spots)
////    shape_end = datetime.datetime.now()
////    shape_delta = shape_end - shape_start
////    shape_microseconds = int(shape_delta.total_seconds() * 1000)
////
////    stub = geometry_grpc.GeometryOperatorsStub(self.channel)
////    geometryData = GeometryData()
////
////    epl_start = datetime.datetime.now()
////    geometryData.geometry_binary.extend([s.wkb for s in spots])
////    geometryData.geometry_encoding_type = Encoding.Value('wkb')
////
////        # opRequestBuffer = GeometryRequest(left_geometry=geometryData,
////            #                                   operator_type=OperatorType.Value('BUFFER'),
////            #                                   buffer_distances=[2.5])
////
////    opRequestUnion = GeometryRequest(left_geometry=geometryData,
////            operator_type=OperatorType.Value('Union'))
////
////    response = stub.operate(opRequestUnion)
////    unioned_result = wkbloads(response.geometry.geometry_binary[0])
////    epl_end = datetime.datetime.now()
////    epl_delta = epl_end - epl_start
////    epl_microseconds = int(epl_delta.total_seconds() * 1000)
////    self.assertGreater(shape_microseconds, epl_microseconds)
////    self.assertGreater(shape_microseconds / 8, epl_microseconds)
////
////    self.assertAlmostEqual(patches.area, unioned_result.area, 4)
//    }
//
    @Test
    public void testCrazyNesting() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-90, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail


        GeometryData geometryDataLeft = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest.BufferParams bufferParams = GeometryRequest.BufferParams.newBuilder().setDistance(.5).build();

        GeometryRequest serviceOpLeft = GeometryRequest
                .newBuilder()
                .setGeometry(geometryDataLeft)
                .setOperator(OperatorType.BUFFER)
                .setBufferParams(bufferParams)
                .setResultSr(spatialReferenceWGS)
                .build();
        GeometryRequest nestedLeft = GeometryRequest
                .newBuilder()
                .setGeometryRequest(serviceOpLeft)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        GeometryData geometryDataRight = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpRight = GeometryRequest
                .newBuilder()
                .setGeometry(geometryDataRight)
                .setOperator(OperatorType.GEODESIC_BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1000).setUnionResult(false).build())
                .setOperationSr(spatialReferenceWGS)
                .build();
        GeometryRequest nestedRight = GeometryRequest
                .newBuilder()
                .setGeometryRequest(serviceOpRight)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        GeometryRequest operatorRequestContains = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperator(OperatorType.CONTAINS)
                .setOperationSr(spatialReferenceMerc)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(operatorRequestContains);
        Map<Long, Boolean> map = operatorResult.getRelateMapMap();

        assertTrue(map.get(0L));
    }


    @Test
    public void testCrazyNestingDataLeft() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-90, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail


        GeometryData geometryLeft = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest.BufferParams bufferParams = GeometryRequest.BufferParams.newBuilder().setDistance(.5).build();

        GeometryRequest serviceOpLeft = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryLeft)
                .setOperator(OperatorType.BUFFER)
                .setBufferParams(bufferParams)

                .setResultSr(spatialReferenceWGS)
                .build();
        GeometryRequest nestedLeft = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpLeft)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        GeometryData geometryDataRight = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpRight = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryDataRight)
                .setOperator(OperatorType.GEODESIC_BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(1000).setUnionResult(false).build())
                .setOperationSr(spatialReferenceWGS)
                .build();
        GeometryRequest nestedRight = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpRight)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        GeometryRequest operatorRequestContains = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperator(OperatorType.CONTAINS)
                .setOperationSr(spatialReferenceMerc)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(operatorRequestContains);
        Map<Long, Boolean> map = operatorResult.getRelateMapMap();

        assertTrue(map.get(0L));
    }



    @Test
    public void testCrazyNesting2() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-91, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();
        OperatorImportFromWkb operatorImportFromWkb = OperatorImportFromWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail
        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);


        GeometryData geometryDataLeft = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpLeft = GeometryRequest
                .newBuilder()
                .setGeometry(geometryDataLeft)
                .setOperator(OperatorType.BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(.5).build())
                .setResultSr(spatialReferenceWGS)
                .build();

        Geometry bufferedLeft = GeometryEngine.buffer(polyline, SpatialReference.create(4269), .5);
        Geometry projectedBuffered = GeometryEngine.project(bufferedLeft, SpatialReference.create(4269), SpatialReference.create(4326));
        GeometryResponse operatorResultLeft = stub.operate(serviceOpLeft);
        SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeft.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBuffered, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));


        GeometryRequest nestedLeft = GeometryRequest
                .newBuilder()
                .setGeometryRequest(serviceOpLeft)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();
        Geometry projectedBufferedConvex = GeometryEngine.convexHull(projectedBuffered);
        Geometry reProjectedBufferedCONVEX_HULL = GeometryEngine.project(projectedBufferedConvex, SpatialReference.create(4326), SpatialReference.create(54016));
        GeometryResponse operatorResultLeftNested = stub.operate(nestedLeft);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeftNested.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedCONVEX_HULL, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        GeometryData geometryDataRight = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpRight = GeometryRequest
                .newBuilder()
                .setGeometry(geometryDataRight)
                .setOperator(OperatorType.GEODESIC_BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder()
                        .setDistance(1000)
                        .setUnionResult(false)
                        .build())
                .setOperationSr(spatialReferenceWGS)
                .build();

        Geometry projectedRight = GeometryEngine.project(polyline, SpatialReference.create(4269), SpatialReference.create(4326));
        Geometry projectedBufferedRight = GeometryEngine.geodesicBuffer(projectedRight, SpatialReference.create(4326), 1000);
        GeometryResponse operatorResultRight = stub.operate(serviceOpRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRight.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBufferedRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));

        GeometryRequest nestedRight = GeometryRequest
                .newBuilder()
                .setGeometryRequest(serviceOpRight)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        Geometry projectedBufferedConvexRight = GeometryEngine.convexHull(projectedBufferedRight);
        Geometry reProjectedBufferedCONVEX_HULLRight = GeometryEngine.project(projectedBufferedConvexRight, SpatialReference.create(4326), SpatialReference.create(54016));
        GeometryResponse operatorResultRightNested = stub.operate(nestedRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRightNested.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedCONVEX_HULLRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        GeometryRequest operatorRequestSymDifference = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperator(OperatorType.SYMMETRIC_DIFFERENCE)
                .setOperationSr(spatialReferenceMerc)
                .setResultSr(spatialReferenceNAD)
                .build();


        Geometry rightFinal = GeometryEngine.project(reProjectedBufferedCONVEX_HULLRight, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry leftFinal = GeometryEngine.project(reProjectedBufferedCONVEX_HULL, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry difference = GeometryEngine.symmetricDifference(leftFinal, rightFinal, SpatialReference.create(3857));
        Geometry differenceProjected = GeometryEngine.project(difference, SpatialReference.create(3857), SpatialReference.create(4269));

        GeometryResponse operatorResult = stub.operate(operatorRequestSymDifference);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(differenceProjected, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4269)));

    }

    @Test
    public void testCrazyNesting2Left() {
        Polyline polyline = new Polyline();
        polyline.startPath(-120, -45);
        polyline.lineTo(-100, -55);
        polyline.lineTo(-91, -63);
        polyline.lineTo(0, 0);
        polyline.lineTo(1, 1);
        polyline.lineTo(100, 25);
        polyline.lineTo(170, 45);
        polyline.lineTo(175, 65);
        OperatorExportToWkb op = OperatorExportToWkb.local();
        OperatorImportFromWkb operatorImportFromWkb = OperatorImportFromWkb.local();

        SpatialReferenceData spatialReferenceNAD = SpatialReferenceData.newBuilder().setWkid(4269).build();
        SpatialReferenceData spatialReferenceMerc = SpatialReferenceData.newBuilder().setWkid(3857).build();
        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(54016).build();
        //TODO why does esri shape fail
        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);


        GeometryData geometryDataLeft = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpLeft = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryDataLeft)
                .setOperator(OperatorType.BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(.5).build())
                .setResultSr(spatialReferenceWGS)
                .build();

        Geometry bufferedLeft = GeometryEngine.buffer(polyline, SpatialReference.create(4269), .5);
        Geometry projectedBuffered = GeometryEngine.project(bufferedLeft, SpatialReference.create(4269), SpatialReference.create(4326));
        GeometryResponse operatorResultLeft = stub.operate(serviceOpLeft);
        SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeft.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBuffered, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));


        GeometryRequest nestedLeft = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpLeft)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();
        Geometry projectedBufferedConvex = GeometryEngine.convexHull(projectedBuffered);
        Geometry reProjectedBufferedCONVEX_HULL = GeometryEngine.project(projectedBufferedConvex, SpatialReference.create(4326), SpatialReference.create(54016));
        GeometryResponse operatorResultLeftNested = stub.operate(nestedLeft);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultLeftNested.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedCONVEX_HULL, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        GeometryData geometryDataRight = GeometryData.newBuilder()
                .setWkb(ByteString.copyFrom(op.execute(0, polyline, null)))
                .setSr(spatialReferenceNAD)
                .build();

        GeometryRequest serviceOpRight = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryDataRight)
                .setOperator(OperatorType.GEODESIC_BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder()
                        .setDistance(1000)
                        .setUnionResult(false)
                        .build())
                .setOperationSr(spatialReferenceWGS)
                .build();

        Geometry projectedRight = GeometryEngine.project(polyline, SpatialReference.create(4269), SpatialReference.create(4326));
        Geometry projectedBufferedRight = GeometryEngine.geodesicBuffer(projectedRight, SpatialReference.create(4326), 1000);
        GeometryResponse operatorResultRight = stub.operate(serviceOpRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRight.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(projectedBufferedRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4326)));

        GeometryRequest nestedRight = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(serviceOpRight)
                .setOperator(OperatorType.CONVEX_HULL)
                .setResultSr(spatialReferenceGall)
                .build();

        Geometry projectedBufferedConvexRight = GeometryEngine.convexHull(projectedBufferedRight);
        Geometry reProjectedBufferedCONVEX_HULLRight = GeometryEngine.project(projectedBufferedConvexRight, SpatialReference.create(4326), SpatialReference.create(54016));
        GeometryResponse operatorResultRightNested = stub.operate(nestedRight);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResultRightNested.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(reProjectedBufferedCONVEX_HULLRight, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(54016)));

        GeometryRequest operatorRequestSymDifference = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(nestedLeft)
                .setRightGeometryRequest(nestedRight)
                .setOperator(OperatorType.SYMMETRIC_DIFFERENCE)
                .setOperationSr(spatialReferenceMerc)
                .setResultSr(spatialReferenceNAD)
                .build();


        Geometry rightFinal = GeometryEngine.project(reProjectedBufferedCONVEX_HULLRight, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry leftFinal = GeometryEngine.project(reProjectedBufferedCONVEX_HULL, SpatialReference.create(54016), SpatialReference.create(3857));
        Geometry difference = GeometryEngine.symmetricDifference(leftFinal, rightFinal, SpatialReference.create(3857));
        Geometry differenceProjected = GeometryEngine.project(difference, SpatialReference.create(3857), SpatialReference.create(4269));

        GeometryResponse operatorResult = stub.operate(operatorRequestSymDifference);
        simpleByteBufferCursor = new SimpleByteBufferCursor(operatorResult.getGeometry().getWkb().asReadOnlyByteBuffer());
        assertTrue(GeometryEngine.equals(differenceProjected, operatorImportFromWkb.execute(0, simpleByteBufferCursor, null).next(), SpatialReference.create(4269)));
        assertEquals(operatorResult.getGeometry().getSr().getWkid(), 4269);

    }

    @Test
    public void testMultipointRoundTrip() {
        MultiPoint multiPoint = new MultiPoint();
        for (double longitude = -180; longitude < 180; longitude+=10.0) {
            for (double latitude = -80; latitude < 80; latitude+=10.0) {
                multiPoint.add(longitude, latitude);
            }
        }

        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(32632).build();

        GeometryData geometryData = GeometryData.newBuilder()
                .setWkt(GeometryEngine.geometryToWkt(multiPoint, 0))
                .setSr(spatialReferenceWGS)
                .build();

        GeometryRequest serviceProjectOp = GeometryRequest.newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.PROJECT)
                .setOperationSr(spatialReferenceGall)
                .build();

        GeometryRequest.Builder serviceReProjectOp = GeometryRequest.newBuilder()
                .setGeometryRequest(serviceProjectOp)
                .setOperator(OperatorType.PROJECT)
                .setOperationSr(spatialReferenceWGS)
                .setResultEncoding(Encoding.WKT);

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        // TODO check the results of this test. the envelope appears to be maxed to inifinty
        GeometryResponse operatorResult = stub.operate(serviceReProjectOp.build());
    }


    @Test
    public void testMultipointRoundTripLeft() {
        MultiPoint multiPoint = new MultiPoint();
        for (double longitude = -180; longitude < 180; longitude+=10.0) {
            for (double latitude = -80; latitude < 80; latitude+=10.0) {
                multiPoint.add(longitude, latitude);
            }
        }

        SpatialReferenceData spatialReferenceWGS = SpatialReferenceData.newBuilder().setWkid(4326).build();
        SpatialReferenceData spatialReferenceGall = SpatialReferenceData.newBuilder().setWkid(32632).build();

        GeometryData geometryData = GeometryData.newBuilder()
                .setWkt(GeometryEngine.geometryToWkt(multiPoint, 0))
                .setSr(spatialReferenceWGS)
                .build();

        GeometryRequest serviceProjectOp = GeometryRequest.newBuilder()
                .setLeftGeometry(geometryData)
                .setOperator(OperatorType.PROJECT)
                .setOperationSr(spatialReferenceGall)
                .build();

        GeometryRequest serviceReProjectOp = GeometryRequest.newBuilder()
                .setLeftGeometryRequest(serviceProjectOp)
                .setOperator(OperatorType.PROJECT)
                .setOperationSr(spatialReferenceWGS)
                .setResultEncoding(Encoding.WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(serviceReProjectOp);

    }


//    @Test
//    public void testETRS() {
//        List<String> arrayDeque = new ArrayList<>();
//        for (double longitude = -180; longitude < 180; longitude+=15.0) {
//            for (double latitude = -90; latitude < 80; latitude+=15.0) {
//                Point point = new Point(longitude, latitude);
//                arrayDeque.add(OperatorExportToWkt.local().execute(0, point,null));
//            }
//        }
//
//        SpatialReferenceData serviceSpatialReference = SpatialReferenceData.newBuilder().setWkid(4326).build();
//        SpatialReferenceData outputSpatialReference = SpatialReferenceData.newBuilder().setWkid(3035).build();
//
//        GeometryData geometryData = GeometryData.newBuilder()
//                .setAllWkt(arrayDeque)
//                .setSr(serviceSpatialReference)
//                .build();
//
//        GeometryRequest serviceProjectOp = GeometryRequest.newBuilder()
//                .setLeftGeometry(geometryData)
//                .setOperator(OperatorType.PROJECT)
//                .setOperationSr(outputSpatialReference)
//                .build();
//
//        GeometryRequest serviceReProjectOp = GeometryRequest.newBuilder()
//                .setLeftGeometryRequest(serviceProjectOp)
//                .setOperator(OperatorType.PROJECT)
//                .setOperationSr(serviceSpatialReference)
//                .setResultEncoding(Encoding.wkt)
//                .build();
//
//        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
//        GeometryResponse operatorResult = stub.operate(serviceReProjectOp);
//        SimpleStringCursor simpleByteBufferCursor = new SimpleStringCursor(operatorResult.getGeometry().getWktList());
//        boolean bFoundEmpty = false;
//        while (simpleByteBufferCursor.hasNext()) {
//            String words = simpleByteBufferCursor.next();
//            if (words.equals("POINT EMPTY")) {
//                bFoundEmpty = true;
//            }
//        }
//        assertTrue(bFoundEmpty);
//    }

    @Test
    public void testProj4() {
        SpatialReferenceData spatialReferenceData = SpatialReferenceData
                .newBuilder()
                .setProj4("+init=epsg:4326")
                .build();
        SpatialReferenceData spatialReferenceDataWKID = SpatialReferenceData
                .newBuilder()
                .setWkid(4326)
                .build();
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setWkt("MULTILINESTRING ((-120 -45, -100 -55, -90 -63, 0 0, 1 1, 100 25, 170 45, 175 65))")
                .setSr(spatialReferenceData)
                .build();

        GeometryRequest operatorRequest = GeometryRequest
                .newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.PROJECT)
                .setResultEncoding(Encoding.WKB)
                .setResultSr(spatialReferenceDataWKID)
                .build();

        GeometryRequest operatorRequestEquals = GeometryRequest
                .newBuilder()
                .setLeftGeometryRequest(operatorRequest)
                .setRightGeometry(geometryData)
                .setOperator(OperatorType.CONTAINS)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(operatorRequestEquals);
        assertTrue(operatorResult.getSpatialRelationship());
    }

    @Test
    public void testCut() {
        GeometryData geometryDataPolygon = GeometryData.newBuilder().setWkt("MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)), ((20 35, 45 20, 30 5, 10 10, 10 30, 20 35), (30 20, 20 25, 20 15, 30 20))) ").build();
        GeometryData geometryDataCutter = GeometryData.newBuilder().setWkt("LINESTRING(0 0, 45 45)").build();
        GeometryRequest geometryRequest = GeometryRequest.newBuilder()
                .setLeftGeometry(geometryDataPolygon)
                .setRightGeometry(geometryDataCutter)
                .setOperator(OperatorType.CUT)
                .setResultEncoding(Encoding.WKT)
                .build();
        CountDownLatch done = new CountDownLatch(1);
        ClientResponseObserver<GeometryRequest, GeometryResponse> clientResponseObserver = new ClientResponseObserver<GeometryRequest, GeometryResponse>() {
            int count = 0;
            @Override
            public void beforeStart(ClientCallStreamObserver<GeometryRequest> clientCallStreamObserver) {

            }

            @Override
            public void onNext(GeometryResponse geometryResponse) {
                count += 1;
            }

            @Override
            public void onError(Throwable throwable) {
                assertFalse("threw exception", false);
            }

            @Override
            public void onCompleted() {
                assertEquals(2, count);
                done.countDown();
            }
        };

        GeometryServiceGrpc.GeometryServiceStub stub = GeometryServiceGrpc.newStub(inProcessChannel);
        stub.operateServerStream(geometryRequest, clientResponseObserver);


        try {
            done.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGeodeticArea() {
        SpatialReferenceData spatialReferenceData = SpatialReferenceData.newBuilder().setWkid(4326).build();
        Polygon polygon = new Polygon();
        polygon.startPath(-1, 1);
        polygon.lineTo(1, 1);
        polygon.lineTo(1, -1);
        polygon.lineTo(-1, -1);
        polygon.closeAllPaths();
        OperatorExportToWkb operatorExportToWkb = (OperatorExportToWkb)OperatorFactoryLocal.getInstance().getOperator(Operator.Type.ExportToWkb);
        ByteBuffer buffer = operatorExportToWkb.execute(0, polygon,null);
        GeometryData geometryData = GeometryData
                .newBuilder()
                .setWkb(ByteString.copyFrom(buffer))
                .setSr(spatialReferenceData).build();
        GeometryRequest geometryRequest = GeometryRequest.newBuilder()
                .setOperator(OperatorType.GEODETIC_AREA)
                .setGeometry(geometryData)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse geometryResponse = stub.operate(geometryRequest);
//        assertEquals(geometryResponse.getMeasure(), 90000.0);

        GeometryRequest projectThenArea = GeometryRequest.newBuilder()
                .setOperator(OperatorType.GEODETIC_AREA)
                .setGeometryRequest(GeometryRequest.newBuilder()
                        .setOperator(OperatorType.PROJECT)
                        .setGeometry(geometryData)
                        .setResultSr(SpatialReferenceData.newBuilder().setWkid(32632)))
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub2 = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse geometryResponse2 = stub2.operate(projectThenArea);
        assertEquals(geometryResponse.getMeasure(), geometryResponse2.getMeasure(), 0.000001);
    }

    @Test
    public void testPointGeodetic() {
        // POINT (4322181.519435114 3212199.338618969) proj4: "+proj=laea +lat_0=31.593750 +lon_0=-94.718750 +x_0=4321000 +y_0=3210000 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"
        SpatialReferenceData spatialReferenceData = SpatialReferenceData.newBuilder().setProj4("+proj=laea +lat_0=31.593750 +lon_0=-94.718750 +x_0=4321000 +y_0=3210000 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs").build();
        GeometryData geometryData = GeometryData.newBuilder().setWkt("POINT (4322181.519435114 3212199.338618969)").setSr(spatialReferenceData).build();
        GeometryRequest geometryRequest = GeometryRequest
                .newBuilder()
                .setGeometry(geometryData)
                .setOperator(OperatorType.GEODESIC_BUFFER)
                .setBufferParams(GeometryRequest.BufferParams.newBuilder().setDistance(200).build())
                .setResultEncoding(Encoding.WKT)
                .build();

        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse geometryResponse = stub.operate(geometryRequest);

        GeometryRequest geometryRequest1 = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryResponse.getGeometry())
                .setRightGeometry(geometryData)
                .setOperator(OperatorType.INTERSECTS)
                .build();

        GeometryResponse geometryResponse1 = stub.operate(geometryRequest1);
        assertTrue(geometryResponse1.getSpatialRelationship());
    }

    @Test
    public void testAffineTransform() {
        double x = -116;
        double y = 46;
        Point pt = new Point(x, y);
        String wkt = pt.toString();
        GeometryRequest geometryRequest = GeometryRequest
                .newBuilder()
                .setGeometry(
                        GeometryData
                                .newBuilder()
                                .setWkt(wkt)
                                .build())
                .setAffineTransformParams(
                        GeometryRequest.AffineTransformParams.newBuilder()
                                .setXOffset(1)
                                .setYOffset(2).build())
                .setOperator(OperatorType.AFFINE_TRANSFORM)
                .setResultEncoding(Encoding.WKT)
                .build();
        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse geometryResponse = stub.operate(geometryRequest);
        assertEquals("POINT (-115 48)", geometryResponse.getGeometry().getWkt());
    }

    @Test
    public void testIntersection() {
        String wkt = "POLYGON ((-116.25 46.37499999905793, -116.1875 46.37499999905793, -116.1875 46.31249999905781, -116.25 46.31249999905781, -116.25 46.37499999905793))";
        double x = -116.21874999999999;
        double y = 46.34374999905787;
        SpatialReferenceData wgs84 = SpatialReferenceData.newBuilder().setWkid(4326).build();
        GeometryData geometryData = GeometryData.newBuilder().setWkt(wkt).setSr(wgs84).build();

        SpatialReferenceData spatialReferenceDataLocal = SpatialReferenceData.newBuilder().setCustom(SpatialReferenceData.Custom.newBuilder().setLon0(x).setLat0(y).build()).build();
        GeometryRequest geometryRequest = GeometryRequest.newBuilder().setGeometry(geometryData).setResultEncoding(Encoding.WKT).setOperator(OperatorType.PROJECT).setResultSr(spatialReferenceDataLocal).build();
        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse operatorResult = stub.operate(geometryRequest);
        GeometryData localGeometry = operatorResult.getGeometry();

        SpatialReference spatialReference = SpatialReference.createUTM(x, y);
        SpatialReferenceData spatialReferenceDataUtm = SpatialReferenceData.newBuilder().setProj4(spatialReference.getProj4()).build();

        GeometryRequest geometryRequestIntersection = GeometryRequest
                .newBuilder()
                .setLeftGeometry(geometryData)
                .setRightGeometry(localGeometry)
                .setOperator(OperatorType.INTERSECTION)
                .setResultSr(spatialReferenceDataUtm)
                .setResultEncoding(Encoding.WKT)
                .build();
        GeometryResponse operatorResult2 = stub.operate(geometryRequestIntersection);

        GeometryRequest geometryRequest1 = GeometryRequest.newBuilder()
                .setLeftGeometry(operatorResult2.getGeometry())
                .setRightGeometry(geometryData)
                .setOperationSr(wgs84)
                .setOperator(OperatorType.INTERSECTS)
                .build();
        GeometryResponse operatorResult3 = stub.operate(geometryRequest1);
        assertTrue(operatorResult3.getSpatialRelationship());
    }

    @Test
    public void testLength() {
        String wkt = "LINESTRING (0 0, 1 0)";
        SpatialReferenceData wgs84 = SpatialReferenceData.newBuilder().setWkid(4326).build();
        GeometryData geometryData = GeometryData.newBuilder().setWkt(wkt).setSr(wgs84).build();
        GeometryRequest geometryRequest = GeometryRequest.newBuilder().setGeometry(geometryData).setOperator(OperatorType.GEODETIC_LENGTH).build();
        GeometryServiceGrpc.GeometryServiceBlockingStub stub = GeometryServiceGrpc.newBlockingStub(inProcessChannel);
        GeometryResponse geometryResponse = stub.operate(geometryRequest);
        assertEquals(111319.4907932264, geometryResponse.getMeasure(), 8);

    }
}
