package org.eclipse.leshan.server.bootstrap.demo;


import org.apache.commons.cli.*;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateMessage;
import org.eclipse.californium.scandium.dtls.DTLSSession;
import org.eclipse.californium.scandium.dtls.HandshakeException;
import org.eclipse.californium.scandium.dtls.x509.CertificateVerifier;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.SecurityMode;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.StaticModel;
import org.eclipse.leshan.server.bootstrap.BootstrapConfig;
import org.eclipse.leshan.server.bootstrap.EditableBootstrapConfigStore;
import org.eclipse.leshan.server.bootstrap.demo.servlet.BootstrapServlet;
import org.eclipse.leshan.server.bootstrap.demo.servlet.ServerServlet;
import org.eclipse.leshan.server.californium.LeshanBootstrapServerBuilder;
import org.eclipse.leshan.server.californium.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.impl.LeshanBootstrapServer;
import org.eclipse.leshan.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.File;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;


@ServletComponentScan
@SpringBootApplication
@EnableEurekaClient
@EnableSwagger2
public class SpringbootLeshanBootstrapServerDemo {

    static {
        // Define a default logback.configurationFile
        String property = System.getProperty("logback.configurationFile");
        if (property == null) {
            System.setProperty("logback.configurationFile", "logback-config.xml");
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(SpringbootLeshanBootstrapServerDemo.class);

    private final static String USAGE = "java -jar leshan-bsserver-demo.jar [OPTION]";


    public static void main(String[] args) {

        startLeshanBootstrap(args);

        SpringApplication.run(SpringbootLeshanBootstrapServerDemo.class, args);
    }

    private final static String _configFilename = JSONFileBootstrapStore.DEFAULT_FILE;

    @Bean
    public EditableBootstrapConfigStore getBSStore() {
        return new JSONFileBootstrapStore(_configFilename);
    }

    private static LeshanBootstrapServer _bsServer;

    @Bean
    public LeshanBootstrapServer getBootstrapServer() {
        return _bsServer;
    }

    private static X509Certificate _serverCertificate;

    @Bean
    public X509Certificate getServerCertificate() {
        return _serverCertificate;
    }

    public static void startLeshanBootstrap(String[] args) {
        // Define options for command line tools
        Options options = new Options();

        options.addOption("h", "help", false, "Display help information.");
        options.addOption("lh", "coaphost", true, "Set the local CoAP address.\n  Default: any local address.");
        options.addOption("lp", "coapport", true,
                String.format("Set the local CoAP port.\n  Default: %d.", LwM2m.DEFAULT_COAP_PORT));
        options.addOption("slh", "coapshost", true, "Set the secure local CoAP address.\nDefault: any local address.");
        options.addOption("slp", "coapsport", true,
                String.format("Set the secure local CoAP port.\nDefault: %d.", LwM2m.DEFAULT_COAP_SECURE_PORT));
        options.addOption("wh", "webhost", true, "Set the HTTP address for web server.\nDefault: any local address.");
        options.addOption("wp", "webport", true, "Set the HTTP port for web server.\nDefault: 8080.");
        options.addOption("m", "modelsfolder", true, "A folder which contains object models in OMA DDF(.xml) format.");
        options.addOption("cfg", "configfile", true,
                "Set the filename for the configuration.\nDefault: " + JSONFileBootstrapStore.DEFAULT_FILE + ".");
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);

        // Parse arguments
        CommandLine cl;
        try {
            cl = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("Parsing failed.  Reason: " + e.getMessage());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Print help
        if (cl.hasOption("help")) {
            formatter.printHelp(USAGE, options);
            return;
        }

        // Abort if unexpected options
        if (cl.getArgs().length > 0) {
            System.err.println("Unexpected option or arguments : " + cl.getArgList());
            formatter.printHelp(USAGE, options);
            return;
        }

        // Get local address
        String localAddress = cl.getOptionValue("lh");
        if (localAddress == null)
            localAddress = "0.0.0.0";
        String localPortOption = cl.getOptionValue("lp");
        int localPort = LwM2m.DEFAULT_COAP_PORT;
        if (localPortOption != null) {
            localPort = Integer.parseInt(localPortOption);
        }

        // Get secure local address
        String secureLocalAddress = cl.getOptionValue("slh");
        if (secureLocalAddress == null)
            secureLocalAddress = "0.0.0.0";
        String secureLocalPortOption = cl.getOptionValue("slp");
        int secureLocalPort = LwM2m.DEFAULT_COAP_SECURE_PORT;
        if (secureLocalPortOption != null) {
            secureLocalPort = Integer.parseInt(secureLocalPortOption);
        }

        // get http address
        String webAddress = cl.getOptionValue("wh");
        String webPortOption = cl.getOptionValue("wp");
        int webPort = 8080;
        if (webPortOption != null) {
            webPort = Integer.parseInt(webPortOption);
        }

        // Get models folder
        String modelsFolderPath = cl.getOptionValue("m");

        // Get config file
        String configFilename = cl.getOptionValue("cfg");
        if (configFilename == null) {
            configFilename = JSONFileBootstrapStore.DEFAULT_FILE;
        }

        try {
            createAndStartServer(webAddress, webPort, localAddress, localPort, secureLocalAddress, secureLocalPort, modelsFolderPath, configFilename);
        } catch (BindException e) {
            System.err.println(String.format("Web port %s is already in use, you can change it using the 'webport' option.", webPort));
            formatter.printHelp(USAGE, options);
        } catch (Exception e) {
            LOG.error("Jetty stopped with unexpected error ...", e);
        }
    }

    public static void createAndStartServer(String webAddress, int webPort, String localAddress, int localPort,
                                            String secureLocalAddress, int secureLocalPort, String modelsFolderPath, String configFilename)
            throws Exception {
        // Create Models
        List<ObjectModel> models = ObjectLoader.loadDefault();
        if (modelsFolderPath != null) {
            models.addAll(ObjectLoader.loadObjectsFromDir(new File(modelsFolderPath)));
        }

        // Prepare and start bootstrap server
        LeshanBootstrapServerBuilder builder = new LeshanBootstrapServerBuilder();
        JSONFileBootstrapStore bsStore = new JSONFileBootstrapStore(configFilename);
        builder.setConfigStore(bsStore);
        builder.setSecurityStore(new BootstrapConfigSecurityStore(bsStore));
        builder.setLocalAddress(localAddress, localPort);
        builder.setLocalSecureAddress(secureLocalAddress, secureLocalPort);
        builder.setModel(new StaticModel(models));

        // Create X509 credentials;
        _serverCertificate = null;
        try {
            PrivateKey privateKey = SecurityUtil.privateKey.readFromResource("credentials/bsserver_privkey.der");
            _serverCertificate = SecurityUtil.certificate.readFromResource("credentials/bsserver_cert.der");
            builder.setPrivateKey(privateKey);
            builder.setCertificateChain(new X509Certificate[]{_serverCertificate});

            // Use a certificate verifier which trust all certificates by default.
            DtlsConnectorConfig.Builder dtlsConfigBuilder = new DtlsConnectorConfig.Builder();
            dtlsConfigBuilder.setCertificateVerifier(new CertificateVerifier() {
                @Override
                public void verifyCertificate(CertificateMessage message, DTLSSession session)
                        throws HandshakeException {
                    // trust all means never raise HandshakeException
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            });
            builder.setDtlsConfig(dtlsConfigBuilder);

        } catch (Exception e) {
            LOG.error("Unable to load embedded X.509 certificate.", e);
            System.exit(-1);
        }

        // Create CoAP Config
        NetworkConfig coapConfig;
        File configFile = new File(NetworkConfig.DEFAULT_FILE_NAME);
        if (configFile.isFile()) {
            coapConfig = new NetworkConfig();
            coapConfig.load(configFile);
        } else {
            coapConfig = LeshanServerBuilder.createDefaultNetworkConfig();
            coapConfig.store(configFile);
        }
        builder.setCoapConfig(coapConfig);

        _bsServer = builder.build();

        // Create a Bootstrap config.
        BootstrapConfig config = new BootstrapConfig();

        // delete object /0 and /1
        config.toDelete = Arrays.asList("/0","/1");

        // write a security instance for LWM2M server.
        // here we will use the leshan sandbox at : https://leshan.eclipseprojects.io/
        BootstrapConfig.ServerSecurity dmSecurity = new BootstrapConfig.ServerSecurity();
        dmSecurity.uri = "coap://localhost:5683";
        dmSecurity.serverId = 2222;
        dmSecurity.securityMode = SecurityMode.NO_SEC;
        config.security.put(1, dmSecurity); // O is reserved for bootstrap server

        // write a server object for LWM2M server
        BootstrapConfig.ServerConfig dmConfig = new BootstrapConfig.ServerConfig();
        dmConfig.shortId = dmSecurity.serverId;
        dmConfig.lifetime = 5*60;
        config.servers.put(0, dmConfig);

        // Add the config to the store for your device.
        EditableBootstrapConfigStore configStore = (EditableBootstrapConfigStore) _bsServer.getBoostrapStore();
        configStore.add("test-device_01", config);

        _bsServer.start();

        // Now prepare and start jetty

        /*

        InetSocketAddress jettyAddr;
        if (webAddress == null) {
            jettyAddr = new InetSocketAddress(webPort);
        } else {
            jettyAddr = new InetSocketAddress(webAddress, webPort);
        }
        Server server = new Server(jettyAddr);
        WebAppContext root = new WebAppContext();

        root.setContextPath("/");
        root.setResourceBase(LeshanBootstrapServerDemo.class.getClassLoader().getResource("webapp").toExternalForm());
        root.setParentLoaderPriority(true);

        ServletHolder bsServletHolder = new ServletHolder(new BootstrapServlet(bsStore));
        root.addServlet(bsServletHolder, "/api/bootstrap/*");

        ServletHolder serverServletHolder = new ServletHolder(new ServerServlet(bsServer, serverCertificate));
        root.addServlet(serverServletHolder, "/api/server/*");

        server.setHandler(root);

        server.start();
        LOG.info("Web server started at {}.", server.getURI());

        */
    }
}
