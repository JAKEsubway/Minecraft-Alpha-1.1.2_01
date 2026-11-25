package mcshim.awt.datatransfer;

import mcshim.Platform;

public class Clipboard {
    public Transferable getContents(Object requestor) {
        final String s = Platform.get().getClipboard();
        return new Transferable() {
            public boolean isDataFlavorSupported(DataFlavor f) { return f == DataFlavor.stringFlavor && s != null; }
            public Object getTransferData(DataFlavor f) { return s; }
        };
    }
}
