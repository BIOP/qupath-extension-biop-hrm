package qupath.ext.biop.hrm;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
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
    public QPHRMSendToHRMCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        Project<BufferedImage> project = qupath.getProject();

        if (project == null) {
            Dialogs.showNoProjectError("Script editor");
            return;
        }

        /** Code taken as is from Pete Bankhead
         * https://github.com/qupath/qupath/blob/7548759fb102a33be8cac6a21c9d7726a69bdbbd/qupath-gui-fx/src/main/java/qupath/lib/gui/scripting/DefaultScriptEditor.java#L1518
         * */
        ArrayList<ProjectImageEntry<BufferedImage>> images = new ArrayList<ProjectImageEntry<BufferedImage>>();
        String sameImageWarning = "A selected image is open in the viewer!\nAny unsaved changes will be ignored.";
        var listSelectionView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), images, sameImageWarning);

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

        List<ProjectImageEntry<BufferedImage>> imagesToSend = new ArrayList<>(images);
        System.out.println(imagesToSend);

        boolean wasSent = QPHRMSendToHRM.send(imagesToSend);
    }
}
