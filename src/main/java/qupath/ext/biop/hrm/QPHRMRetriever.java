package qupath.ext.biop.hrm;

import qupath.lib.gui.QuPathGUI;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface QPHRMRetriever {
    QPHRMRetriever sendBack();

    void toQuPath(QuPathGUI qupath);

    QPHRMRetriever setImage(String imagePath);

    QPHRMRetriever buildTarget();

    QPHRMRetriever setMetadata(Map<String, Map<String, String>> metadata);
}
