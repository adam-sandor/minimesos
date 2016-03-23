package com.containersol.minimesos;

import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.marathon.Marathon;
import com.containersol.minimesos.mesos.ClusterArchitecture;
import com.containersol.minimesos.mesos.DockerClientFactory;
import com.containersol.minimesos.mesos.MesosAgent;
import com.containersol.minimesos.mesos.MesosMaster;
import com.containersol.minimesos.util.ResourceUtil;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONObject;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MesosClusterTest {

    protected static final DockerClient dockerClient = DockerClientFactory.build();
    protected static final ClusterArchitecture CONFIG = new ClusterArchitecture.Builder(dockerClient)
            .withZooKeeper()
            .withMaster()
            .withAgent(zooKeeper -> new MesosAgent(dockerClient, zooKeeper))
            .withAgent(zooKeeper -> new MesosAgent(dockerClient, zooKeeper))
            .withAgent(zooKeeper -> new MesosAgent(dockerClient, zooKeeper))
            .withMarathon(zooKeeper -> new Marathon(dockerClient, zooKeeper))
            .build();

    @ClassRule
    public static final MesosCluster CLUSTER = new MesosCluster(CONFIG);

    @After
    public void after() {
        DockerContainersUtil util = new DockerContainersUtil(CONFIG.dockerClient);
        util.getContainers(false).filterByName(HelloWorldContainer.CONTAINER_NAME_PATTERN).kill().remove();
    }

    @Test(expected = ClusterArchitecture.MesosArchitectureException.class)
    public void testConstructor() {
        MesosCluster cluster = new MesosCluster(null);
    }

    @Test
    public void testLoadCluster() {
        String clusterId = CLUSTER.getClusterId();

        MesosCluster cluster = MesosCluster.loadCluster(clusterId);

        assertArrayEquals(CLUSTER.getContainers().toArray(), cluster.getContainers().toArray());

        assertEquals(CLUSTER.getZkContainer().getIpAddress(), cluster.getZkContainer().getIpAddress());
        assertEquals(CLUSTER.getMasterContainer().getStateUrl(), cluster.getMasterContainer().getStateUrl());

        assertFalse("Deserialize cluster is expected to remember exposed ports setting", cluster.isExposedHostPorts());
    }

    @Test(expected = MinimesosException.class)
    public void testLoadCluster_noContainersFound() {
        MesosCluster cluster = MesosCluster.loadCluster("nonexistent");
    }

    @Test
    public void testInfo() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);

        CLUSTER.info(printStream);

        String output = byteArrayOutputStream.toString();

        assertTrue(output.contains("Minimesos cluster is running: " + CLUSTER.getClusterId() + "\n"));
        assertTrue(output.contains("export MINIMESOS_ZOOKEEPER=zk://" + CLUSTER.getZkContainer().getIpAddress() + ":2181\n"));
        assertTrue(output.contains("export MINIMESOS_MASTER=http://" + CLUSTER.getMasterContainer().getIpAddress() + ":5050\n"));
        assertTrue(output.contains("export MINIMESOS_MARATHON=http://" + CLUSTER.getMarathonContainer().getIpAddress() + ":8080\n"));
    }

    @Test
    public void mesosAgentStateInfoJSONMatchesSchema() throws UnirestException, JsonParseException, JsonMappingException {
        String agentId = CLUSTER.getAgents().get(0).getContainerId();
        JSONObject state = CLUSTER.getAgentStateInfo(agentId);
        assertNotNull(state);
    }

    @Test
    public void mesosClusterCanBeStarted() throws Exception {
        MesosMaster master = CLUSTER.getMasterContainer();
        JSONObject stateInfo = master.getStateInfoJSON();

        assertEquals(3, stateInfo.getInt("activated_slaves"));
    }

    @Test
    public void mesosResourcesCorrect() throws Exception {
        JSONObject stateInfo = CLUSTER.getMasterContainer().getStateInfoJSON();
        for (int i = 0; i < 3; i++) {
            assertEquals((long) 0.2, stateInfo.getJSONArray("slaves").getJSONObject(0).getJSONObject("resources").getLong("cpus"));
            assertEquals(256, stateInfo.getJSONArray("slaves").getJSONObject(0).getJSONObject("resources").getInt("mem"));
        }
    }

    @Test
    public void testAgentStateRetrieval() {
        List<MesosAgent> agents = CLUSTER.getAgents();
        assertNotNull(agents);
        assertTrue(agents.size() > 0);

        MesosAgent agent = agents.get(0);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outputStream, true);

        String cliContainerId = agent.getContainerId().substring(0, 11);

        CLUSTER.state(ps, cliContainerId);

        String state = outputStream.toString();
        assertTrue(state.contains("frameworks"));
        assertTrue(state.contains("resources"));
    }

    @Test
    public void dockerExposeResourcesPorts() throws Exception {
        DockerClient docker = CONFIG.dockerClient;
        List<MesosAgent> containers = CLUSTER.getAgents();

        for (MesosAgent container : containers) {
            ArrayList<Integer> ports = ResourceUtil.parsePorts(container.getResources());
            InspectContainerResponse response = docker.inspectContainerCmd(container.getContainerId()).exec();
            Map bindings = response.getNetworkSettings().getPorts().getBindings();
            for (Integer port : ports) {
                assertTrue(bindings.containsKey(new ExposedPort(port)));
            }
        }
    }

    @Test
    public void testPullAndStartContainer() throws UnirestException {
        HelloWorldContainer container = new HelloWorldContainer(CONFIG.dockerClient);
        String containerId = CLUSTER.addAndStartContainer(container);
        String ipAddress = DockerContainersUtil.getIpAddress(CONFIG.dockerClient, containerId);
        String url = "http://" + ipAddress + ":" + HelloWorldContainer.SERVICE_PORT;
        assertEquals(200, Unirest.get(url).asString().getStatus());
    }

    @Test
    public void testMasterLinkedToAgents() throws UnirestException {
        List<MesosAgent> containers = CLUSTER.getAgents();
        for (MesosAgent container : containers) {
            InspectContainerResponse exec = CONFIG.dockerClient.inspectContainerCmd(container.getContainerId()).exec();

            List<Link> links = Arrays.asList(exec.getHostConfig().getLinks());

            assertNotNull(links);
            assertEquals("link to zookeeper is expected", 1, links.size());
            assertEquals("minimesos-zookeeper", links.get(0).getAlias());
        }
    }

    @Test(expected = MinimesosException.class)
    public void testInstall() {
        CLUSTER.install(null);
    }

    @Test(expected = IllegalStateException.class)
    public void testStartingClusterSecondTime() {
        CLUSTER.start(30);
    }

}
