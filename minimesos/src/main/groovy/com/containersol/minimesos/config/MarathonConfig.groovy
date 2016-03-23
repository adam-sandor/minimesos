package com.containersol.minimesos.config;

public class MarathonConfig extends GroovyBlock implements ContainerConfig {

    public static final String MARATHON_IMAGE = "mesosphere/marathon"
    public static final String MARATHON_IMAGE_TAG = "v0.15.3"
    public static final int MARATHON_PORT = 8080;

    String imageName = MARATHON_IMAGE
    String imageTag = MARATHON_IMAGE_TAG

    static Builder builder() {
        return new Builder()
    }

    public static class Builder {
        private MarathonConfig config

        Builder() {
            this.config = new MarathonConfig()
        }

        public Builder imageName(String imageName) {
            config.imageName = imageName
            return this
        }

        public MarathonConfig build() {
            // TODO check for minimal config
            return config;
        }
    }

}
