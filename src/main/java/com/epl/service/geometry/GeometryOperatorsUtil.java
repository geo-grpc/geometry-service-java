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

import com.google.protobuf.ByteString;
import com.google.protobuf.ProtocolStringList;

import com.fasterxml.jackson.core.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Common utilities for the GeometryOperators demo.
 */
public class GeometryOperatorsUtil {
    public static List<com.google.protobuf.ByteString> __exportByteBufferCursor(ByteBufferCursor byteBufferCursor) {
        ByteBuffer byteBuffer = null;
        // TODO add count on ByteBufferCursor?
        List<com.google.protobuf.ByteString> byteStringList = new ArrayList<>();

        while ((byteBuffer = byteBufferCursor.next()) != null) {
            byteStringList.add(com.google.protobuf.ByteString.copyFrom(byteBuffer));
        }

        return byteStringList;
    }

    public static List<String> __exportStringBufferCursor(StringCursor stringCursor) {
        String geomString = null;
        List<String> stringList = new ArrayList<>();
        while ((geomString = stringCursor.next()) != null)
            stringList.add(geomString);
        return stringList;
    }

    public static ServiceGeometry __encodeGeometry(GeometryCursor geometryCursor, OperatorRequest operatorRequest, GeometryEncodingType encodingType) {
        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder();


        // TODO not getting stubbed out due to grpc proto stubbing bug
        if (encodingType == null) {
            if (operatorRequest.getResultsEncodingType() == null) {
                encodingType = GeometryEncodingType.wkb;
            } else {
                encodingType = operatorRequest.getResultsEncodingType();
            }
        }

        switch (encodingType) {
            case wkb:
                serviceGeometryBuilder.addAllGeometryBinary(__exportByteBufferCursor(new OperatorExportToWkbCursor(0, geometryCursor)));
                break;
            case wkt:
                serviceGeometryBuilder.addAllGeometryString(__exportStringBufferCursor(new OperatorExportToWktCursor(0, geometryCursor, null)));
                break;
            case esrishape:
                serviceGeometryBuilder.addAllGeometryBinary(__exportByteBufferCursor(new OperatorExportToESRIShapeCursor(0, geometryCursor)));
                break;
            case geojson:
                //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
                // TODO add Spatial reference
                serviceGeometryBuilder.addAllGeometryString(__exportStringBufferCursor(new OperatorExportToJsonCursor(null, geometryCursor)));
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
        serviceGeometryBuilder.setSpatialReference(operatorRequest.getResultSpatialReference());

        // TODO There needs to be better tracking of geometry id throughout process
        serviceGeometryBuilder.addAllGeometryId(operatorRequest.getLeftGeometry().getGeometryIdList());

        return serviceGeometryBuilder.build();
    }


    public static GeometryCursor cursorFromRequest(OperatorRequest operatorRequest) throws IOException {
        GeometryCursor resultCursor = null;
        GeometryCursor leftCursor = null;
        if (operatorRequest.hasLeftGeometry()) {
            leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometry());
        } else {
            // assumes there is always a left geometry
            leftCursor = cursorFromRequest(operatorRequest.getLeftCursor());
        }

        GeometryCursor rightCursor = null;
        if (operatorRequest.hasRightGeometry()) {
            rightCursor = __createGeometryCursor(operatorRequest.getRightGeometry());
        } else if (operatorRequest.hasRightCursor()) {
            rightCursor = cursorFromRequest(operatorRequest.getRightCursor());
        }


        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());

        switch (operatorType) {
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                break;
            case GeodeticDensifyByLength:
                break;
            case ShapePreservingDensify:
                break;
            case GeneralizeByArea:
                break;
            case RandomPoints:
                break;
            case Project:
/*                    ProjectionTransformation projectionTransformation = new ProjectionTransformation(leftSpatialReference, operatorSpatialReference);
                resultCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);*/
                break;
            case Union:
                resultCursor = OperatorUnion.local().execute(leftCursor, null, null);
                break;
            case Difference:
                resultCursor = OperatorDifference.local().execute(leftCursor, rightCursor, null, null);
                break;
            case Buffer:
                // TODO clean this up
                double [] d = operatorRequest.getBufferDistancesList().stream().mapToDouble(Double::doubleValue).toArray();
                resultCursor = OperatorBuffer.local().execute(leftCursor, null, d, operatorRequest.getBufferUnionResult(), null);
                break;
            case Intersection:
                // TODO this is totally fucked up. 0 is a dimension. this defaults to 0. hasIntersectionDimensionMask needs to be automagically generated
                if (operatorRequest.getIntersectionDimensionMask() == 0) {
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, null, null);
                } else {
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, null, null, operatorRequest.getIntersectionDimensionMask());
                }
                break;
            case Clip:
                Envelope2D envelope2D = __extractEnvelope2D(operatorRequest.getClipEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, null, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(operatorRequest.getCutConsiderTouch(), leftCursor.next(), (Polyline)rightCursor.next(), null, null);
                break;
            case DensifyByLength:
                resultCursor = OperatorDensifyByLength.local().execute(leftCursor, operatorRequest.getDensifyMaxLength(), null);
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case Offset:
                resultCursor = OperatorOffset.local().execute(
                        leftCursor,
                        null,
                        operatorRequest.getOffsetDistance(),
                        OperatorOffset.JoinType.valueOf(operatorRequest.getOffsetJoinType()),
                        operatorRequest.getOffsetBevelRatio(),
                        operatorRequest.getOffsetFlattenError(), null);
                break;
            case Generalize:
                resultCursor = OperatorGeneralize.local().execute(leftCursor, operatorRequest.getGeneralizeMaxDeviation(), operatorRequest.getGeneralizeRemoveDegenerates(), null);
                break;
            case SymmetricDifference:
                resultCursor = OperatorSymmetricDifference.local().execute(leftCursor, rightCursor, null, null);
                break;
            case ConvexHull:
                resultCursor = OperatorConvexHull.local().execute(leftCursor, operatorRequest.getConvexHullMerge(), null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
        }

        return  resultCursor;
    }


    // TODO this is ignoring the differences between the geometry spatial references, the result spatial references and the operator spatial references
    public static OperatorResult executeOperator(OperatorRequest operatorRequest) throws IOException {
        GeometryCursor resultCursor = null;
        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();

        // optional: this is the spatial reference for performing the geometric operation
        SpatialReference operatorSpatialReference = __extractSpatialReference(operatorRequest.getOperationSpatialReference());

        // optionalish: this is the final spatial reference for the result (project after operator)
        SpatialReference resultSpatialReference = __extractSpatialReference(operatorRequest.getResultSpatialReference());

        GeometryCursor leftCursor = null;
        SpatialReference leftSpatialReference = null;
        if (operatorRequest.hasLeftGeometry()) {
            leftSpatialReference = __extractSpatialReference(operatorRequest.getLeftGeometry());
            leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometry());
        } else {
            // assumes there is always a left geometry
            // TODO confirm that result spatial reference is the correct setting here...
            leftSpatialReference = __extractSpatialReference(operatorRequest.getLeftCursor().getResultSpatialReference());
            leftCursor = cursorFromRequest(operatorRequest.getLeftCursor());
        }

        GeometryCursor rightCursor = null;
        SpatialReference rightSpatialReference = null;
        if (operatorRequest.hasRightGeometry()) {
            rightSpatialReference = __extractSpatialReference(operatorRequest.getRightGeometry());
            rightCursor = __createGeometryCursor(operatorRequest.getRightGeometry());
        } else if (operatorRequest.hasRightCursor()) {
            // TODO confirm that result spatial reference is the correct setting here...
            rightSpatialReference = __extractSpatialReference(operatorRequest.getRightCursor().getResultSpatialReference());
            rightCursor = cursorFromRequest(operatorRequest.getRightCursor());
        }


        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a right and a left geometry but no operator spatial reference, then set operatorSpatialReference
        if (operatorSpatialReference == null
                && leftSpatialReference != null
                && (rightSpatialReference == null || leftSpatialReference.equals(rightSpatialReference))) {
            operatorSpatialReference = leftSpatialReference;
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if ((leftSpatialReference != null && rightSpatialReference == null) || (leftSpatialReference == null && rightSpatialReference != null)) {
            // TODO throw an error here!!
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSpatialReference == null) {
            resultSpatialReference = operatorSpatialReference;
        }

        // project left if needed
        if (operatorSpatialReference != null && !operatorSpatialReference.equals(leftSpatialReference)) {
            // TODO implement Project!!!
        }

        if (operatorSpatialReference != null && !operatorSpatialReference.equals(rightSpatialReference)) {
            // TODO implement Project!!!
        }

        // TODO this could throw an exception if unknown operator type provided
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString() );
        GeometryEncodingType encodingType = null;
        switch (operatorType) {
            case Project:
                ProjectionTransformation projectionTransformation = new ProjectionTransformation(leftSpatialReference, operatorSpatialReference);
                resultCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);
                break;
            case ImportFromJson:
                break;
            case ImportFromESRIShape:
                break;
            case Union:
                resultCursor = OperatorUnion.local().execute(leftCursor, operatorSpatialReference, null);
                break;
            case Difference:
                resultCursor = OperatorDifference.local().execute(leftCursor, rightCursor, operatorSpatialReference, null);
                break;
            case Proximity2D:
                break;
            case Relate:
                break;
            case Equals:
            case Disjoint:
            case Intersects:
            case Within:
            case Contains:
            case Crosses:
            case Touches:
            case Overlaps:
                HashMap<Integer, Boolean> result_map = ((OperatorSimpleRelation)OperatorFactoryLocal.getInstance().getOperator(operatorType)).execute(leftCursor.next(), rightCursor, operatorSpatialReference, null);
                if (result_map.size() == 1) {
                        operatorResultBuilder.setSpatialRelationship(result_map.get(0));
                } else {
                        operatorResultBuilder.putAllRelateMap(result_map);
                }
                break;
            case Buffer:
                // TODO clean this up
                double [] d = operatorRequest.getBufferDistancesList().stream().mapToDouble(Double::doubleValue).toArray();
                resultCursor = OperatorBuffer.local().execute(leftCursor, operatorSpatialReference, d, operatorRequest.getBufferUnionResult(), null);
                break;
            case Distance:
                operatorResultBuilder.setDistance(OperatorDistance.local().execute(leftCursor.next(), rightCursor.next(), null));
                break;
            case Intersection:
                // TODO hasIntersectionDimensionMask needs to be automagically generated
                if (operatorRequest.getIntersectionDimensionMask() == 0)
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, operatorSpatialReference, null);
                else
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, operatorSpatialReference, null, operatorRequest.getIntersectionDimensionMask());
                break;
            case Clip:
                Envelope2D envelope2D = __extractEnvelope2D(operatorRequest.getClipEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, operatorSpatialReference, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(operatorRequest.getCutConsiderTouch(), leftCursor.next(), (Polyline)rightCursor.next(), operatorSpatialReference, null);
                break;
            case DensifyByLength:
                // TODO document that this isn't smart. getDensifyMaxLength is in whatever unit your data comes in as
                resultCursor = OperatorDensifyByLength.local().execute(leftCursor, operatorRequest.getDensifyMaxLength(), null);
                break;
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                break;
            case GeodeticDensifyByLength:
                break;
            case ShapePreservingDensify:
                break;
            case GeodeticLength:
                break;
            case GeodeticArea:
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case Offset:
                resultCursor = OperatorOffset.local().execute(
                        leftCursor,
                        null,
                        operatorRequest.getOffsetDistance(),
                        OperatorOffset.JoinType.valueOf(operatorRequest.getOffsetJoinType()),
                        operatorRequest.getOffsetBevelRatio(),
                        operatorRequest.getOffsetFlattenError(), null);
                break;
            case Generalize:
                resultCursor = OperatorGeneralize.local().execute(leftCursor, operatorRequest.getGeneralizeMaxDeviation(), operatorRequest.getGeneralizeRemoveDegenerates(), null);
                break;
            case GeneralizeByArea:
                break;
            case ImportFromWkb:
                break;
            case ImportFromWkt:
                break;
            case ImportFromGeoJson:
                break;
            case SymmetricDifference:
                resultCursor = OperatorSymmetricDifference.local().execute(leftCursor, rightCursor, null, null);
                break;
            case ConvexHull:
                resultCursor = OperatorConvexHull.local().execute(leftCursor, operatorRequest.getConvexHullMerge(), null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
            case RandomPoints:
                break;
            case ExportToJson:
                break;
            case ExportToESRIShape:
                resultCursor = leftCursor;
                encodingType = GeometryEncodingType.esrishape;
                break;
            case ExportToWkb:
                resultCursor = leftCursor;
                encodingType = GeometryEncodingType.wkb;
                break;
            case ExportToWkt:
                resultCursor = leftCursor;
                encodingType = GeometryEncodingType.wkt;
                break;
            case ExportToGeoJson:
                resultCursor = leftCursor;
                encodingType = GeometryEncodingType.geojson;
                break;
        }

        if (resultSpatialReference != null && !resultSpatialReference.equals(operatorSpatialReference)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(operatorSpatialReference, resultSpatialReference);
            resultCursor = OperatorProject.local().execute(resultCursor, projectionTransformation, null);
        }

        if (resultCursor != null)
            operatorResultBuilder.setGeometry(__encodeGeometry(resultCursor, operatorRequest, encodingType));

        return operatorResultBuilder.build();
    }


    private static GeometryCursor __createGeometryCursor(ServiceGeometry serviceGeometry) throws IOException {
        return __extractGeometryCursor(serviceGeometry);
    }


    private static ServiceGeometry __decodeGeometry(Geometry geometry, SpatialReference spatialReference, GeometryEncodingType encoding_type) {
        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder().setGeometryEncodingType(encoding_type);
        switch (encoding_type) {
            case wkt:
                serviceGeometryBuilder.addGeometryString(OperatorExportToWkt.local().execute(0, geometry, null));
                break;
            case wkb:
                serviceGeometryBuilder.addGeometryBinary(ByteString.copyFrom(OperatorExportToWkb.local().execute(0, geometry, null)));
                break;
            case geojson:
                serviceGeometryBuilder.addGeometryString(OperatorExportToGeoJson.local().execute(spatialReference, geometry));
                break;
            case esrishape:
                serviceGeometryBuilder.addGeometryBinary(ByteString.copyFrom(OperatorExportToESRIShape.local().execute(0,geometry)));
                break;
            case UNRECOGNIZED:
                break;
        }

        // TODO set spatial reference!!
        return serviceGeometryBuilder.build();
    }


    private static SpatialReference __extractSpatialReference(ServiceGeometry serviceGeometry) {
        return serviceGeometry.hasSpatialReference() ? __extractSpatialReference(serviceGeometry.getSpatialReference()) : null;
    }


    private static SpatialReference __extractSpatialReference(ServiceSpatialReference serviceSpatialReference) {
        // TODO there seems to be a bug where hasWkid() is not getting generated. check back later
        if (serviceSpatialReference.getWkid() != 0)
            return SpatialReference.create(serviceSpatialReference.getWkid());
        else if (serviceSpatialReference.getEsriWkt().length() > 0)
            return SpatialReference.create(serviceSpatialReference.getEsriWkt());

        return null;
    }


    private static Envelope2D __extractEnvelope2D(ServiceEnvelope2D env) {
        return Envelope2D.construct(env.getXmin(), env.getYmin(), env.getXmax(), env.getYmax());
    }


    private static GeometryCursor __extractGeometryCursor(ServiceGeometry serviceGeometry) throws IOException {
        JsonFactory factory = new JsonFactory();
        GeometryCursor geometryCursor = null;
        List<ByteBuffer> byteBufferList = null;
        SimpleByteBufferCursor simpleByteBufferCursor = null;
        SimpleStringCursor simpleStringCursor = null;
        ProtocolStringList protocolStringList = null;
        switch (serviceGeometry.getGeometryEncodingType()) {
            case wkb:
                byteBufferList = serviceGeometry.getGeometryBinaryList().stream().map(com.google.protobuf.ByteString::asReadOnlyByteBuffer).collect(Collectors.toList());
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferList);
                geometryCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
                break;
            case esrishape:
                byteBufferList = serviceGeometry.getGeometryBinaryList().stream().map(com.google.protobuf.ByteString::asReadOnlyByteBuffer).collect(Collectors.toList());
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferList);
                geometryCursor = new OperatorImportFromESRIShapeCursor(0, 0, simpleByteBufferCursor);
                break;
            case wkt:
                protocolStringList = serviceGeometry.getGeometryStringList();
                simpleStringCursor = new SimpleStringCursor(protocolStringList.subList(0, protocolStringList.size()));
                geometryCursor = new OperatorImportFromWktCursor(0, simpleStringCursor);
                break;
            case geojson:
//                protocolStringList = serviceGeometry.getGeometryStringList();
                String jsonString = serviceGeometry.getGeometryString(0);
//                simpleStringCursor = new SimpleStringCursor(protocolStringList.subList(0, protocolStringList.size() - 1));
                // TODO no idea whats going on here
                JsonParser jsonParser = factory.createJsonParser(jsonString);
                JsonParserReader jsonParserReader = new JsonParserReader(jsonParser);
                SimpleJsonReaderCursor simpleJsonParserCursor = new SimpleJsonReaderCursor(jsonParserReader);
                MapGeometryCursor mapGeometryCursor = new OperatorImportFromJsonCursor(0, simpleJsonParserCursor);
                geometryCursor = new SimpleGeometryCursor(mapGeometryCursor);
        }
        return geometryCursor;
    }

    private static MapGeometry __extractGeometry(ServiceGeometry serviceGeometry) {
        MapGeometry mapGeometry = null;
        Geometry geometry = null;
        SpatialReference spatialReference = null;
        ByteBuffer byteBuffer = null;
        switch (serviceGeometry.getGeometryEncodingType()) {
            case wkt:
                geometry = OperatorImportFromWkt.local().execute(0, Geometry.Type.Unknown, serviceGeometry.getGeometryString(0), null);
                break;
            case wkb:
                byteBuffer = ByteBuffer.wrap(serviceGeometry.getGeometryBinary(0).toByteArray());
                geometry = OperatorImportFromWkb.local().execute(0, Geometry.Type.Unknown, byteBuffer, null);
                break;
            case geojson:
                mapGeometry = OperatorImportFromGeoJson.local().execute(0, Geometry.Type.Unknown, serviceGeometry.getGeometryString(0), null);
                break;
            case esrishape:
                byteBuffer = ByteBuffer.wrap(serviceGeometry.getGeometryBinary(0).toByteArray());
                geometry = OperatorImportFromESRIShape.local().execute(0, Geometry.Type.Unknown, byteBuffer);
                break;
            case UNRECOGNIZED:
                break;
        }
        if (mapGeometry == null) {
            // TODO this could be moved out of the method
            spatialReference = __extractSpatialReference(serviceGeometry);
            mapGeometry = new MapGeometry(geometry, spatialReference);
        }

        return mapGeometry;
    }
}
