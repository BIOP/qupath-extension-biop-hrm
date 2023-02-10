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

public class QPHRMSendToHRMCommand implements Runnable {
    private final QuPathGUI qupath;
    private boolean overwriteHrmData = false;
    public QPHRMSendToHRMCommand(QuPathGUI qupath) {
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

        /**
         * Build teh GUI
         * Code taken as is from Pete Bankhead
         * https://github.com/qupath/qupath/blob/7548759fb102a33be8cac6a21c9d7726a69bdbbd/qupath-gui-fx/src/main/java/qupath/lib/gui/scripting/DefaultScriptEditor.java#L1518
         * */
        ArrayList<ProjectImageEntry<BufferedImage>> images = new ArrayList<ProjectImageEntry<BufferedImage>>();
        String sameImageWarning = "A selected image is open in the viewer!\nAny unsaved changes will be ignored.";
        var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), images, sameImageWarning);

        // add the checkbox to overwrite data on HRM
        GridPane paneHeader = new GridPane();
        CheckBox chkOverwrite = new CheckBox("Overwrite data on HRM");
        chkOverwrite.setMinWidth(CheckBox.USE_PREF_SIZE);
        chkOverwrite.setSelected(overwriteHrmData);
        chkOverwrite.selectedProperty().addListener((v, o, n) -> overwriteHrmData = chkOverwrite.selectedProperty().get());
        paneHeader.add(chkOverwrite,0,0);
        ((GridPane)(listSelectionView.getTargetFooter())).add(chkOverwrite,0,3);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(qupath.getStage());
        dialog.setTitle("Select images to send to HRM");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.getDialogPane().setContent(listSelectionView);
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(600);
        dialog.initModality(Modality.APPLICATION_MODAL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK)
            return;

        images.clear();
        images.addAll(listSelectionView.getTargetItems());

        if (images.isEmpty())
            return;

        // get images to send
        List<ProjectImageEntry<BufferedImage>> imagesToSend = new ArrayList<>(images);

        // set the root folder
        String rootFolder = "C:\\Users\\dornier\\Downloads";//"\\svraw1.epfl.ch\\ptbiop\\HRM-Share";

        // send images
        int[] sentImages = QPHRMSendToHRM.send(imagesToSend, overwriteHrmData, rootFolder);

        Dialogs.showInfoNotification("Sending To HRM",String.format("%d/%d %s %s successfully sent to HRM server and %d/%d %s skipped.",
                sentImages[0],
                imagesToSend.size(),
                (sentImages[0] == 1 ? "image" : "images"),
                (sentImages[0] == 1 ? "was" : "were"),
                sentImages[1],
                imagesToSend.size(),
                (sentImages[1] == 1 ? "was" : "were")));
    }
}
