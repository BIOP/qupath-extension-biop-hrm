package qupath.ext.biop.hrm;

import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;

public interface QPHRMSender {
    /**
     * get the HRM folder path
     * @return
     */
    String getDestinationFolder();

    /**
     * Copy an image from QuPath to a destination folder
     * @param image
     * @param destinationFolder
     * @return
     */
    boolean copy(ImageServer<BufferedImage> image, String destinationFolder);

    /**
     * get the path to the HRM destination folder
     * @param root
     * @return
     */
    String buildDestinationFolder(String root);
}
