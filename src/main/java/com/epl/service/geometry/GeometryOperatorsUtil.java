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
import com.google.protobuf.util.JsonFormat;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

/**
 * Common utilities for the GeometryOperators demo.
 */
public class GeometryOperatorsUtil {
    private static final double COORD_FACTOR = 1e7;

    /**
     * Gets the latitude for the given point.
     */
    public static double getLatitude(ReplacePoint location) {
        return location.getLatitude() / COORD_FACTOR;
    }

    /**
     * Gets the longitude for the given point.
     */
    public static double getLongitude(ReplacePoint location) {
        return location.getLongitude() / COORD_FACTOR;
    }

    /**
     * Gets the default features file from classpath.
     */
    public static URL getDefaultFeaturesFile() {
        return GeometryOperatorsServer.class.getResource("route_guide_db.json");
    }

    /**
     * Parses the JSON input file containing the list of features.
     */
    public static List<Feature> parseFeatures(URL file) throws IOException {
        InputStream input = file.openStream();
        try {
            Reader reader = new InputStreamReader(input);
            try {
                FeatureDatabase.Builder database = FeatureDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);
                return database.getFeatureList();
            } finally {
                reader.close();
            }
        } finally {
            input.close();
        }
    }

    /**
     * Indicates whether the given feature exists (i.e. has a valid name).
     */
    public static boolean exists(Feature feature) {
        return feature != null && !feature.getName().isEmpty();
    }


    public static ServiceGeometry __encodeGeometry(GeometryCursor geometryCursor, OperatorRequest operatorRequest, String encodingType) {
        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder();

        // TODO not getting stubbed out due to grpc proto stubbing bug
//        if (operatorRequest.hasResultsEncodingType()) {
//
//        }
        if (!operatorRequest.getResultsEncodingType().isEmpty()) {
            encodingType = operatorRequest.getResultsEncodingType();
        } else if (encodingType == null || encodingType.isEmpty()) {
            // Sets default export to wkt
            encodingType = "wkb";
        }


        switch (encodingType) {
            case "wkb":
                serviceGeometryBuilder.addGeometryBinary(ByteString.copyFrom(OperatorExportToWkb.local().execute(0, geometryCursor.next(), null)));
                break;
            case "wkt":
                serviceGeometryBuilder.addGeometryString(OperatorExportToWkt.local().execute(0, geometryCursor.next(), null));
                break;
            case "esrishape":
                serviceGeometryBuilder.addGeometryBinary(ByteString.copyFrom(OperatorExportToESRIShape.local().execute(0, geometryCursor.next())));
                break;
            case "geojson":
                //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
                serviceGeometryBuilder.addGeometryString(OperatorExportToGeoJson.local().execute(null, geometryCursor.next()));
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
        serviceGeometryBuilder.setSpatialReference(operatorRequest.getResultSpatialReference());

        return serviceGeometryBuilder.build();
    }


    public static GeometryCursor cursorFromRequest(OperatorRequest operatorRequest) {
        GeometryCursor resultCursor = null;
        try {
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
        } catch (Exception j) {

        }

        return  resultCursor;
    }


    // TODO this is ignoring the differences between the geometry spatial references, the result spatial references and the operator spatial references
    public static OperatorResult executeOperator(OperatorRequest operatorRequest) {
        GeometryCursor resultCursor = null;
        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();

        try {
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
            if (operatorSpatialReference == null && leftSpatialReference != null && leftSpatialReference.equals(rightSpatialReference)) {
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
            String encodingType = null;
            switch (operatorType) {
                case Project:
                    ProjectionTransformation projectionTransformation = new ProjectionTransformation(leftSpatialReference, operatorSpatialReference);
                    resultCursor = OperatorProject.local().execute(leftCursor, projectionTransformation, null);
                    break;
                case ExportToJson:
                    break;
                case ImportFromJson:
                    break;
                case ImportMapGeometryFromJson:
                    break;
                case ExportToESRIShape:
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
                    // TODO this is totally fucked up. 0 is a dimension. this defaults to 0. hasIntersectionDimensionMask needs to be automagically generated
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
                case ExportToWkb:
                    resultCursor = leftCursor;
                    encodingType = "wkb";
                    break;
                case ExportToWkt:
                    resultCursor = leftCursor;
                    encodingType = "wkt";
                    break;
                case ExportToGeoJson:
                    resultCursor = leftCursor;
                    encodingType = "geojson";
                    break;
            }

            if (resultSpatialReference != null && !resultSpatialReference.equals(operatorSpatialReference)) {
                // TODO project cursor!!!
                ProjectionTransformation projectionTransformation = new ProjectionTransformation(operatorSpatialReference, resultSpatialReference);
                resultCursor = OperatorProject.local().execute(resultCursor, projectionTransformation, null);
            }

            if (resultCursor != null)
                operatorResultBuilder.setGeometry(__encodeGeometry(resultCursor, operatorRequest, encodingType));

            return operatorResultBuilder.build();

        } catch (JSONException j) {
            return null;
        }
    }


    private static GeometryCursor __createGeometryCursor(ServiceGeometry serviceGeometry) throws JSONException {
        MapGeometry mapGeometry = __extractGeometry(serviceGeometry);
        GeometryCursor geometryCursor = new SimpleGeometryCursor(mapGeometry.getGeometry());
        return geometryCursor;
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
            case esri:
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


    private static MapGeometry __extractGeometry(ServiceGeometry serviceGeometry) throws JSONException {
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
            case esri:
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
