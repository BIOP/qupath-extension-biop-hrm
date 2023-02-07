package qupath.ext.biop.hrm;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
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

        boolean confirm = Dialogs.showConfirmDialog("WARNING !", "All images in the HRM-Share/Deconvolved/QuPath folder will be processed.");
        if(!confirm)
            return;

        String username = QPHRMTools.askUsername();
        String root = "C:\\Users\\dornier\\Downloads";//"\\\\svraw1.epfl.ch\\ptbiop\\HRM-Share";

        boolean sentImages = QPHRMRetrieveFromHRM.retrieve(qupath, root, username);

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
