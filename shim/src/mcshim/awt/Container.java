package mcshim.awt;

import java.util.ArrayList;

public class Container extends Component {
    private final ArrayList<Component> kids = new ArrayList<Component>();
    private LayoutManager layout;

    public Component add(Component c) { kids.add(c); c.parent = this; onAdd(c); return c; }
    public void add(Component c, Object constraints) { add(c); }
    public Component add(String name, Component c) { return add(c); }
    public void remove(Component c) { kids.remove(c); }
    public void removeAll() { kids.clear(); }
    public int getComponentCount() { return kids.size(); }
    public Component getComponent(int i) { return kids.get(i); }
    public void setLayout(LayoutManager l) { layout = l; }
    public LayoutManager getLayout() { return layout; }
    public Insets getInsets() { return new Insets(0, 0, 0, 0); }

    void onAdd(Component c) {
        if (parent != null) parent.onAdd(c);
    }
}
