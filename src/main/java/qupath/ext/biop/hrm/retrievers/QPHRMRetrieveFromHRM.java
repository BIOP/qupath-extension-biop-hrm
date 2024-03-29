package qupath.ext.biop.hrm.retrievers;

import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.hrm.HRMConstants;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawClients;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * List deconvolved images from HRM and select the retriever corresponding to where the raw image is located (locally
 * or on OMERO).
 */
public class QPHRMRetrieveFromHRM {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMRetrieveFromHRM.class);
    private static ProgressBar progressBar = new ProgressBar(0.0);
    private static Label lblProgress = new Label();
    private static Label lblMessage = new Label();
    private static Button centralButton = new Button();
    private static String message = "";
    private static  Pattern deconvolvedNamePattern = Pattern.compile("(?<name>.*)_(?<hrmcode>.{13})_hrm.*");


    /**
     * List deconvolved images and send them back to raw location and to QuPath project
     *
     * @param qupath
     * @param root
     * @param owner
     * @param deleteDeconvolved
     * @param omeroHost
     * @return
     */
    public static void retrieve(QuPathGUI qupath, String root, String owner, boolean deleteDeconvolved, boolean deleteRaw, String omeroHost){
        // list all files retrieve in QuPath
        Map<File, String> imageTypeMap = listFileToUpload(root, owner);
        if(imageTypeMap.isEmpty())
            return;

        // check if there is an OMERO connection to get and ask for one in case
        List<Map.Entry<File, String>> omeroList = imageTypeMap.entrySet()
                .stream()
                .filter(e -> e.getValue().equalsIgnoreCase("omero"))
                .collect(Collectors.toList());
        OmeroRawClient client = null;
        if(!omeroList.isEmpty()) {
            client = askForOmeroConnection(omeroHost);
            if (client == null) {
                Dialogs.showErrorMessage("OMERO Connection issue", "Cannot connect to OMERO server. No image will be retrieved from HRM");
                return;
            }
        }

        Task<Void> task = startProcess(qupath, imageTypeMap, deleteDeconvolved, deleteRaw, imageTypeMap.size(), client);
        buildDialog(task);
    }


    /**
     * Background task that retrieve images from HRM
     *
     * @param qupath
     * @param imageTypeMap
     * @param deleteDeconvolved
     * @param nbImagesToRetrieve
     * @param client
     * @return
     */
    private static Task<Void> startProcess(QuPathGUI qupath, Map<File, String> imageTypeMap,
                                           boolean deleteDeconvolved, boolean deleteRaw, int nbImagesToRetrieve,
                                           OmeroRawClient client) {
        // Create a background Task
        Task<Void> task = new Task<Void>() {
            int nRetrievedImages = 0;
            @Override
            protected Void call() {

                List<File> paths = new ArrayList<>(imageTypeMap.keySet());
                message += "\n--- Minimal log window - Please look at the log file for more details ---";
                updateTitle(message);

                // select the type of connection and choose the correct retriever
                for(int i = 0; i < paths.size(); i++){
                    // Update our progress and message properties
                    updateMessage(i + 1 + " / " + nbImagesToRetrieve);
                    updateProgress(i, nbImagesToRetrieve);

                    File imgFile = paths.get(i);
                    String imageServerType = imageTypeMap.get(imgFile);

                    // get the results file
                    File paramFile = getResultsFile(imgFile, ".parameters.txt");

                    // parse the parameter file and extract key-value pairs
                    Map<String, Map<String, String>> metadata = new TreeMap<>();
                    if(paramFile != null)
                        metadata = parseSummaryFile(paramFile);

                    String rawName = "";
                    String hrmCode = "";
                    Matcher matcher = deconvolvedNamePattern.matcher(imgFile.getName());
                    if(matcher.find()){
                        rawName = matcher.group("name");
                        hrmCode = matcher.group("hrmcode");
                    }

                    QPHRMRetriever retriever;
                    switch(imageServerType.toLowerCase()){
                        case "omero":
                            // get the log file
                            File logFile = getResultsFile(imgFile, ".log.txt");
                            retriever = new QPHRMOmeroRetriever()
                                    .setImage(imgFile, rawName, hrmCode)
                                    .setClient(client)
                                    .setMetadata(metadata)
                                    .setLogFile(logFile);

                            break;
                        case "local":
                            retriever = new QPHRMLocalRetriever()
                                    .setImage(imgFile, rawName, hrmCode)
                                    .setMetadata(metadata);
                            break;
                        default:
                            String smallMessage = "Type " + imageServerType + " is not supported for image " + imgFile;
                            logger.warn(smallMessage);
                            message += "\n" + smallMessage;
                            updateTitle(message);
                            continue;
                    }

                    // send back deconvolved image to their location and to QuPath project
                    if(retriever.buildTarget()) {
                        if (retriever.sendBack()) {
                            if (retriever.toQuPath(qupath)) {
                                if (deleteDeconvolved) {
                                    if (deleteDeconvolvedFiles(imgFile))
                                        logger.info("Image [" + imgFile + "] and associated files are deleted from HRM-Share folder");
                                    else {
                                        String smallMessage = "Cannot delete image [" + imgFile + "] neither associated files";
                                        message += "\n" + smallMessage;
                                        updateTitle(message);
                                        logger.error(smallMessage);
                                    }
                                }

                                if(deleteRaw){
                                    if (deleteRawImages(imgFile, rawName))
                                        logger.info("Image [" + imgFile + "] are deleted from HRM-Share folder");
                                    else {
                                        String smallMessage = "Cannot delete image [" + imgFile +"]";
                                        message += "\n" + smallMessage;
                                        updateTitle(message);
                                        logger.error(smallMessage);
                                    }
                                }

                                nRetrievedImages += 1;
                            }else{
                                String smallMessage = "Cannot add image to QuPath for" +imgFile;
                                message += "\n" + smallMessage;
                                updateTitle(message);
                            }
                        }else{
                            String smallMessage = "Cannot send back results for : " +imgFile;
                            message += "\n" + smallMessage;
                            updateTitle(message);
                        }
                    }else{
                        String smallMessage = "Cannot build target folder for : " +imgFile;
                        message += "\n" + smallMessage;
                        updateTitle(message);
                    }
                }
                return null;
            }

            @Override protected void succeeded() {
                super.succeeded();
                updateProgress(nbImagesToRetrieve, nbImagesToRetrieve);
                String finalMessage = "\n" + String.format("Retrieved %s : %d/%d",
                        (nRetrievedImages == 1 ? "image" : "images"),
                        nRetrievedImages,
                        nbImagesToRetrieve);
                updateMessage("Done!"+finalMessage);
                centralButton.setText("Done");
            }

            @Override protected void cancelled() {
                super.cancelled();
                updateMessage("Cancelled!");
                Dialogs.showWarningNotification("Retrieving from HRM","Task has been cancelled");
            }

            @Override protected void failed() {
                super.failed();
                updateMessage("Failed!");
                Dialogs.showErrorNotification("Retrieving from HRM","An error has occurs during the retrieve");
            }
        };

        // This method allows us to handle any Exceptions thrown by the task
        task.setOnFailed(wse -> {
            wse.getSource().getException().printStackTrace();
        });

        // Before starting our task, we need to bind our UI values to the properties on the task
        progressBar.progressProperty().bind(task.progressProperty());
        lblProgress.textProperty().bind(task.messageProperty());
        lblMessage.textProperty().bind(task.titleProperty());

        // Now, start the task on a background thread
        Thread thread = new Thread(task);
        thread.start();

        return task;
    }

    /**
     * deletes deconvolved image and associated files (.txt files and other) from HRM.
     * Deletion is based on image name, that contains a unique HRM ID
     *
     * @param imageFile
     * @return
     */
    private static boolean deleteDeconvolvedFiles(File imageFile){
        // get image name
        String imageName = imageFile.getName();

        // remove file extension
        imageName = imageName.substring(0, imageName.lastIndexOf("."));

        // parent file
        File parentFile = imageFile.getParentFile();

        return deleteFilesAndParent(parentFile, imageName);
    }

    /**
     * deletes raw image from HRM.
     * Deletion is based on the raw image name
     *
     * @param imageFile
     * @param rawImgName
     * @return
     */
    private static boolean deleteRawImages(File imageFile, String rawImgName){
        // deconvolved parent file
        File parentFile = imageFile.getParentFile();

        // change the path to point to the right folder
        String rawParentPath = parentFile.getAbsolutePath().replace(HRMConstants.DECONVOLVED_FOLDER, HRMConstants.RAW_FOLDER);
        File rawParentFile = new File(rawParentPath);

        if(rawParentFile.exists()){
            return deleteFilesAndParent(rawParentFile, rawImgName);
        }else{
            logger.warn("The path ["+ rawParentFile.getAbsolutePath()+"] does not exists ; raw images are not deleted");
            return false;
        }
    }

    /**
     * delete file based on the name to match and also delete the parent folders (i.e. dataset and project folder)
     * only if there are empty
     *
     * @param parentFile
     * @param nameToMatch
     * @return
     */
    private static boolean deleteFilesAndParent(File parentFile, String nameToMatch){
        // list all files in the parent folder
        File[] fileList = parentFile.listFiles();

        if (fileList == null)
            return false;

        // delete files that contain the image name
        boolean filesDeleted = true;
        for(File file : fileList) {
            if (file.getName().contains(nameToMatch)) {
                logger.info("Delete file [" + file.getAbsoluteFile() + "]");
                filesDeleted = filesDeleted && file.delete();
            }
        }

        fileList = parentFile.listFiles();

        // delete parents
        try {
            if (fileList != null && fileList.length == 0) {
                // delete dataset folder
                logger.info("Delete dataset directory ["+parentFile.getAbsoluteFile()+"]");
                File parentParentFile = parentFile.getParentFile();
                FileUtils.deleteDirectory(parentFile);

                // delete project folder
                File[] parentList = parentParentFile.listFiles();
                if (parentList != null && parentList.length == 0) {
                    logger.info("Delete project directory ["+parentParentFile.getAbsoluteFile()+"]");
                    FileUtils.deleteDirectory(parentParentFile);
                }
            }
        }catch (IOException e){
            filesDeleted = false;
        }

        return filesDeleted;
    }

    /**
     * returns the first file that contains image name, with the specified suffix.
     *
     * @param imageFile
     * @param suffix
     * @return
     */
    private static File getResultsFile(File imageFile, String suffix){
        // get image name without extension
        String imageName = imageFile.getName().split("\\.")[0];

        // list all files in the parent folder
        File[] files = imageFile.getParentFile().listFiles();
        if(files == null) {
            logger.warn("There is not file in directory "+imageFile.getParentFile());
            return null;
        }

        // extract only file with image name and suffix
        List<File> parametersFile = Arrays.stream(files).filter(e -> e.getName().endsWith(suffix) && e.getName().contains(imageName)).collect(Collectors.toList());

        if(!parametersFile.isEmpty())
            return parametersFile.get(0);
        else {
            logger.warn("There is not file with extension "+suffix+"in the folder "+imageFile.getParentFile());
            return null;
        }
    }

    /**
     * Add the deconvolved image to the current QuPath project.
     * Add to this image all metatdata parsed from the .parameters.txt file
     *
     * This code has been copied from qupath.lib.gui.commands.ProjectImportImagesCommand.promptToImportImages()
     *
     * @param qupath
     * @param imageServerBuilder
     * @param imageURI URI of the deconvolved image
     * @param keyVals
     * @throws IOException
     */
    protected static void toQuPath(QuPathGUI qupath, ImageServerBuilder<BufferedImage> imageServerBuilder, String imageURI, Map<String, String> keyVals) throws IOException {
        List<ProjectImageEntry<BufferedImage>> projectImages = new ArrayList<>();
        Project<BufferedImage> project = qupath.getProject();

        URI uri = null;
        try {
            uri = GeneralTools.toURI(imageURI);
            var tempProject = ProjectIO.loadProject(uri, BufferedImage.class);
            projectImages = new ArrayList<>(tempProject.getImageList());
        } catch (Exception e) {
            logger.warn("Unable to add images from {} ({})", imageURI, e.getLocalizedMessage());
        }

        // If we have projects, try adding images from these first
        if (!projectImages.isEmpty()) {
            for (var temp : projectImages) {
                try {
                    project.addDuplicate(temp, true);
                } catch (Exception e) {
                    logger.error("Unable to copy images to the current project");
                }
            }
        }

        // define the builder
        ImageServerBuilder.UriImageSupport<BufferedImage> support;
        if(imageServerBuilder == null)
            support = ImageServers.getImageSupport(uri, "");
        else
            support = ImageServers.getImageSupport(imageServerBuilder, uri, "");

        if (support != null){
            List<ImageServerBuilder.ServerBuilder<BufferedImage>> builders = support.getBuilders();

            // Add everything in order first
            List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>();
            for (var builder : builders) {
                entries.add(project.addImage(builder));
            }

            // Initialize (the slow bit)
            for (var entry : entries) {
                // initialize entry
                try (ImageServer<BufferedImage> server = entry.getServerBuilder().build()){
                    // Set the image name
                    String name = ServerTools.getDisplayableImageName(server);
                    entry.setImageName(name);

                    // Pyramidalize this if we need to
                    ImageServer<BufferedImage> server2 = server;
                    int minPyramidDimension = PathPrefs.minPyramidDimensionProperty().get();
                    if (server.nResolutions() == 1 && Math.max(server.getWidth(), server.getHeight()) > minPyramidDimension) {
                        var serverTemp = ImageServers.pyramidalize(server);
                        if (serverTemp.nResolutions() > 1) {
                            logger.debug("Auto-generating image pyramid for " + name);
                            server2 = serverTemp;
                        } else
                            serverTemp.close();
                    }

                    if (server != server2)
                        server2.close();

                } catch (Exception e) {
                    logger.warn("Exception adding " + entry, e);
                }

                // add metadata
                keyVals.forEach((key, value)->entry.putMetadataValue(key,value));
            }

            // refresh the project
            try {
                project.syncChanges();
            } catch (IOException e1) {
                Dialogs.showErrorMessage("Sync project", e1);
            }
            qupath.refreshProject();
        }
    }

    /**
     * Establish the OMERO connection. If the default host is empty, then it asks it to the user.
     *
     * @param host
     * @return
     */
    private static OmeroRawClient askForOmeroConnection(String host){
        if (host.equals("")){
            GridPane gp = new GridPane();
            gp.setVgap(5.0);
            TextField tf = new TextField("");
            tf.setPrefWidth(400);
            PaneTools.addGridRow(gp, 0, 0, "Enter OMERO URL", new Label("Enter an OMERO server URL to browse (e.g. http://idr.openmicroscopy.org/):"));
            PaneTools.addGridRow(gp, 1, 0, "Enter OMERO URL", tf, tf);
            var confirm = Dialogs.showConfirmDialog("Enter OMERO URL", gp);
            if (!confirm)
                return null;

            var path = tf.getText();
        }

        try {
            if (!host.startsWith("http:") && !host.startsWith("https:")) {
                Dialogs.showErrorMessage("Non valid URL", "The input URL must contain a scheme (e.g. \"https://\")!");
                return null;
            }

            // Make the path a URI
            URI uri = new URI(host);

            // Clean the URI (in case it's a full path)
            // TODO find a way to reuse a client if already exists and not to create a new one.
            URI uriServer = OmeroRawTools.getServerURI(uri);
           return OmeroRawClients.createClientAndLogin(uriServer);

        }catch(IOException | URISyntaxException e){
            Dialogs.showErrorMessage("OMERO Connection issue", "Cannot connect to OMERO server with "+host);
            return null;
        }
    }


    /**
     * returns the list of available deconvolved images in the QuPath folder of the user HRM-Share folder.
     *
     * @param root
     * @param owner
     * @return
     */
    private static LinkedHashMap<File, String> listFileToUpload(String root, String owner){
        // check existence of the root folder
        if(!new File(root).exists()){Dialogs.showErrorNotification("List files to upload","Path "+root+" does not exists"); return new LinkedHashMap<>();}

        // check user folder
        File ownerFolder = new File(root + File.separator + owner);
        if(!ownerFolder.exists()) {Dialogs.showErrorNotification("List files to upload","Path "+ownerFolder+" does not exists"); return new LinkedHashMap<>();}

        // check deconvolved folder
        File deconvolvedFolder = new File(ownerFolder + File.separator + HRMConstants.DECONVOLVED_FOLDER);
        if(!deconvolvedFolder.isDirectory()) {Dialogs.showErrorNotification("List files to upload","Path "+deconvolvedFolder+" does not exists"); return new LinkedHashMap<>();}

        // list category folders (local, omero, s3)
        File[] dirs = deconvolvedFolder.listFiles();
        LinkedHashMap<File, String> imageTypeMap = new LinkedHashMap<>();

        // put all images within these folders in a map with their category
        if(dirs != null)
            for(File dir : dirs)
                if(dir.isDirectory())
                    recursiveFileListing(dir, dir.getName(), imageTypeMap);

        // check if there are files to retrieve
        if(imageTypeMap.isEmpty())
            Dialogs.showErrorNotification("Empty directory","There is not image to retrieve from " +deconvolvedFolder.getAbsolutePath());

        return imageTypeMap;
    }


    /**
     * search deconvolved images in sub-folders
     *
     * @param directory
     * @param typeName
     * @param imageTypeMap
     */
    private static void recursiveFileListing(File directory, String typeName, Map<File, String> imageTypeMap) {
        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if(fList != null)
            for (File file : fList) {
                if (file.isFile() &&  file.getName().endsWith(".ids") ) { // TODO change to .ids
                    imageTypeMap.put(file, typeName);
                } else if (file.isDirectory()) {
                    recursiveFileListing(file, typeName, imageTypeMap);
                }
            }
    }


    /**
     * parse the html-encoded .parameters.txt file and
     * return a map of image and restoration parameters of the deconvolution
     *
     * @param file
     * @return
     */
    private static Map<String, Map<String, String>> parseSummaryFile(File file)  {
        Map<String, Map<String, String>> nameSpaceKeyValueMap = new TreeMap<>();

        try{
            // parse the html parameters file
            Document htmlDocument = Jsoup.parse(file);

            // get all the "tables" node
            Elements tables = htmlDocument.getElementsByTag("table");
            tables.forEach(table->{
                // get all table rows (including headers)
                Elements parameters = table.getElementsByTag("tr");

                // get the header
                String header = parameters.get(0).firstElementChild().text();

                // make a sub list with only parameters (without headers)
                List<Element> reducedParameters = parameters.subList(2,parameters.size()-1);
                Map<String, String> keyValues = new TreeMap<>(); // to have the natural order of elements

                reducedParameters.forEach(parameter->{
                    Element element = parameter.firstElementChild();
                    if(element != null) {
                        // parse the key, value and channel
                        String param = element.text();
                        String channel = parameter.after(element).firstElementChild().text();
                        String value = parameter.lastElementChild().text();

                        // check if the parameter is channel-dependent
                        if(channel.equals("All"))
                            keyValues.put(param, value);
                        else
                            keyValues.put(param+ " ch"+channel, value);
                    }
                });
                nameSpaceKeyValueMap.put(header, keyValues);
            });

        } catch(IOException | NullPointerException e){
            Dialogs.showWarningNotification("Parameter file parsing", "Cannot parse the file "+file.toString()+". No key values will be uploaded");
            return new TreeMap<>();
        }
        return nameSpaceKeyValueMap;
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

        lblProgress.setAlignment(Pos.CENTER);
        progressBar.setMinWidth(200);

        // copy the path to clipboard if double-clicking on it
        lblMessage.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if(mouseEvent.getButton().equals(MouseButton.PRIMARY)){
                    if(mouseEvent.getClickCount() == 2){
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(lblMessage.getText());
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
                            new Label("Retrieve image: "),
                            lblProgress
                    );
                }},
                new HBox(5) {{
                    setAlignment(Pos.CENTER);
                    getChildren().addAll(
                            lblMessage
                    );
                }},
                centralButton
        );

        // Show the Stage
        primaryStage.setWidth(350);
        primaryStage.setHeight(250);
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Retrieving deconvolved images from HRM");
        primaryStage.show();
    }
}
