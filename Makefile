default: build
.PHONY: setup deps test build

setup:
	sudo route delete 172.17.0.0/16; sudo route -n add 172.17.0.0/16 $(shell docker-machine ip ${shell DOCKER_MACHINE_NAME})

deps:
	docker pull alpine:3.3
	docker pull jplock/zookeeper:3.4.6
	docker pull containersol/mesos-master:0.25.0-0.2.70.ubuntu1404
	docker pull containersol/mesos-agent:0.25.0-0.2.70.ubuntu1404
	docker pull mesosphere/marathon:v0.13.0
	docker pull tutum/hello-world

clean:
	./gradlew clean
	-docker rmi containersol/minimesos:latest
	-docker rmi containersol/mesos-hello-world-scheduler:latest
	-docker rmi containersol/mesos-hello-world-executor:latest

build:
	./gradlew build --info --stacktrace

build-no-tests:
	./gradlew build --info --stacktrace -x test

test:
	./gradlew test --info --stacktrace

