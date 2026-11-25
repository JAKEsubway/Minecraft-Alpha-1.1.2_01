package mcshim.awt.datatransfer;

public class UnsupportedFlavorException extends Exception {
    public UnsupportedFlavorException(DataFlavor f) { super("unsupported flavor"); }
}
