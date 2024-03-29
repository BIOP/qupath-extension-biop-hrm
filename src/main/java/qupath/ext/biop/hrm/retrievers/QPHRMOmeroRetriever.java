package qupath.ext.biop.hrm.retrievers;

import omero.gateway.model.DatasetData;
import omero.gateway.model.ImageData;
import omero.gateway.model.MapAnnotationData;
import omero.model.NamedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.gui.QuPathGUI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    private long target;

    /** OMERO id of the uploaded deconvolved image */
    private long imageId;

    /** Image and restoration parameters of the deconvolution */
    private Map<String, Map<String, String>> metadata;

    public QPHRMOmeroRetriever(){

    }

    @Override
    public boolean sendBack() {
        // read the target dataset
        DatasetData dataset = OmeroRawTools.readOmeroDataset(this.client, this.target);
        if(dataset != null){
            // read child images and check if it already exists on OMERO.
            // if not, the image is not uploaded
            Set<ImageData> imagesWithinDataset = (Set<ImageData>)dataset.getImages();
            if(imagesWithinDataset.stream().noneMatch(e -> e.getName().equals(this.imageToSend.getName()))) {
                List<Long> ids = OmeroRawTools.uploadImage(this.client, dataset, this.imageToSend.toString());

                if(!ids.isEmpty()){
                    this.imageId = ids.get(0);

                    // convert key value pairs to omero-compatible object NamedValue
                    this.metadata.forEach((header,map)->{
                        List<NamedValue> omeroKeyValues = new ArrayList<>();
                        map.forEach((key, value)->omeroKeyValues.add(new NamedValue(key,value)));

                        // set annotation map
                        MapAnnotationData newOmeroAnnotationMap = new MapAnnotationData();
                        newOmeroAnnotationMap.setContent(omeroKeyValues);

                        // set namespace
                        newOmeroAnnotationMap.setNameSpace(header);

                        // send key-values on OMERO
                        OmeroRawTools.addKeyValuesOnOmero(newOmeroAnnotationMap, this.client, this.imageId);
                    });

                    // add the logFile as attachment to the image
                    OmeroRawTools.addAttachmentToOmero(this.logFile, this.client,this.imageId,"text/plain");
                    return true;

                } else {
                    logger.warn("Upload from HRM : Image "+this.imageToSend.toString()+" cannot be uploaded on OMERO");
                }
            } else {
                logger.warn("Existing images on OMERO : Image "+this.imageToSend.toString()+" already exists on OMERO. It is not uploaded");
                ImageData image = imagesWithinDataset.stream().filter(e -> e.getName().equals(this.imageToSend.getName())).collect(Collectors.toList()).get(0);
                this.imageId = image.getId();
                return true;
            }
        } else {
            logger.error("Un-existing object : The dataset "+this.target+" does not exist on OMERO");
        }
        return false;
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
        Map<String,String> omeroKeyValues = new TreeMap<>();
        metadata.forEach((header,map)-> {
            omeroKeyValues.putAll(map);
        });

        try {
            // add the current image to the QuPath project
            QPHRMRetrieveFromHRM.toQuPath(qupath, omeroBuilder, imageURI, omeroKeyValues);
            return true;
        }catch(IOException e){
            logger.error("Image to QuPath : An error occured when trying to add image "+this.imageId+" to QuPath project");
            logger.error(String.valueOf(e));
            logger.error(OmeroRawTools.getErrorStackTraceAsString(e));
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
            String omeroDataset = this.imageToSend.getParentFile().getName();

            // if orphaned image
            if(omeroDataset.equals("None")){
                // create a new orphaned dataset and retrieve its id
                DatasetData dataset = OmeroRawTools.createNewDataset(this.client, "HRM_"+ new Date());
                if(dataset != null)
                    this.target = dataset.getId();
                else return false;
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
