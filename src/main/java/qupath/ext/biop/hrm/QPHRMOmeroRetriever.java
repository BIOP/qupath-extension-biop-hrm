package qupath.ext.biop.hrm;

import omero.gateway.model.DatasetData;
import omero.gateway.model.ImageData;
import omero.gateway.model.MapAnnotationData;
import omero.model.NamedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServerBuilder;
import qupath.ext.biop.servers.omero.raw.OmeroRawScripting;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QPHRMOmeroRetriever implements QPHRMRetriever {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMOmeroRetriever.class);
    private OmeroRawClient client;
    private File imageToSend;
    private long target;
    private long imageId;

    public QPHRMOmeroRetriever(){

    }

    @Override
    public boolean sendBack(Map<String, String> metadata) {
        if(this.imageToSend != null && this.imageToSend.exists()){
            // read the target dataset
            Collection<DatasetData> datasets = OmeroRawTools.readOmeroDatasets(client, Collections.singletonList(target));
            if(!datasets.isEmpty()){
                DatasetData dataset = datasets.iterator().next();

                // read child images and check if it already exists on OMERO.
                // if not, the image is not uploaded
                Set<ImageData> imagesWithinDataset = (Set<ImageData>)dataset.getImages();
                if(imagesWithinDataset.stream().noneMatch(e -> e.getName().equals(this.imageToSend.getName()))) {
                    List<Long> ids = OmeroRawTools.uploadImage(this.client, dataset, this.imageToSend.toString());

                    if(!ids.isEmpty()){
                        this.imageId = ids.get(0);

                        // convert key value pairs to omero-compatible object NamedValue
                        List<NamedValue> omeroKeyValues = new ArrayList<>();
                        metadata.forEach((key,value)-> omeroKeyValues.add(new NamedValue(key,value)));

                        // set annotation map
                        MapAnnotationData newOmeroAnnotationMap = new MapAnnotationData();
                        newOmeroAnnotationMap.setContent(omeroKeyValues);
                        newOmeroAnnotationMap.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation"); //TODO change the namespace

                        OmeroRawTools.addKeyValuesOnOmero(newOmeroAnnotationMap, this.client, this.imageId);
                        // TODO remove this try
                        try {
                            toQuPath(QuPathGUI.getInstance(), "");
                        }catch(Exception e){
                            System.out.println("Error in IO on qupath projoect");
                        }

                        return true;

                    } else {
                        Dialogs.showWarningNotification("Upload from HRM", "Image "+this.imageToSend.toString()+" cannot be uploaded on OMERO");
                        return false;
                    }
                } else {
                    Dialogs.showWarningNotification("Existing images on OMERO", "Image "+this.imageToSend.toString()+" already exists on OMERO. It is not uploaded");
                    return false;
                }
            } else {
                logger.error("The dataset "+target+" does not exist on OMERO");
                return false;
            }
        }
        Dialogs.showErrorNotification("Un-existing image", "The image "+this.imageToSend+" does not exist");
        return false;
    }

    //TODO make this method in another class with parameters : qupath and the type of server to build
    // TODO find a way to build the server URI not by hand
    // TODO ask Pete if there is a way to import an image in a qp project by scripting
    // TODO remove this exception thown
    @Override
    public void toQuPath(QuPathGUI qupath, String path) throws IOException {
        List<ProjectImageEntry<BufferedImage>> projectImages = new ArrayList<>();

        //String serverUri = this.client.getServerURI().toString();
        String imageURI = /*serverUri + */String.format("https://omero-poc.epfl.ch/webclient/?show=image-%d", this.imageId);

        Project<BufferedImage> project = qupath.getProject();
        URI uri = null;
        try {
            uri = GeneralTools.toURI(imageURI);
            var tempProject = ProjectIO.loadProject(uri, BufferedImage.class);
            projectImages = new ArrayList<>(tempProject.getImageList());
        } catch (Exception e) {
            logger.warn("Unable to add images from {} ({})", imageURI, e.getLocalizedMessage());
        }

        // If we have projects, try adding images from these first
        if (!projectImages.isEmpty()) {
            for (var temp : projectImages) {
                try {
                    project.addDuplicate(temp, true);
                } catch (Exception e) {
                    logger.error("Unable to copy images to the current project");
                }
            }
        }

        // define the builder (here OMERoone)
        OmeroRawImageServerBuilder defaultBuilder = new OmeroRawImageServerBuilder();
        ImageServerBuilder.UriImageSupport<BufferedImage> support = ImageServers.getImageSupport(defaultBuilder, uri, "");

        if (support != null){
            List<ImageServerBuilder.ServerBuilder<BufferedImage>> builders = support.getBuilders();

            // Add everything in order first
            List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>();
            for (var builder : builders) {
                entries.add(project.addImage(builder));
            }

            // Initialize (the slow bit)
            for (var entry : entries) {
                // initialize entry
                try (ImageServer<BufferedImage> server = entry.getServerBuilder().build()){
                    // Set the image name
                    String name = ServerTools.getDisplayableImageName(server);
                    entry.setImageName(name);

                    // Pyramidalize this if we need to
                    ImageServer<BufferedImage> server2 = server;
                    int minPyramidDimension = PathPrefs.minPyramidDimensionProperty().get();
                    if (server.nResolutions() == 1 && Math.max(server.getWidth(), server.getHeight()) > minPyramidDimension) {
                        var serverTemp = ImageServers.pyramidalize(server);
                        if (serverTemp.nResolutions() > 1) {
                            logger.debug("Auto-generating image pyramid for " + name);
                            server2 = serverTemp;
                        } else
                            serverTemp.close();
                    }

                    if (server != server2)
                        server2.close();

                } catch (Exception e) {
                    logger.warn("Exception adding " + entry, e);
                }

                // add metadata
                entry.putMetadataValue("test","1");

            }
            try {
                project.syncChanges();
            } catch (IOException e1) {
                Dialogs.showErrorMessage("Sync project", e1);
            }
            qupath.refreshProject();
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
            this.target = Integer.parseInt(omeroDataset.split("_")[0]);
        }
        return this;
    }
}
