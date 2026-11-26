package mcshim.awt;

import java.io.File;
import java.io.FilenameFilter;

import mcshim.Platform;

/** A file chooser backed by Platform.chooseFile (a native dialog on Android). */
public class FileDialog extends Dialog {
    public static final int LOAD = 0, SAVE = 1;
    private final String title;
    private final int mode;
    private String dir, file;
    private FilenameFilter filter;

    public FileDialog(Dialog owner, String title, int mode) { this.title = title; this.mode = mode; }
    public FileDialog(Frame owner, String title, int mode) { this.title = title; this.mode = mode; }

    public void setDirectory(String d) { dir = d; }
    public void setFile(String f) { file = f; }
    public void setFilenameFilter(FilenameFilter f) { filter = f; }
    public String getDirectory() { return dir; }
    public String getFile() { return file; }
    public int getMode() { return mode; }

    public void setVisible(boolean v) {
        if (!v) return;
        String path = Platform.get().chooseFile(mode == SAVE, title, dir, filter);
        if (path == null) { file = null; return; }
        File f = new File(path);
        dir = f.getParent() + File.separator;
        file = f.getName();
    }
}
