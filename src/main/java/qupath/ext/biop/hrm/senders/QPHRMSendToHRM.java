package qupath.ext.biop.hrm.senders;

import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QPHRMSendToHRM {

    private final static Logger logger = LoggerFactory.getLogger(QPHRMSendToHRM.class);
    // Create our ProgressBar
    private static ProgressBar progressBar = new ProgressBar(0.0);

    // Create a label to show current progress %
    private static Label lblProgress = new Label();
    protected static Label resultsFolder = new Label();

    /**
     * sends a list of images to HRM folder
     *
     * @param images
     * @param overwrite
     * @param rootFolder
     * @return
     */
    public static void send(List<ProjectImageEntry<BufferedImage>> images, boolean overwrite, String rootFolder) {

        // get image servers
        List<ImageServer<BufferedImage>> imageServers = new ArrayList<>();
        for (ProjectImageEntry<BufferedImage> image : images) {
            try {
                imageServers.add(image.readImageData().getServer());
            } catch (Exception e) {
                Dialogs.showErrorMessage("Reading image server", "Cannot Read Image data for image " + image.getImageName());
                return;
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
        buildDialog(task);
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
                        updateMessage(i + 1 + " / " + nbImagesToDownload);
                        updateProgress(i + 1, nbImagesToDownload);
                        if (finalUsername.equals(""))
                            finalUsername = ((OmeroRawImageServer) omeroServersList.get(i)).getClient().getLoggedInUser().getOmeName().getValue();
                        QPHRMOmeroSender qphrmOmeroSender = downloadOmeroImage(omeroServersList.get(i), rootFolder, overwrite);
                        nSentImages += qphrmOmeroSender.isSent() ? 1 : 0;
                        nSkippedImages += qphrmOmeroSender.isSkipped() ? 1 : 0;
                        updateTitle(qphrmOmeroSender.getDestinationFolder());
                    }
                }

                for(int j = i; j < localServersList.size(); j++){
                    if(!isCancelled()) {
                        // Update our progress and message properties
                        updateMessage(j + 1 + " / " + nbImagesToDownload);
                        updateProgress(j + 1, nbImagesToDownload);
                        QPHRMLocalSender qphrmLocalSender = downloadLocalImage(omeroServersList.get(i), rootFolder, overwrite, finalUsername);
                        nSentImages += qphrmLocalSender.isSent() ? 1 : 0;
                        nSkippedImages += qphrmLocalSender.isSkipped() ? 1 : 0;
                        updateTitle(qphrmLocalSender.getDestinationFolder());
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
        resultsFolder.textProperty().bind(task.titleProperty());




        // Now, start the task on a background thread
        Thread thread = new Thread(task);
        thread.start();

        return task;
    }

    private static QPHRMOmeroSender downloadOmeroImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite){
        QPHRMOmeroSender qphrmOmeroSender = new QPHRMOmeroSender()
                .setClient(((OmeroRawImageServer) imageServer).getClient())
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder)
                .copy(overwrite);
        try {
            imageServer.close();
        } catch (Exception e) {
            logger.error("Cannot close the OMERO reader");
        }

        return qphrmOmeroSender;
    }


    private static QPHRMLocalSender downloadLocalImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite, String username){
        QPHRMLocalSender qphrmLocalSender = new QPHRMLocalSender()
                .setUsername(username)
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder)
                .copy(overwrite);
        try {
            imageServer.close();
        } catch (Exception e) {
            logger.error("Cannot close the local reader");
        }

        return qphrmLocalSender;
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

    private static void buildDialog(Task<Void> task) {
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
        lblProgress.setAlignment(Pos.CENTER);

        resultsFolder.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                    if(mouseEvent.getClickCount() == 2){
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(resultsFolder.getText());
                        clipboard.setContent(content);

                        resultsFolder.setTextFill(Color.WHITE);
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        resultsFolder.setTextFill(Color.BLACK);

                    }
                }
            }
        });

        // Add our controls to the scene
        root.getChildren().addAll(
                progressBar,
                new HBox(5) {{
                    setAlignment(Pos.CENTER);
                    getChildren().addAll(
                            new Label("Download image: "),
                            lblProgress
                    );
                }},
                new HBox(5) {{
                    setAlignment(Pos.CENTER);
                    getChildren().addAll(
                            resultsFolder
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
