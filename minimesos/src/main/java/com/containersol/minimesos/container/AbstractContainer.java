package com.containersol.minimesos.container;

import com.containersol.minimesos.MinimesosException;
import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.mesos.DockerClientFactory;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.jayway.awaitility.core.ConditionTimeoutException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;

import static com.jayway.awaitility.Awaitility.await;

/**
 * Extend this class to start and manage your own containers
 */
public abstract class AbstractContainer {

    private static final int IMAGE_PULL_TIMEOUT_SECS = 5 * 60;
    private static final Logger log = Logger.getLogger(AbstractContainer.class);

    private MesosCluster cluster;
    private String uuid;
    private String containerId;
    private String ipAddress = null;
    private boolean removed;

    protected Map<String, String> envVars = new TreeMap<>();

    protected AbstractContainer() {
        this.uuid = Integer.toUnsignedString(new SecureRandom().nextInt());
    }

    public AbstractContainer(MesosCluster cluster, String uuid, String containerId) {
        this.cluster = cluster;
        this.uuid = uuid;
        this.containerId = containerId;
    }

    public abstract String getRole();

    /**
     * Implement this method to pull your image. This will be called before the container is run.
     */
    protected abstract void pullImage();

    /**
     * Implement this method to create your container.
     *
     * @return Your {@link CreateContainerCmd} for docker.
     */
    protected abstract CreateContainerCmd dockerCommand();

    /**
     * Starts the container and waits until is started
     *
     * @param timeout in seconds
     */
    public void start(int timeout) {
        pullImage();

        CreateContainerCmd createCommand = dockerCommand();
        log.debug("Creating container [" + createCommand.getName() + "]");
        containerId = createCommand.exec().getId();

        DockerClientFactory.get().startContainerCmd(containerId).exec();

        try {
            await().atMost(timeout, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).until(new ContainerIsRunning(containerId));
        } catch (ConditionTimeoutException cte) {
            log.error(String.format("Container [" + createCommand.getName() + "] did not start within %d seconds.", timeout));

            try {
                LogContainerCmd logContainerCmd = DockerClientFactory.get().logContainerCmd(containerId);
                logContainerCmd.withStdOut().withStdErr();
                logContainerCmd.exec(new LogContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        log.error(item.toString());
                    }
                }).awaitCompletion();
            } catch (InterruptedException e) {
                log.error("Could not print container logs");
            }
        }

        log.debug("Container is up and running");
    }

    /**
     * @return the hostname of the container
     */
    public String getHostname() {
        String res = "";
        if (!getContainerId().isEmpty()) {
            res = DockerClientFactory.get().inspectContainerCmd(containerId).exec().getConfig().getHostName();
        }
        return res;
    }

    /**
     * @return the ID of the container.
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * @return the IP address of the container
     */
    public String getIpAddress() {
        if (ipAddress == null) {
            retrieveIpAddress();
        }
        return ipAddress;
    }

    private synchronized void retrieveIpAddress() {
        String res = "";
        if (!getContainerId().isEmpty()) {
            res = DockerContainersUtil.getIpAddress(getContainerId());
        }
        this.ipAddress = res;
    }

    /**
     * Builds container name following the naming convention
     *
     * @return container name
     */
    public String getName() {
        return ContainerName.get(this);
    }

    /**
     * Removes a container with force
     */
    public void remove() {
        try {
            if (DockerContainersUtil.getContainer(containerId) != null) {
                DockerClientFactory.get().removeContainerCmd(containerId).withForce().withRemoveVolumes(true).exec();
            }
            this.removed = true;
        } catch (Exception e) {
            log.error("Could not remove container " + dockerCommand().getName(), e);
        }
    }

    protected Boolean imageExists(String imageName, String registryTag) {
        List<Image> images = DockerClientFactory.get().listImagesCmd().exec();
        for (Image image : images) {
            for (String repoTag : image.getRepoTags()) {
                if (repoTag.equals(imageName + ":" + registryTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void pullImage(String imageName, String registryTag) {

        if (imageExists(imageName, registryTag)) {
            return;
        }

        log.debug("Image [" + imageName + ":" + registryTag + "] not found. Pulling...");

        final CompletableFuture<Void> result = new CompletableFuture<>();
        DockerClientFactory.get().pullImageCmd(imageName).withTag(registryTag).exec(new PullImageResultCallback() {

            @Override
            public void onNext(PullResponseItem item) {
                String status = item.getStatus();
                if (status == null) {
                    String msg = String.format("# Error pulling image from registry. Try executing the command below manually\ndocker pull %s:%s", imageName, registryTag);
                    result.completeExceptionally(new MinimesosException(msg));
                }
            }

            @Override
            public void onComplete() {
                super.onComplete();
                result.complete(null);
            }

        });

        try {
            result.get(IMAGE_PULL_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new MinimesosException(e.getCause().getMessage());
        } catch (InterruptedException | TimeoutException | RuntimeException e) {
            String msg = "Error pulling image or image not found in registry: " + imageName + ":" + registryTag;
            throw new MinimesosException(msg, e);
        }

        if (!imageExists(imageName, registryTag)) {
            throw new MinimesosException("Pulling of " + imageName + ":" + registryTag + " completed. However the image is not found");
        }
    }

    public void setCluster(MesosCluster cluster) {
        this.cluster = cluster;
    }

    public MesosCluster getCluster() {
        return cluster;
    }

    /**
     * @return if set, ID of the cluster the container belongs to
     */
    public String getClusterId() {
        return (cluster != null) ? cluster.getClusterId() : null;
    }

    protected String[] createEnvironment() {
        return envVars.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private class ContainerIsRunning implements Callable<Boolean> {

        private String containerId;

        public ContainerIsRunning(String containerId) {
            this.containerId = containerId;
        }

        @Override
        public Boolean call() throws Exception {
            List<Container> containers = DockerClientFactory.get().listContainersCmd().exec();
            for (Container container : containers) {
                if (container.getId().equals(containerId)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format(": %s-%s-%s", getRole(), getClusterId(), uuid);
    }

    public boolean isRemoved() {
        return removed;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractContainer that = (AbstractContainer) o;

        if (!StringUtils.equals(this.getClusterId(), that.getClusterId())) return false;

        if (!uuid.equals(that.uuid)) return false;
        return containerId.equals(that.containerId);
    }

    @Override
    public int hashCode() {
        int result = (cluster != null) ? cluster.hashCode() : 0;
        result = 31 * result + uuid.hashCode();
        result = 31 * result + containerId.hashCode();
        return result;
    }
}

