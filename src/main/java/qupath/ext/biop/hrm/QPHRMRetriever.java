package qupath.ext.biop.hrm;

import qupath.lib.gui.QuPathGUI;

import java.io.IOException;
import java.util.Map;

public interface QPHRMRetriever {
    boolean sendBack(Map<String, String> metadata);

    void toQuPath(QuPathGUI qupath, String path) throws IOException;

    QPHRMRetriever setImage(String imagePath);

    QPHRMRetriever buildTarget();
}
