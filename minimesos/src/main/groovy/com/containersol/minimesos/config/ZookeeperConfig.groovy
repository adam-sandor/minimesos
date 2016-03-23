package com.containersol.minimesos.config

public class ZooKeeperConfig extends GroovyBlock implements ContainerConfig {

    public static final String MESOS_LOCAL_IMAGE = "jplock/zookeeper"
    public static final String ZOOKEEPER_IMAGE_TAG = "3.4.6"

    String imageName = MESOS_LOCAL_IMAGE
    String imageTag = ZOOKEEPER_IMAGE_TAG

    static Builder builder() {
        return new Builder()
    }

    public static class Builder {
        private ZooKeeperConfig config

        Builder() {
            this.config = new ZooKeeperConfig()
        }

        public Builder imageName(String imageName) {
            config.imageName = imageName
            return this
        }

        public ZooKeeperConfig build() {
            // TODO check for minimal config
            return config;
        }
    }

}
