package mcshim.awt.image;

public class DataBufferInt extends DataBuffer {
    private final int[] data;
    public DataBufferInt(int[] data) { this.data = data; }
    public int[] getData() { return data; }
    public int getSize() { return data.length; }
}
