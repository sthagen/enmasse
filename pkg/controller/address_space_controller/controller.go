/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package address_space_controller

import (
	"context"
	"os"
	"time"

	"github.com/enmasseproject/enmasse/pkg/util"
	"github.com/enmasseproject/enmasse/pkg/util/images"
	"github.com/enmasseproject/enmasse/pkg/util/install"

	"github.com/go-logr/logr"

	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	k8errors "k8s.io/apimachinery/pkg/api/errors"
	resource "k8s.io/apimachinery/pkg/api/resource"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	runtime "k8s.io/apimachinery/pkg/runtime"
	controllertypes "k8s.io/apimachinery/pkg/types"
	intstr "k8s.io/apimachinery/pkg/util/intstr"
)

var log = logf.Log.WithName("controller_address_space_controller")

const ADDRESS_SPACE_CONTROLLER_NAME = "address-space-controller"
const ANNOTATION_VERSION = "enmasse.io/version"
const ENV_VERSION = "VERSION"

var _ reconcile.Reconciler = &ReconcileAddressSpaceController{}

type ReconcileAddressSpaceController struct {
	client    client.Client
	reader    client.Reader
	scheme    *runtime.Scheme
	namespace string
}

// Gets called by parent "init", adding as to the manager
func Add(mgr manager.Manager) error {
	reconciler, err := newReconciler(mgr)
	if err != nil {
		return err
	}
	return add(mgr, reconciler)
}

func newReconciler(mgr manager.Manager) (*ReconcileAddressSpaceController, error) {
	return &ReconcileAddressSpaceController{
		client:    mgr.GetClient(),
		reader:    mgr.GetAPIReader(),
		scheme:    mgr.GetScheme(),
		namespace: util.GetEnvOrDefault("NAMESPACE", "enmasse-infra"),
	}, nil
}

func add(mgr manager.Manager, r *ReconcileAddressSpaceController) error {

	// Create initial deployment if it does not exist. We cannot yet rely on
	// the controller-runtime client cache (CreateOrUpdate), as the runtime has not yet started.
	deployment := &appsv1.Deployment{}
	err := r.reader.Get(context.TODO(), controllertypes.NamespacedName{Namespace: r.namespace, Name: ADDRESS_SPACE_CONTROLLER_NAME}, deployment)
	if err != nil {
		if k8errors.IsNotFound(err) {
			deployment = &appsv1.Deployment{
				ObjectMeta: metav1.ObjectMeta{
					Namespace: r.namespace,
					Name:      ADDRESS_SPACE_CONTROLLER_NAME,
				},
			}
			err = ApplyDeployment(deployment)
			if err != nil {
				return err
			}
			err = r.client.Create(context.TODO(), deployment)
			if err != nil {
				return err
			}
		} else {
			return err
		}
	}

	service := &corev1.Service{}
	err = r.reader.Get(context.TODO(), controllertypes.NamespacedName{Namespace: r.namespace, Name: ADDRESS_SPACE_CONTROLLER_NAME}, service)
	if err != nil {
		if k8errors.IsNotFound(err) {
			service = &corev1.Service{
				ObjectMeta: metav1.ObjectMeta{
					Namespace: r.namespace,
					Name:      ADDRESS_SPACE_CONTROLLER_NAME,
				},
			}
			err = applyService(service)
			if err != nil {
				return err
			}
			err = r.client.Create(context.TODO(), service)
			if err != nil {
				return err
			}
		} else {
			return err
		}
	}

	// Start reconciler for address-space-controller deployment
	c, err := controller.New("address-space-controller-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	return c.Watch(&source.Kind{Type: &appsv1.Deployment{}}, &handler.EnqueueRequestForObject{})
}

func (r *ReconcileAddressSpaceController) Reconcile(request reconcile.Request) (reconcile.Result, error) {

	expectedName := controllertypes.NamespacedName{
		Namespace: r.namespace,
		Name:      ADDRESS_SPACE_CONTROLLER_NAME,
	}

	if expectedName == request.NamespacedName {
		reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
		reqLogger.Info("Reconciling Address Space Controller")

		ctx := context.TODO()

		_, err := r.ensureDeployment(ctx, request, reqLogger)
		if err != nil {
			reqLogger.Error(err, "Error creating address space controller deployment")
			return reconcile.Result{}, err
		}

		_, err = r.ensureService(ctx, request, reqLogger)
		if err != nil {
			reqLogger.Error(err, "Error creating address space controller service")
			return reconcile.Result{}, err
		}
	}
	return reconcile.Result{RequeueAfter: 30 * time.Second}, nil
}

func (r *ReconcileAddressSpaceController) ensureDeployment(ctx context.Context, request reconcile.Request, reqLogger logr.Logger) (reconcile.Result, error) {

	deployment := &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{
			Name:      request.NamespacedName.Name,
			Namespace: request.NamespacedName.Namespace,
		},
	}

	_, err := controllerutil.CreateOrUpdate(ctx, r.client, deployment, func() error {
		return ApplyDeployment(deployment)
	})

	return reconcile.Result{}, err
}

func ApplyDeployment(deployment *appsv1.Deployment) error {
	install.ApplyDeploymentDefaults(deployment, "address-space-controller", deployment.Name)
	err := install.ApplyDeploymentContainerWithError(deployment, "address-space-controller", func(container *corev1.Container) error {
		install.ApplyContainerImage(container, "address-space-controller", nil)
		container.LivenessProbe = install.ApplyHttpProbe(container.LivenessProbe, 30, "/healthz", 8080)
		container.ReadinessProbe = install.ApplyHttpProbe(container.ReadinessProbe, 30, "/healthz", 8080)
		container.Ports = []corev1.ContainerPort{
			{Name: "http", ContainerPort: 8080, Protocol: corev1.ProtocolTCP},
		}
		install.ApplyEnvSimple(container, "JAVA_OPTS", "-verbose:gc")
		install.ApplyEnvSimple(container, "ENABLE_EVENT_LOGGER", "true")
		install.ApplyEnvSimple(container, "TEMPLATE_DIR", "/opt/templates")
		install.ApplyEnvSimple(container, "RESOURCES_DIR", "/opt")
		if value, ok := os.LookupEnv("FS_GROUP_FALLBACK_MAP"); ok {
			install.ApplyOrRemoveEnvSimple(container, "FS_GROUP_FALLBACK_MAP", value)
		}

		t := true
		install.ApplyEnvConfigMap(container, "WILDCARD_ENDPOINT_CERT_SECRET", "wildcardEndpointCertSecret", "address-space-controller-config", &t)
		install.ApplyEnvConfigMap(container, "RESYNC_INTERVAL", "resyncInterval", "address-space-controller-config", &t)
		install.ApplyEnvConfigMap(container, "RECHECK_INTERVAL", "recheckInterval", "address-space-controller-config", &t)
		install.ApplyEnvConfigMap(container, "ROUTER_STATUS_CHECK_INTERVAL", "routerStatusCheckInterval", "address-space-controller-config", &t)
		install.ApplyEnvConfigMap(container, "EXPOSE_ENDPOINTS_BY_DEFAULT", "exposeEndpointsByDefault", "address-space-controller-config", &t)
		install.ApplyEnvConfigMap(container, "DISABLE_EXTERNAL_CERT_PROVISIONING", "disableExternalCertProvisioning", "address-space-controller-config", &t)

		install.ApplyEnvSimple(container, "IMAGE_PULL_POLICY", string(images.PullPolicyFromImageName(container.Image)))
		applyImageEnv(container, "ROUTER_IMAGE", "router")
		applyImageEnv(container, "STANDARD_CONTROLLER_IMAGE", "standard-controller")
		applyImageEnv(container, "AGENT_IMAGE", "agent")
		applyImageEnv(container, "BROKER_IMAGE", "broker")
		applyImageEnv(container, "BROKER_PLUGIN_IMAGE", "broker-plugin")
		applyImageEnv(container, "TOPIC_FORWARDER_IMAGE", "topic-forwarder")
		if util.IsOpenshift() {
			install.ApplyEnvSimple(container, util.EnMasseOpenshiftEnvVar, "true")
			if util.IsOpenshift4() {
				install.ApplyEnvSimple(container, util.EnMasseOpenshift4EnvVar, "true")
			}
		}

		if value, ok := os.LookupEnv("ENABLE_MONITORING_ANNOTATIONS"); ok && value == "true" {
			deployment.ObjectMeta.Annotations["prometheus.io/scrape"] = "true"
			deployment.ObjectMeta.Annotations["prometheus.io/path"] = "/metrics"
			deployment.ObjectMeta.Annotations["prometheus.io/port"] = "8080"
		}

		if container.Resources.Requests == nil {
			container.Resources.Requests = make(map[corev1.ResourceName]resource.Quantity, 0)
		}

		if container.Resources.Limits == nil {
			container.Resources.Limits = make(map[corev1.ResourceName]resource.Quantity, 0)
		}

		cpuEnv, ok := os.LookupEnv("ADDRESS_SPACE_CONTROLLER_CPU_LIMIT")
		if ok {
			cpuLimit, err := resource.ParseQuantity(cpuEnv)
			if err != nil {
				return err
			}
			container.Resources.Requests[corev1.ResourceCPU] = cpuLimit
			container.Resources.Limits[corev1.ResourceCPU] = cpuLimit
		} else {
			delete(container.Resources.Requests, corev1.ResourceCPU)
			delete(container.Resources.Limits, corev1.ResourceCPU)
		}

		memoryEnv, ok := os.LookupEnv("ADDRESS_SPACE_CONTROLLER_MEMORY_LIMIT")
		if ok {
			memoryLimit, err := resource.ParseQuantity(memoryEnv)
			if err != nil {
				return err
			}
			container.Resources.Requests[corev1.ResourceMemory] = memoryLimit
			container.Resources.Limits[corev1.ResourceMemory] = memoryLimit
		} else {
			delete(container.Resources.Requests, corev1.ResourceMemory)
			delete(container.Resources.Limits, corev1.ResourceMemory)
		}
		return nil
	})
	if err != nil {
		return err
	}
	// This means we are running
	version, ok := os.LookupEnv(ENV_VERSION)
	if ok {
		deployment.Annotations[ANNOTATION_VERSION] = version
	}

	var one int32 = 1
	deployment.Spec.Template.Spec.ServiceAccountName = "address-space-controller"
	deployment.Spec.Replicas = &one
	install.ApplyNodeAffinity(&deployment.Spec.Template, "node-role.enmasse.io/operator-infra")

	return nil
}

func applyImageEnv(container *corev1.Container, env string, imageName string) error {
	image, err := images.GetImage(imageName)
	if err != nil {
		return err
	}
	install.ApplyEnvSimple(container, env, image)
	return nil
}

func (r *ReconcileAddressSpaceController) ensureService(ctx context.Context, request reconcile.Request, reqLogger logr.Logger) (reconcile.Result, error) {

	service := &corev1.Service{
		ObjectMeta: metav1.ObjectMeta{
			Name:      request.NamespacedName.Name,
			Namespace: request.NamespacedName.Namespace,
		},
	}

	_, err := controllerutil.CreateOrUpdate(ctx, r.client, service, func() error {
		return applyService(service)
	})

	return reconcile.Result{}, err
}

func applyService(service *corev1.Service) error {
	install.ApplyServiceDefaults(service, service.Name, service.Name)
	install.ApplyCustomLabel(&service.ObjectMeta, "monitoring-key", "enmasse-tenants")
	service.Spec.Ports = []corev1.ServicePort{
		{
			Port:       8080,
			Protocol:   corev1.ProtocolTCP,
			TargetPort: intstr.FromString("http"),
			Name:       "health",
		},
	}
	return nil
}
