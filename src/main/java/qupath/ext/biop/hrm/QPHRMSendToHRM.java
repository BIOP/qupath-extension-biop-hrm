package qupath.ext.biop.hrm;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QPHRMSendToHRM {

    public static int[] send(List<ProjectImageEntry<BufferedImage>> images, boolean overwrite){

        String rootFolder = "C:\\Users\\dornier\\Downloads";//"\\svraw1.epfl.ch\\ptbiop\\HRM-Share";
        int nSentImages = 0;
        int nSkippedImages = 0;

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
            username = QPHRMTools.askUsername();

        // omero images to send
        for(ImageServer<BufferedImage> imageServer:omeroServersList) {
            int hasBeenSent = new QPHRMOmeroSender()
                       .setClient(((OmeroRawImageServer) imageServer).getClient())
                       .setImage(imageServer)
                       .buildDestinationFolder(rootFolder)
                       .copy(overwrite);
            nSentImages += hasBeenSent == 1 ? 1 : 0;
            nSkippedImages += hasBeenSent == 2 ? 1 : 0;

            if(username.equals(""))
                username = ((OmeroRawImageServer) imageServer).getClient().getLoggedInUser().getOmeName().getValue();
        }

        // local images to send
        for(ImageServer<BufferedImage> imageServer:localServersList) {
            int hasBeenSent = new QPHRMLocalSender()
                    .setUsername(username)
                    .setImage(imageServer)
                    .buildDestinationFolder(rootFolder)
                    .copy(overwrite);
            nSentImages += hasBeenSent == 1 ? 1 : 0;
            nSkippedImages += hasBeenSent == 2 ? 1 : 0;
        }

        return (new int[]{nSentImages, nSkippedImages});
    }

}
