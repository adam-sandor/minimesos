package com.containersol.minimesos.config

class MesosMasterConfig extends MesosContainerConfig {

    public static final String MESOS_MASTER_IMAGE = "containersol/mesos-master"
    public static final int MESOS_MASTER_PORT = 5050

    String imageName = MESOS_MASTER_IMAGE
    String imageTag = MESOS_IMAGE_TAG

    static Builder builder() {
        return new Builder()
    }

    public static class Builder {
        private MesosMasterConfig config

        Builder() {
            this.config = new MesosMasterConfig()
        }

        public Builder imageName(String imageName) {
            config.imageName = imageName
            return this
        }

        public MesosMasterConfig build() {
            // TODO check for minimal config
            return config;
        }
    }

}
