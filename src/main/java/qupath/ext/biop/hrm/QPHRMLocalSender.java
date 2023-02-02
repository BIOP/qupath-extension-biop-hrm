package qupath.ext.biop.hrm;

import org.apache.commons.io.FileUtils;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class QPHRMLocalSender implements QPHRMSender {
    private String destinationFolder = "";
    private ImageServer<BufferedImage> image;
    private String username = "";
    final private int SKIPPED = 2;
    final private int COPIED = 1;
    final private int ERROR = -1;

    public QPHRMLocalSender(){

    }

    @Override
    public String getDestinationFolder() {
        return this.destinationFolder;
    }

    @Override
    public int copy(boolean overwrite) {
        File destinationFolderPath = new File(this.destinationFolder);

        // check if the destination folder exists
        if(destinationFolderPath.exists()) {
            URI uri = this.image.getURIs().iterator().next();

            try {
                // check if the image already exists on HRM
                File sourceImage = new File(uri);
                if(overwrite || !(new File(this.destinationFolder + File.separator + sourceImage.getName()).exists())) {
                    // copy the image into HRM folder
                    FileUtils.copyFileToDirectory(sourceImage, destinationFolderPath);
                    return COPIED;
                }
                return SKIPPED;
            } catch (IOException e) {
                Dialogs.showErrorNotification("Copy File to HRM","Cannot copy "+uri+" to "+this.destinationFolder);
                return ERROR;
            }
        } else{
            Dialogs.showErrorNotification("Copying local file","Destination folder "+this.destinationFolder+" does not exists");
            return ERROR;
        }
    }

    @Override
    public QPHRMSender buildDestinationFolder(String rootPath) {
        File rootPathFile = new File(rootPath);
        if(!rootPathFile.isDirectory())
            return this;

        File userPathFile = new File(rootPath + File.separator + username);
        if(!userPathFile.isDirectory()) {Dialogs.showErrorNotification("Building destination folder","Path "+userPathFile+" does not exists"); return this;}

        File rawPathFile = new File(userPathFile + File.separator + "Raw");
        if(!rawPathFile.isDirectory())
            if(!rawPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+rawPathFile+" does not exists"); return this;}

        File qupathPathFile = new File(rawPathFile + File.separator + "QuPath");
        if(!qupathPathFile.isDirectory())
            if(!qupathPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+qupathPathFile+" does not exists"); return this;}

        File localPathFile = new File(qupathPathFile + File.separator + "Local");
        if(!localPathFile.isDirectory())
            if(!localPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+localPathFile+" does not exists"); return this;}

        this.destinationFolder = localPathFile.toString();

        return this;
    }

    @Override
    public QPHRMSender setImage(ImageServer<BufferedImage> image) {
        this.image = image;
        return this;
    }

    public QPHRMSender setUsername(String username){
        this.username = username;
        return this;
    }
}
