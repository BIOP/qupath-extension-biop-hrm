package qupath.ext.biop.hrm;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawClients;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QPHRMRetrieveFromHRM {


    public static boolean retrieve(String root, String owner){
        // list all files retrieve in QuPath
        Map<String, String> imageTypeMap = listFileToUpload(root, owner);

        // check if there is an OMERO connection to get and ask for one in case
        List<Map.Entry<String, String>> omeroList = imageTypeMap.entrySet()
                .stream()
                .filter(e -> e.getValue().equalsIgnoreCase("omero"))
                .collect(Collectors.toList());
        OmeroRawClient client = null;
        if(!omeroList.isEmpty())
            client = askForOmeroConnection();
        if(client == null){
            Dialogs.showErrorMessage("OMERO Connection issue", "Cannot connect to OMERO server. No image will be retrieved from HRM");
            return false;
        }


        // select the type of connection and choose the correct retriever
        for(Map.Entry<String,String> entry : imageTypeMap.entrySet()){
            Map<String, String> metadata = new HashMap<>(){{put("test","1"); put("retest","2");}}; //TODO find a way to parse the txt file
            switch(entry.getValue().toLowerCase()){
                case "omero":
                    new QPHRMOmeroRetriever()
                            .setImage(entry.getKey())
                            .setClient(client)
                            .buildTarget()
                            .sendBack(metadata);
                    break;
                case "local":
                    ;
                default:
                    Dialogs.showWarningNotification("Type does not exists", "Type "+entry.getValue()+" is not supported for image "+entry.getKey());
            }
        }
        return true;
    }


    //TODO should find a way to not duplicate this code from OMERO Raw extension
    private static OmeroRawClient askForOmeroConnection(){
        GridPane gp = new GridPane();
        gp.setVgap(5.0);
        TextField tf = new TextField("https://omero-server.epfl.ch/");
        tf.setPrefWidth(400);
        PaneTools.addGridRow(gp, 0, 0, "Enter OMERO URL", new Label("Enter an OMERO server URL to browse (e.g. http://idr.openmicroscopy.org/):"));
        PaneTools.addGridRow(gp, 1, 0, "Enter OMERO URL", tf, tf);
        var confirm = Dialogs.showConfirmDialog("Enter OMERO URL", gp);
        if (!confirm)
            return null;

        var path = tf.getText();
        if (path == null || path.isEmpty())
            return null;

        try {
            if (!path.startsWith("http:") && !path.startsWith("https:")) {
                Dialogs.showErrorMessage("Non valid URL", "The input URL must contain a scheme (e.g. \"https://\")!");
                return null;
            }

            // Make the path a URI
            URI uri = new URI(path);

            // Clean the URI (in case it's a full path)
            URI uriServer = OmeroRawTools.getServerURI(uri);
           return OmeroRawClients.createClientAndLogin(uriServer);

        }catch(IOException | URISyntaxException e){
            Dialogs.showErrorMessage("OMERO Connection issue", "Cannot connect to OMERO server with "+path);
            return null;
        }
    }



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

        return imageTypeMap;

    }


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
}
