package qupath.ext.biop.hrm.senders;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
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

    /**
     * sends a list of images to HRM folder
     *
     * @param images
     * @param overwrite
     * @param rootFolder
     * @return
     */
    public static int[] send(List<ProjectImageEntry<BufferedImage>> images, boolean overwrite, String rootFolder){
        int nSentImages = 0;
        int nSkippedImages = 0;

        // get image servers
        List<ImageServer<BufferedImage>> imageServers = new ArrayList<>();
        for(ProjectImageEntry<BufferedImage> image : images) {
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
        if(omeroServersList.isEmpty())
            username = askUsername();

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
            try {
                imageServer.close();
            }catch(Exception e){
                logger.error("Cannot close the reader");
            }
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

            try {
                imageServer.close();
            }catch(Exception e){
                logger.error("Cannot close the reader");
            }
        }

        return (new int[]{nSentImages, nSkippedImages});
    }

    /**
     * ask the HRM username
     *
     * @return
     */
    private static String askUsername(){

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
