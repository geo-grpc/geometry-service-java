package com.epl.service.geometry;

import com.esri.core.geometry.Envelope2D;
import com.esri.core.geometry.Geometry;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class ShapefileChunkedReader {
    private final List<MixedEndianDataInputStream> inputStreamList;
    private long fileLengthBytes;
    private final Envelope2D envelope2D;
    private int position; //keeps track of where inputstream is
    private int recordNumber;
    private int recordRemainingBytes;
    private final Geometry.Type geomType;

    ShapefileChunkedReader(InputStream in, int chunk_size) throws IOException {
        if (chunk_size < 108) {
            throw new IllegalArgumentException("An InputStream must have more than 100 bytes to initialize ShapefileChunkedReader");
        }

        position = 0;
        inputStreamList = Collections.synchronizedList(new ArrayList<MixedEndianDataInputStream>());
        synchronized (inputStreamList) {
            MixedEndianDataInputStream mixedEndianDataInputStream = new MixedEndianDataInputStream(in, chunk_size);
            inputStreamList.add(mixedEndianDataInputStream);

            /*
                Byte 0 File Code 9994 Integer Big
            */
            int fileCode = mixedEndianDataInputStream.readInt();
            if (fileCode != 9994) {
                throw new IOException("file code " + fileCode + " is not supported.");
            }

            /*
                Byte 4 Unused 0 Integer Big
                Byte 8 Unused 0 Integer Big
                Byte 12 Unused 0 Integer Big
                Byte 16 Unused 0 Integer Big
                Byte 20 Unused 0 Integer Big
             */
            mixedEndianDataInputStream.skipBytes(20);

            fileLengthBytes = mixedEndianDataInputStream.readInt() * 2;

            int v = mixedEndianDataInputStream.readLittleEndianInt();

            if (v != 1000) {
                throw new IOException("version " + v + " is not supported.");
            }

            int shpTypeId = mixedEndianDataInputStream.readLittleEndianInt();
            geomType = geometryTypeFromShpType(shpTypeId);

            /*
            Byte 24 File Length File Length Integer Big
            Byte 28 Version 1000 Integer Little
            Byte 32 Shape Type Shape Type Integer Little
            Byte 36 Bounding Box Xmin Double Little
            Byte 44 Bounding Box Ymin Double Little
            Byte 52 Bounding Box Xmax Double Little
            Byte 60 Bounding Box Ymax Double Little
            Byte 68* Bounding Box Zmin Double Little
            Byte 76* Bounding Box Zmax Double Little
            Byte 84* Bounding Box Mmin Double Little
            Byte 92* Bounding Box Mmax Double Little
            */
            double xmin = mixedEndianDataInputStream.readLittleEndianDouble();
            double ymin = mixedEndianDataInputStream.readLittleEndianDouble();
            double xmax = mixedEndianDataInputStream.readLittleEndianDouble();
            double ymax = mixedEndianDataInputStream.readLittleEndianDouble();
            double zmin = mixedEndianDataInputStream.readLittleEndianDouble();
            double zmax = mixedEndianDataInputStream.readLittleEndianDouble();
            double mmin = mixedEndianDataInputStream.readLittleEndianDouble();
            double mmax = mixedEndianDataInputStream.readLittleEndianDouble();

            envelope2D = new Envelope2D(xmin, ymin, xmax, ymax);
            //  envelope3D = new Envelope3D(xmin, ymin, zmin, xmax, ymax, zmax);

            position = 2 * 50; //header is always 50 words long
            mixedEndianDataInputStream.updateSize(2 * 50);

            recordNumber = mixedEndianDataInputStream.readInt();//1 based
            recordRemainingBytes = mixedEndianDataInputStream.readInt();
            position += 8;
            mixedEndianDataInputStream.updateSize(8);

            inputStreamList.notify();
        }
    }

    public byte[] next() throws InterruptedException {
        if (!hasNext()) {
            return null;
        }
        try {

            synchronized (inputStreamList) {
                MixedEndianDataInputStream inputStream = inputStreamList.get(0);

                int recordSizeBytes = (recordRemainingBytes * 2);
                byte[] bytes = new byte[recordSizeBytes];
                int read = inputStream.read(bytes);
                position += read;
                inputStream.updateSize(read);

                recordNumber = inputStream.readInt();//1 based
                recordRemainingBytes = inputStream.readInt();
                position += 8;
                inputStream.updateSize(8);

                inputStreamList.notify();
                return bytes;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void addStream(InputStream in, int chunk_size) {
        synchronized (inputStreamList) {
            MixedEndianDataInputStream mixedEndianDataInputStream = new MixedEndianDataInputStream(in, chunk_size);
            inputStreamList.add(mixedEndianDataInputStream);
            inputStreamList.notify();
        }
    }

    /**
     * from esri spec:
     * 0 Null Shape
     * 1 Point
     * 3 PolyLine
     * 5 Polygon
     * 8 MultiPoint
     * 11 PointZ
     * 13 PolyLineZ
     * 15 PolygonZ
     * 18 MultiPointZ
     * 21 PointM
     * 23 PolyLineM
     * 25 PolygonM
     * 28 MultiPointM
     * 31 MultiPatch
     * therefore final digit suffices to determine type (PolyLine, PolyLineM, PolylineZ are 1, 13 and 23 respectively).
     *
     * @param shpTypeId shape type id from shapfile
     * @return the geom type
     */
    private Geometry.Type geometryTypeFromShpType(int shpTypeId) {
        int shpType = shpTypeId % 10;

        switch (shpType) {
            case 1: //Point
                return Geometry.Type.Point;
            case 3: //Polyline
                return Geometry.Type.Polyline;
            case 5: //Polygon
                return Geometry.Type.Polygon;
            case 8: //Multipoint
                return Geometry.Type.MultiPoint;
            default:
                return Geometry.Type.Unknown;
        }
    }

    public int getGeometryID() {
        return recordNumber;
    }

    public Envelope2D getEnvelope2D() {
        return envelope2D;
    }

    public Geometry.Type getGeometryType() { return geomType; }

    public boolean hasNext() throws InterruptedException {
        synchronized (inputStreamList) {
            int count = 0;

            long value = inputStreamList.stream().mapToInt(i -> i.getSize()).sum();
            while (count++ < 5 && position < fileLengthBytes && value < recordRemainingBytes) {
                inputStreamList.wait(1000);
                value = inputStreamList.stream().mapToInt(i -> i.getSize()).sum();
            }

            if (position < fileLengthBytes && value < recordRemainingBytes) {
                throw new InterruptedException("failed to collected enough bytes to proceed");
            }

            // plus 8 is
            long bytesRequired = recordRemainingBytes + 8;
            if (recordRemainingBytes == fileLengthBytes - position) {
                bytesRequired = recordRemainingBytes;
            }

            while (bytesRequired > inputStreamList.get(0).getSize()) {
                MixedEndianDataInputStream input1 = inputStreamList.remove(0);
                MixedEndianDataInputStream input2 = inputStreamList.remove(0);
                InputStream merged = new java.io.SequenceInputStream(input1, input2);
                MixedEndianDataInputStream mixedEndianDataInputStream = new MixedEndianDataInputStream(merged, input1.getSize() + input2.getSize());
                inputStreamList.set(0, mixedEndianDataInputStream);
            }

            return position < fileLengthBytes;
        }
    }

}
