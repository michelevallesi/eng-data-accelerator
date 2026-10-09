# Developer setup

This file collects environment-specific setup notes that do not belong in `CLAUDE.md`
(which is platform-agnostic project context).

## Container runtime

The project uses a container runtime with a Compose provider for `make up`, `make
images` and the containerised Maven used by `make verify`. Any of the following works:

- **Docker Desktop** on Windows, macOS or Linux.
- **Podman** 5.x or 6.x with `podman compose` delegating to a `docker-compose`
  binary on `PATH`. On Windows this is typically Podman Desktop plus the
  `docker-compose.exe` shipped under `%LocalAppData%\Microsoft\WindowsApps\`.
- **Rancher Desktop** with the `dockerd (moby)` engine.

Override which binary the Makefile calls:

```
make verify CONTAINER=docker COMPOSE="docker compose"
```

## Local JDK (optional)

The Makefile defaults to running Maven inside a pinned
`maven:3.9.9-eclipse-temurin-21` container, so a host JDK is not strictly required.
If you prefer a local build (faster incremental loops, better IDE integration):

1. Install **Eclipse Temurin JDK 21** (the maven-enforcer plugin fails the build on
   anything else).
2. Install Maven 3.9+ or use the Maven Wrapper when it is added (not yet in S00).
3. Run `make verify MVN=mvn` (or `MVN=./mvnw` once the wrapper exists).

## Podman on Windows (WSL) — cgroup fix

Symptom: `podman run` or `podman compose up` fails with

```
crun: controller `pids` is not available under
/sys/fs/cgroup/non-systemd/machine.slice/...
```

Cause: the WSL kernel inside the Podman VM does not expose the `pids` cgroup v2
controller, which `crun` requires by default under the systemd cgroup manager.

Fix once per machine:

```
podman machine ssh
sudo tee /etc/containers/containers.conf <<'CONF'
[containers]
cgroups = "disabled"

[engine]
cgroup_manager = "cgroupfs"
CONF
exit
podman machine stop
podman machine start
```

After the restart `podman info` reports `cgroupManager: cgroupfs` and
`podman run --rm docker.io/library/hello-world` succeeds without extra flags.

## Verifying the stack

```
make verify          # all 14 modules compile
make up              # kafka, fuseki, postgres all become healthy
podman ps            # three containers Up (healthy)
```

To tear down and reclaim disk:

```
make down            # keeps named volumes
podman volume prune  # drops fuseki-data and postgres-data when you want a clean slate
```
