package qupath.ext.biop.hrm;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.bioformats.BioFormatsImageServer;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.util.List;

public class QPHRMSendToHRM {

    public static boolean send(List<ProjectImageEntry<BufferedImage>> images){

        String destinationFolder = "\\svraw1.epfl.ch\\ptbiop\\HRM-Share";

            images.forEach(image -> {
                try {
                    ImageServer<BufferedImage> imageServer = image.readImageData().getServer();
                    if (imageServer.getClass().equals(BioFormatsImageServer.class)) {
                        QPHRMSender qphrmSender = new QPHRMLocalSender(destinationFolder);
                        qphrmSender.copy(imageServer,destinationFolder);
                    }
                }catch(Exception e){

                }

            });
     return true;
    }
}
