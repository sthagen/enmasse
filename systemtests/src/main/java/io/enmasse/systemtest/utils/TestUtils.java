/*
 * Copyright 2016-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.systemtest.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.enmasse.systemtest.operator.OLMOperatorManager;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;

import com.google.common.io.BaseEncoding;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.BrokerState;
import io.enmasse.address.model.BrokerStatus;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.systemtest.Endpoint;
import io.enmasse.systemtest.Environment;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.logs.GlobalLogCollector;
import io.enmasse.systemtest.model.addressspace.AddressSpaceType;
import io.enmasse.systemtest.platform.Kubernetes;
import io.enmasse.systemtest.platform.apps.SystemtestsKubernetesApps;
import io.enmasse.systemtest.time.SystemtestsOperation;
import io.enmasse.systemtest.time.TimeMeasuringSystem;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.time.WaitPhase;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;

public class TestUtils {

    public interface TimeoutHandler<X extends Throwable> {
        void timeout() throws X;
    }

    private static final Random R = new Random();
    private static Logger log = CustomLogger.getLogger();

    public static void waitForNReplicas(int expectedReplicas, String namespace, Map<String, String> labelSelector, TimeoutBudget budget) throws InterruptedException {
        waitForNReplicas(expectedReplicas, namespace, labelSelector, Collections.emptyMap(), budget);
    }

    /**
     * wait for expected count of Destination replicas in address space
     */
    public static void waitForNBrokerReplicas(AddressSpace addressSpace, int expectedReplicas, boolean readyRequired,
            Address destination, TimeoutBudget budget, long checkInterval) throws Exception {
        Address address = Kubernetes.getInstance().getAddressClient(addressSpace.getMetadata().getNamespace()).withName(destination.getMetadata().getName()).get();
        Map<String, String> labels = new HashMap<>();
        labels.put("role", "broker");
        labels.put("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace));

        for (BrokerStatus brokerStatus : address.getStatus().getBrokerStatuses()) {
            if (brokerStatus.getState().equals(BrokerState.Active)) {
                waitForNReplicas(
                        expectedReplicas,
                        readyRequired,
                        Kubernetes.getInstance().getInfraNamespace(),
                        labels,
                        Collections.singletonMap("cluster_id", brokerStatus.getClusterId()),
                        budget,
                        checkInterval);
            }
        }
    }

    public static void waitForNBrokerReplicas(AddressSpace addressSpace, int expectedReplicas, Address destination, TimeoutBudget budget) throws Exception {
        waitForNBrokerReplicas(addressSpace, expectedReplicas, true, destination, budget, 5000);
    }

    /**
     * Wait for expected count of replicas
     *
     * @param expectedReplicas count of expected replicas
     * @param labelSelector labels on scaled pod
     * @param annotationSelector annotations on sclaed pod
     * @param budget timeout budget - throws Exception when timeout is reached
     * @throws InterruptedException
     */
    public static void waitForNReplicas(int expectedReplicas, boolean readyRequired, String namespace,
            Map<String, String> labelSelector, Map<String, String> annotationSelector, TimeoutBudget budget, long checkInterval) throws InterruptedException {
        int actualReplicas;
        do {
            final List<Pod> pods;
            if (annotationSelector.isEmpty()) {
                pods = Kubernetes.getInstance().listPods(namespace, labelSelector);
            } else {
                pods = Kubernetes.getInstance().listPods(namespace, labelSelector, annotationSelector);
            }
            if (!readyRequired) {
                actualReplicas = pods.size();
            } else {
                actualReplicas = numReady(pods);
            }
            log.info("Have {} out of {} replicas. Expecting={}, ReadyRequired={}", actualReplicas, pods.size(), expectedReplicas, readyRequired);

            if (budget.timeoutExpired()) {
                // our time budged expired ... throw exception
                String message = String.format("Only '%s' out of '%s' in state 'Running' before timeout %s", actualReplicas, expectedReplicas,
                        pods.stream().map(pod -> pod.getMetadata().getName()).collect(Collectors.joining(",")));
                throw new RuntimeException(message);
            }
            // try again next cycle
            Thread.sleep(checkInterval);
        } while (actualReplicas != expectedReplicas);
        // finished successfully
    }

    public static void waitForNReplicas(int expectedReplicas, String namespace, Map<String, String> labelSelector, Map<String, String> annotationSelector, TimeoutBudget budget,
            long checkInterval) throws InterruptedException {
        waitForNReplicas(expectedReplicas, true, namespace, labelSelector, annotationSelector, budget, checkInterval);
    }

    public static void waitForNReplicas(int expectedReplicas, String namespace, Map<String, String> labelSelector, Map<String, String> annotationSelector, TimeoutBudget budget)
            throws InterruptedException {
        waitForNReplicas(expectedReplicas, namespace, labelSelector, annotationSelector, budget, 5000);
    }

    /**
     * Check ready status of all pods in list
     *
     * @param pods list of pods
     * @return
     */
    private static int numReady(List<Pod> pods) {
        return (int) pods.stream().filter(pod -> isPodReady(pod, true)).count();
    }

    public static boolean isPodReady(final Pod pod, final boolean doLog) {

        if (!"Running".equals(pod.getStatus().getPhase())) {
            if (doLog) {
                log.info("POD {} in status : {}", pod.getMetadata().getName(), pod.getStatus().getPhase());
            }
            return false;
        }

        var nonReadyContainers = pod.getStatus().getContainerStatuses().stream()
                .filter(cs -> !Boolean.TRUE.equals(cs.getReady()))
                .map(ContainerStatus::getName)
                .collect(Collectors.toList());

        if (!nonReadyContainers.isEmpty()) {
            if (doLog) {
                log.info("POD {} non-ready containers: [{}]", pod.getMetadata().getName(), String.join(", ", nonReadyContainers));
            }
            return false;
        }

        return true;
    }

    /**
     * Wait for expected count of pods within AddressSpace
     *
     * @param client client for manipulation with kubernetes cluster
     * @param numExpected count of expected pods
     * @param budget timeout budget - this method throws Exception when timeout is reached
     * @throws InterruptedException
     */
    public static void waitForExpectedReadyPods(Kubernetes client, String namespace, int numExpected, TimeoutBudget budget) throws InterruptedException {
        boolean shouldRetry;
        do {
            log.info("Waiting for expected ready pods: {}", numExpected);
            shouldRetry = false;
            List<Pod> pods = listReadyPods(client, namespace);
            while (budget.timeLeft() >= 0 && pods.size() != numExpected) {
                Thread.sleep(2000);
                pods = listReadyPods(client, namespace);
                log.info("Got {} pods, expected: {}", pods.size(), numExpected);
            }
            if (pods.size() != numExpected) {
                throw new IllegalStateException("Unable to find " + numExpected + " pods. Found : " + printPods(pods));
            }
            for (Pod pod : pods) {
                try {
                    client.waitUntilPodIsReady(pod);
                } catch (NullPointerException | IllegalArgumentException e) {
                    // TODO: remove NPE guard once upgrade to Fabric8 kubernetes-client 4.6.0 or beyond is complete.
                    // (kubernetes-client 450b94745b68403293a55956be2aa7ec483c0a6c)
                    log.warn("Failed to await pod %s", pod, e);
                    shouldRetry = true;
                    break;
                }
            }
        } while (shouldRetry);
    }

    /**
     * Print name of all pods in list
     *
     * @param pods list of pods that should be printed
     * @return
     */
    public static String printPods(List<Pod> pods) {
        return pods.stream()
                .map(pod -> "{" + pod.getMetadata().getName() + ", " + pod.getStatus().getPhase() + "}")
                .collect(Collectors.joining(","));
    }

    /**
     * Get list of all running pods from specific AddressSpace
     *
     * @param kubernetes client for manipulation with kubernetes cluster
     * @param addressSpace
     * @return
     */
    public static List<Pod> listRunningPods(Kubernetes kubernetes, AddressSpace addressSpace) throws Exception {
        return kubernetes.listPods(Collections.singletonMap("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace))).stream()
                .filter(pod -> pod.getStatus().getPhase().equals("Running"))
                .collect(Collectors.toList());
    }

    /**
     * Get list of all ready pods
     *
     * @param kubernetes client for manipulation with kubernetes cluster
     * @return
     */
    public static List<Pod> listReadyPods(Kubernetes kubernetes, String namespace) {
        return kubernetes.listPods(namespace).stream()
                .filter(pod -> pod.getStatus().getContainerStatuses().stream().allMatch(ContainerStatus::getReady))
                .collect(Collectors.toList());
    }

    public static List<Pod> listBrokerPods(Kubernetes kubernetes, AddressSpace addressSpace) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("role", "broker");
        labels.put("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace));
        return kubernetes.listPods(labels);
    }

    public static List<Pod> listRouterPods(Kubernetes kubernetes, AddressSpace addressSpace) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("capability", "router");
        labels.put("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace));
        return kubernetes.listPods(labels);
    }

    public static List<Pod> listAdminConsolePods(Kubernetes kubernetes, AddressSpace addressSpace) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (addressSpace.getSpec().getType().equals(AddressSpaceType.STANDARD.toString())) {
            labels.put("name", "admin");
        } else {
            labels.put("name", "agent");
        }
        labels.put("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace));
        return kubernetes.listPods(labels);
    }

    public static List<PersistentVolumeClaim> listPersistentVolumeClaims(Kubernetes kubernetes, AddressSpace addressSpace) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("infraUuid", AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace));
        return kubernetes.listPersistentVolumeClaims(labels);
    }

    /**
     * Generate message body with prefix
     */
    public static List<String> generateMessages(String prefix, int numMessages) {
        return IntStream.range(0, numMessages).mapToObj(i -> prefix + i).collect(Collectors.toList());
    }

    /**
     * Generate message body with "testmessage" content and without prefix
     */
    public static List<String> generateMessages(int numMessages) {
        return generateMessages("testmessage", numMessages);
    }

    /**
     * Check if endpoint is accessible
     */
    public static boolean resolvable(Endpoint endpoint) {
        return waitUntilCondition(() -> {
            try {
                InetAddress[] addresses = Inet4Address.getAllByName(endpoint.getHost());
                return addresses.length > 0;
            } catch (UnknownHostException ignore) {
            }
            return false;
        }, Duration.ofSeconds(10), Duration.ofSeconds(1));
    }

    /**
     * Wait until Namespace will be removed
     *
     * @param kubernetes client for manipulation with kubernetes cluster
     * @param namespace project/namespace to remove
     */
    public static void waitForNamespaceDeleted(Kubernetes kubernetes, String namespace) throws Exception {
        waitUntilCondition(
                () -> !kubernetes.listNamespaces().contains(namespace),
                Duration.ofMinutes(10), Duration.ofSeconds(1),
                () -> {
                    throw new TimeoutException("Timed out waiting for namespace " + namespace + " to disappear");
                });
    }

    /**
     * Repeat command n-times
     *
     * @param retry count of remaining retries
     * @param fn request function
     * @return The value from the first successful call to the callable
     */
    public static <T> T runUntilPass(int retry, Callable<T> fn) throws InterruptedException {
        for (int i = 0; i < retry; i++) {
            try {
                log.debug("Running command, attempt: {}", i);
                return fn.call();
            } catch (Exception | AssertionError ex) {
                log.warn("Command failed", ex);
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException(String.format("Command wasn't pass in %s attempts", retry));
    }

    /**
     * Repeat command n-times.
     *
     * @param retries Number of retries.
     * @param callable Code to execute.
     */
    public static void runUntilPass(int retries, ThrowingCallable callable) throws InterruptedException {
        runUntilPass(retries, () -> {
            callable.call();
            return null;
        });
    }

    public static void deleteAddressSpaceCreatedBySC(Kubernetes kubernetes, AddressSpace addressSpace, GlobalLogCollector logCollector) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.DELETE_ADDRESS_SPACE);
        logCollector.collectEvents();
        logCollector.collectLogsTerminatedPods();
        logCollector.collectRouterState("deleteAddressSpaceCreatedBySC");
        kubernetes.deleteNamespace(addressSpace.getMetadata().getNamespace());
        waitForNamespaceDeleted(kubernetes, addressSpace.getMetadata().getNamespace());
        AddressSpaceUtils.waitForAddressSpaceDeleted(addressSpace);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    public static RemoteWebDriver getFirefoxDriver() throws Exception {
        Endpoint endpoint = SystemtestsKubernetesApps.getFirefoxSeleniumAppEndpoint(Kubernetes.getInstance());
        FirefoxOptions options = new FirefoxOptions();
        // https://github.com/mozilla/geckodriver/issues/330 enable the emission of console.info(), warn()
        // etc
        // to stdout of the browser process. Works around the fact that Firefox logs are not available
        // through
        // WebDriver.manage().logs().
        options.addPreference("devtools.console.stdout.content", true);
        options.addPreference("browser.tabs.unloadOnLowMemory", false);
        return getRemoteDriver(endpoint.getHost(), endpoint.getPort(), options);
    }

    public static RemoteWebDriver getChromeDriver() throws Exception {
        Endpoint endpoint = SystemtestsKubernetesApps.getChromeSeleniumAppEndpoint(Kubernetes.getInstance());
        ChromeOptions options = new ChromeOptions();
        options.setAcceptInsecureCerts(true);
        options.addArguments("test-type", "--headless", "--no-sandbox", "--disable-dev-shm-usage", "--disable-extensions");
        return getRemoteDriver(endpoint.getHost(), endpoint.getPort(), options);
    }

    private static RemoteWebDriver getRemoteDriver(String host, int port, Capabilities options) throws Exception {
        int attempts = 60;
        URL hubUrl = new URL(String.format("http://%s:%s/wd/hub", host, port));
        for (int i = 0; i < attempts; i++) {
            try {
                testReachable(hubUrl);
                return new RemoteWebDriver(hubUrl, options);
            } catch (IOException e) {
                if (i == attempts - 1) {
                    log.warn("Cannot connect to hub", e);
                } else {
                    log.warn("Cannot connect to hub: {}", e.getMessage());
                }
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Selenium webdriver cannot connect to selenium container");
    }

    private static void testReachable(URL url) throws IOException {
        log.info("Trying to connect to {}", url.toString());
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = ((HttpURLConnection) url.openConnection());
            urlConnection.getContent();
            log.info("Client is able to connect to the selenium hub");
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public static void waitUntilCondition(Callable<String> fn, String expected, TimeoutBudget budget) throws Exception {
        String actual = "Too small time out budget!!";
        while (!budget.timeoutExpired()) {
            actual = fn.call();
            log.debug(actual);
            if (actual.contains(expected)) {
                return;
            }
            log.debug("next iteration, remaining time: {}", budget.timeLeft());
            Thread.sleep(2000);
        }
        throw new IllegalStateException(String.format("Expected: '%s' in content, but was: '%s'", expected, actual));
    }

    public static void waitUntilCondition(final String forWhat, final Predicate<WaitPhase> condition, final TimeoutBudget budget) {

        Objects.requireNonNull(condition);
        Objects.requireNonNull(budget);

        log.info("Waiting {} ms for - {}", budget.timeLeft(), forWhat);

        waitUntilCondition(

                () -> condition.test(WaitPhase.LOOP),
                budget.remaining(), Duration.ofSeconds(5),

                () -> {
                    // try once more
                    if (condition.test(WaitPhase.LAST_TRY)) {
                        log.info("Successfully wait for: {} , it passed on last try", forWhat);
                        return;
                    }

                    throw new IllegalStateException("Failed to wait for: " + forWhat);
                });

        log.info("Successfully waited for: {}, it took {} ms", forWhat, budget.timeSpent());

    }

    /**
     * Wait for a condition, fail otherwise.
     *
     * @param condition The condition to check, returning {@code true} means success.
     * @param timeout The maximum time to wait for
     * @param delay The delay between checks.
     * @param timeoutMessageSupplier The supplier of a timeout message.
     * @throws AssertionFailedError In case the timeout expired
     */
    public static void waitUntilConditionOrFail(final BooleanSupplier condition, final Duration timeout, final Duration delay, final Supplier<String> timeoutMessageSupplier) {

        Objects.requireNonNull(timeoutMessageSupplier);

        waitUntilConditionOrThrow(condition, timeout, delay, () -> new AssertionFailedError(timeoutMessageSupplier.get()));

    }

    /**
     * Wait for a condition, throw exception otherwise.
     *
     * @param condition The condition to check, returning {@code true} means success.
     * @param timeout The maximum time to wait for
     * @param delay The delay between checks.
     * @param exceptionSupplier The supplier of the exception to throw.
     * @throws AssertionFailedError In case the timeout expired
     */
    public static <X extends Throwable> void waitUntilConditionOrThrow(final BooleanSupplier condition, final Duration timeout, final Duration delay,
            final Supplier<X> exceptionSupplier) throws X {

        Objects.requireNonNull(exceptionSupplier);

        waitUntilCondition(condition, timeout, delay, () -> {
            throw exceptionSupplier.get();
        });

    }

    /**
     * Wait for a condition, call handler otherwise.
     *
     * @param condition The condition to check, returning {@code true} means success.
     * @param delay The delay between checks.
     * @param timeoutHandler The handler to call in case of the timeout.
     * @param <X> The type of exception thrown by the timeout handler.
     * @throws AssertionFailedError In case the timeout expired
     */
    public static <X extends Throwable> void waitUntilCondition(final BooleanSupplier condition, final Duration timeout, final Duration delay,
            final TimeoutHandler<X> timeoutHandler) throws X {

        Objects.requireNonNull(timeoutHandler);

        if (!waitUntilCondition(condition, timeout, delay)) {
            timeoutHandler.timeout();
        }

    }

    /**
     * Wait for condition, return result.
     * <p>
     * This will check will put a priority on checking the condition, and only wait, when there is
     * remaining time budget left.
     *
     * @param condition The condition to check, returning {@code true} means success.
     * @param delay The delay between checks.
     * @return {@code true} if the condition was met, {@code false otherwise}.
     */
    public static boolean waitUntilCondition(final BooleanSupplier condition, final Duration timeout, final Duration delay) {

        Objects.requireNonNull(condition);
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(delay);

        final Instant deadline = Instant.now().plus(timeout);

        while (true) {

            // test first

            if (condition.getAsBoolean()) {
                return true;
            }

            // if the timeout has expired ... stop

            final Duration remaining = Duration.between(deadline, Instant.now());
            if (!remaining.isNegative()) {
                return false;
            }

            // otherwise sleep

            log.debug("next iteration, remaining time: {}", remaining);
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

    }

    public static void waitForChangedResourceVersion(final TimeoutBudget budget, final String namespace, final String name, final String currentResourceVersion) throws Exception {
        waitForChangedResourceVersion(budget, currentResourceVersion,
                () -> Kubernetes.getInstance().getAddressSpaceClient(namespace).withName(name).get().getMetadata().getResourceVersion());
    }

    public static void waitForChangedResourceVersion(final TimeoutBudget budget, final String currentResourceVersion, final ThrowingSupplier<String> provideNewResourceVersion)
            throws Exception {
        Objects.requireNonNull(currentResourceVersion, "'currentResourceVersion' must not be null");

        waitUntilCondition("Resource version to change away from: " + currentResourceVersion, phase -> {
            try {
                final String newVersion = provideNewResourceVersion.get();
                return !currentResourceVersion.equals(newVersion);
            } catch (RuntimeException e) {
                throw e; // don't pollute the cause chain
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, budget);
    }

    public static String getGlobalConsoleRoute() {
        return Kubernetes.getInstance().getConsoleServiceClient().withName("console").get().getStatus().getUrl();

    }

    /**
     * Return a number of random characters in the range of {@code 0-9a-f}.
     *
     * @param length the number of characters to return
     * @return The random string, never {@code null}.
     */
    public static String randomCharacters(int length) {
        var b = new byte[(int) Math.ceil(length / 2.0)];
        R.nextBytes(b);
        return BaseEncoding.base16().encode(b).substring(length % 2);
    }

    public static void waitUntilDeployed(String namespace) {
        waitUntilDeployed(namespace, Collections.emptyList(), Collections.singletonMap("app", "enmasse"));
    }

    /**
     * Waith until all deployments, replicasets, statefulsets are ready
     *
     * @param namespace    namespace where you want to check resource ready
     * @param expectedPods optional list if you want to be sure all pods from this list are deployed
     */
    public static void waitUntilDeployed(String namespace, List<String> expectedPods, Map labels) {
        log.info("-------------------------------------------------------------");
        TestUtils.waitUntilCondition("All Deployments, StatefulSets, ReplicaSets and Pods are ready", waitPhase -> {
            List<Deployment> deployments = Kubernetes.getInstance().listDeployments(namespace, labels);
            for (Deployment deployment : deployments) {
                if (!Objects.equals(deployment.getStatus().getReplicas(), deployment.getStatus().getReadyReplicas())) {
                    log.info("Deployment {} has not all replicas ready", deployment.getMetadata().getName());
                    return false;
                }
            }
            log.info("All current Deployments are ready");
            List<StatefulSet> statefulSets = Kubernetes.getInstance().listStatefulSets(namespace, labels);
            for (StatefulSet statefulSet : statefulSets) {
                if (!Objects.equals(statefulSet.getStatus().getReplicas(), statefulSet.getStatus().getReadyReplicas())) {
                    log.info("StatefulSet {} has not all replicas ready", statefulSet.getMetadata().getName());
                    return false;
                }
            }
            log.info("All current StatefulSets are ready");
            List<ReplicaSet> replicaSets = Kubernetes.getInstance().listReplicaSets(namespace, labels);
            for (ReplicaSet replicaSet : replicaSets) {
                if (replicaSet.getSpec().getReplicas() > 0 && !Objects.equals(replicaSet.getStatus().getReplicas(), replicaSet.getStatus().getReadyReplicas())) {
                    log.info("ReplicaSet {} has not all replicas ready", replicaSet.getMetadata().getName());
                    return false;
                }
            }
            log.info("All current ReplicaSets are ready");
            List<Pod> pods = Kubernetes.getInstance().listPods(namespace, labels);
            for (String expectedPod : expectedPods) {
                if (pods.stream().noneMatch(pod -> pod.getMetadata().getName().contains(expectedPod))) {
                    log.info("Pod {} is still not deployed", expectedPod);
                    return false;
                }
            }
            if (expectedPods.size() > 0) {
                log.info("All expected Pods are deployed");
            }
            if (pods.size() > 0) {
                for (Pod pod : pods) {
                    List<ContainerStatus> initContainers = pod.getStatus().getInitContainerStatuses();
                    for (ContainerStatus s : initContainers) {
                        if (!s.getReady()) {
                            log.info("Pod {} is in ready state, init container is not in ready state", pod.getMetadata().getName());
                            return false;
                        }
                    }
                    List<ContainerStatus> containers = pod.getStatus().getContainerStatuses();
                    for (ContainerStatus s : containers) {
                        if (!s.getReady()) {
                            log.info("Pod {} is in ready state, container {} is not in ready state", pod.getMetadata().getName(), s.getName());
                            return false;
                        }
                    }
                    log.info("Pod {} is in ready state", pod.getMetadata().getName());
                }
                return true;
            }
            return false;
        }, new TimeoutBudget(10, TimeUnit.MINUTES));
        log.info("-------------------------------------------------------------");
    }

    public static void waitForConsoleRollingUpdate(String namespace) throws Exception {
        TestUtils.waitUntilCondition("Wait for console rolling update to complete", waitPhase -> {
            List<Pod> pods = Kubernetes.getInstance().listPods(namespace);
            pods.removeIf(pod -> !pod.getSpec().getContainers().get(0).getName().equals("console-proxy"));
            return pods.size() == 1;
        }, new TimeoutBudget(10, TimeUnit.MINUTES));
    }

    public static void waitForPodReady(String name, String namespace) throws Exception {
        TestUtils.waitUntilCondition(String.format("Pod is ready %s", name), waitPhase -> {
            try {
                Pod pod = Kubernetes.getInstance().listPods(namespace).stream().filter(p -> p.getMetadata().getName().contains(name)).findFirst().get();
                return TestUtils.isPodReady(pod, true);
            } catch (Exception ex) {
                return false;
            }
        }, new TimeoutBudget(10, TimeUnit.MINUTES));
    }

    public static void waitForInstallPlanPresent(String namespace) {
        TestUtils.waitUntilCondition("Wait for install plan to be present", waitPhase -> {
            try {
                ArrayList installPlans = OLMOperatorManager.getInstance().getInstallPlanName(namespace);
                return (!installPlans.isEmpty());
            } catch (Exception ex) {
                return false;
            }
        }, new TimeoutBudget(10, TimeUnit.MINUTES));
    }

    public static void waitForSchemaInSync(String addressSpacePlan) throws Exception {
        TestUtils.waitUntilCondition(String.format("Address space plan %s is applied", addressSpacePlan),
                waitPhase -> Kubernetes.getInstance().getSchemaClient().inAnyNamespace().list().getItems().stream()
                        .anyMatch(schema -> schema.getSpec().getPlans().stream()
                                .anyMatch(plan -> plan.getName().contains(addressSpacePlan))),
                new TimeoutBudget(5, TimeUnit.MINUTES));
    }

    public static void waitForRoutersInSync(AddressSpace addressSpace) throws Exception {
        String appliedConfig = addressSpace.getAnnotation(AnnotationKeys.APPLIED_CONFIGURATION);
        TestUtils.waitUntilCondition(String.format("Router configuration in sync for %s/%s", addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName()),
                waitPhase -> {
                    int inSync = 0;
                    List<Pod> routerPods = listRouterPods(Kubernetes.getInstance(), addressSpace);
                    for (Pod pod : routerPods) {
                        if (appliedConfig.equals(pod.getMetadata().getAnnotations().get(AnnotationKeys.APPLIED_CONFIGURATION))) {
                            inSync++;
                        }
                    }
                    return inSync == routerPods.size();
                }, new TimeoutBudget(10, TimeUnit.MINUTES));
    }

    public static Path getFailedTestLogsPath(ExtensionContext extensionContext) {
        return getLogsPath(extensionContext, "failed_test_logs");
    }

    public static Path getPerformanceTestLogsPath(ExtensionContext extensionContext) {
        return getLogsPath(extensionContext, "performance_test");
    }

    public static Path getScaleTestLogsPath(ExtensionContext extensionContext) {
        return getLogsPath(extensionContext, "scale_test");
    }

    public static Path getUpgradeTestLogsPath(ExtensionContext extensionContext) {
        return getLogsPath(extensionContext, "upgrade_test");
    }

    public static Path getLogsPath(ExtensionContext extensionContext, String rootFolder) {
        String testMethod = extensionContext.getDisplayName();
        Class<?> testClass = extensionContext.getRequiredTestClass();
        Path path = Environment.getInstance().testLogDir().resolve(Paths.get(rootFolder, testClass.getName()));
        if (testMethod != null) {
            path = path.resolve(testMethod);
        }
        return path;
    }

    /**
     * Encode an X509 certificate into PEM format.
     *
     * @param certificates The certificates to encode.
     * @return the PEM encoded certificates, or {@code null} if the input was {@code null}.
     */
    public static String toPem(final X509Certificate... certificates) {

        if (certificates == null) {
            return null;
        }

        final StringWriter sw = new StringWriter();

        try (JcaPEMWriter pw = new JcaPEMWriter(sw)) {
            for (X509Certificate certificate : certificates) {
                pw.writeObject(certificate);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sw.toString();

    }

}
