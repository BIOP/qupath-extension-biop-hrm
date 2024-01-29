package qupath.ext.biop.hrm.senders;

import org.apache.commons.io.FileUtils;
import qupath.ext.biop.hrm.HRMConstants;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Send locally stored images to HRM from the current QuPath project
 */
public class QPHRMLocalSender implements QPHRMSender {
    /** path to the HRM-user folder where to save the image */
    private String destinationFolder = "";

    /** image to send to HRM */
    private ImageServer<BufferedImage> imageServer;
    private boolean isSent = false;
    private boolean isFailed = false;
    private boolean isSkipped = false;

    public QPHRMLocalSender(){

    }

    @Override
    public String getDestinationFolder() {
        return this.destinationFolder;
    }

    @Override
    public boolean isSent() {
        return this.isSent;
    }

    @Override
    public boolean isSkipped() {
        return this.isSkipped;
    }

    @Override
    public boolean isFailed() {
        return this.isFailed;
    }

    @Override
    public QPHRMLocalSender copy(boolean overwrite) {
        File destinationFolderPath = new File(this.destinationFolder);

        // check if the destination folder exists
        if(destinationFolderPath.exists()) {
            URI uri = this.imageServer.getURIs().iterator().next();

            try {
                // check if the image already exists on HRM
                File sourceImage = new File(uri);
                if(overwrite || !(new File(this.destinationFolder + File.separator + sourceImage.getName()).exists())) {
                    // copy the image into HRM folder
                    FileUtils.copyFileToDirectory(sourceImage, destinationFolderPath);
                    this.isSent = true;
                } else this.isSkipped = true;
            } catch (IOException e) {
                Dialogs.showErrorNotification("Copy File to HRM","Cannot copy "+uri+" to "+this.destinationFolder);
                this.isFailed = true;
            }
        } else{
            this.isFailed = true;
        }
        return this;
    }

    @Override
    public QPHRMLocalSender buildDestinationFolder(String rootPath, String username) {
        File rootPathFile = new File(rootPath);
        // check if HRM share folder exists
        if(!rootPathFile.isDirectory())
            return this;

        // check username folder
        File userPathFile = new File(rootPath + File.separator + username);
        if(!userPathFile.isDirectory()) {Dialogs.showErrorNotification("Building destination folder","Path "+userPathFile+" does not exists"); return this;}

        // check Raw folder
        File rawPathFile = new File(userPathFile + File.separator + HRMConstants.RAW_FOLDER);
        if(!rawPathFile.isDirectory())
            if(!rawPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+rawPathFile+" does not exists"); return this;}

        // check local folder
        File localPathFile = new File(rawPathFile + File.separator + HRMConstants.LOCAL_FOLDER);
        if(!localPathFile.isDirectory())
            if(!localPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+localPathFile+" does not exists"); return this;}

        this.destinationFolder = localPathFile.toString();

        return this;
    }

    @Override
    public QPHRMLocalSender setImageServer(ImageServer<BufferedImage> imageServer) {
        this.imageServer = imageServer;
        return this;
    }

}
