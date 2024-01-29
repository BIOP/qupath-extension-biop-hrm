package qupath.ext.biop.hrm.senders;

import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.hrm.HRMConstants;
import qupath.ext.biop.servers.omero.raw.client.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.ext.biop.servers.omero.raw.utils.OmeroRawTools;
import qupath.ext.biop.servers.omero.raw.utils.Utils;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class QPHRMOmeroSender implements QPHRMSender {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMOmeroSender.class);

    /** path to the HRM-user folder where to save the image */
    private String destinationFolder = "";

    /** image to send to HRM */
    private OmeroRawImageServer imageServer;

    /** OMERO client */
    private OmeroRawClient client;
    private boolean isSent = false;
    private boolean isFailed = false;
    private boolean isSkipped = false;

    public QPHRMOmeroSender(){}

    @Override
    public String getDestinationFolder() { return this.destinationFolder; }

    @Override
    public boolean isSent() {
        return this.isSent;
    }

    @Override
    public boolean isSkipped() {
        return this.isSkipped;
    }

    @Override
    public boolean isFailed() {
        return this.isFailed;
    }

    @Override
    public QPHRMOmeroSender copy(boolean overwrite) {
        File destinationFolderFile = new File(this.destinationFolder);

        // check if the destination folder exists
        if(destinationFolderFile.exists()) {
            String imageName = this.imageServer.getImageWrapper().getName();
            if(overwrite || !(new File(this.destinationFolder + File.separator + imageName).exists())) {
                // copy image in HRM folder
                try {
                    this.imageServer.getImageWrapper().download(this.client.getSimpleClient(), destinationFolderFile.toString());
                    this.isSent = true;
                }catch(AccessException | OMEROServerError | ServiceException e){
                    Utils.errorLog(logger, "Copying OMERO file", "Cannot download image '"+this.imageServer.getId()+"' from OMERO",e,false);
                    this.isFailed = true;
                }
            }else this.isSkipped = true;
        } else{
            Utils.errorLog(logger, "Copying OMERO file","Destination folder "+this.destinationFolder+" does not exists", false);
            this.isFailed = true;
        }
        return this;
    }

    @Override
    public QPHRMOmeroSender buildDestinationFolder(String rootPath, String username) {
        File rootPathFile = new File(rootPath);
        // check the root directory
        if(!rootPathFile.isDirectory())
            return this;

        // check if the user folder already exists and stop if it does not.
        File userPathFile = new File(rootPath + File.separator + username);
        if(!userPathFile.isDirectory()) {Utils.errorLog(logger, "Building destination folder","Path "+userPathFile+" does not exists", true); return this;}

        // get or create "Raw" folder
        File rawPathFile = new File(userPathFile + File.separator + HRMConstants.RAW_FOLDER);
        if(!rawPathFile.isDirectory())
            if(!rawPathFile.mkdir()){Utils.errorLog(logger, "Building destination folder","Path "+rawPathFile+" does not exists", true); return this;}

        // get or create "omero" folder
        File localPathFile = new File(rawPathFile + File.separator + HRMConstants.OMERO_FOLDER);
        if(!localPathFile.isDirectory())
            if(!localPathFile.mkdir()){Utils.errorLog(logger, "Building destination folder","Path "+localPathFile+" does not exists", true); return this;}

        // get image parent dataset
        String projectName = "None";
        String datasetName = "None";
        ImageWrapper imageWrapper = this.imageServer.getImageWrapper();
        try {
            List<DatasetWrapper> datasets = (List<DatasetWrapper>)OmeroRawTools.getParentContainer(client, imageWrapper, false);

            // get dataset parent project
            if(!datasets.isEmpty()) {
                DatasetWrapper datasetWrapper = datasets.iterator().next();
                datasetName = datasetWrapper.getId() + "_" + datasetWrapper.getName();
                try{
                    List<ProjectWrapper> projects = (List<ProjectWrapper>)OmeroRawTools.getParentContainer(client, datasetWrapper, false);
                    if (!projects.isEmpty()) {
                        ProjectWrapper project = projects.iterator().next();
                        projectName = project.getId() + "_" + project.getName();
                    }
                } catch (AccessException | ServiceException | ExecutionException | OMEROServerError e) {
                    Utils.errorLog(logger, "Building destination folder","Cannot get the parent project of dataset "+datasetWrapper.getName(),e, false);
                }
            }
        } catch (AccessException | ServiceException | ExecutionException | OMEROServerError e) {
            Utils.errorLog(logger, "Building destination folder","Cannot get the parent dataset of image "+imageWrapper.getName(),e, false);
        }

        // get or create "project" folder
        File projectPathFile = new File(localPathFile + File.separator + projectName);
        if(!projectPathFile.isDirectory())
            if(!projectPathFile.mkdir()){Utils.errorLog(logger, "Building destination folder","Path "+projectPathFile+" does not exists", true); return this;}

        // get or create "dataset" folder
        File datasetPathFile = new File(projectPathFile + File.separator + datasetName);
        if(!datasetPathFile.isDirectory())
            if(!datasetPathFile.mkdir()){Utils.errorLog(logger, "Building destination folder","Path "+datasetPathFile+" does not exists", true); return this;}

        this.destinationFolder = datasetPathFile.toString();

        return this;
    }

    @Override
    public QPHRMOmeroSender setImageServer(ImageServer<BufferedImage> imageServer) {
        this.imageServer = (OmeroRawImageServer) imageServer;
        return this;
    }

    public QPHRMOmeroSender setClient(OmeroRawClient client){
        this.client = client;
        return this;
    }
}
