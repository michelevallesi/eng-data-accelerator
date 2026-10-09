# Engineering Data Accelerator — walking-skeleton targets.
# See docs/backlog/increment-01-walking-skeleton.md story S00.

GIT_SHA := $(shell git rev-parse --short HEAD)
ifeq ($(strip $(GIT_SHA)),)
GIT_SHA := dev
endif
JAVA_IMAGE ?= docker.io/library/maven:3.9.9-eclipse-temurin-21
CONTAINER  ?= podman
COMPOSE    ?= $(CONTAINER) compose

# Maven runs inside a container so no host JDK 21 is required.
# Override with `make verify MVN=./mvnw` once JDK 21 is installed locally.
# The Podman socket is mounted so Testcontainers inside the Maven container can
# start sibling containers; TESTCONTAINERS_HOST_OVERRIDE points connection URLs
# at the Podman VM instead of the Maven container's own loopback.
MVN ?= $(CONTAINER) run --rm \
       -v "$(CURDIR)":/w -w /w \
       --mount type=bind,source=/run/podman/podman.sock,target=/var/run/docker.sock \
       -e MAVEN_OPTS=-Dmaven.repo.local=/w/.m2/repository \
       -e DOCKER_HOST=unix:///var/run/docker.sock \
       -e TESTCONTAINERS_HOST_OVERRIDE=host.containers.internal \
       -e TESTCONTAINERS_RYUK_DISABLED=true \
       $(JAVA_IMAGE) mvn

SERVICES   := resolver polarion-adapter teamcenter-adapter projection-service link-index-service query-service
SIMULATORS := polarion-simulator teamcenter-simulator

.PHONY: help verify up down images e2e rebuild-graph

help:
	@echo "Targets:"
	@echo "  verify         Build and run unit tests (mvn verify)."
	@echo "  up             Start Kafka, Fuseki and PostgreSQL via docker-compose."
	@echo "  down           Stop and remove the local stack (keeps named volumes with -v off)."
	@echo "  images         Build an OCI image per service, tagged with the git SHA."
	@echo "  e2e            Run the end-to-end scenario (story S11, not yet implemented)."
	@echo "  rebuild-graph  Replay the event log into Fuseki (story S06, not yet implemented)."

verify:
	$(MVN) -B -ntp -T 1C verify

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

images: verify $(addprefix image-,$(SERVICES)) $(addprefix image-sim-,$(SIMULATORS))

image-%:
	$(CONTAINER) build -t eda-$*:$(GIT_SHA) -t eda-$*:latest --build-arg GIT_SHA=$(GIT_SHA) services/$*

image-sim-%:
	$(CONTAINER) build -t eda-$*:$(GIT_SHA) -t eda-$*:latest --build-arg GIT_SHA=$(GIT_SHA) simulators/$*

e2e:
	@echo "e2e: not yet implemented (story S11)."
	@exit 1

rebuild-graph:
	@echo "rebuild-graph: not yet implemented (story S06)."
	@exit 1
