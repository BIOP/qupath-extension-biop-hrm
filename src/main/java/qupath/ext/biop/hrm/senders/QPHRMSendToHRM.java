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
    private static ProgressBar progressBar = new ProgressBar(0.0);
    private static Label lblProgress = new Label();
    protected static Label lblResultsFolder = new Label();
    private static String message = "";
    private static List<String> resolutsFolderlist = new ArrayList<>();
    private static Button centralButton = new Button();


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

        //send images to HRM
        int nbImagesToDownload = omeroServersList.size() + localServersList.size();
        Task<Void> task = startProcess(omeroServersList, localServersList, nbImagesToDownload, rootFolder, username, overwrite);
        buildDialog(task);
    }


    /**
     * Background task that send images to HRM
     *
     * @param omeroServersList
     * @param localServersList
     * @param nbImagesToDownload
     * @param rootFolder
     * @param username
     * @param overwrite
     * @return
     */
    private static Task<Void> startProcess(List<ImageServer<BufferedImage>> omeroServersList,
                                     List<ImageServer<BufferedImage>> localServersList, int nbImagesToDownload,
                                     String rootFolder, String username, boolean overwrite) {
        // Create a background Task
        Task<Void> task = new Task<Void>() {
            int nSentImages = 0;
            int nSkippedImages = 0;
            @Override
            protected Void call() {

                int i = 0;
                String finalUsername = username;

                for(i = 0; i <omeroServersList.size(); i++){
                    if(!isCancelled()) {
                        // Update our progress and message properties
                        updateMessage(i + 1 + " / " + nbImagesToDownload);
                        updateProgress(i, nbImagesToDownload);

                        // update username for local sender
                        String omeroUsername = ((OmeroRawImageServer) omeroServersList.get(i)).getClient().getLoggedInUser().getOmeName().getValue();
                        if (finalUsername.equals(""))
                            finalUsername = omeroUsername;

                        // send images to HRM
                        QPHRMOmeroSender qphrmOmeroSender = downloadOmeroImage(omeroServersList.get(i), rootFolder, overwrite, omeroUsername);
                        nSentImages += qphrmOmeroSender.isSent() ? 1 : 0;
                        nSkippedImages += qphrmOmeroSender.isSkipped() ? 1 : 0;

                        String destFold = qphrmOmeroSender.getDestinationFolder();
                        if(!resolutsFolderlist.contains(destFold)) {
                            resolutsFolderlist.add(destFold);
                            message += "\n"+destFold;
                            updateTitle(message);
                        }
                    }
                }

                for(int j = 0; j < localServersList.size(); j++){
                    if(!isCancelled()) {
                        // Update our progress and message properties
                        updateMessage(i + j + 1 + " / " + nbImagesToDownload);
                        updateProgress(i + j, nbImagesToDownload);

                        // send images to HRM
                        QPHRMLocalSender qphrmLocalSender = downloadLocalImage(localServersList.get(j), rootFolder, overwrite, finalUsername);
                        nSentImages += qphrmLocalSender.isSent() ? 1 : 0;
                        nSkippedImages += qphrmLocalSender.isSkipped() ? 1 : 0;

                        String destFold = qphrmLocalSender.getDestinationFolder();
                        if(!resolutsFolderlist.contains(destFold)) {
                            resolutsFolderlist.add(destFold);
                            message += "\n"+destFold;
                            updateTitle(message);
                        }
                    }
                }
                return null;
            }

            @Override protected void succeeded() {
                super.succeeded();
                updateProgress(nbImagesToDownload, nbImagesToDownload);
                String finalMessage = "\n" + String.format("Sent %s : %d/%d  \nSkipped %s : %d/%d ",
                        (nSentImages == 1 ? "image" : "images"),
                        nSentImages,
                        nbImagesToDownload,
                        (nSentImages == 1 ? "image" : "images"),
                        nSkippedImages,
                        nbImagesToDownload);
                updateMessage("Done!"+finalMessage);
                centralButton.setText("Done");
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
        lblResultsFolder.textProperty().bind(task.titleProperty());

        // Now, start the task on a background thread
        Thread thread = new Thread(task);
        thread.start();

        return task;
    }


    /**
     * send omero images to HRM server
     *
     * @param imageServer
     * @param rootFolder
     * @param overwrite
     * @param username
     * @return the omero sender
     */
    private static QPHRMOmeroSender downloadOmeroImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite, String username){
        QPHRMOmeroSender qphrmOmeroSender = new QPHRMOmeroSender()
                .setClient(((OmeroRawImageServer) imageServer).getClient())
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder, username)
                .copy(overwrite);
        try {
            imageServer.close();
        } catch (Exception e) {
            logger.error("Cannot close the OMERO reader");
        }

        return qphrmOmeroSender;
    }


    /**
     * send local images to HRM server
     *
     * @param imageServer
     * @param rootFolder
     * @param overwrite
     * @param username
     * @return the local sender
     */
    private static QPHRMLocalSender downloadLocalImage(ImageServer<BufferedImage> imageServer, String rootFolder, boolean overwrite, String username){
        QPHRMLocalSender qphrmLocalSender = new QPHRMLocalSender()
                .setImage(imageServer)
                .buildDestinationFolder(rootFolder, username)
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

    /**
     * build the progress Dialog box
     * @param task
     */
    private static void buildDialog(Task<Void> task) {
        // Simple interface
        VBox root = new VBox(5);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);
        Stage primaryStage = new Stage();

        // Button to cancel the background task
        centralButton = new Button("Cancel");
        centralButton.setOnAction(event -> {
            task.cancel();
            primaryStage.close();
        });

        lblProgress.setAlignment(Pos.TOP_LEFT);
        progressBar.setMinWidth(200);

        // copy the path to clipboard if double-clicking on it
        lblResultsFolder.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                    if(mouseEvent.getClickCount() == 2){
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(lblResultsFolder.getText());
                        clipboard.setContent(content);
                    }
                }
            }
        });

        // Add boxes du the main box
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
                            lblResultsFolder
                    );
                }},
                centralButton
        );

        // Show the Stage
        primaryStage.setWidth(350);
        primaryStage.setHeight(250);
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Sending images to HRM");
        primaryStage.show();
    }
}
