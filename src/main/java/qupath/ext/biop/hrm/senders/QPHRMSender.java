package qupath.ext.biop.hrm.senders;

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
     * if the image has been sent to HRM
     * @return
     */
    boolean isSent();

    /**
     * if the image has been skipped because already on HRM server
     * @return
     */
    boolean isSkipped();

    /**
     * if there is an issue during the sending process
     * @return
     */
    boolean isFailed();

    /**
     * Copy an image from QuPath to a destination folder
     * @param overwrite
     * @return
     */
    QPHRMSender copy(boolean overwrite);

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
