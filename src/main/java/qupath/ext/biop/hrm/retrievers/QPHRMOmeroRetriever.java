package qupath.ext.biop.hrm.retrievers;

import fr.igred.omero.annotations.MapAnnotationWrapper;
import fr.igred.omero.annotations.TagAnnotationWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import omero.gateway.model.TagAnnotationData;
import omero.model.NamedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.hrm.HRMConstants;
import qupath.ext.biop.servers.omero.raw.client.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder;
import qupath.ext.biop.servers.omero.raw.utils.OmeroRawScripting;
import qupath.ext.biop.servers.omero.raw.utils.Utils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class QPHRMOmeroRetriever implements QPHRMRetriever {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMOmeroRetriever.class);

    /** OMERO client */
    private OmeroRawClient client;

    /** deconvolved image */
    private File imageToSend;

    /** Raw image name*/
    private String rawName;

    /** HRM code*/
    private String hrmCode;

    /** HRM log file */
    private File logFile;

    /** Dataset id where to upload the deconvolved image */
    private long target = -1;

    /** OMERO id of the uploaded deconvolved image */
    private long imageId;

    /** Image wrapper object */
    private ImageWrapper imageWrapper;

    /** Image and restoration parameters of the deconvolution */
    private Map<String, Map<String, String>> metadata;

    /** tags to upload */
    private List<String> imageTags = new ArrayList<>();

    public QPHRMOmeroRetriever(){

    }

    @Override
    public boolean sendBack() {
        // read the target dataset
        DatasetWrapper dataset;
        try {
            dataset = this.client.getSimpleClient().getDataset(this.target);
        }catch(ServiceException | ExecutionException | AccessException e){
            Utils.errorLog(logger, "Send back", "Un-existing object : The dataset '"+this.target+"' does not exist on OMERO", e, false);
            return false;
        }

        // read child images and check if it already exists on OMERO.
        // if not, the image is not uploaded
        List<ImageWrapper> imagesWithinDataset = new ArrayList<>();
        boolean imageRead = false;
        try {
          imagesWithinDataset = dataset.getImages(this.client.getSimpleClient());
          imageRead = true;
        }catch(AccessException | ServiceException | ExecutionException e){
            Utils.errorLog(logger, "Send back", "Cannot get images from dataset '"+dataset.getName()+"' ; Import new images anyway", e, false);
        }

        if(!imageRead || imagesWithinDataset.stream().noneMatch(e -> e.getName().equals(this.imageToSend.getName()))) {
            // read raw image if exists
            try{
                ImageWrapper rawImage = imagesWithinDataset.stream().filter(e -> this.imageToSend.getName().contains(e.getName().split("\\.")[0])).findFirst().get();
                this.imageTags = rawImage.getTags(this.client.getSimpleClient()).stream().map(TagAnnotationWrapper::getName).collect(Collectors.toList());
                List<String> additionalRawTags = new ArrayList<>();
                additionalRawTags.add(HRMConstants.HRM_TAG);
                additionalRawTags.add(HRMConstants.RAW_FOLDER.toLowerCase());
                sendTags(additionalRawTags, rawImage);
            }catch (ServiceException | AccessException | ExecutionException e){
                Utils.warnLog(logger, "Send back", "The raw image of '"+this.imageToSend.toString()+"' cannot be retrieved in dataset '"+dataset.getName()+"'", e, false);
            }

            // import image
            List<Long> ids;
            try{
                ids = dataset.importImage(this.client.getSimpleClient(), this.imageToSend.toString());
            }catch(OMEROServerError | ServiceException | AccessException | ExecutionException e){
                Utils.errorLog(logger, "Send back", "Cannot import image '"+this.imageToSend.toString()+"' on OMERO", e, false);
                return false;
            }

            // get image
            this.imageId = ids.get(0);
            try{
                this.imageWrapper = this.client.getSimpleClient().getImage(this.imageId);
            }catch(ServiceException | AccessException | ExecutionException e){
                Utils.errorLog(logger, "Send back", "Cannot read image '"+this.imageId+"' from OMERO", e, false);
                return false;
            }

            // send tags
            this.imageTags.add(HRMConstants.HRM_TAG);
            this.imageTags.add(HRMConstants.DECONVOLVED_FOLDER.toLowerCase());
            sendTags(this.imageTags, this.imageWrapper);

            // convert key value pairs to omero-compatible object NamedValue
            this.metadata.forEach((header,map)->{
                List<NamedValue> omeroKeyValues = new ArrayList<>();
                map.forEach((key, value)->omeroKeyValues.add(new NamedValue(key,value)));

                // set annotation map
                MapAnnotationWrapper mapAnnotationWrapper = new MapAnnotationWrapper();
                mapAnnotationWrapper.setContent(omeroKeyValues);

                // set namespace
                mapAnnotationWrapper.setNameSpace(header);

                // send key-values on OMERO
                try {
                    this.imageWrapper.link(this.client.getSimpleClient(), mapAnnotationWrapper);
                }catch(ServiceException | AccessException | ExecutionException e){
                    Utils.errorLog(logger, "Send back", "Cannot attach metadata as key-values pairs to image '"+this.imageId+"'", e, false);
                }
            });

            // add the logFile as attachment to the image
            if(this.logFile != null) {
                try {
                    this.imageWrapper.addFile(this.client.getSimpleClient(), this.logFile);
                } catch (ExecutionException | InterruptedException e) {
                    Utils.errorLog(logger, "Send back", "Cannot attach the log file to image '" + this.imageId + "'", e, false);
                }
                return true;
            }else{
                Utils.warnLog(logger, "Send back", "The log file is not available in HRM folder", false);
                return false;
            }
        } else {
            logger.warn("Existing images on OMERO : Image "+this.imageToSend.toString()+" already exists on OMERO. It is not uploaded");
            this.imageWrapper = imagesWithinDataset.stream().filter(e -> e.getName().equals(this.imageToSend.getName())).collect(Collectors.toList()).get(0);
            this.imageId = this.imageWrapper.getId();
            return true;
        }
    }

    /**
     * Check if tags are already existing / linked and add / link them to the image
     *
     * @param tags
     * @param imageWrapper
     * @return
     */
    private boolean sendTags(List<String> tags, ImageWrapper imageWrapper){
        List<TagAnnotationWrapper> tagsToAdd = new ArrayList<>();
        List<TagAnnotationWrapper> groupTags;
        try {
            groupTags= this.client.getSimpleClient().getTags();
        }catch(ServiceException  | OMEROServerError e){
            Utils.errorLog(logger,"OMERO - tags",
                    "Cannot read tags from the current user '"+this.client.getSimpleClient().getUser().getUserName()+"'", e, true);
            return false;
        }

        for(String tag:tags) {
            if(tagsToAdd.stream().noneMatch(e-> e.getName().equalsIgnoreCase(tag))){
                TagAnnotationWrapper newOmeroTagAnnotation;
                List<TagAnnotationWrapper> matchedTags = groupTags.stream().filter(e -> e.getName().equalsIgnoreCase(tag)).collect(Collectors.toList());
                if(matchedTags.isEmpty()){
                    newOmeroTagAnnotation = new TagAnnotationWrapper(new TagAnnotationData(tag));
                } else {
                    newOmeroTagAnnotation = matchedTags.get(0);
                }
                // find if the requested tag already exists
                tagsToAdd.add(newOmeroTagAnnotation);
            }
        }

        try {
            imageWrapper.link(this.client.getSimpleClient(), tagsToAdd.toArray(TagAnnotationWrapper[]::new));
        }catch(ServiceException | AccessException | ExecutionException e){
            Utils.errorLog(logger,"OMERO - tags", "Cannot add tags to the image '"+imageWrapper.getId()+"'", e, true);
            return false;
        }
        return true;
    }

    //TODO ask Pete if there is a way to import an image in a qp project by scripting
    @Override
    public boolean toQuPath(QuPathGUI qupath) {
        // get the image uri
        String serverUri = this.client.getServerURI().toString();
        String imageURI = serverUri + String.format("/webclient/?show=image-%d", this.imageId);

        // define the builder
        OmeroRawImageServerBuilder omeroBuilder = new OmeroRawImageServerBuilder();

        // add all key-values independently of their parent namespace
        Map<String,String> hrmKeyValues = new TreeMap<>();
        metadata.forEach((header,map)-> {
            hrmKeyValues.putAll(map);
        });

        try {
            // add the current image to the QuPath project
            List<ProjectImageEntry<BufferedImage>> entries = QPHRMRetrieveFromHRM.toQuPath(qupath, omeroBuilder, imageURI);
            for(ProjectImageEntry<BufferedImage> entry:entries) {
                // add hrm KVPs
                hrmKeyValues.forEach(entry::putMetadataValue);
                // add tags
                OmeroRawScripting.addTagsToQuPath(entry, this.imageTags, Utils.UpdatePolicy.UPDATE_KEYS, true);
            }
            return true;
        }catch(IOException e){
            Utils.errorLog(logger, "Image to QuPath", "An error occurred when trying to add image \"+this.imageId+\" to QuPath project",e,false);
            return false;
        }
    }

    @Override
    public QPHRMOmeroRetriever setImage(File imageFile, String rawName, String hrmCode) {
        this.imageToSend = imageFile;
        this.rawName = rawName;
        this.hrmCode = hrmCode;
        return this;
    }

    public QPHRMOmeroRetriever setClient(OmeroRawClient client){
        this.client = client;
        return this;
    }

    @Override
    public boolean buildTarget(){
        if(this.imageToSend != null && this.imageToSend.exists()) {
            String omeroDataset = this.imageToSend.getParentFile().getParentFile().getName();

            // if orphaned image
            if(omeroDataset.equals("None")){
                // create a new orphaned dataset and retrieve its id
                DatasetWrapper datasetWrapper = new DatasetWrapper("HRM_"+ new Date(), "");
                try {
                    datasetWrapper.saveAndUpdate(this.client.getSimpleClient());
                    this.target = datasetWrapper.getId();
                }catch (AccessException | ExecutionException | ServiceException e){
                    Utils.errorLog(logger, "Build target", "Cannot create OMERO dataset '"+datasetWrapper.getName()+"'", e, false);
                }
                return this.target > 0;
            }else
                // parse the parent dataset id
                this.target = Integer.parseInt(omeroDataset.split("_")[0]);
            return true;
        }
        logger.error("Un-existing image : The image "+this.imageToSend+" does not exist");
        return false;
    }

    @Override
    public QPHRMOmeroRetriever setMetadata(Map<String, Map<String, String>> metadata) {
        this.metadata = metadata;
        return this;
    }

    public QPHRMOmeroRetriever setLogFile(File logFile) {
        this.logFile = logFile;
        return this;
    }
}
