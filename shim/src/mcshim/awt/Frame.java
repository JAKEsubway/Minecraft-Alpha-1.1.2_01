package mcshim.awt;

import mcshim.Platform;

public class Frame extends Window {
    private String title;
    public Frame() { this(""); }
    public Frame(String title) { this.title = title; }
    public void setTitle(String t) { title = t; }
    public String getTitle() { return title; }
    public void setResizable(boolean b) {}
    public void setIconImage(Image i) {}
    public void setUndecorated(boolean b) {}
    public void setExtendedState(int s) {}

    void onAdd(Component c) { reportCrash(c); }

    static void reportCrash(Component c) {
        if (c instanceof TextArea) {
            Platform.get().crash(((TextArea) c).getText());
        } else if (c instanceof Container) {
            Container k = (Container) c;
            for (int i = 0; i < k.getComponentCount(); i++) reportCrash(k.getComponent(i));
        }
    }
}
