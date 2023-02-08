package qupath.ext.biop.hrm;

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
import qupath.lib.gui.dialogs.Dialogs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class QPHRMOmeroRetriever implements QPHRMRetriever {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMOmeroRetriever.class);
    private OmeroRawClient client;
    private File imageToSend;
    private File logFile;
    private long target;
    private long imageId;
    private Map<String, Map<String, String>> metadata;

    public QPHRMOmeroRetriever(){

    }

    @Override
    public QPHRMOmeroRetriever sendBack() {
        if(this.imageToSend != null && this.imageToSend.exists()){
            // read the target dataset
            DatasetData dataset = OmeroRawTools.readOmeroDataset(client, target);
            if(dataset != null){
                // read child images and check if it already exists on OMERO.
                // if not, the image is not uploaded
                Set<ImageData> imagesWithinDataset = (Set<ImageData>)dataset.getImages();
                if(imagesWithinDataset.stream().noneMatch(e -> e.getName().equals(this.imageToSend.getName()))) {
                    List<Long> ids = OmeroRawTools.uploadImage(this.client, dataset, this.imageToSend.toString());

                    if(!ids.isEmpty()){
                        this.imageId = ids.get(0);

                        // convert key value pairs to omero-compatible object NamedValue
                        metadata.forEach((header,map)->{
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

                    } else {
                        Dialogs.showWarningNotification("Upload from HRM", "Image "+this.imageToSend.toString()+" cannot be uploaded on OMERO");
                    }
                } else {
                    Dialogs.showWarningNotification("Existing images on OMERO", "Image "+this.imageToSend.toString()+" already exists on OMERO. It is not uploaded");
                }
            } else {
                logger.error("The dataset "+target+" does not exist on OMERO");
            }
        }
        else
            Dialogs.showErrorNotification("Un-existing image", "The image "+this.imageToSend+" does not exist");

        return this;
    }

    // TODO ask Pete if there is a way to import an image in a qp project by scripting
    @Override
    public void toQuPath(QuPathGUI qupath) {
        String serverUri = this.client.getServerURI().toString();
        String imageURI = serverUri + String.format("/webclient/?show=image-%d", this.imageId);

        // define the builder
        OmeroRawImageServerBuilder omeroBuilder = new OmeroRawImageServerBuilder();

        Map<String,String> omeroKeyValues = new TreeMap<>();
        metadata.forEach((header,map)-> {
            omeroKeyValues.putAll(map);
        });

        try {
            QPHRMRetrieveFromHRM.toQuPath(qupath, omeroBuilder, imageURI, omeroKeyValues);
        }catch(IOException e){
            Dialogs.showErrorNotification("Image to QuPath", "An error occured when trying to add image "+this.target+" to QuPath project");
            logger.error(""+e);
            logger.error(OmeroRawTools.getErrorStackTraceAsString(e));
        }
    }

    @Override
    public QPHRMOmeroRetriever setImage(String imagePath) {
        this.imageToSend = new File(imagePath);
        return this;
    }

    public QPHRMOmeroRetriever setClient(OmeroRawClient client){
        this.client = client;
        return this;
    }

    @Override
    public QPHRMOmeroRetriever buildTarget(){
        if(this.imageToSend != null && this.imageToSend.exists()) {
            String omeroDataset = this.imageToSend.getParentFile().getName();

            if(omeroDataset.equals("None")){
                DatasetData dataset = OmeroRawTools.createNewDataset(this.client, "HRM_"+ new Date());
                if(dataset != null)
                    this.target = dataset.getId();
            }else
                this.target = Integer.parseInt(omeroDataset.split("_")[0]);
        }
        return this;
    }

    @Override
    public QPHRMRetriever setMetadata(Map<String, Map<String, String>> metadata, File logFile) {
        this.metadata = metadata;
        this.logFile = logFile;
        return this;
    }
}
