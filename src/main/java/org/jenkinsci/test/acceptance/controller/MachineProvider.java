package org.jenkinsci.test.acceptance.controller;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Module;
import com.google.inject.Provider;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.ApiMetadata;
import org.jclouds.apis.Apis;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.providers.ProviderMetadata;
import org.jclouds.providers.Providers;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Iterables.*;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;

/**
 * jcloud based cloud controller. Implementation is partly based on jcloud compute-basic example.
 *
 * @author Vivek Pandey
 */
// TODO: split this to JCloudsMachineProvider class and MachineProvider interface
public abstract class MachineProvider implements Provider<Machine> {
    private static final Map<String, ApiMetadata> allApis = Maps.uniqueIndex(Apis.viewableAs(ComputeServiceContext.class),
            Apis.idFunction());

    private static final Map<String, ProviderMetadata> appProviders = Maps.uniqueIndex(Providers.viewableAs(ComputeServiceContext.class),
            Providers.idFunction());

    private static final Set<String> supportedProviders = ImmutableSet.copyOf(Iterables.concat(appProviders.keySet(), allApis.keySet()));

    protected final ComputeService computeService;

    protected final ContextBuilder contextBuilder;

    private final String groupName="jenkins-test";

    private final Map<String, Machine> machines = new ConcurrentHashMap<>();



    public MachineProvider(String provider, String identity, String credential) {
        logger.info("Machine Provider created");
        if(!contains(supportedProviders, provider)){
            throw new RuntimeException(String.format("Provider %s is not supported. Supported providers: %s",provider, Arrays.toString(supportedProviders.toArray())));
        }

        this.contextBuilder = initComputeService(provider, identity, credential);
        this.computeService =  contextBuilder.buildView(ComputeServiceContext.class).getComputeService();
    }


    public abstract void authorizePorts();


    @Override
    public Machine get() {
        logger.info("new Machine instantiated...");
        logger.info(String.format("Adding node to group %s", groupName));

        Template template;
        try {
            template = getTemplate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


//        NodeMetadata node=computeService.getNodeMetadata("i-913433b0");


        NodeMetadata node;

        try {
            node = getOnlyElement(computeService.createNodesInGroup(groupName, 1, template));
        } catch (RunNodesException e) {
            throw new RuntimeException(e);
        }

        logger.info(String.format("Added node %s: %s", node.getId(), concat(node.getPrivateAddresses(), node.getPublicAddresses())));

        authorizePorts(); //authorize ports for inbound connections

        Machine machine = new JcloudsMachine(this,node);

        machines.put(node.getId(), machine);
        waitForSsh(machine); //wait for ssh to be ready

        return machine;
    }

    public abstract int[] getAvailableInboundPorts();


    public Machine get(String id){
        if(machines.get(id) != null){
            return machines.get(id);
        }
        return get();
    }

    public void destroy(String id){
        if(machines.get(id) != null){
            computeService.destroyNode(id);
            machines.remove(id);
        }
    }



    /**
     * jcloud {@link org.jclouds.compute.domain.Template} to build machine instance from.
     *
     * For example for EC2 the Temaplate is created as:
     *
     * Template template = context.getComputeService().templateBuilder().imageId("ami-ccb35ea5").build();
     *
     * @return
     */
    public abstract Template getTemplate() throws IOException;

    /**
     * Options to be used during instance provisioning.
     */
    public  Properties getOptions(){
        Properties properties = new Properties();
        long scriptTimeout = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);
        properties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");
        return properties;
    }

    private ContextBuilder initComputeService(String provider, String identity, String credential) {
        Iterable<Module> modules = ImmutableSet.<Module>of(
                new SshjSshClientModule(),
                new SLF4JLoggingModule(),
                new EnterpriseConfigurationModule());

        ContextBuilder contextBuilder =  ContextBuilder.newBuilder(provider)
                .credentials(identity, credential)
                .modules(modules)
                .overrides(getOptions());
        logger.info(String.format("Initializing %s", contextBuilder.getApiMetadata()));

        return contextBuilder;
    }

    private void waitForSsh(Machine machine){
        int timeout = 120000; //2 minute
        long startTime = System.currentTimeMillis();
        while(true){
            try {
                if(System.currentTimeMillis() - startTime > timeout){
                    break;
                }
                Ssh ssh = new Ssh(machine.getUser(), machine.getPublicIpAddress());
                ssh.destroy();
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(10000); //sleep 10 sec
                } catch (InterruptedException e1) {
                    logger.error(e.getMessage(), e);
                    return; //exit from this loop
                }
            }
        }
    }
    private static final Logger logger = LoggerFactory.getLogger(MachineProvider.class);
}