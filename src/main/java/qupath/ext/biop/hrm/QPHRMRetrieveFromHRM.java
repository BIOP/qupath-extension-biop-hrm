package qupath.ext.biop.hrm;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawClients;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * List deconvolved images from HRM and select the retriever corresponding to where the raw image is located (locally
 * or on OMERO).
 */
public class QPHRMRetrieveFromHRM {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMRetrieveFromHRM.class);

    /**
     * List deconvolved images and send them back to raw location and to QuPath project
     *
     * @param qupath
     * @param root
     * @param owner
     * @param deleteOnHRM
     * @param omeroHost
     * @return
     */
    public static boolean retrieve(QuPathGUI qupath, String root, String owner, boolean deleteOnHRM, String omeroHost){
        // list all files retrieve in QuPath
        Map<String, String> imageTypeMap = listFileToUpload(root, owner);
        if(imageTypeMap.isEmpty())
            return false;

        // check if there is an OMERO connection to get and ask for one in case
        List<Map.Entry<String, String>> omeroList = imageTypeMap.entrySet()
                .stream()
                .filter(e -> e.getValue().equalsIgnoreCase("omero"))
                .collect(Collectors.toList());
        OmeroRawClient client = null;
        if(!omeroList.isEmpty()) {
            client = askForOmeroConnection(omeroHost);
            if (client == null) {
                Dialogs.showErrorMessage("OMERO Connection issue", "Cannot connect to OMERO server. No image will be retrieved from HRM");
                return false;
            }
        }

        // select the type of connection and choose the correct retriever
        for(Map.Entry<String,String> entry : imageTypeMap.entrySet()){
            // get the results file
            File paramFile = getResultsFile(entry.getKey(), ".parameters.txt");

            // parse the parameter file and extract key-value pairs
            Map<String, Map<String, String>> metadata = new TreeMap<>();
            if(paramFile != null)
                metadata = parseSummaryFile(paramFile);

            QPHRMRetriever retriever;
            switch(entry.getValue().toLowerCase()){
                case "omero":
                    // get the log file
                    File logFile = getResultsFile(entry.getKey(), ".log.txt");
                    retriever = new QPHRMOmeroRetriever()
                            .setImage(entry.getKey())
                            .setClient(client)
                            .setMetadata(metadata)
                            .setLogFile(logFile);

                    break;
                case "local":
                    retriever = new QPHRMLocalRetriever()
                            .setImage(entry.getKey())
                            .setMetadata(metadata);
                    break;
                default:
                    Dialogs.showWarningNotification("Type does not exists", "Type "+entry.getValue()+" is not supported for image "+entry.getKey());
                    return false;
            }

            // send back deconvolved image to their location and to QuPath project
            if(retriever.buildTarget())
                if(retriever.sendBack())
                    if(retriever.toQuPath(qupath))
                        if(deleteOnHRM)
                            if(deleteAssociatedFiles(entry.getKey()))
                                logger.info("Image"+entry.getKey()+" and associated files are deleted on HRM-Share folder");
                            else
                                logger.error("Cannot delete image "+entry.getKey()+" neither associated files");
        }
        return true;
    }

    /**
     * deletes deconvolved image and associated files (.txt files and other) from HRM.
     * Deletion is based on image name, that contains a unique HRM ID
     *
     * @param filePath
     * @return
     */
    private static boolean deleteAssociatedFiles(String filePath){
        // get image name
        File imageFile = new File(filePath);
        String imageName = imageFile.getName();

        // remove extension
        imageName = imageName.substring(0,imageName.lastIndexOf("."));

        // list all files in the parent folder
        File[] fileList = imageFile.getParentFile().listFiles();

        if (fileList == null)
            return false;

        // delete files that contain the image name
        boolean filesDeleted = true;
        for(File file : fileList)
            if(file.getName().contains(imageName))
                filesDeleted = filesDeleted && file.delete();

        return filesDeleted;
    }

    /**
     * returns the first file that contains image name, with the specified suffix.
     *
     * @param imagePath
     * @param suffix
     * @return
     */
    private static File getResultsFile(String imagePath, String suffix){
        File imageFile = new File(imagePath);

        // get image name without extension
        int extensionPosition = imageFile.getName().lastIndexOf(".");
        String imageName = imageFile.getName().substring(0, extensionPosition);

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
    public static void toQuPath(QuPathGUI qupath, ImageServerBuilder<BufferedImage> imageServerBuilder, String imageURI, Map<String, String> keyVals) throws IOException {
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
    private static Map<String, String> listFileToUpload(String root, String owner){
        // check existence of the root folder
        if(!new File(root).exists()){Dialogs.showErrorNotification("List files to upload","Path "+root+" does not exists"); return new HashMap<>();}

        // check user folder
        File ownerFolder = new File(root + File.separator + owner);
        if(!ownerFolder.exists()) {Dialogs.showErrorNotification("List files to upload","Path "+ownerFolder+" does not exists"); return new HashMap<>();}

        // check deconvolved folder
        File deconvolvedFolder = new File(ownerFolder + File.separator + "Deconvolved");
        if(!deconvolvedFolder.isDirectory()) {Dialogs.showErrorNotification("List files to upload","Path "+deconvolvedFolder+" does not exists"); return new HashMap<>();}

        // check QuPath folder
        File qupathFolder = new File(deconvolvedFolder + File.separator + "QuPath");
        if(!qupathFolder.isDirectory()) {Dialogs.showErrorNotification("List files to upload","Path "+qupathFolder+" does not exists"); return new HashMap<>();}

        // list category folders (local, omero, s3)
        File[] dirs = qupathFolder.listFiles();
        Map<String, String> imageTypeMap = new HashMap<>();

        // put all images within these folders in a map with their category
        if(dirs != null)
            for(File dir : dirs)
                if(dir.isDirectory())
                    recursiveFileListing(dir, dir.getName(), imageTypeMap);

        // check if there are files to retrieve
        if(imageTypeMap.isEmpty())
            Dialogs.showErrorNotification("Empty directory","There is not image to retrieve from " +qupathFolder.getAbsolutePath());

        return imageTypeMap;
    }


    /**
     * search deconvolved images in sub-folders
     *
     * @param directory
     * @param typeName
     * @param imageTypeMap
     */
    private static void recursiveFileListing(File directory, String typeName, Map<String, String> imageTypeMap) {
        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if(fList != null)
            for (File file : fList) {
                if (file.isFile() && file.getName().endsWith(".dv")) { // TODO change to .ids
                    imageTypeMap.put(file.getAbsolutePath(), typeName);
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
    public static Map<String, Map<String, String>> parseSummaryFile(File file)  {
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
}
