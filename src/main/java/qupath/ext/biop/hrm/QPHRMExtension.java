package qupath.ext.biop.hrm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.biop.hrm.retrievers.QPHRMRetrieveFromHRMCommand;
import qupath.ext.biop.hrm.senders.QPHRMSendToHRMCommand;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

/**
 * Extension to manage HRM jobs from a QuPath project
 */
public class QPHRMExtension implements QuPathExtension, GitHubProject {
    private final static Logger logger = LoggerFactory.getLogger(QPHRMExtension.class);
    private static boolean alreadyInstalled = false;

    @Override
    public void installExtension(QuPathGUI qupath) {

        if (alreadyInstalled)
            return;

        logger.debug("Installing OMERO extension");

        alreadyInstalled = true;

        // for HRM extension
        var sendToHRMMenu = ActionTools.createAction(new QPHRMSendToHRMCommand(qupath), "Send to HRM");
        var retrieveFromHRMMenu = ActionTools.createAction(new QPHRMRetrieveFromHRMCommand(qupath), "Retrieve from HRM");

        MenuTools.addMenuItems(qupath.getMenu("Extensions", false),
                MenuTools.createMenu("HRM",
                        sendToHRMMenu,
                        retrieveFromHRMMenu
                )
        );
    }

    @Override
    public String getName() {
        return "HRM BIOP extension";
    }

    @Override
    public String getDescription() {
        return "Adds the ability to send images to HRM and retrieve deconvolved images from HRM. Images can be stored locally or on OMERO";
    }

    @Override
    public GitHubRepo getRepository() {
        return GitHubRepo.create(getName(), "biop", "qupath-extension-biop-hrm");
    }
}
