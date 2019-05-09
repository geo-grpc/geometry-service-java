/*
Copyright 2017-2018 Echo Park Labs

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

import java.io.IOException;
import java.util.*;

enum Side {
    Left,
    Right
}

/**
 * Common utilities for the GeometryService demo.
 */
class SpatialReferenceGroup {
    SpatialReference leftSR;
    SpatialReference rightSR;
    SpatialReference resultSR;
    SpatialReference operatorSR;

    static SpatialReference spatialFromGeometry(GeometryData geometryBagData,
                                                GeometryRequest geometryRequest) {
        if (geometryBagData.hasSpatialReference()) {
            return GeometryServiceUtil.extractSpatialReference(geometryBagData);
        }

        return GeometryServiceUtil.extractSpatialReferenceCursor(geometryRequest);
    }

    SpatialReferenceGroup(GeometryRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryData geometryBagData,
                          GeometryRequest geometryRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryServiceUtil.extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryServiceUtil.extractSpatialReference(operatorRequest1.getResultSpatialReference());

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

    SpatialReferenceGroup(GeometryRequest operatorRequest1,
                          SpatialReferenceData paramsSR,
                          GeometryData leftGeometryBagData,
                          GeometryRequest leftGeometryRequest,
                          GeometryData rightGeometryBagData,
                          GeometryRequest rightGeometryRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryServiceUtil.extractSpatialReference(paramsSR);

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryServiceUtil.extractSpatialReference(operatorRequest1.getResultSpatialReference());

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

    SpatialReferenceGroup(GeometryRequest operatorRequest) {
        // optional: this is the spatial reference for performing the geometric operation
        operatorSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getOperationSpatialReference());

        // optionalish: this is the final spatial reference for the resultSR (project after operatorSR)
        resultSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getResultSpatialReference());

//        if (operatorRequest.hasLeftGeometryBag() && operatorRequest.getLeftGeometryBag().hasSpatialReference()) {
//            leftSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getLeftGeometryBag());
//        } else if (operatorRequest.hasGeometryBag() && operatorRequest.getGeometryBag().hasSpatialReference()) {
//            leftSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getGeometryBag());
        if (operatorRequest.hasLeftGeometry() && operatorRequest.getLeftGeometry().hasSpatialReference()) {
            leftSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getLeftGeometry());
        } else if (operatorRequest.hasGeometry() && operatorRequest.getGeometry().hasSpatialReference()) {
            leftSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getGeometry());
        } else if (operatorRequest.hasLeftGeometryRequest()) {
            leftSR = GeometryServiceUtil.extractSpatialReferenceCursor(operatorRequest.getLeftGeometryRequest());
        } else {
            // assumes left cursor exists
            leftSR = GeometryServiceUtil.extractSpatialReferenceCursor(operatorRequest.getGeometryRequest());
        }

//        if (operatorRequest.hasRightGeometryBag() && operatorRequest.getRightGeometryBag().hasSpatialReference()) {
//            rightSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getRightGeometryBag());
        if (operatorRequest.hasRightGeometry() && operatorRequest.getRightGeometry().hasSpatialReference()) {
            rightSR = GeometryServiceUtil.extractSpatialReference(operatorRequest.getRightGeometry());
        } else if (operatorRequest.hasRightGeometryRequest()){
            rightSR = GeometryServiceUtil.extractSpatialReferenceCursor(operatorRequest.getRightGeometryRequest());
        }

        // TODO, there are possibilities for error in here. Also possiblities for too many assumptions. ass of you an me.
        // if there is a rightSR and a leftSR geometry but no operatorSR spatial reference, then set operatorSpatialReference
        if (operatorSR == null && leftSR != null
                && (rightSR == null || leftSR.equals(rightSR))) {
            operatorSR = leftSR;
        }

        if (leftSR == null) {
            leftSR = operatorSR;
            if (rightSR == null && (operatorRequest.hasRightGeometry() || operatorRequest.hasRightGeometryRequest())) {
                rightSR = operatorSR;
            }
        }

        // TODO improve geometry to work with local spatial references. This is super ugly as it stands
        if ((operatorRequest.hasRightGeometryRequest() || operatorRequest.hasRightGeometry()) &&
                ((leftSR != null && rightSR == null) ||
                        (leftSR == null && rightSR != null))) {
            throw new IllegalArgumentException("either both spatial references are local or neither");
        }

        // if there is no resultSpatialReference set it to be the operatorSpatialReference
        if (resultSR == null) {
            resultSR = operatorSR;
        }
    }

    static SpatialReferenceData createSpatialReferenceData(SpatialReference spatialReference) {
        if (spatialReference.getID() != 0) {
            return SpatialReferenceData.newBuilder().setWkid(spatialReference.getID()).build();
        } else if (spatialReference.getProj4().length() > 0) {
            return SpatialReferenceData.newBuilder().setProj4(spatialReference.getProj4()).build();
        } else if (spatialReference.getText().length() > 0) {
            return SpatialReferenceData.newBuilder().setEsriWkt(spatialReference.getText()).build();
        }
        return null;
    }

    public SpatialReferenceData getFinalSpatialRef() {
        if (resultSR != null) {
            return createSpatialReferenceData(resultSR);
        } else if (operatorSR != null) {
            return createSpatialReferenceData(operatorSR);
        } else if (leftSR != null) {
            return createSpatialReferenceData(leftSR);
        }
        return null;
    }
}

class GeometryResponsesIterator implements Iterator<GeometryResponse> {
    private StringCursor m_stringCursor = null;
    private ByteBufferCursor m_byteBufferCursor = null;
    private GeometryCursor m_geometryCursor = null;
    private GeometryEncodingType m_encodingType = GeometryEncodingType.wkb;
    private SpatialReferenceData m_spatialReferenceData;
    private boolean m_bForceCompact;

    private GeometryResponse m_precookedResult = null;
    private boolean m_bPrecookedRetrieved = false;

    GeometryResponsesIterator(GeometryResponse operatorResult) {
        m_precookedResult = operatorResult;
    }

    GeometryResponsesIterator(GeometryCursor geometryCursor,
                            GeometryRequest operatorRequest,
                            GeometryEncodingType geometryEncodingType,
                            boolean bForceCompact) {
        m_bForceCompact = bForceCompact;
        m_encodingType = geometryEncodingType;
        SpatialReferenceGroup spatialRefGroup = new SpatialReferenceGroup(operatorRequest);
        m_spatialReferenceData = spatialRefGroup.getFinalSpatialRef();

        if (m_encodingType == null || m_encodingType == GeometryEncodingType.unknown) {
            if (operatorRequest.getResultEncodingType() == GeometryEncodingType.unknown) {
                m_encodingType = GeometryEncodingType.wkb;
            } else {
                m_encodingType = operatorRequest.getResultEncodingType();
            }
        }

        switch (m_encodingType) {
            case unknown:
            case wkb:
                m_byteBufferCursor = new OperatorExportToWkbCursor(0, geometryCursor);
                break;
            case wkt:
                m_stringCursor = new OperatorExportToWktCursor(0, geometryCursor, null);
                break;
            case geojson:
                m_stringCursor = new OperatorExportToGeoJsonCursor(GeoJsonExportFlags.geoJsonExportSkipCRS, null, geometryCursor);
                break;
            case esrishape:
                m_byteBufferCursor = new OperatorExportToESRIShapeCursor(0, geometryCursor);
                break;
            case envelope_type:
                m_geometryCursor = geometryCursor;
            default:
                break;
        }
    }


    @Override
    public boolean hasNext() {
        if (m_precookedResult != null && !m_bPrecookedRetrieved) {
            return true;
        }

        return (m_byteBufferCursor != null && m_byteBufferCursor.hasNext()) || (m_stringCursor != null && m_stringCursor.hasNext()) || (m_geometryCursor != null && m_geometryCursor.hasNext());
    }

    @Override
    public GeometryResponse next() {
        if (m_precookedResult != null) {
            m_bPrecookedRetrieved = true;
            GeometryResponse tempResults = m_precookedResult;
            m_precookedResult = null;
            return tempResults;
        }


        GeometryData.Builder geometryBuilder = GeometryData
                .newBuilder();
        if (m_spatialReferenceData != null) {
            geometryBuilder.setSpatialReference(m_spatialReferenceData);
        }


        while (hasNext()) {
            switch (m_encodingType) {
                case unknown:
                case wkb:
                    geometryBuilder.setWkb(ByteString.copyFrom(m_byteBufferCursor.next()));
                    geometryBuilder.setGeometryId(m_byteBufferCursor.getByteBufferID());
                    //        TODO add feature IDs
                    break;
                case wkt:
                    geometryBuilder.setWkt(m_stringCursor.next());
                    geometryBuilder.setGeometryId(m_stringCursor.getID());
                    //        TODO add feature IDs
                    break;
                case geojson:
                    geometryBuilder.setGeojson(m_stringCursor.next());
                    geometryBuilder.setGeometryId(m_stringCursor.getID());
                    //        TODO add feature IDs
                    break;
                case esrishape:
                    geometryBuilder.setEsriShape(ByteString.copyFrom(m_byteBufferCursor.next()));
                    geometryBuilder.setGeometryId(m_byteBufferCursor.getByteBufferID());
                    //        TODO add feature IDs
                    break;
                case envelope_type:
                    Envelope2D envelope2D = new Envelope2D();
                    m_geometryCursor.next().queryEnvelope2D(envelope2D);
                    EnvelopeData.Builder envBuilder = EnvelopeData
                            .newBuilder()
                            .setXmin(envelope2D.xmin)
                            .setYmin(envelope2D.ymin)
                            .setXmax(envelope2D.xmax)
                            .setYmax(envelope2D.ymax);
                    if (m_spatialReferenceData != null) {
                        envBuilder.setSpatialReference(m_spatialReferenceData);
                    }

                    return GeometryResponse.newBuilder().setEnvelope(envBuilder).build();
                default:
                    break;
            }

            // the while loop will continue if all geometries are to be compact into one bag
            if (!m_bForceCompact) {
                break;
            }
        }

        return GeometryResponse.newBuilder().setGeometry(geometryBuilder).build();
    }
}

public class GeometryServiceUtil {
    private static GeometryCursor getLeftGeometryRequestFromRequest(
            GeometryRequest operatorRequest,
            GeometryCursor leftCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor == null) {
            leftCursor = createGeometryCursor(operatorRequest, Side.Left);
            if (leftCursor == null && operatorRequest.hasLeftGeometryRequest()) {
                leftCursor = cursorFromRequest(operatorRequest.getLeftGeometryRequest(), null, null);
            } else if (leftCursor == null && operatorRequest.hasGeometryRequest()) {
                // assumes there is always a nested request if none of the above worked
                leftCursor = cursorFromRequest(operatorRequest.getGeometryRequest(), null, null);
            }
        } else {
            if (operatorRequest.hasLeftGeometryRequest()) {
                leftCursor = cursorFromRequest(operatorRequest.getLeftGeometryRequest(), leftCursor, null);
            } else if (operatorRequest.hasGeometryRequest()) {
                leftCursor = cursorFromRequest(operatorRequest.getGeometryRequest(), leftCursor, null);
            }
        }

        if (leftCursor == null){
            throw new IOException("Geometry / operator request not defined for operation.");
        }

        // project left if needed
        if (srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.leftSR)) {
            ProjectionTransformation projTransformation = new ProjectionTransformation(srGroup.leftSR, srGroup.operatorSR);
            leftCursor = OperatorProject.local().execute(leftCursor, projTransformation, null);
        }

        return leftCursor;
    }

    private static GeometryCursor getRightGeometryRequestFromRequest(
            GeometryRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor,
            SpatialReferenceGroup srGroup) throws IOException {
        if (leftCursor != null && rightCursor == null) {
            rightCursor = createGeometryCursor(operatorRequest, Side.Right);
            if (rightCursor == null && operatorRequest.hasRightGeometryRequest()) {
                rightCursor = cursorFromRequest(operatorRequest.getRightGeometryRequest(), null, null);
            }
        }

        if (rightCursor != null && srGroup.operatorSR != null && !srGroup.operatorSR.equals(srGroup.rightSR)) {
            ProjectionTransformation projTransformation = new ProjectionTransformation(srGroup.rightSR, srGroup.operatorSR);
            rightCursor = OperatorProject.local().execute(rightCursor, projTransformation, null);
        }
        return rightCursor;
    }

    public static GeometryResponse nonCursorFromRequest(
            GeometryRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = getLeftGeometryRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = getRightGeometryRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        GeometryResponse.Builder operatorResultBuilder = GeometryResponse.newBuilder();
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
                HashMap<Long, Boolean> result_map = ((OperatorSimpleRelation) OperatorFactoryLocal
                        .getInstance()
                        .getOperator(operatorType)).execute(
                                leftCursor.next(),
                                rightCursor,
                                srGroup.operatorSR,
                                null);

                if (result_map.size() == 1) {
                    operatorResultBuilder.setSpatialRelationship(result_map.get(0L));
                    operatorResultBuilder.putAllRelateMap(result_map);
                } else {
                    operatorResultBuilder.putAllRelateMap(result_map);
                }
                break;
            case Distance:
                operatorResultBuilder.setMeasure(OperatorDistance.local().execute(leftCursor.next(), rightCursor.next(), null));
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
            GeometryRequest operatorRequest,
            GeometryCursor leftCursor,
            GeometryCursor rightCursor) throws IOException {
        SpatialReferenceGroup srGroup = new SpatialReferenceGroup(operatorRequest);
        leftCursor = getLeftGeometryRequestFromRequest(operatorRequest, leftCursor, srGroup);
        rightCursor = getRightGeometryRequestFromRequest(operatorRequest, leftCursor, rightCursor, srGroup);

        GeometryCursor resultCursor = null;
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        switch (operatorType) {
            case DensifyByAngle:
                break;
            case LabelPoint:
                break;
            case GeodesicBuffer:
                // TODO this should recycled or a member variable
                var doubleList = new double[] {operatorRequest.getBufferParams().getDistance()};
                double maxDeviation = Double.NaN;
                if (operatorRequest.getBufferParams().getMaxDeviation() > 0) {
                    maxDeviation = operatorRequest.getBufferParams().getMaxDeviation();
                }
                resultCursor = OperatorGeodesicBuffer.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        0,
                        doubleList,
                        maxDeviation,
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
                GeneralizeByAreaParams generalizeByAreaParams = operatorRequest.getGeneralizeByAreaParams();
                if (generalizeByAreaParams.getPercentReduction() > 0) {
                    resultCursor = OperatorGeneralizeByArea.local().execute(
                            leftCursor,
                            generalizeByAreaParams.getPercentReduction(),
                            generalizeByAreaParams.getRemoveDegenerates(),
                            GeneralizeType.ResultContainsOriginal,
                            srGroup.operatorSR,
                            null);
                } else if (generalizeByAreaParams.getMaxPointCount() > 0) {
                    resultCursor = OperatorGeneralizeByArea.local().execute(
                            leftCursor,
                            generalizeByAreaParams.getRemoveDegenerates(),
                            generalizeByAreaParams.getMaxPointCount(),
                            GeneralizeType.ResultContainsOriginal,
                            srGroup.operatorSR,
                            null);
                } else {
                    // maybe a user passes a 0 for maxPoint count, which is impossible or 0 for percent reduced
                    // which means not reduced at all. so we just pass back the input
                    resultCursor = leftCursor;
                }
                break;
            case Project:
                resultCursor = leftCursor;
                break;
            case Union:
                resultCursor = OperatorUnion.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        null);
                break;
            case Difference:
                resultCursor = OperatorDifference.local().execute(
                        leftCursor,
                        rightCursor,
                        srGroup.operatorSR,
                        null);
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

                double[] d = new double[] {operatorRequest.getBufferParams().getDistance()};

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
                if (operatorRequest.hasIntersectionParams() &&
                        operatorRequest.getIntersectionParams().getDimensionMask() != 0) {
                    resultCursor = OperatorIntersection.local().execute(
                            leftCursor,
                            rightCursor,
                            srGroup.operatorSR,
                            null,
                            operatorRequest.getIntersectionParams().getDimensionMask());
                } else {
                    resultCursor = OperatorIntersection.local().execute(leftCursor, rightCursor, srGroup.operatorSR, null);
                }
                break;
            case Clip:
                Envelope2D envelope2D = extractEnvelope2D(operatorRequest.getClipParams().getEnvelope());
                resultCursor = OperatorClip.local().execute(leftCursor, envelope2D, srGroup.operatorSR, null);
                break;
            case Cut:
                resultCursor = OperatorCut.local().execute(
                        operatorRequest.getCutParams().getConsiderTouch(),
                        leftCursor.next(),
                        (Polyline) rightCursor.next(),
                        srGroup.operatorSR,
                        null);
                break;
            case DensifyByLength:
                resultCursor = OperatorDensifyByLength.local().execute(
                        leftCursor,
                        operatorRequest.getDensifyParams().getMaxLength(),
                        null);
                break;
            case Simplify:
                resultCursor = OperatorSimplify.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getSimplifyParams().getForce(),
                        null);
                break;
            case SimplifyOGC:
                resultCursor = OperatorSimplifyOGC.local().execute(
                        leftCursor,
                        srGroup.operatorSR,
                        operatorRequest.getSimplifyParams().getForce(),
                        null);
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
                resultCursor = OperatorSymmetricDifference.local().execute(
                        leftCursor,
                        rightCursor,
                        srGroup.operatorSR,
                        null);
                break;
            case ConvexHull:
                resultCursor = OperatorConvexHull.local().execute(
                        leftCursor,
                        operatorRequest.getConvexParams().getMerge(),
                        null);
                break;
            case Boundary:
                resultCursor = OperatorBoundary.local().execute(leftCursor, null);
                break;
            case EnclosingCircle:
                resultCursor = new OperatorEnclosingCircleCursor(leftCursor, srGroup.operatorSR, null);
                break;
            case RandomPoints:
                double[] pointsPerSqrKm = new double [] {operatorRequest
                        .getRandomPointsParams()
                        .getPointsPerSquareKm()};

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
            ProjectionTransformation projTransformation = new ProjectionTransformation(srGroup.operatorSR, srGroup.resultSR);
            resultCursor = OperatorProject.local().execute(resultCursor, projTransformation, null);
        }

        return resultCursor;
    }

    public static GeometryResponsesIterator buildResultsIterable(GeometryRequest operatorRequest,
                                                               GeometryCursor leftCursor,
                                                               boolean bForceCompact) throws IOException {
        Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType().toString());
        GeometryEncodingType encodingType = GeometryEncodingType.unknown;
        GeometryCursor resultCursor = null;
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
                return new GeometryResponsesIterator(nonCursorFromRequest(operatorRequest, leftCursor, null));

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
                resultCursor = cursorFromRequest(operatorRequest, leftCursor, null);
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
            resultCursor = createGeometryCursor(operatorRequest, Side.Left);
        }

        return new GeometryResponsesIterator(resultCursor, operatorRequest, encodingType, bForceCompact);
    }


    private static GeometryCursor createGeometryCursor(GeometryRequest operatorRequest, Side side) throws IOException {
        GeometryCursor resultCursor = null;
        if (side == Side.Left) {
            if (operatorRequest.hasGeometry()) {
                resultCursor = createGeometryCursor(operatorRequest.getGeometry());
            } else if (operatorRequest.hasLeftGeometry()) {
                resultCursor = createGeometryCursor(operatorRequest.getLeftGeometry());
            }
        } else if (side == Side.Right) {
            if (operatorRequest.hasRightGeometry()) {
                resultCursor = createGeometryCursor(operatorRequest.getRightGeometry());
            }
        }

        return resultCursor;
    }


    private static GeometryCursor createGeometryCursor(GeometryData geometryData) throws IOException {
        return extractGeometryCursor(geometryData);
    }

    static SpatialReference extractSpatialReference(GeometryData geometryData) {
        return geometryData.hasSpatialReference() ? extractSpatialReference(geometryData.getSpatialReference()) : null;
    }


    protected static SpatialReference extractSpatialReferenceCursor(GeometryRequest operatorRequestCursor) {
        if (operatorRequestCursor.hasResultSpatialReference()) {
            return extractSpatialReference(operatorRequestCursor.getResultSpatialReference());
        } else if (operatorRequestCursor.hasOperationSpatialReference()) {
            return extractSpatialReference(operatorRequestCursor.getOperationSpatialReference());
        } else if (operatorRequestCursor.hasLeftGeometryRequest()) {
            return extractSpatialReferenceCursor(operatorRequestCursor.getLeftGeometryRequest());
        } else if (operatorRequestCursor.hasLeftGeometry()) {
            return extractSpatialReference(operatorRequestCursor.getLeftGeometry().getSpatialReference());
        } else if (operatorRequestCursor.hasGeometryRequest()) {
            return extractSpatialReferenceCursor(operatorRequestCursor.getGeometryRequest());
        } else if (operatorRequestCursor.hasRightGeometry()) {
            return extractSpatialReference(operatorRequestCursor.getRightGeometry().getSpatialReference());
        }
        return null;
    }


    protected static SpatialReference extractSpatialReference(SpatialReferenceData serviceSpatialReference) {
        // TODO there seems to be a bug where hasWkid() is not getting generated. check back later
        if (serviceSpatialReference.getWkid() != 0)
            return SpatialReference.create(serviceSpatialReference.getWkid());
        else if (serviceSpatialReference.getEsriWkt().length() > 0)
            return SpatialReference.create(serviceSpatialReference.getEsriWkt());
        else if (serviceSpatialReference.getProj4().length() > 0)
            return SpatialReference.createFromProj4(serviceSpatialReference.getProj4());

        return null;
    }


    private static Envelope2D extractEnvelope2D(EnvelopeData env) {
        return Envelope2D.construct(env.getXmin(), env.getYmin(), env.getXmax(), env.getYmax());
    }


    private static GeometryCursor extractGeometryCursor(GeometryData geometryData) throws IOException {
        GeometryCursor geometryCursor = null;

        if (geometryData.getWkb().size() > 0) {
            SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(geometryData.getWkb().asReadOnlyByteBuffer(), geometryData.getGeometryId()); //simpleByteBufferCursor = new SimpleByteBufferCursor(byteBufferArrayDeque, idsDeque);
            geometryCursor = new OperatorImportFromWkbCursor(0, simpleByteBufferCursor);
        } else if (geometryData.getEsriShape().size() > 0) {
            SimpleByteBufferCursor simpleByteBufferCursor = new SimpleByteBufferCursor(geometryData.getEsriShape().asReadOnlyByteBuffer(), geometryData.getGeometryId());
            geometryCursor = new OperatorImportFromESRIShapeCursor(0, 0, simpleByteBufferCursor);
        } else if (geometryData.getWkt().length() > 0) {
            SimpleStringCursor simpleStringCursor = new SimpleStringCursor(geometryData.getWkt(), geometryData.getGeometryId());
            geometryCursor = new OperatorImportFromWktCursor(0, simpleStringCursor);
        } else if (geometryData.getGeojson().length() > 0) {
            SimpleStringCursor simpleStringCursor = new SimpleStringCursor(geometryData.getGeojson(), geometryData.getGeometryId());
            MapGeometryCursor mapGeometryCursor = new OperatorImportFromGeoJsonCursor(
                    GeoJsonImportFlags.geoJsonImportSkipCRS,
                    simpleStringCursor,
                    null);
            geometryCursor = new SimpleGeometryCursor(mapGeometryCursor);
        } else {
            throw new GeometryException("No geometry data found");
        }
        return geometryCursor;
    }

    private static boolean requestPreservesIDs(GeometryRequest geometryRequest) {
        if (geometryRequest.hasRightGeometry() || geometryRequest.hasRightGeometryRequest()) {
            return false;
        } else if (geometryRequest.hasLeftGeometry() || geometryRequest.hasGeometry()) {
            return true;
        } else if (geometryRequest.hasLeftGeometryRequest()) {
            return requestPreservesIDs(geometryRequest.getLeftGeometryRequest());
        } else if (geometryRequest.hasGeometryRequest()) {
            return requestPreservesIDs(geometryRequest.getGeometryRequest());
        }
        return false;
    }
}
