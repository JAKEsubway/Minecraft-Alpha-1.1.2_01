package mcshim.awt.datatransfer;

public interface Transferable {
    boolean isDataFlavorSupported(DataFlavor f);
    Object getTransferData(DataFlavor f) throws java.io.IOException, UnsupportedFlavorException;
}
