package mcshim.awt;

public class TextArea extends Component {
    private String text;
    public TextArea(String text, int rows, int cols, int scroll) { this.text = text; }
    public TextArea(String text) { this.text = text; }
    public String getText() { return text; }
    public void setText(String t) { text = t; }
    public void setEditable(boolean b) {}
}
