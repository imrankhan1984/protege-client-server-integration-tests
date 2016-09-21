package org.protege.editor.owl.integration;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.commons.io.FileUtils;
import org.protege.editor.owl.client.LocalHttpClient;
import org.protege.editor.owl.server.http.HTTPServer;
import org.protege.editor.owl.server.versioning.api.DocumentRevision;
import org.semanticweb.owlapi.model.OWLRuntimeException;

import edu.stanford.protege.metaproject.ConfigurationManager;
import edu.stanford.protege.metaproject.api.PlainPassword;
import edu.stanford.protege.metaproject.api.PolicyFactory;
import edu.stanford.protege.metaproject.api.UserId;

/**
 * @author Josef Hardi <johardi@stanford.edu> <br>
 * Stanford Center for Biomedical Informatics Research
 */
public abstract class BaseTest {

    private static final String orginalConfigLocation = "src/test/resources/server-configuration.json";
    private static final String workingConfigLocation = "src/test/resources/working-server-configuration.json";

    protected static final String SERVER_ADDRESS = "http://localhost:8080";
    protected static final String ADMIN_SERVER_ADDRESS = "http://localhost:8081";

    protected static final DocumentRevision R0 = DocumentRevision.START_REVISION;
    protected static final DocumentRevision R1 = DocumentRevision.create(1);
    protected static final DocumentRevision R2 = DocumentRevision.create(2);
    protected static final DocumentRevision R3 = DocumentRevision.create(3);
    protected static final DocumentRevision R4 = DocumentRevision.create(4);
    protected static final DocumentRevision R5 = DocumentRevision.create(5);

    protected static final PolicyFactory f = ConfigurationManager.getFactory();

    private static HTTPServer httpServer = null;

    private LocalHttpClient admin;

    protected static class PizzaOntology {
        static final String getId() {
            return "http://www.co-ode.org/ontologies/pizza/pizza.owl";
        }
        static final File getResource() {
            try {
                return new File(BaseTest.class.getResource("/pizza.owl").toURI());
            }
            catch (URISyntaxException e) {
                throw new OWLRuntimeException("File not found", e);
            }
        }
    }

    protected static class LargeOntology {
        static final String getId() {
            return "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl";
        }
        static final File getResource() {
            try {
                return new File(BaseTest.class.getResource("/thesaurus.owl").toURI());
            }
            catch (URISyntaxException e) {
                throw new OWLRuntimeException("File not found", e);
            }
        }
    }

    protected void startCleanServer() throws Exception {
        initServerConfiguration();
        httpServer = new HTTPServer();
        httpServer.start();
    }

    private void initServerConfiguration() throws IOException {
        File originalCopy = new File(orginalConfigLocation);
        File workingCopy = new File(workingConfigLocation);
        FileUtils.copyFile(originalCopy, workingCopy);
        System.setProperty(HTTPServer.SERVER_CONFIGURATION_PROPERTY, workingConfigLocation);
    }

    protected void stopServer() throws Exception {
        httpServer.stop();
        removeServerConfiguration();
    }

    private void removeServerConfiguration() {
        File workingCopy = new File(workingConfigLocation);
        if (workingCopy.exists()) {
            workingCopy.delete();
        }
    }

    @Deprecated
    /**
     * Use method connect() instead
     */
    public void connectToServer(String address) throws Exception {
        UserId userId = f.getUserId("root");
        PlainPassword password = f.getPlainPassword("rootpwd");
        admin = login(userId, password, address);
    }

    protected LocalHttpClient connect(String username, String password, String serverAddress) throws Exception {
        return new LocalHttpClient(username, password, serverAddress);
    }

    protected LocalHttpClient connectAsAdmin() throws Exception {
        return connect("root", "rootpwd", ADMIN_SERVER_ADDRESS);
    }

    protected LocalHttpClient connectAsGuest() throws Exception {
        return connect("guest", "guestpwd", SERVER_ADDRESS);
    }

    @Deprecated
    /**
     * Use method connect() instead
     */
    protected LocalHttpClient login(UserId userId, PlainPassword password, String address) throws Exception {
        return new LocalHttpClient(userId.get(), password.getPassword(), address);
    }

    @Deprecated
    /**
     * Use method connectAsAdmin() instead
     */
    protected LocalHttpClient getAdmin() {
        return admin;
    }
}
