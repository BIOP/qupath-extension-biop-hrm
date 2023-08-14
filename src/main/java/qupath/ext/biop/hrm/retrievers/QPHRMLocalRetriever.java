package qupath.ext.biop.hrm.retrievers;

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

/**
 * Retrieve deconvolved images from HRM and send them back in the folder of the raw image.
 * Add the deconvolved images to the current QuPath project, with metadata
 */
public class QPHRMLocalRetriever implements QPHRMRetriever {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMLocalRetriever.class);

    /** Deconvolved image */
    private File imageToSend;

    /** Parent folder of the raw image */
    private File target;

    /** Image and restoration parameters of the deconvolution */
    private Map<String, Map<String, String>> metadata;

    @Override
    public boolean sendBack() {
        try {
            // test if the deconvolved folder already exists
            if(this.target.exists()){
                Dialogs.showWarningNotification("Sending back images",
                        "Image and results in  "+this.target.getAbsolutePath()+" already exists");
                return true;
            }

            // create the deconvolved folder
            if (this.target.mkdir()){
                File[] filesToCopy = this.imageToSend.getParentFile().listFiles();
                String imageName = this.imageToSend.getName();
                int index = imageName.lastIndexOf(".");
                imageName = imageName.substring(0, index);

                // copy all files referring to the current image name in the target folder
                if (filesToCopy != null)
                    for (File sourceImage : filesToCopy)
                        if (sourceImage.getName().contains(imageName))
                            FileUtils.copyFileToDirectory(sourceImage, this.target);

                return true;
            }
            else Dialogs.showErrorNotification("Sending back images",
                    "Cannot create the folder "+this.target.getAbsolutePath()+
                            " to copy files from "+this.imageToSend.getParentFile().getAbsolutePath());
        }catch(IOException e){
            Dialogs.showErrorNotification("Sending back images",
                    "Cannot create the folder "+this.target.getAbsolutePath()+
                            " to copy files from "+this.imageToSend.getParentFile().getAbsolutePath());
        }
        return false;
    }

    @Override
    public boolean toQuPath(QuPathGUI qupath) {
        // get image uri
        String imageURI = this.target.getAbsolutePath() + File.separator + this.imageToSend.getName();

        // check if the image within the target folder exists
        if(!new File(imageURI).exists()){
            Dialogs.showErrorNotification("Import to QuPath", "The image "+imageURI+" does not exists.");
            return false;
        }

        // add all key-values independently of their parent namespace
        Map<String,String> omeroKeyValues = new TreeMap<>();
        metadata.forEach((header,map)-> {
            omeroKeyValues.putAll(map);
        });

        try {
            // add the image to QuPath project
            QPHRMRetrieveFromHRM.toQuPath(qupath, null, imageURI, omeroKeyValues);
            return true;
        }catch(IOException e){
            Dialogs.showErrorNotification("Image to QuPath", "An error occured when trying to add image "+imageURI+" to QuPath project");
            logger.error(""+e);
            logger.error(OmeroRawTools.getErrorStackTraceAsString(e));
            return false;
        }
    }

    @Override
    public QPHRMLocalRetriever setImage(String imagePath) {
        this.imageToSend = new File(imagePath);
        return this;
    }

    @Override
    public boolean buildTarget() {
        if(this.imageToSend != null && this.imageToSend.exists()) {
            String hrmName = this.imageToSend.getName();
            // remove "hrm.extension" suffix from image name
            int index = hrmName.lastIndexOf("_");
            hrmName = hrmName.substring(0,index);

            // extract hrm code
            index = hrmName.lastIndexOf("_");
            String hrmCode = hrmName.substring(index);

            // remove hrm code from image name
            hrmName = hrmName.substring(0,index);

            // list all available images
            // TODO find a way to pass qupathGui in argument
            List<ProjectImageEntry<BufferedImage>> imageList = QuPathGUI.getInstance().getProject().getImageList();

            // get the closest image to the hrm image name from the current project
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

                    // remove the prefix from the image's absolute path
                    parentFolder = parentFolder.replace("file:\\","");

                    // set the deconvolved folder name and path
                    String deconvolvedFolderPath = parentFolder + File.separator + finalImage.getImageName() + "_Deconvolved" + hrmCode;
                    this.target = new File(deconvolvedFolderPath);

                    return true;
                }catch(IOException e){
                    Dialogs.showWarningNotification("Building Local target", "Error when trying to get URI from image "+finalImage.getImageName());
                }
            }else Dialogs.showWarningNotification("Building Local target", "No image available in your project. Cannot copy deconvolution results");
        }
        return false;
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
