package qupath.ext.biop.hrm;

import omero.gateway.model.DatasetData;
import omero.gateway.model.ProjectData;
import qupath.ext.biop.servers.omero.raw.OmeroRawClient;
import qupath.ext.biop.servers.omero.raw.OmeroRawImageServer;
import qupath.ext.biop.servers.omero.raw.OmeroRawTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;

public class QPHRMOmeroSender implements QPHRMSender {
    /** path to the HRM-user folder where to save the image */
    private String destinationFolder = "";

    /** image to send to HRM */
    private OmeroRawImageServer image;

    /** OMERO client */
    private OmeroRawClient client;

    final private int SKIPPED = 2;
    final private int COPIED = 1;
    final private int FAILED = 0;
    final private int ERROR = -1;

    public QPHRMOmeroSender(){

    }

    @Override
    public String getDestinationFolder() { return this.destinationFolder; }

    @Override
    public int copy(boolean overwrite) {
        File destinationFolderFile = new File(this.destinationFolder);

        // check if the destination folder exists
        if(destinationFolderFile.exists()) {
            Long omeroId = this.image.getId();
            // check if the image already exists on HRM
            String imageName = OmeroRawTools.readOmeroImage(this.client, omeroId).getName();
            if(overwrite || !(new File(this.destinationFolder + File.separator + imageName).exists()))
                // copy image in HRM folder
                return (OmeroRawTools.downloadImage(this.client, omeroId, destinationFolderFile.toString()) ? COPIED : FAILED);
            return SKIPPED;
        } else{
            Dialogs.showErrorNotification("Copying OMERO file","Destination folder "+this.destinationFolder+" does not exists");
            return ERROR;
        }
    }

    @Override
    public QPHRMSender buildDestinationFolder(String rootPath) {
        File rootPathFile = new File(rootPath);
        // check the root directory
        if(!rootPathFile.isDirectory())
            return this;

        // check if the user folder already exists and stop if it does not.
        File userPathFile = new File(rootPath + File.separator + this.client.getLoggedInUser().getOmeName().getValue());
        if(!userPathFile.isDirectory()) {Dialogs.showErrorNotification("Building destination folder","Path "+userPathFile+" does not exists"); return this;}

        // get or create "Raw" folder
        File rawPathFile = new File(userPathFile + File.separator + "Raw");
        if(!rawPathFile.isDirectory())
            if(!rawPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+rawPathFile+" does not exists"); return this;}

        // get or create "QuPath" folder
        File qupathPathFile = new File(rawPathFile + File.separator + "QuPath");
        if(!qupathPathFile.isDirectory())
            if(!qupathPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+qupathPathFile+" does not exists"); return this;}

        // get or create "omero" folder
        File localPathFile = new File(qupathPathFile + File.separator + "omero");
        if(!localPathFile.isDirectory())
            if(!localPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+localPathFile+" does not exists"); return this;}

        // get image parent dataset
        String projectName = "";
        String datasetName = "";
        Collection<DatasetData> datasets = (Collection<DatasetData>) OmeroRawTools.getParent(client, "Image", this.image.getId());

        if(datasets.isEmpty()){
            projectName = "None";
            datasetName = "None";
        }else{
            // get dataset parent project
            DatasetData dataset = datasets.iterator().next();
            datasetName = dataset.getId()+"_"+dataset.getName();
            Collection<ProjectData> projects = (Collection<ProjectData>) OmeroRawTools.getParent(client, "Dataset",dataset.getId());
            if(projects.isEmpty())
                projectName = "None";
            else {
                ProjectData project = projects.iterator().next();
                projectName = project.getId()+"_"+project.getName();
            }
        }

        // get or create "project" folder
        File projectPathFile = new File(localPathFile + File.separator + projectName);
        if(!projectPathFile.isDirectory())
            if(!projectPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+projectPathFile+" does not exists"); return this;}

        // get or create "dataset" folder
        File datasetPathFile = new File(projectPathFile + File.separator + datasetName);
        if(!datasetPathFile.isDirectory())
            if(!datasetPathFile.mkdir()){Dialogs.showErrorNotification("Building destination folder","Path "+datasetPathFile+" does not exists"); return this;}

        this.destinationFolder = datasetPathFile.toString();

        return this;
    }

    @Override
    public QPHRMSender setImage(ImageServer<BufferedImage> image) {
        this.image = (OmeroRawImageServer) image;
        return this;
    }

    public QPHRMSender setClient(OmeroRawClient client){
        this.client = client;
        return this;
    }
}
