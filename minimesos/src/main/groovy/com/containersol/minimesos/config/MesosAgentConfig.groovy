package com.containersol.minimesos.config

class MesosAgentConfig extends MesosContainerConfig {

    public static final String MESOS_AGENT_IMAGE = "containersol/mesos-agent"
    public static final int DEFAULT_MESOS_AGENT_PORT = 5051

    int portNumber = DEFAULT_MESOS_AGENT_PORT

    String imageName = MESOS_AGENT_IMAGE
    String imageTag = MESOS_IMAGE_TAG

    AgentResourcesConfig resources = new AgentResourcesConfig()

    def resources(@DelegatesTo(AgentResourcesConfig) Closure cl) {
        delegateTo(resources, cl)
    }

    static Builder builder() {
        return new Builder()
    }

    public static class Builder {
        private MesosAgentConfig config

        Builder() {
            this.config = new MesosAgentConfig()
        }

        public Builder imageName(String imageName) {
            config.imageName = imageName
            return this
        }

        public MesosAgentConfig build() {
            // TODO check for minimal config
            return config;
        }
    }
}
