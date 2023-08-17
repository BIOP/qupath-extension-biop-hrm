package qupath.ext.biop.hrm.retrievers;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;

public class QPHRMRetrieveFromHRMCommand implements Runnable {
    private final QuPathGUI qupath;
    public QPHRMRetrieveFromHRMCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        Project<BufferedImage> project = qupath.getProject();

        // check if a project is open
        if (project == null) {
            Dialogs.showNoProjectError("Script editor");
            return;
        }

        // build teh GUI
        GridPane pane = new GridPane();
        Label labUsername = new Label("HRM Username");
        TextField tfUsername = new TextField("");
        labUsername.setLabelFor(tfUsername);

        Label labHost = new Label("OMERO Host (https://hostname)");
        TextField tfHost = new TextField("https://omero-server.epfl.ch");
        labHost.setLabelFor(tfHost);
        labHost.setDisable(true);
        tfHost.setDisable(true);

        String deleteImagesWarning = "Warning : every data related to images \n will be deleted from your QuPath folder \n in HRM-Share";
        Label labelSameImageWarning = new Label(deleteImagesWarning);
        labelSameImageWarning.setTextFill(Color.RED);
        labelSameImageWarning.setMaxWidth(Double.MAX_VALUE);
        labelSameImageWarning.setMinHeight(Label.USE_PREF_SIZE);
        labelSameImageWarning.setTextAlignment(TextAlignment.CENTER);
        labelSameImageWarning.setAlignment(Pos.CENTER);
        labelSameImageWarning.setVisible(false);

        CheckBox chkOmero = new CheckBox("Connect to OMERO");
        chkOmero.setMinWidth(CheckBox.USE_PREF_SIZE);
        chkOmero.setSelected(false);
        chkOmero.selectedProperty().addListener((v, o, n) -> {
            labHost.setDisable(!chkOmero.selectedProperty().get());
            tfHost.setDisable(!chkOmero.selectedProperty().get());
        });

        CheckBox chkDelete = new CheckBox("Delete data on HRM");
        chkDelete.setMinWidth(CheckBox.USE_PREF_SIZE);
        chkDelete.setSelected(false);
        chkDelete.selectedProperty().addListener((v, o, n) -> {
            labelSameImageWarning.setVisible(chkDelete.selectedProperty().get());
        });

        int row = 0;
        pane.add(labUsername, 0, row);
        pane.add(tfUsername, 1, row++);
        pane.add(chkOmero,0, row++);
        pane.add(labHost, 0, row);
        pane.add(tfHost, 1, row++);
        pane.add(chkDelete,0, row++);
        pane.add(labelSameImageWarning,0,row);

        pane.setHgap(5);
        pane.setVgap(5);

        if (!Dialogs.showConfirmDialog("Login", pane))
            return;

        // get the user entries
        String username = tfUsername.getText();
        boolean deleteOnHRM = chkDelete.selectedProperty().get();
        String host;
        if(chkOmero.selectedProperty().get())
            host = tfHost.getText();
        else host= "";

        // username is mandatory
        if(username.equals("")){
            Dialogs.showErrorNotification("Invalid username", "Please fill the username field");
            return;
        }

        // set the root folder
        String root = "C:\\Users\\dornier\\Downloads";//"\\\\sv-nas1.rcp.epfl.ch\\ptbiop-raw\\HRM-Share";//"C:\\Users\\dornier\\Downloads";

        // retrieve images
        QPHRMRetrieveFromHRM.retrieve(qupath, root, username, deleteOnHRM, host);
    }

}
