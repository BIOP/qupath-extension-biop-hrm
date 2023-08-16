package qupath.ext.biop.hrm.senders;

import javafx.beans.property.ObjectProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import qupath.ext.biop.hrm.PluginRunnerFX;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import javax.swing.UIManager;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class QPHRMSendToHRM {

    private final static Logger logger = LoggerFactory.getLogger(QPHRMSendToHRM.class);
    // Create our ProgressBar
    private static ProgressBar progressBar = new ProgressBar(0.0);

    // Create a label to show current progress %
    private static Label lblProgress = new Label();

    /**
     * sends a list of images to HRM folder
     *
     * @param images
     * @param overwrite
     * @param rootFolder
     * @return
     */
    public static int[] send(List<ProjectImageEntry<BufferedImage>> images, boolean overwrite, String rootFolder, QuPathGUI quPathGUI) {
        int nSentImages = 0;
        int nSkippedImages = 0;

        // get image servers
        List<ImageServer<BufferedImage>> imageServers = new ArrayList<>();
        for (ProjectImageEntry<BufferedImage> image : images) {
            try {
                imageServers.add(image.readImageData().getServer());
            } catch (Exception e) {
                Dialogs.showErrorMessage("Reading image server", "Cannot Read Image data for image " + image.getImageName());
                return null;
            }
        }

        // filter omero and local images
        List<ImageServer<BufferedImage>> omeroServersList = imageServers.stream().filter(e -> e instanceof OmeroRawImageServer).collect(Collectors.toList());
        List<ImageServer<BufferedImage>> localServersList = imageServers.stream().filter(e -> !(e instanceof OmeroRawImageServer)).collect(Collectors.toList());

        // set the username for local images only
        String username = "";
        if (omeroServersList.isEmpty())
            username = askUsername();

        int nbImagesToDownload = omeroServersList.size() + localServersList.size();
        Task<Void> task = startProcess(omeroServersList, localServersList, nbImagesToDownload, rootFolder, username, overwrite);
        buildDialog(nbImagesToDownload, task);

        return (new int[]{nSentImages, nSkippedImages});
    }




    private static Task<Void> startProcess(List<ImageServer<BufferedImage>> omeroServersList,
                                     List<ImageServer<BufferedImage>> localServersList, int nbImagesToDownload,
                                     String rootFolder, String username, boolean overwrite) {

        // Create a background Task
        Task<Void> task = new Task<Void>() {
            int nSentImages = 0;
            int nSkippedImages = 0;
            @Override
            protected Void call() throws Exception {

                int i = 0;
                String finalUsername = username;

                for(i = 0; i <omeroServersList.size(); i++){
                    if(!isCancelled()) {
                        // Update our progress and message properties
                        updateProgress(i + 1, nbImagesToDownload);
                        updateMessage(String.valueOf(i + 1));
                        if (finalUsername.equals(""))
                            finalUsername = ((OmeroRawImageServer) omeroServersList.get(i)).getClient().getLoggedInUser().getOmeName().getValue();
                        int[] sentImages = downloadOmeroImage(omeroServersList.get(i), rootFolder, overwrite);
                        nSentImages += sentImages[0];
                        nSkippedImages += sentImages[1];
                    }
                }

                for(int j = i; j < localServersList.size(); j++){
                    if(!isCancelled()) {
                        // Update our progress and message properties
                        updateProgress(j + 1, nbImagesToDownload);
                        updateMessage(String.valueOf(j + 1));
                        int[] sentImages = downloadLocalImage(omeroServersList.get(i), rootFolder, overwrite, finalUsername);
                        nSentImages += sentImages[0];
                        nSkippedImages += sentImages[1];
                    }
                }
                return null;
            }

            @Override protected void succeeded() {
                super.succeeded();
                updateMessage("Done!");
                Dialogs.showInfoNotification("Sending To HRM",String.format("%d/%d %s %s successfully sent to HRM server and %d/%d %s skipped.",
                        nSentImages,
                        nbImagesToDownload,
                        (nSentImages == 1 ? "image" : "images"),
                        (nSentImages == 1 ? "was" : "were"),
                        nSkippedImages,
                        nbImagesToDownload,
                        (nSkippedImages == 1 ? "was" : "were")));
            }

            @Override protected void cancelled() {
                super.cancelled();
                updateMessage("Cancelled!");
                Dialogs.showWarningNotification("Sending To HRM","The download has been cancelled");
            }

            @Override protected void failed() {
                super.failed();
                updateMessage("Failed!");
                Dialogs.showErrorNotification("Sending To HRM","An error has occurs during the download");
            }
        };


        // This method allows us to handle any Exceptions thrown by the task
        task.setOnFailed(wse -> {
            wse.getSource().getException().printStackTrace();
        });


        // Before starting our task, we need to bind our UI values to the properties on the task
        progressBar.progressProperty().bind(task.progressProperty());
        lblProgress.textProperty().bind(task.messageProperty());

        // Now, start the task on a background thread
        Thread thread = new Thread(task);
        thread.start();

        return task;
    }

    private static int[] downloadOmeroImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite){
        int hasBeenSent = new QPHRMOmeroSender()
                .setClient(((OmeroRawImageServer) imageServer).getClient())
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder)
                .copy(overwrite);
        try {
            imageServer.close();
        } catch (Exception e) {
            logger.error("Cannot close the OMERO reader");
        }

        return (new int[]{(hasBeenSent == 1 ? 1 : 0), (hasBeenSent == 2 ? 1 : 0)});
    }


    private static int[] downloadLocalImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite, String username){
        int hasBeenSent = new QPHRMLocalSender()
                .setUsername(username)
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder)
                .copy(overwrite);
        try {
            imageServer.close();
        } catch (Exception e) {
            logger.error("Cannot close the local reader");
        }

        return (new int[]{(hasBeenSent == 1 ? 1 : 0), (hasBeenSent == 2 ? 1 : 0)});
    }



    /**
     * ask the HRM username
     *
     * @return
     */
    private static String askUsername() {

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

    private static void buildDialog(int nbImagesToDownload, Task<Void> task) {
        // Simple interface
        VBox root = new VBox(5);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);
        Stage primaryStage = new Stage();

        // Button to start the background task
        Button button = new Button("Cancel");
        button.setOnAction(event -> {
            task.cancel();
            primaryStage.close();

        });

        // Add our controls to the scene
        root.getChildren().addAll(
                progressBar,
                new HBox(5) {{
                    setAlignment(Pos.CENTER);
                    getChildren().addAll(
                            new Label("Download image: "),
                            lblProgress,
                            new Label(" / "+nbImagesToDownload)
                    );
                }},
                button
        );

        // Show the Stage
        primaryStage.setWidth(300);
        primaryStage.setHeight(200);
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

}
