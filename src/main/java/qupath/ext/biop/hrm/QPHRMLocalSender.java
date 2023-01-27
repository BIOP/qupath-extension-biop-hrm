package qupath.ext.biop.hrm;

import org.apache.commons.io.FileUtils;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.bioformats.BioFormatsImageServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class QPHRMLocalSender implements QPHRMSender {

    private String destinationFolder = "";

    public QPHRMLocalSender(String destinationFolder){
        this.destinationFolder = destinationFolder;

    }


    @Override
    public String getDestinationFolder() {
        return this.destinationFolder;
    }

    @Override
    public boolean copy(ImageServer<BufferedImage> image, String destinationFolder) {
        File destinationFolderPath = new File(destinationFolder);

        if(destinationFolderPath.exists()) {
            URI uri = image.getURIs().iterator().next();

            File sourceImage = new File(uri);
            try {
                FileUtils.copyFileToDirectory(sourceImage, destinationFolderPath);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

        } else{
            Dialogs.showErrorNotification("File error","Destination folder "+destinationFolder+" does not exists");
            return false;
        }
    }

    @Override
    public String buildDestinationFolder(String root) {
        File rootPath = new File(root);
        if(rootPath.isDirectory()){
            File userPath = new File("dornier");// askForGasparUsername()



        }

    }
}
