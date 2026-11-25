package mcshim.applet;

import java.net.MalformedURLException;
import java.net.URL;

import mcshim.awt.Panel;

public class Applet extends Panel {
    public Applet() {}
    public void init() {}
    public void start() {}
    public void stop() {}
    public void destroy() {}
    public String getParameter(String name) { return null; }
    public URL getDocumentBase() { try { return new URL("http://www.minecraft.net/game/"); } catch (MalformedURLException e) { return null; } }
    public URL getCodeBase() { return getDocumentBase(); }
    public boolean isActive() { return true; }
    public AppletContext getAppletContext() { return new AppletContext(); }
}
