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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common utilities for the GeometryOperators demo.
 */
class SpatialReferenceGroup {
    SpatialReference leftSR;
    SpatialReference rightSR;
    SpatialReference resultSR;
    SpatialReference operatorSR;

    static SpatialReference spatialFromGeometry(GeometryBagData geometryBagData,
                                                OperatorRequest geometryRequest) {
        if (geometryBagData.hasSpatialReference()) {
            return GeometryOperatorsUtil.__extractSpatialReference(geometryBagData);
        }

        return GeometryOperatorsUtil.__extractSpatialReferenceCursor(geometryRequest);
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryBagData geometryBagData,
                          OperatorRequest geometryRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest1.getResultSpatialReference());

        leftSR = SpatialReferenceGroup.spatialFromGeometry(geometryBagData, geometryRequest);

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryBagData leftGeometryBagData,
                          OperatorRequest leftGeometryRequest,
                          GeometryBagData rightGeometryBagData,
                          OperatorRequest rightGeometryRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest1.getResultSpatialReference());

        leftSR = SpatialReferenceGroup.spatialFromGeometry(leftGeometryBagData, leftGeometryRequest);

        rightSR = SpatialReferenceGroup.spatialFromGeometry(rightGeometryBagData, rightGeometryRequest);

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null) {
                rightSR = operatorSR;
            }
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if (((leftSR != null && rightSR == null) || (leftSR == null && rightSR != null))) {
            throw new IllegalArgumentException("either both spatial references are local or neither");
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }

    SpatialReferenceGroup(OperatorRequest operatorRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getOperationSpatialReference());

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getResultSpatialReference());

        if (operatorRequest.hasLeftGeometryBag() && operatorRequest.getLeftGeometryBag().hasSpatialReference()) {
            leftSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getLeftGeometryBag());
        } else if (operatorRequest.hasGeometryBag() && operatorRequest.getGeometryBag().hasSpatialReference()) {
            leftSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getGeometryBag());
        } else if (operatorRequest.hasLeftGeometryRequest()) {
            leftSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getLeftGeometryRequest());
        } else {
            // assumes left cursor exists
            leftSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getGeometryRequest());
        }

        if (operatorRequest.hasRightGeometryBag() && operatorRequest.getRightGeometryBag().hasSpatialReference()) {
            rightSR = GeometryOperatorsUtil.__extractSpatialReference(operatorRequest.getRightGeometryBag());
        } else if (operatorRequest.hasRightGeometryRequest()){
            rightSR = GeometryOperatorsUtil.__extractSpatialReferenceCursor(operatorRequest.getRightGeometryRequest());
        }

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null
                && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null && (operatorRequest.hasRightGeometryBag() || operatorRequest.hasRightGeometryRequest())) {
                rightSR = operatorSR;
            }
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if ((operatorRequest.hasRightGeometryRequest() || operatorRequest.hasRightGeometryBag()) &&
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
    private StringCursor m_stringCursor;
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
    private static GeometryBagData __encodeGeometry(GeometryCursor geometryCursor, OperatorRequest operatorRequest, GeometryEncodingType encodingType) {
        GeometryBagData.Builder geometryBagBuilder = GeometryBagData.newBuilder();


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
                geometryBagBuilder.addAllWkb(binaryStringIterable);
                break;
            case wkt:
                stringIterable = new StringIterable(new OperatorExportToWktCursor(0, geometryCursor, null));
                geometryBagBuilder.addAllWkt(stringIterable);
                break;
            case esrishape:
                binaryStringIterable = new ByteStringIterable(new OperatorExportToESRIShapeCursor(0, geometryCursor));
                geometryBagBuilder.addAllEsriShape(binaryStringIterable);
                break;
            case geojson:
                //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
                // TODO add Spatial reference
                stringIterable = new StringIterable(new OperatorExportToJsonCursor(null, geometryCursor));
                geometryBagBuilder.addAllGeojson(stringIterable);
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the resultSR into the spatial reference
        geometryBagBuilder
                .setGeometryEncodingType(encodingType)
                .setSpatialReference(operatorRequest.getResultSpatialReference());

        // TODO There needs to be better tracking of geometry id throughout process
        // the only way that input geometry ids should be carried onto the result is if the input is not a left geometry, but just a geometry
        if (operatorRequest.hasGeometryBag()) {
            geometryBagBuilder.addAllGeometryIds(operatorRequest.getGeometryBag().getGeometryIdsList());
        }

        return geometryBagBuilder.build();
    }


    private static GeometryCursor __getLeftGeometryRequestFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor == null) {
            if (operatorRequest.hasLeftGeometryBag()) {
                leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometryBag());
            } else if (operatorRequest.hasGeometryBag()) {
                leftCursor = __createGeometryCursor(operatorRequest.getGeometryBag());
            } else if (operatorRequest.hasLeftGeometryRequest()) {
                leftCursor = cursorFromRequest(operatorRequest.getLeftGeometryRequest(), null, null);
            } else {
                // assumes there is always a nested request if none of the above worked
                leftCursor = cursorFromRequest(operatorRequest.getGeometryRequest(), null, null);
            }
        }

        // project left if needed
        if (srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.leftSR)) {
            ProjectionTransformation projectionTransformation = new ProjectionTransformation(srGroup.leftSR, srGroup.operatorSR);
            leftCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);
        }

        return leftCursor;
    }

    private static GeometryCursor __getRightGeometryRequestFromRequest(
            OperatorRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor != null && rightCursor == null) {
            if (operatorRequest.hasRightGeometryBag()) {
                rightCursor = __createGeometryCursor(operatorRequest.getRightGeometryBag());
            } else if (operatorRequest.hasRightGeometryRequest()) {
                rightCursor = cursorFromRequest(operatorRequest.getRightGeometryRequest(), null, null);
            }
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
        leftCursor = __getLeftGeometryRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightGeometryRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case Proximity2D:
                break;
            case Relate:
                boolean result = OperatorRelate.local().execute(leftCursor.next(), rightCursor.next(), srGroup.operatorSR, operatorRequest.getRelateParams().getDe9Im(), null);
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
        leftCursor = __getLeftGeometryRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = __getRightGeometryRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        GeometryCursor resultCursor = null;
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                List<Double> doubleList = operatorRequest.getBufferParams().getDistancesList();
                double maxDeviations = Double.NaN;
                if (operatorRequest.getBufferParams().getMaxDeviationsCount() > 0) {
                    maxDeviations = operatorRequest.getBufferParams().getMaxDeviations(0);
                }
                resultCursor = OperatorGeodesicBuffer.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        0,
                        doubleList.stream().mapToDouble(Double::doubleValue).toArray(),
                        maxDeviations,
                        false,
                        operatorRequest.getBufferParams().getUnionResult(),
                        null);
                break;
            case GeodeticDensifyByLength:
                resultCursor = OperatorGeodeticDensifyByLength.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getDensifyParams().getMaxLength(),
                        0,
                        null);
                break;
            case ShapePreservingDensify:
                break;
            case GeneralizeByArea:
                resultCursor = OperatorGeneralizeByArea.local().execute(
                        leftCursor,
                        operatorRequest.getGeneralizeParams().getMaxDeviation(),
                        operatorRequest.getGeneralizeParams().getRemoveDegenerates(),
                        GeneralizeType.ResultContainsOriginal,
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
                //                GeometryCursor inputGeometryBag,
                //                SpatialReference sr,
                //                double[] distances,
                //                double max_deviation,
                //                int max_vertices_in_full_circle,
                //                boolean b_union,
                //                ProgressTracker progressTracker
                //
                int maxverticesFullCircle = operatorRequest.getBufferParams().getMaxVerticesInFullCircle();
                if (maxverticesFullCircle == 0) {
                    maxverticesFullCircle = 96;
                }

                double[] d = operatorRequest.getBufferParams().getDistancesList().stream().mapToDouble(Double::doubleValue).toArray();
                resultCursor = OperatorBuffer.local().execute(leftCursor,
                                                              srGroup.operatorSR,
                                                              d,
                                                              Double.NaN,
                                                              maxverticesFullCircle,
                                                              operatorRequest.getBufferParams().getUnionResult(),
                                                              null);

                break;
            case Intersection:
                // TODO hasIntersectionDimensionMask needs to be automagically generated
                if (operatorRequest.hasIntersectionParams() && operatorRequest.getIntersectionParams().getDimensionMask() != 0)
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null, operatorRequest.getIntersectionParams().getDimensionMask());
                else
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case Clip:
                Envelope2D envelope2D = __extractEnvelope2D(operatorRequest.getClipParams().getEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, srGroup.operatorSR, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(operatorRequest.getCutParams().getConsiderTouch(), leftCursor.next(), (Polyline) rightCursor.next(), srGroup.operatorSR, null);
                break;
            case DensifyByLength:
                resultCursor = OperatorDensifyByLength.local().execute(leftCursor, operatorRequest.getDensifyParams().getMaxLength(), null);
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(leftCursor, srGroup.operatorSR, operatorRequest.getSimplifyParams().getForce(), null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(leftCursor, srGroup.operatorSR, operatorRequest.getSimplifyParams().getForce(), null);
                break;
            case Offset:
                resultCursor = OperatorOffset.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getOffsetParams().getDistance(),
                        OperatorOffset.JoinType.valueOf(operatorRequest.getOffsetParams().getJoinType().toString()),
                        operatorRequest.getOffsetParams().getBevelRatio(),
                        operatorRequest.getOffsetParams().getFlattenError(), null);
                break;
            case Generalize:
                resultCursor = OperatorGeneralize.local().execute(
                        leftCursor,
                        operatorRequest.getGeneralizeParams().getMaxDeviation(),
                        operatorRequest.getGeneralizeParams().getRemoveDegenerates(),
                        null);
                break;
            case SymmetricDifference:
                resultCursor = OperatorSymmetricDifference.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                break;
            case ConvexHull:
                resultCursor = OperatorConvexHull.local().execute(leftCursor, operatorRequest.getConvexParams().getMerge(), null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
            case EnclosingCircle:
                resultCursor = new OperatorEnclosingCircleCursor(leftCursor, srGroup.operatorSR, null);
                break;
            case RandomPoints:
                double[] pointsPerSqrKm = operatorRequest.getRandomPointsParams().getPointsPerSquareKmList().stream().mapToDouble(Double::doubleValue).toArray();
                long seed = operatorRequest.getRandomPointsParams().getSeed();
                resultCursor = new OperatorRandomPointsCursor(
                        leftCursor,
                        pointsPerSqrKm,
                        seed,
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
            if (operatorRequest.hasLeftGeometryBag()) {
                resultCursor = __createGeometryCursor(operatorRequest.getLeftGeometryBag());
            } else {
                resultCursor = __createGeometryCursor(operatorRequest.getGeometryBag());
            }
        }
        operatorResultBuilder.setGeometryBag(__encodeGeometry(resultCursor, operatorRequest, encodingType));
        return operatorResultBuilder.build();
    }


    private static GeometryCursor __createGeometryCursor(GeometryBagData geometryBag) throws IOException {
        return __extractGeometryCursor(geometryBag);
    }


    protected static SpatialReference __extractSpatialReference(GeometryBagData geometryBag) {
        return geometryBag.hasSpatialReference() ? __extractSpatialReference(geometryBag.getSpatialReference()) : null;
    }


    protected static SpatialReference __extractSpatialReferenceCursor(OperatorRequest operatorRequestCursor) {
        if (operatorRequestCursor.hasResultSpatialReference()) {
            return __extractSpatialReference(operatorRequestCursor.getResultSpatialReference());
        } else if (operatorRequestCursor.hasOperationSpatialReference()) {
            return __extractSpatialReference(operatorRequestCursor.getOperationSpatialReference());
        } else if (operatorRequestCursor.hasLeftGeometryRequest()) {
            return __extractSpatialReferenceCursor(operatorRequestCursor.getLeftGeometryRequest());
        } else if (operatorRequestCursor.hasLeftGeometryBag()) {
            return __extractSpatialReference(operatorRequestCursor.getLeftGeometryBag().getSpatialReference());
        } else if (operatorRequestCursor.hasGeometryRequest()) {
            return __extractSpatialReferenceCursor(operatorRequestCursor.getGeometryRequest());
        } else if (operatorRequestCursor.hasGeometryBag()) {
            return __extractSpatialReference(operatorRequestCursor.getGeometryBag().getSpatialReference());
        }
        return null;
    }


    protected static SpatialReference __extractSpatialReference(SpatialReferenceData serviceSpatialReference) {
        // TODO there seems to be a bug where hasWkid() is not getting generated. check back later
        if (serviceSpatialReference.getWkid() != 0)
            return SpatialReference.create(serviceSpatialReference.getWkid());
        else if (serviceSpatialReference.getEsriWkt().length() > 0)
            return SpatialReference.create(serviceSpatialReference.getEsriWkt());

        return null;
    }


    private static Envelope2D __extractEnvelope2D(EnvelopeData env) {
        return Envelope2D.construct(env.getXmin(), env.getYmin(), env.getXmax(), env.getYmax());
    }


    private static GeometryCursor __extractGeometryCursor(GeometryBagData geometryBag) throws IOException {
        GeometryCursor geometryCursor = null;

        ArrayDeque<ByteBuffer> byteBufferArrayDeque = null;
        ArrayDeque<String> stringArrayDeque = null;
        SimpleByteBufferCursor simpleByteBufferCursor = null;
        SimpleStringCursor simpleStringCursor = null;
        if (geometryBag.getWkbCount() > 0) {
            byteBufferArrayDeque = geometryBag
                    .getWkbList()
                    .stream()
                    .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                    .collect(Collectors.toCollection(ArrayDeque::new));
            simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
            geometryCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
        } else if (geometryBag.getEsriShapeCount() > 0) {
            byteBufferArrayDeque = geometryBag
                    .getEsriShapeList()
                    .stream()
                    .map(com.google.protobuf.ByteString::asReadOnlyByteBuffer)
                    .collect(Collectors.toCollection(ArrayDeque::new));
            simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque);
            geometryCursor = new OperatorImportFromESRIShapeCursor(0, 0, simpleByteBufferCursor);
        } else if (geometryBag.getWktCount() > 0) {
            stringArrayDeque = new ArrayDeque<>(geometryBag.getWktList());
            simpleStringCursor = new SimpleStringCursor(stringArrayDeque);
            geometryCursor = new OperatorImportFromWktCursor(0, simpleStringCursor);
        } else if (geometryBag.getGeojsonCount() > 0) {
            JsonFactory factory = new JsonFactory();
            String jsonString = geometryBag.getGeojson(0);
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
