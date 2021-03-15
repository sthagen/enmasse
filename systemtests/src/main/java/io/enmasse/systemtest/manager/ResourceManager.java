/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.systemtest.manager;

import static io.enmasse.systemtest.time.TimeoutBudget.ofDuration;
import static java.time.Duration.ofMinutes;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.admin.model.v1.*;
import io.enmasse.config.AnnotationKeys;
import io.enmasse.systemtest.Environment;
import io.enmasse.systemtest.UserCredentials;
import io.enmasse.systemtest.amqp.AmqpClientFactory;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.logs.GlobalLogCollector;
import io.enmasse.systemtest.platform.Kubernetes;
import io.enmasse.systemtest.time.SystemtestsOperation;
import io.enmasse.systemtest.time.TimeMeasuringSystem;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.enmasse.systemtest.utils.UserUtils;
import io.enmasse.user.model.v1.Operation;
import io.enmasse.user.model.v1.User;
import io.enmasse.user.model.v1.UserAuthenticationType;
import io.enmasse.user.model.v1.UserBuilder;
import io.fabric8.kubernetes.api.model.Pod;

public abstract class ResourceManager {
    protected static final Environment environment = Environment.getInstance();
    protected static final Kubernetes kubernetes = Kubernetes.getInstance();
    protected static final GlobalLogCollector logCollector = new GlobalLogCollector(kubernetes, environment.testLogDir());
    private static Logger LOGGER = CustomLogger.getLogger();

    protected String defaultAddSpaceIdentifier;
    protected String addressSpaceType;
    protected String addressSpacePlan;
    protected List<AddressSpace> currentAddressSpaces;

    public void setDefaultAddSpaceIdentifier(String defaultAddSpaceIdentifier) {
        this.defaultAddSpaceIdentifier = defaultAddSpaceIdentifier;
    }

    public void setAddressSpaceType(String addressSpaceType) {
        this.addressSpaceType = addressSpaceType;
    }

    public void setAddressSpacePlan(String addressSpacePlan) {
        this.addressSpacePlan = addressSpacePlan;
    }

    public abstract void setup() throws Exception;

    public abstract void tearDown(ExtensionContext context) throws Exception;

    public abstract AmqpClientFactory getAmqpClientFactory();

    public abstract void setAmqpClientFactory(AmqpClientFactory amqpClientFactory);

    public AddressSpace getSharedAddressSpace() {
        return null;
    }

    public void addToAddressSpaces(AddressSpace addressSpace) throws Exception {
        throw new Exception("Not implemented in resource manager");
    }

    public void deleteAddressSpaceCreatedBySC(AddressSpace addressSpace) throws Exception {
        throw new Exception("Not implemented in resource manager");
    }

    //------------------------------------------------------------------------------------------------
    // Client factories
    //------------------------------------------------------------------------------------------------

    public void closeClientFactories(AmqpClientFactory amqpClientFactory) throws Exception {
        if (amqpClientFactory != null) {
            amqpClientFactory.close();
        }
    }

    //------------------------------------------------------------------------------------------------
    // Address plans
    //------------------------------------------------------------------------------------------------

    public void createAddressPlan(AddressPlan addressPlan) throws Exception {
        LOGGER.info("Address plan {} will be created {}", addressPlan.getMetadata().getName(), addressPlan);
        var client = Kubernetes.getInstance().getAddressPlanClient();
        client.create(addressPlan);
        Thread.sleep(1000);
    }

    public void replaceAddressPlan(AddressPlan addressPlan) throws InterruptedException {
        LOGGER.info("Address plan {} will be replaced {}", addressPlan.getMetadata().getName(), addressPlan);
        var client = Kubernetes.getInstance().getAddressPlanClient();
        client.createOrReplace(addressPlan);
        Thread.sleep(1000);
    }

    public void removeAddressPlan(AddressPlan addressPlan) throws Exception {
        Kubernetes.getInstance().getAddressPlanClient().withName(addressPlan.getMetadata().getName()).cascading(true).delete();
    }

    public AddressPlan getAddressPlan(String name) throws Exception {
        return Kubernetes.getInstance().getAddressPlanClient().withName(name).get();
    }

    //------------------------------------------------------------------------------------------------
    // Address space plans
    //------------------------------------------------------------------------------------------------

    public void createAddressSpacePlan(AddressSpacePlan addressSpacePlan) throws Exception {
        createAddressSpacePlan(addressSpacePlan, true);
    }

    public void createAddressSpacePlan(AddressSpacePlan addressSpacePlan, boolean wait) throws Exception {
        LOGGER.info("AddressSpace plan {} will be created {}", addressSpacePlan.getMetadata().getName(), addressSpacePlan);
        if (addressSpacePlan.getMetadata().getNamespace() == null || addressSpacePlan.getMetadata().getNamespace().equals("")) {
            addressSpacePlan.getMetadata().setNamespace(Kubernetes.getInstance().getInfraNamespace());
        }
        var client = Kubernetes.getInstance().getAddressSpacePlanClient();
        client.create(addressSpacePlan);
        if (wait) {
            TestUtils.waitForSchemaInSync(addressSpacePlan.getMetadata().getName());
        }
        Thread.sleep(1000);
    }

    public void removeAddressSpacePlan(AddressSpacePlan addressSpacePlan) throws Exception {
        Kubernetes.getInstance().getAddressSpacePlanClient().withName(addressSpacePlan.getMetadata().getName()).cascading(true).delete();
    }

    public AddressSpacePlan getAddressSpacePlan(String config) throws Exception {
        return Kubernetes.getInstance().getAddressSpacePlanClient().withName(config).get();
    }

    //------------------------------------------------------------------------------------------------
    // Infra configs
    //------------------------------------------------------------------------------------------------

    public BrokeredInfraConfig getBrokeredInfraConfig(String name) throws Exception {
        return Kubernetes.getInstance().getBrokeredInfraConfigClient().withName(name).get();
    }

    public StandardInfraConfig getStandardInfraConfig(String name) throws Exception {
        return Kubernetes.getInstance().getStandardInfraConfigClient().withName(name).get();
    }

    public void createInfraConfig(StandardInfraConfig standardInfraConfig) {
        LOGGER.info("StandardInfraConfig {} will be created {}", standardInfraConfig.getMetadata().getName(), standardInfraConfig);
        var client = Kubernetes.getInstance().getStandardInfraConfigClient();
        client.createOrReplace(standardInfraConfig);
    }

    public void createInfraConfig(BrokeredInfraConfig brokeredInfraConfig) {
        LOGGER.info("BrokeredInfraConfig {} will be created {}", brokeredInfraConfig.getMetadata().getName(), brokeredInfraConfig);
        var client = Kubernetes.getInstance().getBrokeredInfraConfigClient();
        client.createOrReplace(brokeredInfraConfig);
    }

    public void removeInfraConfig(StandardInfraConfig infraConfig) {
        var client = Kubernetes.getInstance().getStandardInfraConfigClient();
        client.withName(infraConfig.getMetadata().getName()).cascading(true).delete();
    }

    public void removeInfraConfig(BrokeredInfraConfig infraConfig) {
        var client = Kubernetes.getInstance().getBrokeredInfraConfigClient();
        client.withName(infraConfig.getMetadata().getName()).cascading(true).delete();
    }

    //------------------------------------------------------------------------------------------------
    // Authentication services
    //------------------------------------------------------------------------------------------------

    public AuthenticationService getAuthService(String name) {
        return Kubernetes.getInstance().getAuthenticationServiceClient().withName(name).get();
    }

    public void createAuthService(AuthenticationService authenticationService) throws Exception {
        createAuthService(authenticationService, true);
    }

    public void createAuthService(AuthenticationService authenticationService, boolean wait) throws Exception {
        var client = Kubernetes.getInstance().getAuthenticationServiceClient();
        LOGGER.info("AuthService {} will be created {}", authenticationService.getMetadata().getName(), authenticationService);
        client.create(authenticationService);
        if (wait) {
            waitForAuthPods(authenticationService);
        }
    }


    public void replaceAuthService(AuthenticationService authenticationService) throws Exception {
        LOGGER.info("AuthService {} will be created {}", authenticationService.getMetadata().getName(), authenticationService);
        var client = Kubernetes.getInstance().getAuthenticationServiceClient();
        client.createOrReplace(authenticationService);
        waitForAuthPods(authenticationService);
    }

    public void waitForAuthPods(AuthenticationService authenticationService) throws Exception {
        String desiredPodName = authenticationService.getMetadata().getName();
        int expectedMatches = 1;
        if (authenticationService.getSpec().getType().equals(AuthenticationServiceType.none) &&
                authenticationService.getSpec().getNone() != null &&
                authenticationService.getSpec().getNone().getReplicas() != null) {
            expectedMatches = authenticationService.getSpec().getNone().getReplicas();
        } else if (authenticationService.getSpec().getType().equals(AuthenticationServiceType.standard) &&
                authenticationService.getSpec().getStandard() != null &&
                authenticationService.getSpec().getStandard().getReplicas() != null) {
            expectedMatches = authenticationService.getSpec().getStandard().getReplicas();
        }
        int finalExpectedMatches = expectedMatches;
        TestUtils.waitUntilCondition("Auth service is deployed: " + desiredPodName, phase -> {
                    List<Pod> pods = TestUtils.listReadyPods(Kubernetes.getInstance(), authenticationService.getMetadata().getNamespace());
                    long matching = pods.stream().filter(pod ->
                            pod.getMetadata().getName().contains(desiredPodName)).count();
                    if (matching != finalExpectedMatches) {
                        List<String> podNames = pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toList());
                        LOGGER.info("Still awaiting pod with name : {}, matching : {}, current pods  {}",
                                desiredPodName, matching, podNames);
                    }

                    return matching == finalExpectedMatches;
                },
                new TimeoutBudget(5, TimeUnit.MINUTES));
    }

    public void removeAuthService(AuthenticationService authService) throws Exception {
        Kubernetes.getInstance().getAuthenticationServiceClient(authService.getMetadata().getNamespace())
            .withName(authService.getMetadata().getName()).cascading(true).delete();
        TestUtils.waitUntilCondition("Auth service is deleted: " + authService.getMetadata().getName(), (phase) ->
                        TestUtils.listReadyPods(Kubernetes.getInstance(), authService.getMetadata().getNamespace()).stream().noneMatch(pod ->
                                pod.getMetadata().getName().contains(authService.getMetadata().getName())),
                new TimeoutBudget(1, TimeUnit.MINUTES));
    }

    public void createAddressSpace(AddressSpace addressSpace) throws Exception {
        createAddressSpace(addressSpace, true);
    }

    public void createAddressSpace(AddressSpace addressSpace, boolean waitUntilReady) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.CREATE_ADDRESS_SPACE);
        if (!AddressSpaceUtils.existAddressSpace(addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName())) {
            LOGGER.info("Address space '{}' doesn't exist and will be created.", addressSpace);
            kubernetes.getAddressSpaceClient(addressSpace.getMetadata().getNamespace()).createOrReplace(addressSpace);
            if (waitUntilReady) {
                AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
            }
        } else {
            if (waitUntilReady) {
                AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
            }
            LOGGER.info("Address space '" + addressSpace + "' already exists.");
        }
        AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    public void createAddressSpace(AddressSpace... addressSpaces) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.CREATE_ADDRESS_SPACE);
        for (AddressSpace addressSpace : addressSpaces) {
            if (!AddressSpaceUtils.existAddressSpace(addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName())) {
                LOGGER.info("Address space '{}' doesn't exist and will be created.", addressSpace);
                kubernetes.getAddressSpaceClient(addressSpace.getMetadata().getNamespace()).createOrReplace(addressSpace);
            } else {
                LOGGER.info("Address space '" + addressSpace + "' already exists.");
            }
        }
        for (AddressSpace addressSpace : addressSpaces) {
            AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
            AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
        }
        TimeMeasuringSystem.stopOperation(operationID);
    }

    public void waitForAddressSpaceReady(AddressSpace addressSpace) throws Exception {
        LOGGER.info("Waiting for address space ready");
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.CREATE_ADDRESS_SPACE);
        AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
        AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    //------------------------------------------------------------------------------------------------
    // Address space methods
    //------------------------------------------------------------------------------------------------

    public void deleteAddressSpace(AddressSpace addressSpace) throws Exception {
        if (AddressSpaceUtils.existAddressSpace(addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName())) {
            AddressSpaceUtils.deleteAddressSpaceAndWait(addressSpace, logCollector);
        } else {
            LOGGER.info("Address space '" + addressSpace.getMetadata().getName() + "' doesn't exists!");
        }
    }

    public void deleteAddressSpaceWithoutWait(AddressSpace addressSpace) throws Exception {
        if (AddressSpaceUtils.existAddressSpace(addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName())) {
            AddressSpaceUtils.deleteAddressSpace(addressSpace, logCollector);
        } else {
            LOGGER.info("Address space '" + addressSpace.getMetadata().getName() + "' doesn't exists!");
        }
    }

    protected void createAddressSpaces(List<AddressSpace> addressSpaces, String operationID) throws Exception {
        addressSpaces.forEach(addressSpace ->
                kubernetes.getAddressSpaceClient(addressSpace.getMetadata().getNamespace()).createOrReplace(addressSpace));
        for (AddressSpace addressSpace : addressSpaces) {
            AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
            AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
        }
        TimeMeasuringSystem.stopOperation(operationID);
    }

    public void replaceAddressSpace(AddressSpace addressSpace, boolean waitForConfigApplied, TimeoutBudget waitBudget) throws Exception {
        String operationID = TimeMeasuringSystem.startOperation(SystemtestsOperation.UPDATE_ADDRESS_SPACE);
        var client = kubernetes.getAddressSpaceClient(addressSpace.getMetadata().getNamespace());
        if (AddressSpaceUtils.existAddressSpace(addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName())) {
            LOGGER.info("Address space '{}' exists and will be updated.", addressSpace);
            final AddressSpace current = client.withName(addressSpace.getMetadata().getName()).get();
            final String currentResourceVersion = current.getMetadata().getResourceVersion();
            final String currentConfig = current.getAnnotation(AnnotationKeys.APPLIED_CONFIGURATION);
            client.createOrReplace(addressSpace);
            Thread.sleep(10_000);
            TestUtils.waitForChangedResourceVersion(ofDuration(ofMinutes(5)), addressSpace.getMetadata().getNamespace(), addressSpace.getMetadata().getName(), currentResourceVersion);
            if (waitForConfigApplied) {
                AddressSpaceUtils.waitForAddressSpaceConfigurationApplied(addressSpace, currentConfig);
            }
            if (waitBudget == null) {
                AddressSpaceUtils.waitForAddressSpaceReady(addressSpace);
            } else {
                AddressSpaceUtils.waitForAddressSpaceReady(addressSpace, waitBudget);
            }

            AddressSpaceUtils.syncAddressSpaceObject(addressSpace);
            currentAddressSpaces.add(addressSpace);
        } else {
            LOGGER.info("Address space '{}' does not exists.", addressSpace.getMetadata().getName());
        }
        LOGGER.info("Address space updated: {}", addressSpace);
        TimeMeasuringSystem.stopOperation(operationID);
    }

    public AddressSpace getAddressSpace(String namespace, String addressSpaceName) {
        return kubernetes.getAddressSpaceClient(namespace).withName(addressSpaceName).get();
    }

    public AddressSpace getAddressSpace(String addressSpaceName) {
        return kubernetes.getAddressSpaceClient().withName(addressSpaceName).get();
    }


    //================================================================================================
    //====================================== Address methods =========================================
    //================================================================================================

    public void deleteAddresses(Address... destinations) {
        logCollector.collectRouterState("deleteAddresses");
        AddressUtils.delete(destinations);
    }

    public void deleteAddresses(AddressSpace addressSpace) throws Exception {
        LOGGER.info("Addresses in " + addressSpace.getMetadata().getName() + " will be deleted!");
        logCollector.collectRouterState("deleteAddresses");
        AddressUtils.delete(addressSpace);
    }


    public void appendAddresses(Address... destinations) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(15, TimeUnit.MINUTES);
        appendAddresses(budget, destinations);
    }

    public void appendAddresses(TimeoutBudget timeout, Address... destinations) throws Exception {
        appendAddresses(true, timeout, destinations);
    }

    public void appendAddresses(boolean wait, Address... destinations) throws Exception {
        TimeoutBudget budget = new TimeoutBudget(15, TimeUnit.MINUTES);
        appendAddresses(wait, budget, destinations);
    }

    private void appendAddresses(boolean wait, TimeoutBudget timeout, Address... destinations) throws Exception {
        AddressUtils.appendAddresses(timeout, wait, destinations);
    }

    public void setAddresses(TimeoutBudget budget, Address... addresses) throws Exception {
        logCollector.collectRouterState("setAddresses");
        AddressUtils.setAddresses(budget, true, addresses);
    }

    public void setAddresses(boolean wait, Address... addresses) throws Exception {
        logCollector.collectRouterState("setAddresses");
        AddressUtils.setAddresses(null, wait, addresses);
    }

    public void setAddresses(Address... addresses) throws Exception {
        setAddresses(new TimeoutBudget(15, TimeUnit.MINUTES), addresses);
    }

    public void replaceAddress(Address destination) throws Exception {
        AddressUtils.replaceAddress(destination, true, new TimeoutBudget(10, TimeUnit.MINUTES));
    }

    public Address getAddress(String namespace, Address destination) {
        return kubernetes.getAddressClient().inNamespace(namespace).withName(destination.getMetadata().getName()).get();
    }

    public Address getAddress(String addressName) {
        return kubernetes.getAddressClient().withName(addressName).get();
    }

    //================================================================================================
    //======================================= User methods ===========================================
    //================================================================================================

    public User createOrUpdateUser(AddressSpace addressSpace, UserCredentials credentials) throws Exception {
        return createOrUpdateUser(addressSpace, credentials, true);
    }

    public User createOrUpdateUser(AddressSpace addressSpace, UserCredentials credentials, boolean wait) throws Exception {
        Objects.requireNonNull(addressSpace);
        Objects.requireNonNull(credentials);
        User user = new UserBuilder()
                .withNewMetadata()
                .withName(addressSpace.getMetadata().getName() + "." + credentials.getUsername())
                .endMetadata()
                .withNewSpec()
                .withUsername(credentials.getUsername())
                .withNewAuthentication()
                .withType(UserAuthenticationType.password)
                .withNewPassword(UserUtils.passwordToBase64(credentials.getPassword()))
                .endAuthentication()
                .addNewAuthorization()
                .withAddresses("*")
                .addToOperations(Operation.send)
                .addToOperations(Operation.recv)
                .endAuthorization()
                .addNewAuthorization()
                .addToOperations(Operation.manage)
                .endAuthorization()
                .endSpec()
                .build();
        return createOrUpdateUser(addressSpace, user, wait);
    }

    public User createOrUpdateUser(AddressSpace addressSpace, User user) throws Exception {
        return createOrUpdateUser(addressSpace, user, true);
    }

    public User createOrUpdateUser(AddressSpace addressSpace, User user, boolean wait) throws Exception {
        if (user.getMetadata() == null) {
            user.setMetadata(new ObjectMeta());
        }
        if (user.getMetadata().getName() == null || !user.getMetadata().getName().contains(addressSpace.getMetadata().getName())) {
            user.getMetadata().setName(addressSpace.getMetadata().getName() + "." + user.getSpec().getUsername());
        }
        if (user.getMetadata().getNamespace() == null) {
            user.getMetadata().setNamespace(addressSpace.getMetadata().getNamespace());
        }
        LOGGER.info("User {} in address space {} will be created/replaced", user, addressSpace.getMetadata().getName());
        User existing = kubernetes.getUserClient(user.getMetadata().getNamespace()).withName(user.getMetadata().getName()).get();
        if (existing != null) {
            existing.setSpec(user.getSpec());
            user = existing;
        }
        kubernetes.getUserClient(user.getMetadata().getNamespace()).createOrReplace(user);
        if (wait) {
            return UserUtils.waitForUserActive(user, new TimeoutBudget(1, TimeUnit.MINUTES));
        }
        return kubernetes.getUserClient(user.getMetadata().getNamespace()).withName(user.getMetadata().getName()).get();
    }

    public User createUserServiceAccount(AddressSpace addressSpace, UserCredentials cred) throws Exception {
        LOGGER.info("ServiceAccount user {} in address space {} will be created", cred.getUsername(), addressSpace.getMetadata().getName());
        String serviceaccountName = kubernetes.createServiceAccount(cred.getUsername(), addressSpace.getMetadata().getNamespace());
        User user = new UserBuilder()
                .withNewMetadata()
                .withName(String.format("%s.%s", addressSpace.getMetadata().getName(),
                        Pattern.compile(".*:").matcher(serviceaccountName).replaceAll("")))
                .endMetadata()
                .withNewSpec()
                .withUsername(serviceaccountName)
                .withNewAuthentication()
                .withType(UserAuthenticationType.serviceaccount)
                .endAuthentication()
                .addNewAuthorization()
                .withAddresses("*")
                .addToOperations(Operation.send)
                .addToOperations(Operation.recv)
                .endAuthorization()
                .endSpec()
                .build();
        return createOrUpdateUser(addressSpace, user);
    }

    public void removeUser(AddressSpace addressSpace, User user) throws InterruptedException {
        Objects.requireNonNull(addressSpace);
        Objects.requireNonNull(user);
        LOGGER.info("User {} in address space {} will be removed", user.getMetadata().getName(), addressSpace.getMetadata().getName());
        kubernetes.getUserClient(addressSpace.getMetadata().getNamespace()).withName(user.getMetadata().getName()).cascading(true).delete();
        UserUtils.waitForUserDeleted(user.getMetadata().getNamespace(), user.getMetadata().getName(), new TimeoutBudget(1, TimeUnit.MINUTES));
    }

    public void removeUser(AddressSpace addressSpace, String userName) throws InterruptedException {
        Objects.requireNonNull(addressSpace);
        LOGGER.info("User {} in address space {} will be removed", userName, addressSpace.getMetadata().getName());
        String name = String.format("%s.%s", addressSpace.getMetadata().getName(), userName);
        kubernetes.getUserClient(addressSpace.getMetadata().getNamespace()).withName(name).cascading(true).delete();
        UserUtils.waitForUserDeleted(addressSpace.getMetadata().getNamespace(), name, new TimeoutBudget(1, TimeUnit.MINUTES));
    }

    public User getUser(AddressSpace addressSpace, String username) {
        String id = String.format("%s.%s", addressSpace.getMetadata().getName(), username);
        List<User> response = kubernetes.getUserClient(addressSpace.getMetadata().getNamespace()).list().getItems();
        LOGGER.info("User list for {}: {}", addressSpace.getMetadata().getName(), response);
        for (User user : response) {
            if (user.getMetadata().getName().equals(id)) {
                LOGGER.info("User {} in addressspace {} already exists", username, addressSpace.getMetadata().getName());
                return user;
            }
        }
        return null;
    }

}
