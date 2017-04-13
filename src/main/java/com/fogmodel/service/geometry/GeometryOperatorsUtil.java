/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.fogmodel.service.geometry;

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



//    public static GeometryCursor executeOperator(OperatorRequest serviceOperator) {
//        GeometryCursor geometryCursor = null;
//        try {
//            GeometryCursor leftCursor = __createGeometryCursor(serviceOperator.getLeftGeometry());
//            GeometryCursor rightCursor = null;
//            if (serviceOperator.hasRightGeometry())
//                rightCursor = __createGeometryCursor(serviceOperator.getRightGeometry());
//
//            // TODO this could throw an exception if unknown operator type provided
//            Operator.Type operatorType = Operator.Type.valueOf(serviceOperator.getOperatorType());
//            switch (operatorType) {
//                case Project:
//                    break;
//                case ImportFromJson:
//                    break;
//                case ImportFromESRIShape:
//                    break;
//                case Union:
////                    geometryCursor = OperatorUnion.local().execute(leftCursor, )
//                    break;
//                case Difference:
//                    break;
//                case Proximity2D:
//                    break;
//                case Relate:
//                    break;
//                case Equals:
//                    break;
//                case Disjoint:
//                    break;
//                case Intersects:
//                    break;
//                case Within:
//                    break;
//                case Contains:
//                    break;
//                case Crosses:
//                    break;
//                case Touches:
//                    break;
//                case Overlaps:
//                    break;
//                case Buffer:
//                    break;
//                case Distance:
//                    break;
//                case Intersection:
//                    break;
//                case Clip:
//                    break;
//                case Cut:
//                    break;
//                case DensifyByLength:
//                    break;
//                case DensifyByAngle:
//                    break;
//                case LabelPoint:
//                    break;
//                case GeodesicBuffer:
//                    break;
//                case GeodeticDensifyByLength:
//                    break;
//                case ShapePreservingDensify:
//                    break;
//                case GeodeticLength:
//                    break;
//                case GeodeticArea:
//                    break;
//                case Simplify:
//                    break;
//                case SimplifyOGC:
//                    break;
//                case Offset:
//                    break;
//                case Generalize:
//                    break;
//                case ExportToWkb:
//                    break;
//                case ImportFromWkb:
//                    break;
//                case ExportToWkt:
//                    break;
//                case ImportFromWkt:
//                    break;
//                case ImportFromGeoJson:
//                    break;
//                case ExportToGeoJson:
//                    break;
//                case SymmetricDifference:
//                    break;
//                case ConvexHull:
//
//                    geometryCursor = OperatorConvexHull.local().execute(leftCursor, serviceOperator.getConvexHullMerge(), null);
//                    break;
//                case Boundary:
//                    break;
//                default:
//                    geometryCursor = leftCursor;
//
//            }
//
//            return geometryCursor;
//
//        } catch (JSONException j) {
//            return null;
//        }
//    }

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
            encodingType = "wkt";
        }


        switch (encodingType) {
            case "wkb":
                serviceGeometryBuilder.setGeometryBinary(ByteString.copyFrom(OperatorExportToWkb.local().execute(0, geometryCursor.next(), null)));
                break;
            case "wkt":
                serviceGeometryBuilder.setGeometryString(OperatorExportToWkt.local().execute(0, geometryCursor.next(), null));
                break;
            case "esrishape":
                serviceGeometryBuilder.setGeometryBinary(ByteString.copyFrom(OperatorExportToESRIShape.local().execute(0, geometryCursor.next())));
                break;
            case "geojson":
                //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
                serviceGeometryBuilder.setGeometryString(OperatorExportToGeoJson.local().execute(null, geometryCursor.next()));
                break;
        }

        //TODO I'm just blindly setting the spatial reference here instead of projecting the result into the spatial reference
        serviceGeometryBuilder.setSpatialReference(operatorRequest.getResultSpatialReference());

        return serviceGeometryBuilder.build();
    }


    // TODO this is ignoring the differences between the geometry spatial references, the result spatial references and the operator spatial references
    public static OperatorResult executeOperator(OperatorRequest operatorRequest) {
        GeometryCursor geometryCursor = null;
        OperatorResult.Builder operatorResultBuilder = OperatorResult.newBuilder();
        try {
            GeometryCursor leftCursor = __createGeometryCursor(operatorRequest.getLeftGeometry());
            SpatialReference leftSpatialReference = __extractSpatialReference(operatorRequest.getLeftGeometry());
            GeometryCursor rightCursor = null;
            SpatialReference rightSpatialReference = null;
            if (operatorRequest.hasRightGeometry()) {
                rightCursor = __createGeometryCursor(operatorRequest.getRightGeometry());
                rightSpatialReference = __extractSpatialReference(operatorRequest.getRightGeometry());
            }

            SpatialReference operatorSpatialReference = __extractSpatialReference(operatorRequest.getOperationSpatialReference());
            SpatialReference resultSpatialReference = __extractSpatialReference(operatorRequest.getResultSpatialReference());
            // TODO this could throw an exception if unknown operator type provided
            Operator.Type operatorType = Operator.Type.valueOf(operatorRequest.getOperatorType());
            String encodingType = null;
            switch (operatorType) {
                case Project:
                    break;
                case ExportToJson:
                    // TODO I don't know what this is yet....
                    geometryCursor = leftCursor;
                    encodingType = "geojson";
                    break;
                case ImportFromJson:
                    break;
                case ImportMapGeometryFromJson:
                    break;
                case ExportToESRIShape:
                    geometryCursor = leftCursor;
                    encodingType = "esrishape";
                    break;
                case ImportFromESRIShape:
                    break;
                case Union:
                    break;
                case Difference:
                    break;
                case Proximity2D:
                    break;
                case Relate:
                    break;
                case Equals:
                    break;
                case Disjoint:
                    boolean result = OperatorDisjoint.local().execute(leftCursor.next(), rightCursor.next(), operatorSpatialReference, null);
                    break;
                case Intersects:
                    break;
                case Within:
                    break;
                case Contains:
                    break;
                case Crosses:
                    break;
                case Touches:
                    break;
                case Overlaps:
                    break;
                case Buffer:
                    break;
                case Distance:
                    break;
                case Intersection:
                    break;
                case Clip:
                    break;
                case Cut:
                    break;
                case DensifyByLength:
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
                    break;
                case SimplifyOGC:
                    break;
                case Offset:
                    break;
                case Generalize:
                    break;
                case ExportToWkb:
                    geometryCursor = leftCursor;
                    encodingType = "wkb";
                    break;
                case ImportFromWkb:
                    break;
                case ExportToWkt:
                    geometryCursor = leftCursor;
                    encodingType = "wkt";
                    break;
                case ImportFromWkt:
                    break;
                case ImportFromGeoJson:
                    break;
                case ExportToGeoJson:
                    geometryCursor = leftCursor;
                    encodingType = "geojson";
                    break;
                case SymmetricDifference:
                    break;
                case ConvexHull:
                    geometryCursor = OperatorConvexHull.local().execute(leftCursor, operatorRequest.getConvexHullMerge(), null);
                    break;
                case Boundary:
                    break;
                default:
                    geometryCursor = leftCursor;

            }

            if (geometryCursor != null)
                operatorResultBuilder.setGeometry(__encodeGeometry(geometryCursor, operatorRequest, encodingType));

            return operatorResultBuilder.build();

        } catch (JSONException j) {
            return null;
        }
        //return geometryCursor;
    }

    private static GeometryCursor __createGeometryCursor(ServiceGeometry serviceGeometry) throws JSONException {
        MapGeometry mapGeometry = __extractGeometry(serviceGeometry);
        GeometryCursor geometryCursor = new SimpleGeometryCursor(mapGeometry.getGeometry());
        return geometryCursor;
    }


    private static ServiceGeometry __decodeGeometry(Geometry geometry, SpatialReference spatialReference, String encoding_type) {
        ServiceGeometry.Builder serviceGeometryBuilder = ServiceGeometry.newBuilder().setGeometryEncodingType(encoding_type);
        switch (encoding_type) {
            case "wkt":
                serviceGeometryBuilder.setGeometryString(OperatorExportToWkt.local().execute(0, geometry, null));
                break;
            case "geojson":
                serviceGeometryBuilder.setGeometryString(OperatorExportToGeoJson.local().execute(spatialReference, geometry));
                break;
            case "wkb":
                serviceGeometryBuilder.setGeometryBinary(ByteString.copyFrom(OperatorExportToWkb.local().execute(0, geometry, null)));
                break;
            case "esrishape":
                serviceGeometryBuilder.setGeometryBinary(ByteString.copyFrom(OperatorExportToESRIShape.local().execute(0,geometry)));
                break;
            default:
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

    private static MapGeometry __extractGeometry(ServiceGeometry serviceGeometry) throws JSONException {
        MapGeometry mapGeometry = null;
        Geometry geometry = null;
        SpatialReference spatialReference = null;
        ByteBuffer byteBuffer = null;
        switch (serviceGeometry.getGeometryEncodingType()) {
            case "wkt":
                geometry = OperatorImportFromWkt.local().execute(0, Geometry.Type.Unknown, serviceGeometry.getGeometryString(), null);
                break;
            case "geojson":
                mapGeometry = OperatorImportFromGeoJson.local().execute(0, Geometry.Type.Unknown, serviceGeometry.getGeometryString(), null);
                break;
            case "wkb":
                byteBuffer = ByteBuffer.wrap(serviceGeometry.getGeometryBinary().toByteArray());
                geometry = OperatorImportFromWkb.local().execute(0, Geometry.Type.Unknown, byteBuffer, null);
                break;
            case "esrishape":
                byteBuffer = ByteBuffer.wrap(serviceGeometry.getGeometryBinary().toByteArray());
                geometry = OperatorImportFromESRIShape.local().execute(0, Geometry.Type.Unknown, byteBuffer);
            default:
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
