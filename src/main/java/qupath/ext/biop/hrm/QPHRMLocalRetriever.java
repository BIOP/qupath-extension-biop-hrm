package qupath.ext.biop.hrm;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class QPHRMLocalRetriever implements QPHRMRetriever {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMLocalRetriever.class);
    private File imageToSend;
    private File target;
    private Map<String, Map<String, String>> metadata;
    @Override
    public QPHRMLocalRetriever sendBack() {
        try {
            File[] filesToCopy = this.imageToSend.getParentFile().listFiles();
            String imageName = this.imageToSend.getName();
            int index = imageName.lastIndexOf(".");
            imageName = imageName.substring(0, index);

            if (filesToCopy != null)
                for (File sourceImage : filesToCopy)
                    if (sourceImage.getName().contains(imageName))
                        FileUtils.copyFileToDirectory(sourceImage, this.target);
        }catch(IOException e){
            Dialogs.showWarningNotification("Sending back",
                    "Cannot copy files from "+this.imageToSend.getParentFile().getAbsolutePath() +
                            " to "+this.target.getAbsolutePath());
        }

        return this;
    }

    @Override
    public void toQuPath(QuPathGUI qupath) {
        String imageURI = this.target.getAbsolutePath() + File.separator + this.imageToSend.getName();

        Map<String,String> omeroKeyValues = new TreeMap<>();
        metadata.forEach((header,map)-> {
            omeroKeyValues.putAll(map);
        });

        try {
            QPHRMRetrieveFromHRM.toQuPath(qupath, null, imageURI, omeroKeyValues);
        }catch(IOException e){
            Dialogs.showErrorNotification("Image to QuPath", "An error occured when trying to add image "+imageURI+" to QuPath project");
            logger.error(""+e);
            logger.error(OmeroRawTools.getErrorStackTraceAsString(e));
        }
    }

    @Override
    public QPHRMLocalRetriever setImage(String imagePath) {
        this.imageToSend = new File(imagePath);
        return this;
    }

    @Override
    public QPHRMLocalRetriever buildTarget() {
        if(this.imageToSend != null && this.imageToSend.exists()) {
            // extract the raw image name (without the hrm code)
            String hrmName = this.imageToSend.getName();
            int index = hrmName.lastIndexOf("_");
            hrmName = hrmName.substring(0,index);
            index = hrmName.lastIndexOf("_");
            String hrmCode = hrmName.substring(index);
            hrmName = hrmName.substring(0,index);

            // list all available images
            List<ProjectImageEntry<BufferedImage>> imageList = QuPathGUI.getInstance().getProject().getImageList();

            // ge the closest image in the project to the hrm image name
            ProjectImageEntry<BufferedImage> finalImage = null;
            double higherSimilarity = 0;
            for(ProjectImageEntry<BufferedImage> image :imageList){
                double sim = similarity(image.getImageName(), hrmName);
                if(sim > higherSimilarity){
                    higherSimilarity = sim;
                    finalImage = image;
                }
            }

            if(finalImage != null){
                try {
                    // get parent folder of the raw image
                    String parentFolder = new File(finalImage.getURIs().iterator().next().toString()).getParent();
                    parentFolder = parentFolder.replace("file:\\","");
                    String deconvolvedFolderPath = parentFolder + File.separator + "Deconvolved" + hrmCode;

                    // create a deconvolved folder
                    File deconvolvedFolder = new File(deconvolvedFolderPath);
                    if (deconvolvedFolder.mkdir())
                        this.target = deconvolvedFolder;
                    else System.out.println("cannot create the folder "+deconvolvedFolder);

                }catch(IOException e){
                    Dialogs.showWarningNotification("Building Local target", "Error when trying to get URI from image "+finalImage.getImageName());
                }
            }else Dialogs.showWarningNotification("Building Local target", "No image available in your project. Cannot copy deconvolution results");
        }
        return this;
    }

    @Override
    public QPHRMLocalRetriever setMetadata(Map<String, Map<String, String>> metadata) {
        this.metadata = metadata;
        return this;
    }



    /**
     * Calculates the similarity (a number within 0 and 1) between two strings.
     * https://stackoverflow.com/questions/955110/similarity-string-comparison-in-java
     */
    public static double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2; shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) { return 1.0; /* both strings are zero length */ }

        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;

    }

    /**
     * https://stackoverflow.com/questions/955110/similarity-string-comparison-in-java
     * Example implementation of the Levenshtein Edit Distance
     * See http://rosettacode.org/wiki/Levenshtein_distance#Java
     */
    public static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
