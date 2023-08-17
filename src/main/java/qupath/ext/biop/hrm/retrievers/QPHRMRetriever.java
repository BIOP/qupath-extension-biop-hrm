package qupath.ext.biop.hrm.retrievers;

import qupath.lib.gui.QuPathGUI;

import java.io.File;
import java.util.Map;

/**
 * Retrieve deconvolved images from HRM
 */
public interface QPHRMRetriever {
    /**
     * sends back the deconvolved image to the target destination
     * This method has to be called after buildTarget() one
     *
     * @return
     */
    boolean sendBack();

    /**
     * sends back the deconvolved image to QuPath.
     * This method has to be called after sendBack() one
     *
     * @param qupath
     * @return
     */
    boolean toQuPath(QuPathGUI qupath);

    /**
     * set the deconvolved image to retrieve
     * @param imageFile
     * @param rawName
     * @param hrmCode
     * @return
     */
    QPHRMRetriever setImage(File imageFile, String rawName, String hrmCode);

    /**
     * build the target destination where to store the deconvolved image
     * This method has to be called after all setter methods.
     * @return
     */
    boolean buildTarget();

    /**
     * set Image and restoration parameters of the deconvolution
     * @param metadata
     * @return
     */
    QPHRMRetriever setMetadata(Map<String, Map<String, String>> metadata);
}
