package com.containersol.minimesos;

import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.config.AgentResourcesConfig;
import com.containersol.minimesos.config.ClusterConfig;
import com.containersol.minimesos.config.MesosAgentConfig;
import com.containersol.minimesos.container.AbstractContainer;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.mesos.ClusterArchitecture;
import com.containersol.minimesos.mesos.DockerClientFactory;
import com.containersol.minimesos.mesos.MesosAgent;
import com.containersol.minimesos.util.ResourceUtil;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Replicates MesosClusterTest with new API
 */

public class DefaultMinimumClusterTest {

    @ClassRule
    public static final MesosCluster cluster = new MesosCluster(new ClusterArchitecture.Builder().build());

    @After
    public void after() {
        DockerContainersUtil util = new DockerContainersUtil();
        util.getContainers(false).filterByName(HelloWorldContainer.CONTAINER_NAME_PATTERN).kill().remove();
    }

    @Test
    public void mesosClusterCanBeStarted() throws Exception {
        MesosAgent a = MesosAgent.builder().mesosAgentConfig(new MesosAgentConfig()).build();

        MesosCluster c = MesosCluster.builder().clusterConfig(new ClusterConfig()).container(new AbstractContainer() {
            @Override
            public String getRole() {
                return null;
            }

            @Override
            protected void pullImage() {

            }

            @Override
            protected CreateContainerCmd dockerCommand() {
                return null;
            }
        }).build();


//        MesosCluster c = new MesosCluster.builder()
//                .agent(


//                .build();


        JSONObject stateInfo = cluster.getMasterContainer().getStateInfoJSON();

        assertEquals(1, stateInfo.getInt("activated_slaves")); // Only one agent is actually _required_ to have a cluster
    }

    @Test
    public void mesosResourcesCorrect() throws Exception {
        JSONObject stateInfo = cluster.getMasterContainer().getStateInfoJSON();
        for (int i = 0; i < 3; i++) {
            assertEquals(AgentResourcesConfig.DEFAULT_CPU.getValue(), stateInfo.getJSONArray("slaves").getJSONObject(0).getJSONObject("resources").getDouble("cpus"), 0.0001);
            assertEquals(256, stateInfo.getJSONArray("slaves").getJSONObject(0).getJSONObject("resources").getInt("mem"));
        }
    }

    @Test
    public void dockerExposeResourcesPorts() throws Exception {
        String mesosResourceString = "ports(*):[31000-32000]";
        ArrayList<Integer> ports = ResourceUtil.parsePorts(mesosResourceString);
        List<MesosAgent> containers = cluster.getAgents();
        for (MesosAgent container : containers) {
            InspectContainerResponse response = DockerClientFactory.get().inspectContainerCmd(container.getContainerId()).exec();
            Map bindings = response.getNetworkSettings().getPorts().getBindings();
            for (Integer port : ports) {
                Assert.assertTrue(bindings.containsKey(new ExposedPort(port)));
            }
        }
    }

    @Test
    public void testPullAndStartContainer() throws UnirestException {
        HelloWorldContainer container = new HelloWorldContainer();
        String containerId = cluster.addAndStartContainer(container);
        String ipAddress = DockerContainersUtil.getIpAddress(containerId);

        String url = "http://" + ipAddress + ":" + HelloWorldContainer.SERVICE_PORT;
        HttpResponse<String> response = Unirest.get(url).asString();

        assertEquals(200, response.getStatus());
        assertTrue("Wrong message is received", response.getBody().contains("<h1>Hello world!</h1>"));
    }

}
