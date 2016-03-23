package com.containersol.minimesos.docker;


import com.containersol.minimesos.mesos.DockerClientFactory;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Filters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable utility class, which represents set of docker containers with filters and operations on this list
 */
public class DockerContainersUtil {

    private Set<Container> containers = new HashSet<>();

    public DockerContainersUtil() {
    }

    private DockerContainersUtil(Set<Container> containers) {
        this.containers = containers;
    }

    /**
     * @param showAll should the list include stopped containers
     * @return set of docker containers
     */
    public DockerContainersUtil getContainers(boolean showAll) {
        Set<Container> containers = new HashSet<>(DockerClientFactory.get().listContainersCmd().withShowAll(showAll).exec());
        return new DockerContainersUtil(containers);
    }

    public int size() {
        return containers.size();
    }

    /**
     * Filters the set based on the constainer name
     *
     * @param pattern regular expression pattern of the container name
     * @return filtered set
     */
    public DockerContainersUtil filterByName(String pattern) {
        Set<Container> matched = new HashSet<>();
        for (Container container : containers) {
            String[] names = container.getNames();
            for (String name : names) {
                // all names start with '/'
                if (name.substring(1).matches(pattern)) {
                    matched.add(container);
                }
            }
        }

        return new DockerContainersUtil(matched);
    }

    /**
     * Filters the set based on the constainer name
     *
     * @param pattern regular expression pattern of the container name
     * @return filtered set
     */
    public DockerContainersUtil filterByImage(String pattern) {
        Set<Container> matched = containers.stream().filter(container -> container.getImage().matches(pattern)).collect(Collectors.toSet());

        return new DockerContainersUtil(matched);
    }

    /**
     * Removes all containers in the util object
     */
    public void remove() {
        if (containers != null) {
            for (Container container : containers) {
                DockerClientFactory.get().removeContainerCmd(container.getId()).withForce(true).withRemoveVolumes(true).exec();
            }
        }
    }

    /**
     * Removes all containers in the util object
     */
    public DockerContainersUtil kill() {
        return kill(false);
    }

    /**
     * Removes all containers in the util object
     *
     * @param ignoreFailure - use <code>true</code> if you expect containers might be stopped by this time
     */
    public DockerContainersUtil kill(boolean ignoreFailure) {
        if (containers != null) {
            for (Container container : containers) {
                try {
                    DockerClientFactory.get().killContainerCmd(container.getId()).exec();
                } catch (DockerException failure) {
                    if (!ignoreFailure) {
                        throw failure;
                    }
                }
            }
        }
        return this;
    }

    /**
     * @return IP addresses of containers
     */
    public Set<String> getIpAddresses() {
        Set<String> ips = new HashSet<>();
        ips.addAll(containers.stream().map(container -> getIpAddress(container.getId())).collect(Collectors.toList()));
        return ips;
    }

    /**
     * @param containerId  id of the container to inspect
     * @return IP Address of the container
     */
    public static String getIpAddress(String containerId) {
        InspectContainerResponse response = DockerClientFactory.get().inspectContainerCmd(containerId).exec();
        return response.getNetworkSettings().getIpAddress();
    }

    /**
     * @return IP Address of the container's gateway (which would be docker0)
     */
    public static String getGatewayIpAddress() {
        List<Container> containers = DockerClientFactory.get().listContainersCmd().exec();
        if (containers == null || containers.size() == 0) {
            throw new IllegalStateException("Cannot get docker0 IP address because no containers are running");
        }

        InspectContainerResponse response = DockerClientFactory.get().inspectContainerCmd(containers.get(0).getId()).exec();
        return response.getNetworkSettings().getGateway();
    }

    /**
     * @param containerId  id of the container to retrieve
     * @return container or null
     */
    public static Container getContainer(String containerId) {
        List<Container> containers = DockerClientFactory.get().listContainersCmd().withFilters(new Filters().withFilter("id", containerId)).exec();
        if (containers != null && containers.size() == 1) {
            return containers.get(0);
        } else {
            return null;
        }
    }

}
