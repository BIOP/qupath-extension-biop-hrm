package qupath.ext.biop.hrm;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QPHRMSendToHRM {

    public static boolean send(List<ProjectImageEntry<BufferedImage>> images, boolean overwrite){

        String rootFolder = "C:\\Users\\dornier\\Downloads";//"\\svraw1.epfl.ch\\ptbiop\\HRM-Share";
        boolean allSent = true;

        // get image servers
        List<ImageServer<BufferedImage>> imageServers = new ArrayList<>();
        images.forEach(image -> {
            try {
                imageServers.add(image.readImageData().getServer());
            } catch (Exception e) {
                Dialogs.showErrorMessage("Reading image server", "Cannot Read Image data for image " + image.getImageName());
            }
        });

        // filter omero and local images
        List<ImageServer<BufferedImage>> omeroServersList = imageServers.stream().filter(e -> e instanceof OmeroRawImageServer).collect(Collectors.toList());
        List<ImageServer<BufferedImage>> localServersList = imageServers.stream().filter(e -> !(e instanceof OmeroRawImageServer)).collect(Collectors.toList());

        // set the username for local images only
        String username = "";
        if(omeroServersList.isEmpty())
            username = askUsername();

        // omero images to send
        for(ImageServer<BufferedImage> imageServer:omeroServersList) {
            boolean hasBeenSent = new QPHRMOmeroSender()
                       .setClient(((OmeroRawImageServer) imageServer).getClient())
                       .setImage(imageServer)
                       .buildDestinationFolder(rootFolder)
                       .copy(overwrite);
            allSent = allSent && hasBeenSent;
            if(username.equals(""))
                username = ((OmeroRawImageServer) imageServer).getClient().getLoggedInUser().getOmeName().getValue();
        }

        // local images to send
        for(ImageServer<BufferedImage> imageServer:localServersList) {
            boolean hasBeenSent = new QPHRMLocalSender()
                    .setUsername(username)
                    .setImage(imageServer)
                    .buildDestinationFolder(rootFolder)
                    .copy(overwrite);
            allSent = allSent && hasBeenSent;
        }

        return allSent;
    }


    protected static String askUsername(){

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
}
