package qupath.ext.biop.hrm;

import qupath.lib.images.servers.ImageServer;
import java.awt.image.BufferedImage;

/**
 * Send images from the current QuPath project to HRM
 */
public interface QPHRMSender {
    /**
     * get the HRM folder path
     * @return
     */
    String getDestinationFolder();

    /**
     * Copy an image from QuPath to a destination folder
     * @param overwrite
     * @return
     */
    int copy(boolean overwrite);

    /**
     * set the path to the HRM destination folder
     * @param root
     * @return
     */
    QPHRMSender buildDestinationFolder(String root);

    /**
     * set the current image server
     *
     * @param image
     * @return
     */
    QPHRMSender setImage(ImageServer<BufferedImage> image);
}
