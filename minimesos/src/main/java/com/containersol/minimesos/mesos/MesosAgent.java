package com.containersol.minimesos.mesos;

import com.containersol.minimesos.MinimesosException;
import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.config.MesosAgentConfig;
import com.containersol.minimesos.util.ResourceUtil;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mesos Master adds the "agent" component for Apache Mesos
 */
@Builder
@Slf4j
public class MesosAgent extends MesosContainer {

    private final MesosAgentConfig mesosAgentConfig;

    public MesosAgent(ZooKeeper zooKeeperContainer) {
        this(zooKeeperContainer, new MesosAgentConfig());
    }

    public MesosAgent(ZooKeeper zooKeeperContainer, MesosAgentConfig mesosAgentConfig) {
        super(zooKeeperContainer, mesosAgentConfig);
        this.mesosAgentConfig = mesosAgentConfig;
    }

    public MesosAgent(MesosCluster cluster, String uuid, String containerId) {
        this(cluster, uuid, containerId, new MesosAgentConfig());
    }

    private MesosAgent(MesosCluster cluster, String uuid, String containerId, MesosAgentConfig mesosAgentConfig) {
        super(cluster, uuid, containerId, mesosAgentConfig);
        this.mesosAgentConfig = mesosAgentConfig;
    }

    public String getResources() {
        return mesosAgentConfig.getResources().asMesosString();
    }


    @Override
    public int getPortNumber() {
        return mesosAgentConfig.getPortNumber();
    }

    public CreateContainerCmd getBaseCommand() {
        String hostDir = MesosCluster.getHostDir().getAbsolutePath();

        return DockerClientFactory.get().createContainerCmd(getMesosImageName() + ":" + getMesosImageTag())
                .withName(getName())
                .withPrivileged(true)
                .withEnv(createMesosLocalEnvironment())
                .withPid("host")
                .withLinks(new Link(getZooKeeperContainer().getContainerId(), "minimesos-zookeeper"))
                .withBinds(
                        Bind.parse("/var/run/docker.sock:/var/run/docker.sock"),
                        Bind.parse("/sys/fs/cgroup:/sys/fs/cgroup"),
                        Bind.parse(hostDir + ":" + hostDir)
                );
    }

    @Override
    public String getRole() {
        return "agent";
    }

    @Override
    protected CreateContainerCmd dockerCommand() {
        ArrayList<ExposedPort> exposedPorts = new ArrayList<>();
        exposedPorts.add(new ExposedPort(getPortNumber()));
        try {
            ArrayList<Integer> resourcePorts = ResourceUtil.parsePorts(getResources());
            for (Integer port : resourcePorts) {
                exposedPorts.add(new ExposedPort(port));
            }
        } catch (MinimesosException e) {
            log.error("Port binding is incorrect: {}", e.getMessage());
        }

        return getBaseCommand()
                .withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]));

    }

    @Override
    public Map<String, String> getDefaultEnvVars() {
        Map<String, String> envs = new TreeMap<>();
        envs.put("MESOS_RESOURCES", getResources());
        envs.put("MESOS_PORT", String.valueOf(getPortNumber()));
        envs.put("MESOS_MASTER", getFormattedZKAddress());
        envs.put("MESOS_SWITCH_USER", "false");
        envs.put("MESOS_LOGGING_LEVEL", getLoggingLevel());
        envs.put("SERVICE_IGNORE", "1");
        return envs;
    }
}
