package mcshim.awt.image;

public class Raster {
    final DataBuffer buf;
    Raster(DataBuffer b) { buf = b; }
    public DataBuffer getDataBuffer() { return buf; }
}
