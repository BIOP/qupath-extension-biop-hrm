package qupath.ext.biop.hrm;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.gui.dialogs.Dialogs;

public class QPHRMTools {

    public static String askUsername(){

        GridPane pane = new GridPane();
        Label labUsername = new Label("Username");
        TextField tfUsername = new TextField("");
        labUsername.setLabelFor(tfUsername);

        int row = 0;

        pane.add(labUsername, 0, row++);
        pane.add(tfUsername, 1, row);
        pane.setHgap(5);
        pane.setVgap(5);

        if (!Dialogs.showConfirmDialog("Login", pane))
            return null;

        return tfUsername.getText();
    }
}
