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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Common utilities for the GeometryOperators demo.
 */
class SpatialReferenceGroup {
    SpatialReference leftSR;
    SpatialReference rightSR;
    SpatialReference resultSR;
    SpatialReference operatorSR;

    SpatialReferenceGroup(OperatorRequest operatorRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getOperationSpatialReference());

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getResultSpatialReference());

        if (operatorRequest.hasLeftGeometry() && operatorRequest.getLeftGeometry().hasSpatialReference()) {
            leftSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getLeftGeometry());
        } else {
            // assumes left cursor exists
            leftSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getLeftCursor());
        }

        if (operatorRequest.hasRightGeometry() && operatorRequest.getRightGeometry().hasSpatialReference()) {
            rightSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getRightGeometry());
        } else if (operatorRequest.hasRightCursor()){
            rightSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getRightCursor());
        }

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null
                && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null && (operatorRequest.hasRightGeometry() || operatorRequest.hasRightCursor()))
                rightSR = operatorSR;
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if ((operatorRequest.hasRightCursor() || operatorRequest.hasRightGeometry()) &&
                ((leftSR != null && rightSR == null) ||
                (leftSR == null && rightSR != null))) {
            throw new IllegalArgumentException("either both spatial references are local or neither");
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }
}

class ByteStringIterable implements Iterable<com.google.protobuf.ByteString> {
    ByteBufferCursor m_byteBufferCursor;
    ByteStringIterable(ByteBufferCursor byteBufferCursor) {
        m_byteBufferCursor = byteBufferCursor;
    }

    @Override
    public Iterator<ByteString> iterator() {
        return new Iterator<ByteString>() {
            @Override
            public boolean hasNext() {
                return m_byteBufferCursor.hasNext();
            }

            @Override
            public ByteString next() {
                return ByteString.copyFrom(m_byteBufferCursor.next());
            }
        };
    }
}

class StringIterable implements Iterable<String> {
    StringCursor m_stringCursor;
    StringIterable(StringCursor stringCursor) {
        m_stringCursor = stringCursor;
    }


    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return m_stringCursor.hasNext();
            }

            @Override
            public String next() {
                return m_stringCursor.next();
            }
        };
    }
}

public class GeometryOperatorsUtil {
    public static ServiceGeometry __encodeGeometry(GeometryCursor geometryCursor, OperatorRequest operatorRequest, GeometryEncodingType encodingType) {
        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder();


        // TODO not getting stubbed out due to grpc proto stubbing bug
        if (encodingType == null || encodingType == GeometryEncodingType.unknown) {
            if (operatorRequest.getResultsEncodingType() == GeometryEncodingType.unknown) {
                encodingType = GeometryEncodingType.wkb;
            } else {
                encodingType = operatorRequest.getResultsEncodingType();
            }
        }

        ByteStringIterable binaryStringIterable;
        StringIterable stringIterable;
        switch (encodingType) {
            case wkb:
                binaryStringIterable = new ByteStringIterable(new OperatorExportToWkbCursor(0, geometryCursor));
                serviceGeometryBuilder.addAllGeometryBinary(binaryStringIterable);
                break;
            case wkt:
                stringIterable = new StringIterable(new OperatorExportToWktCursor(0, geometryCursor, null));
                serviceGeometryBuilder.addAllGeometryString(stringIterable);
                break;
            case esrishape:
                binaryStringIterable = new ByteStringIterable(new OperatorExportToESRIShapeCursor(0, geometryCursor));
                serviceGeometryBuilder.addAllGeometryBinary(binaryStringIterable);
                break;
            case geojson:
                //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
                // TODO add Spatial reference
                stringIterable = new StringIterable(new OperatorExportToJsonCursor(null, geometryCursor));
                serviceGeometryBuilder.addAllGeometryString(stringIterable);
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
        // TODO There needs to be better tracking of geometry id throughout process
        serviceGeometryBuilder
                .setGeometryEncodingType(encodingType)
                .addAllGeometryId(operatorRequest.getLeftGeometry().getGeometryIdList())
                .setSpatialReference(operatorRequest.getResultSpatialReference());

        return serviceGeometryBuilder.build();
    }

    public static GeometryCursor __getLeftCursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor == null) {
            if (operatorRequest.hasLeftGeometry())
                leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometry());
            else
                // assumes there is always a left geometry
                leftCursor = cursorFromRequest(operatorRequest.getLeftCursor(), null, null);
        }

        // project left if needed
        if (srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.leftSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.leftSR, srGroup.operatorSR);
            leftCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);
        }

        return leftCursor;
    }

    public static GeometryCursor __getRightCursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor != null && rightCursor == null) {
            if (operatorRequest.hasRightGeometry())
                rightCursor = __createGeometryCursor(operatorRequest.getRightGeometry());
            else if (operatorRequest.hasRightCursor())
                rightCursor = cursorFromRequest(operatorRequest.getRightCursor(), null, null);
        }

        if (rightCursor != null && srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.rightSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.rightSR, srGroup.operatorSR);
            rightCursor = OperatorProject.local().execute(rightCursor, projectionTransformation, null);
        }
        return rightCursor;
    }

    public static OperatorResult nonCursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = __getLeftCursorFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightCursorFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case Proximity2D:
                break;
            case Relate:
                boolean result = OperatorRelate.local().execute(leftCursor.next(), rightCursor.next(), srGroup.operatorSR, operatorRequest.getDe9Im(), null);
                operatorResultBuilder.setSpatialRelationship(result);
                break;
            case Equals:
            case Disjoint:
            case Intersects:
            case Within:
            case Contains:
            case Crosses:
            case Touches:
            case Overlaps:
                HashMap<Integer, Boolean> result_map = ((OperatorSimpleRelation) OperatorFactoryLocal.getInstance().getOperator(operatorType)).execute(leftCursor.next(), rightCursor, srGroup.operatorSR, null);
                if (result_map.size() == 1) {
                    operatorResultBuilder.setSpatialRelationship(result_map.get(0));
                    operatorResultBuilder.putAllRelateMap(result_map);
                } else {
                    operatorResultBuilder.putAllRelateMap(result_map);
                }
                break;
            case Distance:
                operatorResultBuilder.setDistance(OperatorDistance.local().execute(leftCursor.next(), rightCursor.next(), null));
                break;
            case GeodeticLength:
                break;
            case GeodeticArea:
                break;
            default:
                throw new IllegalArgumentException();
        }
        return operatorResultBuilder.build();
    }

    public static GeometryCursor cursorFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = __getLeftCursorFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightCursorFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        GeometryCursor resultCursor = null;
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                List<Double> doubleList;
                if (operatorRequest.getBufferDistancesCount() > 0)
                    doubleList = operatorRequest.getBufferDistancesList();
                else
                    doubleList = operatorRequest.getGenericDoublesList().subList(0, 1);

                resultCursor = OperatorGeodesicBuffer.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        0,
                        doubleList.stream().mapToDouble(Double::doubleValue).toArray(),
                        Double.NaN,
                        false,
                        operatorRequest.getGenericBooleans(0),
                        null);
                break;
            case GeodeticDensifyByLength:
                resultCursor = OperatorGeodeticDensifyByLength.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getGenericDoubles(0),
                        operatorRequest.getGenericIntegers(0),
                        null);
                break;
            case ShapePreservingDensify:
                break;
            case GeneralizeByArea:
                resultCursor = OperatorGeneralizeByArea.local().execute(
                        leftCursor,
                        operatorRequest.getGenericDoubles(0),
                        operatorRequest.getGenericBooleans(0),
                        GeneralizeType.valueOf(operatorRequest.getGenericStrings(0)),
                        srGroup.operatorSR,
                        null);
                break;
            case Project:
                resultCursor = leftCursor;
                break;
            case Union:
                resultCursor = OperatorUnion.local().execute(leftCursor, srGroup.operatorSR, null);
                break;
            case Difference:
                resultCursor = OperatorDifference.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case Buffer:
                // TODO clean this up
                //                GeometryCursor inputGeometries,
                //                SpatialReference sr,
                //                double[] distances,
                //                double max_deviation,
                //                int max_vertices_in_full_circle,
                //                boolean b_union,
                //                ProgressTracker progressTracker
                //
                int maxverticesFullCircle = operatorRequest.getMaxVerticesInFullCircle();
                if (maxverticesFullCircle == 0)
                    maxverticesFullCircle = 96;

                double[] d;
                if (operatorRequest.getBufferDistancesCount() == 0) {
                    d = operatorRequest.getGenericDoublesList().stream().mapToDouble(Double::doubleValue).toArray();
                } else {
                    d = operatorRequest.getBufferDistancesList().stream().mapToDouble(Double::doubleValue).toArray();
                }
                resultCursor = OperatorBuffer.local().execute(leftCursor,
                                                              srGroup.operatorSR,
                                                              d,
                                                              Double.NaN,
                                                              maxverticesFullCircle,
                                                              operatorRequest.getBufferUnionResult(),
                                                              null);

                //                resultCursor = OperatorBuffer.local().execute(leftCursor, srGroup.operatorSR, d, operatorRequest.getBufferUnionResult(), null);
                break;
            case Intersection:
                // TODO hasIntersectionDimensionMask needs to be automagically generated
                if (operatorRequest.getIntersectionDimensionMask() == 0)
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                else
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null, operatorRequest.getIntersectionDimensionMask());
                break;
            case Clip:
                Envelope2D envelope2D = __extractEnvelope2D(operatorRequest.getClipEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, srGroup.operatorSR, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(operatorRequest.getCutConsiderTouch(), leftCursor.next(), (Polyline) rightCursor.next(), srGroup.operatorSR, null);
                break;
            case DensifyByLength:
                double densifyMax = operatorRequest.getGenericDoublesCount() == 0 ? operatorRequest.getDensifyMaxLength() : operatorRequest.getGenericDoubles(0);
                resultCursor = OperatorDensifyByLength.local().execute(leftCursor, densifyMax, null);
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(leftCursor, null, operatorRequest.getSimplifyForce(), null);
                break;
            case Offset:
                double offsetDistance = 0;
                if (operatorRequest.getGenericDoublesCount() > 0) {
                    offsetDistance = operatorRequest.getGenericDoubles(0);
                } else {
                    offsetDistance = operatorRequest.getOffsetDistance();
                }

                resultCursor = OperatorOffset.local().execute(
                        leftCursor,
                        null,
                        offsetDistance,
                        OperatorOffset.JoinType.valueOf(operatorRequest.getOffsetJoinType()),
                        operatorRequest.getOffsetBevelRatio(),
                        operatorRequest.getOffsetFlattenError(), null);
                break;
            case Generalize:
                resultCursor = OperatorGeneralize.local().execute(
                        leftCursor,
                        operatorRequest.getGeneralizeMaxDeviation(),
                        operatorRequest.getGeneralizeRemoveDegenerates(),
                        null);
                break;
            case SymmetricDifference:
                resultCursor = OperatorSymmetricDifference.local().execute(leftCursor, rightCursor, null, null);
                break;
            case ConvexHull:
                boolean convexMerge = (operatorRequest.getGenericBooleansCount() > 0 && operatorRequest.getGenericBooleans(0)) || operatorRequest.getConvexHullMerge() ? true : false;
                resultCursor = OperatorConvexHull.local().execute(leftCursor, convexMerge, null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
            case EnclosingCircle:
                resultCursor = new OperatorEnclosingCircleCursor(leftCursor, srGroup.operatorSR, null);
                break;
            case RandomPoints:
                resultCursor = new OperatorRandomPointsCursor(
                        leftCursor,
                        operatorRequest.getGenericDoublesList().stream().mapToDouble(Double::doubleValue).toArray(),
                        operatorRequest.getGenericIntegers(0),
                        srGroup.operatorSR,
                        null);
                break;
            default:
                throw new IllegalArgumentException();

        }

        if (srGroup.resultSR != null && !srGroup.resultSR.equals(srGroup.operatorSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.operatorSR, srGroup.resultSR);
            resultCursor = OperatorProject.local().execute(resultCursor, projectionTransformation, null);
        }

        return resultCursor;
    }

    public static OperatorResult initExecuteOperatorEx(OperatorRequest operatorRequest) throws IOException {
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        GeometryEncodingType encodingType = GeometryEncodingType.unknown;
        GeometryCursor resultCursor = null;
        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        switch (operatorType) {
            // results
            case Proximity2D:
            case Relate:
            case Equals:
            case Disjoint:
            case Intersects:
            case Within:
            case Contains:
            case Crosses:
            case Touches:
            case Overlaps:
            case Distance:
            case GeodeticLength:
            case GeodeticArea:
                return nonCursorFromRequest(operatorRequest, null, null);

            // cursors
            case Project:
            case Union:
            case Difference:
            case Buffer:
            case Intersection:
            case Clip:
            case Cut:
            case DensifyByLength:
            case DensifyByAngle:
            case LabelPoint:
            case GeodesicBuffer:
            case GeodeticDensifyByLength:
            case ShapePreservingDensify:
            case Simplify:
            case SimplifyOGC:
            case Offset:
            case Generalize:
            case GeneralizeByArea:
            case SymmetricDifference:
            case ConvexHull:
            case Boundary:
            case RandomPoints:
            case EnclosingCircle:
                resultCursor = cursorFromRequest(operatorRequest, null, null);
                break;
            case ExportToESRIShape:
                encodingType = GeometryEncodingType.esrishape;
                break;
            case ExportToWkb:
                encodingType = GeometryEncodingType.wkb;
                break;
            case ExportToWkt:
                encodingType = GeometryEncodingType.wkt;
                break;
            case ExportToGeoJson:
                encodingType = GeometryEncodingType.geojson;
                break;
        }
        // If the only operation used by the user is to export to one of the formats then enter this if statement and
        // assign the left cursor to the result cursor
        if (encodingType != GeometryEncodingType.unknown) {
            resultCursor = __createGeometryCursor(operatorRequest.getLeftGeometry());
        }
        operatorResultBuilder.setGeometry(__encodeGeometry(resultCursor, operatorRequest, encodingType));
        return operatorResultBuilder.build();
    }


    protected static GeometryCursor __createGeometryCursor(ServiceGeometry serviceGeometry) throws IOException {
        return __extractGeometryCursor(serviceGeometry);
    }


    protected static SpatialReference __extractSpatialReference(ServiceGeometry serviceGeometry) {
        return serviceGeometry.hasSpatialReference() ? __extractSpatialReference(serviceGeometry.getSpatialReference()) : null;
    }


    protected static SpatialReference __extractSpatialReferenceCursor(OperatorRequest operatorRequestCursor) {
        if (operatorRequestCursor.hasResultSpatialReference())
            return __extractSpatialReference(operatorRequestCursor.getResultSpatialReference());
        else if (operatorRequestCursor.hasOperationSpatialReference())
            return __extractSpatialReference(operatorRequestCursor.getOperationSpatialReference());
        else if (operatorRequestCursor.hasLeftCursor())
            return __extractSpatialReferenceCursor(operatorRequestCursor.getLeftCursor());
        else if (operatorRequestCursor.hasLeftGeometry())
            return __extractSpatialReference(operatorRequestCursor.getLeftGeometry().getSpatialReference());
        return null;
    }


    protected static SpatialReference __extractSpatialReference(ServiceSpatialReference serviceSpatialReference) {
        // TODO there seems to be a bug where hasWkid() is not getting generated. check back later
        if (serviceSpatialReference.getWkid() != 0)
            return SpatialReference.create(serviceSpatialReference.getWkid());
        else if (serviceSpatialReference.getEsriWkt().length() > 0)
            return SpatialReference.create(serviceSpatialReference.getEsriWkt());

        return null;
    }


    protected static Envelope2D __extractEnvelope2D(ServiceEnvelope2D env) {
        return Envelope2D.construct(env.getXmin(), env.getYmin(), env.getXmax(), env.getYmax());
    }


    protected static GeometryCursor __extractGeometryCursor(ServiceGeometry serviceGeometry) throws IOException {
        GeometryCursor geometryCursor = null;

        ArrayDeque<ByteBuffer> byteBufferArrayDeque = null;
        ArrayDeque<String> stringArrayDeque = null;
        SimpleByteBufferCursor simpleByteBufferCursor = null;
        SimpleStringCursor simpleStringCursor = null;
        switch (serviceGeometry.getGeometryEncodingType()) {
            case wkb:
                byteBufferArrayDeque = serviceGeometry
                        .getGeometryBinaryList()
                        .stream()
                        .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                        .collect(Collectors.toCollection(ArrayDeque::new));
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
                geometryCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
                break;
            case esrishape:
                byteBufferArrayDeque = serviceGeometry
                        .getGeometryBinaryList()
                        .stream()
                        .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                        .collect(Collectors.toCollection(ArrayDeque::new));
                simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
                geometryCursor = new OperatorImportFromESRIShapeCursor(0, 0, simpleByteBufferCursor);
                break;
            case wkt:
                stringArrayDeque = new ArrayDeque<>(serviceGeometry.getGeometryStringList());
                simpleStringCursor = new SimpleStringCursor(stringArrayDeque);
                geometryCursor = new OperatorImportFromWktCursor(0, simpleStringCursor);
                break;
            case geojson:
                JsonFactory factory = new JsonFactory();
                String jsonString = serviceGeometry.getGeometryString(0);
                // TODO no idea whats going on here
                JsonParser jsonParser = factory.createJsonParser(jsonString);
                JsonParserReader jsonParserReader = new JsonParserReader(jsonParser);
                SimpleJsonReaderCursor simpleJsonParserCursor = new SimpleJsonReaderCursor(jsonParserReader);
                MapGeometryCursor mapGeometryCursor = new OperatorImportFromJsonCursor(0, simpleJsonParserCursor);
                geometryCursor = new SimpleGeometryCursor(mapGeometryCursor);
        }
        return geometryCursor;
    }
}
