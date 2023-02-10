package qupath.ext.biop.hrm;

import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QPHRMRetrieveFromHRMCommand implements Runnable {

    private final QuPathGUI qupath;

    public QPHRMRetrieveFromHRMCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        Project<BufferedImage> project = qupath.getProject();

        if (project == null) {
            Dialogs.showNoProjectError("Script editor");
            return;
        }

        GridPane pane = new GridPane();
        Label labUsername = new Label("HRM Username");
        TextField tfUsername = new TextField("");
        labUsername.setLabelFor(tfUsername);

        Label labHost = new Label("OMERO Host (https://hostname)");
        TextField tfHost = new TextField("https://omero-poc.epfl.ch");
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

        CheckBox chkOverwrite = new CheckBox("Overwrite data on HRM");
        chkOverwrite.setMinWidth(CheckBox.USE_PREF_SIZE);
        chkOverwrite.setSelected(false);
        chkOverwrite.selectedProperty().addListener((v, o, n) -> {
            labelSameImageWarning.setVisible(chkOverwrite.selectedProperty().get());
        });


        int row = 0;
        pane.add(labUsername, 0, row);
        pane.add(tfUsername, 1, row++);
        pane.add(chkOmero,0, row++);
        pane.add(labHost, 0, row);
        pane.add(tfHost, 1, row++);
        pane.add(chkOverwrite,0, row++);
        pane.add(labelSameImageWarning,0,row);

        pane.setHgap(5);
        pane.setVgap(5);

        if (!Dialogs.showConfirmDialog("Login", pane))
            return;

        String username = tfUsername.getText();
        boolean deleteOnHRM = chkOverwrite.selectedProperty().get();
        String host;
        if(chkOmero.selectedProperty().get())
            host = tfHost.getText();
        else host= "";

        if(username.equals("")){
            Dialogs.showErrorNotification("Invalid username", "Please fill the username field");
            return;
        }

        String root = "C:\\Users\\dornier\\Downloads";//"\\\\svraw1.epfl.ch\\ptbiop\\HRM-Share";
        boolean sentImages = QPHRMRetrieveFromHRM.retrieve(qupath, root, username, deleteOnHRM, host);

        /*Dialogs.showInfoNotification("Sending To HRM",String.format("%d/%d %s %s successfully sent to HRM server and %d/%d %s skipped.",
                sentImages[0],
                imagesToSend.size(),
                (sentImages[0] == 1 ? "image" : "images"),
                (sentImages[0] == 1 ? "was" : "were"),
                sentImages[1],
                imagesToSend.size(),
                (sentImages[1] == 1 ? "was" : "were")));*/

    }

}
