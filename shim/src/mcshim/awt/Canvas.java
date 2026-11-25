package mcshim.awt;

import mcshim.Platform;

/** The game's render surface: its size is the platform surface size. */
public class Canvas extends Component {
    public Canvas() {}
    public int getWidth() { Platform p = Platform.INSTANCE; return p != null ? p.width() : super.getWidth(); }
    public int getHeight() { Platform p = Platform.INSTANCE; return p != null ? p.height() : super.getHeight(); }
}
