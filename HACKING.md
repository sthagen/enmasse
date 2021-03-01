# Developing EnMasse

## Build requirements

To build EnMasse, you need

   * [JDK](http://openjdk.java.net/) >= 11
   * [Apache Maven](https://maven.apache.org/) >= 3.5.4
   * [Docker](https://www.docker.com/)
   * [GNU Make](https://www.gnu.org/software/make/)
   * [Go](https://golang.org/) >= 1.13.0
   * [Go Yacc](golang.org/x/tools/cmd/goyacc)
   * [Ragel](http://www.colm.net/open-source/ragel/)

*Note*: On OSX, make sure you have [Coreutils](https://www.gnu.org/software/coreutils/) installed, e.g. `brew install coreutils`

The EnMasse java and node modules are built using maven. All docker images are built using make.

## Runtime requirements

To run EnMasse you need a Kubernetes cluster. Most EnMasse developers use [OKD](https://www.okd.io/)
for running tests on their machine.

## Checking out for Go

If you want to work with the Go parts of this repository, you will need to perform the
following steps.

### Create a Go workspace

Create a new directory and set the `GOPATH` environment variable:

    export GOPATH=/home/user/my-enmasse-gobase
    mkdir -p $GOPATH/src/github.com/enmasseproject

### Clone the git repository into this workspace

    cd $GOPATH/src/github.com/enmasseproject
    git clone https://github.com/enmasseproject/enmasse

## Building

### Pre-installation

*Note*: Make sure docker daemon is in running state.

#### Full build, run unit tests

    make

This can be run at the top level or within each module. You can also run the `build`, `test`, and `package` targets individually.

#### Building docker images

    make docker_build

#### Full build and pushing docker images to a registry

    export DOCKER_ORG=myorg
    export DOCKER_REGISTRY=quay.io
    export TAG=v1.0.3 # Optional: 'latest' by default

    docker login -u myuser -p mypassword $DOCKER_REGISTRY

    make buildpush

*Note*: If you are using OKD and `oc cluster up`, you can push images directly to the built-in registry
by setting `DOCKER_ORG=myproject` and `DOCKER_REGISTRY=172.30.1.1:5000` instead.

#### Full build and load images in a local [KIND](https://kind.sigs.k8s.io/) instance

    IMAGE_PULL_POLICY=IfNotPresent make buildpushkind

*Note*: Using the IfNotPresent policy prevents KIND from attempting to pull the images from the
external registries.

#### Build a single component and load image in a local [KIND](https://kind.sigs.k8s.io/) instance

    make -C <component> buildpushkind
    kubectl delete pod <component pod>

#### Deploying to a Kubernetes instance assuming already logged in with cluster-admin permissions

    kubectl create namespace enmasse-infra
    kubectl config set-context $(kubectl config current-context) --namespace=enmasse-infra
    
    kubectl apply -f templates/build/default/enmasse-latest/install/bundles/enmasse
    kubectl apply -f templates/build/default/enmasse-latest/install/components/example-plans
    kubectl apply -f templates/build/default/enmasse-latest/install/components/example-authservices

#### Deploying to an OKD instance assuming already logged in with cluster-admin permissions

    oc new-project enmasse-infra || oc project enmasse-infra
    oc apply -f templates/build/default/enmasse-latest/install/bundles/enmasse
    oc apply -f templates/build/default/enmasse-latest/install/components/example-plans
    oc apply -f templates/build/default/enmasse-latest/install/components/example-authservices


#### Running smoketests against a deployed instance

    make PROFILE=smoke systemtests

### Running full systemtest suite

By default the test suite tears down the EnMasse deployment and namespace after the test run.  To suppress this behaviour set environment variable `SKIP_UNINSTALL`.  This is important if relying on local built images pushed into the in-built registry.

#### Running the systemtests

    make systemtests

#### Run single system test

    make TESTCASE="shared.standard.QueueTest#testCreateDeleteQueue" systemtests

#### Systemtests management of enmasse's installation

Systemtests can manage the installation of enmasse before the actual test suite is run, this eases the testing of the various installation types.
Currently we can have enmasse installed by systemtests in two ways, using the bundles or using OLM. This can be instructed using the environment variable `INSTALL_TYPE` which
can take the values `BUNDLE` or `OLM`. By default the install type is `BUNDLE`
Then when using OLM installation enmasse can be installed into OLM default namespace, ie: `openshift-operators`, or into `enmasse-infra` namespace, this can be instructed too with
the environment variable `OLM_INSTALL_TYPE` that can take the values `DEFAULT` to installation into `openshift-operators` namespace or `SPECIFIC` to installation into `enmasse-infra`namespace. The olm installation type by default has the value `SPECIFIC`.

This functionalities can be used in our PR's too, in example to run the smoke profile in an ocp4 cluster (actually CRC) and installing via OLM you have to type the comment:
`@enmasse-ci run tests profile=smoke ocp4 OLM`

### Adding / Updating Go dependencies

This project currently uses go modules to vendor go sources. Change dependencies in the file `go.mod` and then run:

    go mod vendor
    go mod tidy
    git add --all vendor
    git add go.sum
    git commit

### Updating the Go generated code

Run the following script:

    hack/update-codegen.sh

Running `hack/verify-codegen.sh` checks whether the generated code is up to date.

At the moment updating the Console's generated code is separate:

    hack/run-console-codegen.sh

## Reference

This is a reference of the different make targets and options that can be set when building an
individual module:

#### Make targets

   * `build`        - build
   * `test`         - run tests
   * `package`      - create artifact bundle
   * `docker_build` - build docker image
   * `docker_tag`   - tag docker image
   * `docker_push`  - push docker image
   * `buildpush`    - build, test, package, docker build, docker_tag and docker_push
   * `systemtests`  - run systemtests

Some of these tasks can be configured using environment variables as listed below.

#### Debugging Java Code on OpenShift or Kubernetes

To enable debug mode for the Java based components, it's necessary to setup following environment variables:

   * `JAVA_DEBUG` - set to true to enable or false to disable
   * `JAVA_DEBUG_PORT` - 8787 by default and can be any value above 1000 if need to change it

Use this command to change environment variables values for the deployment

    $CMD set env deployments/<deployment-name> JAVA_DEBUG=true

Where $CMD is `oc` or `kubectl` command depends of the environment.

The following deployment names are available depending on their types and EnMasse configuration:

   * `address-space-controller`
   * `admin`
   * `keycloak-controller`
   * `standard-controller`
   * `service-broker`
   * `topic-forwarder`

For forwarding port from the remote pod to the local host invoke following command (it will lock terminal) and then
connect with development tool to the forwarded port on localhost

    $CMD port-forward $(oc get pods | grep <deployment-name> | awk '{print $1}') $JAVA_DEBUG_PORT:$JAVA_DEBUG_PORT

#### Go unit tests

Go unit tests output can be converted into xUnit compatible format by a tool named `go2xunit`. This is
being used by the build to have a combined test result of Java and Go parts.  You can enable the conversion process by setting the make variable `GO2XUNIT` to the go2xunit binary. In this case the build will execute `go test` and convert the results.

#### Environment variables

There are several environment variables that control the behavior of the build. Some of them are
only consumed by some tasks:

   * `KUBERNETES_API_URL`   - URL to Kubernetes master. Consumed by `systemtests` target.
   * `KUBERNETES_API_TOKEN` - Kubernetes API token. Consumed by `systemtests` target.
   * `KUBERNETES_NAMESPACE` - Kubernetes namespace for EnMasse. Consumed by `systemtests` targets.
   * `DOCKER_ORG`           - Docker organization for EnMasse images. Consumed by `build`, `package`, `docker*` targets. tasks. Defaults to `enmasse`.
   * `DOCKER_REGISTRY`      - Docker registry for EnMasse images. Consumed by `build`, `package`, `docker_tag` and `docker_push` targets. Defaults to `quay.io`.
   * `TAG`                  - Tag used as docker image tag in snapshots and in the generated templates. Consumed by `build`, `package`, `docker_tag` and `docker_push` targets.

## Debugging

### Remote Debugging

In order to remote debug an EnMasse component deployed to a pod within the cluster, you first need to enable the remote
debugging options of the runtime, and the forward port from the host to the target pod.  You then connect your IDE's
debugger to the host/port.

The instructions vary depending on whether the component is written in Java or NodeJS.  The precise steps vary by
developer tooling you use.  The below is just offered as a guide.

#### Java components

If you have a Java component running in a pod that you wish to debug, temporarily edit the deployment
(`oc edit deployment` etc.) and add the Java debug options to the standard `_JAVA_OPTIONS` environment variable to the
container.

```yaml
- env:
 - name: _JAVA_OPTIONS
   value: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

#### NodeJS components

If you have a NodeJS component running in a pod that you wish to debug, temporarily edit the deployment and add the
NodeJS debug option `--inspect` to a `_NODE_OPTIONS` environment variable to the container.  By default, NodeJS
will listen on port 9229.

```yaml
- env:
 - name: _NODE_OPTIONS
   value: --inspect
```

#### Port Forwarding

On OpenShift, you can then issue a `oc port-forward <pod> <LOCAL_PORT>:<REMOTE_PORT>` command to conveniently route
traffic to the pod's bound port.  Attach your IDE debugger the host/port.

# Releasing EnMasse

When releasing EnMasse, be sure to enable the `-Prelease` profile so that third-party license information
and source bundle is created.
