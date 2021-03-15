/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package consoleservice

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"github.com/enmasseproject/enmasse/pkg/controller/ca_bundle"
	"net"
	"net/url"
	"os"
	"reflect"
	"sort"
	"strconv"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"

	monitoringv1 "github.com/coreos/prometheus-operator/pkg/apis/monitoring/v1"
	"github.com/enmasseproject/enmasse/pkg/apis/admin/v1beta1"
	"github.com/enmasseproject/enmasse/pkg/util"
	"github.com/enmasseproject/enmasse/pkg/util/install"
	oauthv1 "github.com/openshift/api/oauth/v1"
	routev1 "github.com/openshift/api/route/v1"
	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	k8errors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/types"
	"k8s.io/apimachinery/pkg/util/intstr"
	"k8s.io/client-go/rest"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/controller"
	"sigs.k8s.io/controller-runtime/pkg/controller/controllerutil"
	"sigs.k8s.io/controller-runtime/pkg/handler"
	"sigs.k8s.io/controller-runtime/pkg/manager"
	"sigs.k8s.io/controller-runtime/pkg/reconcile"
	logf "sigs.k8s.io/controller-runtime/pkg/runtime/log"
	"sigs.k8s.io/controller-runtime/pkg/source"
)

const CONSOLE_NAME = "console"

var log = logf.Log.WithName("controller_consoleservice")

var consoleLinkGVK = schema.GroupVersionKind{
	Group:   "console.openshift.io",
	Version: "v1",
	Kind:    "ConsoleLink",
}

var monitoringGVK = schema.GroupVersionKind{
	Group:   "monitoring.coreos.com",
	Version: "v1",
	Kind:    "PrometheusRule",
}

// information for console link
var consoleLinkSectionName = util.GetEnvOrDefault("CONSOLE_LINK_SECTION_NAME", "Messaging")
var consoleLinkName = util.GetEnvOrDefault("CONSOLE_LINK_NAME", "Messaging Console")
var consoleLinkImageUrl = util.GetEnvOrDefault("CONSOLE_LINK_IMAGE_URL", "")

// Gets called by parent "init", adding as to the manager
func Add(mgr manager.Manager) error {

	return add(mgr, newReconciler(mgr))
}

func newReconciler(mgr manager.Manager) *ReconcileConsoleService {
	return &ReconcileConsoleService{config: mgr.GetConfig(), client: mgr.GetClient(), reader: mgr.GetAPIReader(), scheme: mgr.GetScheme(), namespace: util.GetEnvOrDefault("NAMESPACE", "enmasse-infra")}
}

func add(mgr manager.Manager, r *ReconcileConsoleService) error {

	// Create a new controller
	c, err := controller.New("consoleservice-controller", mgr, controller.Options{Reconciler: r})
	if err != nil {
		return err
	}

	// Watch for changes to primary resource ConsoleService
	err = c.Watch(&source.Kind{Type: &v1beta1.ConsoleService{}}, &handler.EnqueueRequestForObject{})
	if err != nil {
		return err
	}

	err = c.Watch(&source.Kind{Type: &appsv1.Deployment{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &v1beta1.ConsoleService{},
	})
	if err != nil {
		return err
	}

	err = c.Watch(&source.Kind{Type: &corev1.Service{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &v1beta1.ConsoleService{},
	})
	if err != nil {
		return err
	}

	err = c.Watch(&source.Kind{Type: &corev1.ConfigMap{}}, &handler.EnqueueRequestForOwner{
		IsController: true,
		OwnerType:    &v1beta1.ConsoleService{},
	})
	if err != nil {
		return err
	}

	if util.IsOpenshift() {
		// Changes to the secret or routes potentially need to be written to the oauthclient
		err = c.Watch(&source.Kind{Type: &corev1.Secret{}}, &handler.EnqueueRequestForOwner{
			IsController: true,
			OwnerType:    &v1beta1.ConsoleService{},
		})
		if err != nil {
			return err
		}
	}

	// Currently we need a single instance of console called "console", ensure that it exists.
	err = ensureSingletonConsoleService(context.TODO(), metav1.ObjectMeta{Namespace: r.namespace, Name: CONSOLE_NAME}, r.client, r.reader)
	if err != nil {
		log.Error(err, "Failed create singleton ConsoleService instance")
	}

	return nil
}

var _ reconcile.Reconciler = &ReconcileConsoleService{}

type ReconcileConsoleService struct {
	// This client, initialized using mgr.Client() above, is a split client
	// that reads objects from the cache and writes to the apiserver
	config    *rest.Config
	reader    client.Reader
	client    client.Client
	scheme    *runtime.Scheme
	namespace string
}

// Reconcile by reading the console service spec and making required changes
//
// returning an error will get the request re-queued
func (r *ReconcileConsoleService) Reconcile(request reconcile.Request) (reconcile.Result, error) {
	reqLogger := log.WithValues("Request.Namespace", request.Namespace, "Request.Name", request.Name)
	reqLogger.Info("Reconciling ConsoleService")

	ctx := context.TODO()
	consoleservice := &v1beta1.ConsoleService{}
	err := r.reader.Get(ctx, request.NamespacedName, consoleservice)
	if err != nil {
		if k8errors.IsNotFound(err) {
			if CONSOLE_NAME == request.NamespacedName.Name {
				err = ensureSingletonConsoleService(ctx, metav1.ObjectMeta{Namespace: request.NamespacedName.Namespace,
					Name: request.NamespacedName.Name}, r.client, r.reader)
				return reconcile.Result{}, err
			} else {
				reqLogger.Info("ConsoleService resource not found. Ignoring since object must be deleted")
				return reconcile.Result{}, nil
			}
		}
		// Error reading the object - requeue the request
		reqLogger.Error(err, "Failed to get ConsoleService")
		return reconcile.Result{}, err
	}

	rewritten, err := applyConsoleServiceDefaults(ctx, r.client, r.scheme, consoleservice)
	if err != nil || rewritten {
		return reconcile.Result{}, err
	}

	// Validate we have sufficient information to proceed with the deployment.  On OpenShift, the defaults
	// will satisfy these requirements. On Kubernetes the user will have to supply the details.
	if consoleservice.Spec.DiscoveryMetadataURL == nil || consoleservice.Spec.OauthClientSecret == nil {
		reqLogger.Info("Cannot deploy console as ConsoleService does not define DiscoveryMetadataURL " +
			"and OauthClientSecret.")
		return reconcile.Result{}, nil
	} else {
		if util.IsOpenshift() {
			// Secret will be created later if necessary
		} else {
			secretName := types.NamespacedName{
				Name:      consoleservice.Spec.OauthClientSecret.Name,
				Namespace: consoleservice.Namespace,
			}
			oauthsecret := &corev1.Secret{}
			err := r.client.Get(ctx, secretName, oauthsecret)
			if err != nil {
				if k8errors.IsNotFound(err) {
					reqLogger.Info("Cannot deploy console as ConsoleService OauthClientSecret does not " +
						"refer to a secret.")
					return reconcile.Result{}, nil
				} else {
					return reconcile.Result{}, err
				}
			}
		}
	}

	// trusted cabundle configmap
	result, err := r.reconcileTrustedCabundleConfigMap(ctx, consoleservice)
	requeue := result.Requeue

	// service
	result, err = r.reconcileService(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// route
	result, route, err := r.reconcileRoute(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// console link
	result, err = r.reconcileConsoleLink(ctx, consoleservice, route)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// sso secret
	result, err = r.reconcileSsoCookieSecret(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// oauthclient
	result, redirects, err := r.reconcileOauthClient(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// deployment
	result, err = r.reconcileDeployment(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	// prometheus rules
	result, err = r.reconcilePrometheusRule(ctx, consoleservice)
	if err != nil {
		return reconcile.Result{}, err
	}
	requeue = requeue || result.Requeue

	result, err = r.updateService(ctx, consoleservice, func(status *v1beta1.ConsoleServiceStatus) error {

		if route != nil && len(route.Status.Ingress) > 0 {
			status.Host = route.Status.Ingress[0].Host
			status.Port = 443
			var cert = consoleservice.Spec.CertificateSecret
			status.CaCertSecret = cert
		}
		return nil
	}, func() (*string, error) {

		if getBooleanAnnotationValue(consoleservice.Annotations, "enmasse.io/disable-autocompute-sso-cookie-domain") {
			return consoleservice.Spec.SsoCookieDomain, nil
		}

		hosts := make([]string, len(redirects))
		for i, v := range redirects {
			hosts[i] = v.Hostname()
			if net.ParseIP(hosts[i]) != nil {
				return nil, nil
			}
		}

		newSsoCookieDomain, domainPortionCount := GetCommonDomain(hosts)

		if newSsoCookieDomain != nil && consoleservice.Spec.SsoCookieDomain != nil && *newSsoCookieDomain == *consoleservice.Spec.SsoCookieDomain {
			return consoleservice.Spec.SsoCookieDomain, nil
		} else if domainPortionCount < 2 {
			// Disallow laying cookies at TLD
			return nil, nil
		}

		return newSsoCookieDomain, nil
	})

	return reconcile.Result{Requeue: requeue}, err
}

type UpdateStatusFn func(status *v1beta1.ConsoleServiceStatus) error
type UpdateDomainFn func() (*string, error)

func (r *ReconcileConsoleService) updateService(ctx context.Context, consoleservice *v1beta1.ConsoleService, updateFn UpdateStatusFn, fn UpdateDomainFn) (reconcile.Result, error) {

	newStatus := v1beta1.ConsoleServiceStatus{}
	if err := updateFn(&newStatus); err != nil {
		return reconcile.Result{}, err
	}

	newSsoCookieDomain, err := fn()
	if err != nil {
		return reconcile.Result{}, err
	}

	if consoleservice.Spec.SsoCookieDomain != newSsoCookieDomain ||
		consoleservice.Status.Host != newStatus.Host ||
		consoleservice.Status.Port != newStatus.Port ||
		!reflect.DeepEqual(consoleservice.Status.CaCertSecret, newStatus.CaCertSecret) {

		consoleservice.Spec.SsoCookieDomain = newSsoCookieDomain
		consoleservice.Status = newStatus
		log.Info("Updating console service")
		err := r.client.Update(ctx, consoleservice)
		if err != nil {
			return reconcile.Result{}, err
		}
		return reconcile.Result{Requeue: false}, nil
	}
	return reconcile.Result{}, nil
}

func applyConsoleServiceDefaults(ctx context.Context, client client.Client, scheme *runtime.Scheme, consoleservice *v1beta1.ConsoleService) (bool, error) {
	var dirty = false

	if consoleservice.Spec.CertificateSecret == nil {
		dirty = true
		secretName := consoleservice.Name + "-cert"
		consoleservice.Spec.CertificateSecret = &corev1.SecretReference{
			Name: secretName,
		}

		if !util.IsOpenshift() {
			err := util.CreateSecret(ctx, client, scheme, consoleservice.Namespace, secretName, consoleservice, func(secret *corev1.Secret) error {
				install.ApplyDefaultLabels(&secret.ObjectMeta, "consoleservice", secretName)

				cn := util.ServiceToCommonName(consoleservice.Namespace, consoleservice.Name)
				return util.GenerateSelfSignedCertSecret(cn, nil, nil, secret)
			})
			if err != nil {
				return false, err
			}
		}
	}

	if consoleservice.Spec.SsoCookieSecret == nil {
		dirty = true
		secretName := consoleservice.Name + "-sso-cookie-secret"
		consoleservice.Spec.SsoCookieSecret = &corev1.SecretReference{Name: secretName}
	}

	if consoleservice.Spec.OauthClientSecret == nil {
		dirty = true
		secretName := consoleservice.Name + "-oauth"
		consoleservice.Spec.OauthClientSecret = &corev1.SecretReference{Name: secretName}
	}

	if util.IsOpenshift() {
		if consoleservice.Spec.Scope == nil {
			dirty = true
			scope := "user:full"
			consoleservice.Spec.Scope = &scope
		}

		if consoleservice.Spec.DiscoveryMetadataURL == nil {
			dirty = true
			discoveryURL := "https://openshift.default.svc/.well-known/oauth-authorization-server"

			openshiftUri, rewritten, err := util.OpenshiftUri()
			if err != nil {
				return false, err
			}

			if rewritten {
				// The well known metadata will be unusable
				metadata, err := util.WellKnownOauthMetadata()
				if err != nil {
					return false, err
				}

				keys := []string{
					"issuer",
					"authorization_endpoint",
					"token_endpoint"}

				for _, k := range keys {
					if u, ok := metadata[k]; ok {
						metadata_url, err := url.Parse(u.(string))
						if err == nil {
							metadata_url.Host = openshiftUri.Host
							metadata_url.Scheme = openshiftUri.Scheme
							metadata[k] = metadata_url.String()
						}
					}
				}

				metadata_bytes, err := json.Marshal(metadata)
				if err != nil {
					return false, err
				}

				discoveryURL = "data:application/json;base64," + base64.StdEncoding.EncodeToString(metadata_bytes)
			}

			consoleservice.Spec.DiscoveryMetadataURL = &discoveryURL
		}
	} else {
		if consoleservice.Spec.Scope == nil {
			dirty = true
			scope := "openid"
			consoleservice.Spec.Scope = &scope
		}
	}

	if dirty {
		// address-space-controller needs to know the default values, so we rewrite the object.
		log.Info("Materializing console service defaults.")
		err := client.Update(ctx, consoleservice)
		return true, err
	}

	return false, nil
}

func (r *ReconcileConsoleService) reconcileTrustedCabundleConfigMap(ctx context.Context, consoleService *v1beta1.ConsoleService) (reconcile.Result, error) {
	if !util.IsOpenshift() {
		return reconcile.Result{}, nil
	}

	name := getTrustedCabundleConfigMapName(consoleService)
	configMap := &corev1.ConfigMap{
		ObjectMeta: metav1.ObjectMeta{Namespace: consoleService.Namespace, Name: name},
	}
	_, err := controllerutil.CreateOrUpdate(ctx, r.client, configMap, func() error {
		if err := controllerutil.SetControllerReference(consoleService, configMap, r.scheme); err != nil {
			return err
		}
		configMap.SetLabels(install.CreateDefaultLabels(configMap.GetLabels(), "console", name))
		install.ApplyCustomLabel(&configMap.ObjectMeta, "config.openshift.io/inject-trusted-cabundle", "true")
		return nil
	})

	if err != nil {
		log.Error(err, "Failed reconciling trusted cabundle configmap")
		return reconcile.Result{}, err
	}
	return reconcile.Result{}, nil
}

func (r *ReconcileConsoleService) reconcileService(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, error) {
	service := &corev1.Service{
		ObjectMeta: metav1.ObjectMeta{Namespace: consoleservice.Namespace, Name: consoleservice.Name},
	}
	_, err := controllerutil.CreateOrUpdate(ctx, r.client, service, func() error {
		if err := controllerutil.SetControllerReference(consoleservice, service, r.scheme); err != nil {
			return err
		}

		return applyService(consoleservice, service)
	})

	if err != nil {
		log.Error(err, "Failed reconciling Service")
		return reconcile.Result{}, err
	}
	return reconcile.Result{}, nil
}

func applyService(consoleService *v1beta1.ConsoleService, service *corev1.Service) error {

	install.ApplyServiceDefaults(service, "consoleservice", consoleService.Name)
	service.Spec.Selector = install.CreateDefaultLabels(nil, "consoleservice", consoleService.Name)

	if service.Annotations == nil {
		service.Annotations = make(map[string]string)
	}

	install.ApplyOpenShiftServingCertAnnotation(service.Annotations, consoleService.Spec.CertificateSecret.Name, util.IsOpenshift, util.IsOpenshift4)

	service.Spec.Ports = []corev1.ServicePort{
		{
			Port:       8443,
			Protocol:   corev1.ProtocolTCP,
			TargetPort: intstr.FromString("https"),
			Name:       "https",
		},
		{
			Port:       8080,
			Protocol:   corev1.ProtocolTCP,
			TargetPort: intstr.FromString("metrics"),
			Name:       "metrics",
		},
	}
	return nil
}

func (r *ReconcileConsoleService) reconcilePrometheusRule(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, error) {

	monitoringEnabled := util.GetBooleanEnv("ENABLE_MONITORING")

	if monitoringEnabled {

		if !util.HasApi(monitoringGVK) {
			return reconcile.Result{}, nil
		}

		prometheusRule := &monitoringv1.PrometheusRule{
			ObjectMeta: metav1.ObjectMeta{
				Name:      "enmasse-console-rules",
				Namespace: consoleservice.Namespace,
			},
		}

		_, err := controllerutil.CreateOrUpdate(ctx, r.client, prometheusRule, func() error {

			install.ApplyDefaultLabels(&prometheusRule.ObjectMeta, "consoleservice", consoleservice.Name)
			install.ApplyCustomLabel(&prometheusRule.ObjectMeta, "monitoring-key", "middleware")
			prometheusRule.Spec = monitoringv1.PrometheusRuleSpec{
				Groups: []monitoringv1.RuleGroup{
					{
						Name: "ComponentHealth",
						Rules: []monitoringv1.Rule{
							{
								Record: "enmasse_component_health",
								Expr:   intstr.FromString("up{job='console',namespace='" + util.GetEnvOrDefault("NAMESPACE", "enmasse-infra") + "'} or on(namespace) (1- absent(up{job='console',namespace='" + util.GetEnvOrDefault("NAMESPACE", "enmasse-infra") + "'}))"),
							},
						},
					},
				},
			}
			return nil
		})
		if err != nil {
			log.Error(err, "Failed reconciling PrometheusRule")
			return reconcile.Result{}, err
		}
	}
	return reconcile.Result{}, nil
}

func (r *ReconcileConsoleService) reconcileRoute(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, *routev1.Route, error) {

	if !util.IsOpenshift() {
		// we have routes only in OpenShift
		return reconcile.Result{}, nil, nil
	}

	route := &routev1.Route{
		ObjectMeta: metav1.ObjectMeta{Namespace: consoleservice.Namespace, Name: consoleservice.Name},
	}
	_, err := controllerutil.CreateOrUpdate(ctx, r.client, route, func() error {
		secretName := types.NamespacedName{
			Name:      consoleservice.Spec.CertificateSecret.Name,
			Namespace: consoleservice.Namespace,
		}
		certsecret := &corev1.Secret{}
		err := r.client.Get(ctx, secretName, certsecret)
		if err != nil {
			return err
		}
		cert := certsecret.Data["tls.crt"]
		if err := controllerutil.SetControllerReference(consoleservice, route, r.scheme); err != nil {
			return err
		}
		return applyRoute(consoleservice, route, string(cert[:]))
	})

	if err != nil {
		log.Error(err, "Failed reconciling Route")
		return reconcile.Result{}, nil, err
	}

	return reconcile.Result{}, route, nil
}

func applyRoute(consoleservice *v1beta1.ConsoleService, route *routev1.Route, caCertificate string) error {

	install.ApplyDefaultLabels(&route.ObjectMeta, "consoleservice", consoleservice.Name)

	route.Spec = routev1.RouteSpec{
		To: routev1.RouteTargetReference{
			Kind: "Service",
			Name: consoleservice.Name,
		},
		TLS: &routev1.TLSConfig{
			Termination:   routev1.TLSTerminationReencrypt,
			CACertificate: caCertificate,
		},
		Port: &routev1.RoutePort{
			TargetPort: intstr.FromString("https"),
		},
	}

	if consoleservice.Spec.Host != nil {
		route.Spec.Host = *consoleservice.Spec.Host
	}
	return nil
}

func (r *ReconcileConsoleService) reconcileConsoleLink(ctx context.Context, consoleservice *v1beta1.ConsoleService, route *routev1.Route) (reconcile.Result, error) {

	if !util.HasApi(consoleLinkGVK) {
		return reconcile.Result{}, nil
	}

	// eval the host name, there should only be one

	host := ""
	if route != nil {
		for _, r := range route.Status.Ingress {
			if r.Host != "" {
				if route.Spec.TLS != nil {
					host = "https://" + r.Host
				} else {
					host = "http://" + r.Host
				}
				// we take the first one, and stop here
				break
			}
		}
	}

	// we could also use the structured type here, but we do use the
	// unstructured type in this simple case, as a reference if we should
	// need it in the future

	consoleLink := unstructured.Unstructured{}
	consoleLink.SetGroupVersionKind(consoleLinkGVK)
	consoleLink.SetName("enmasse-consoleservice")

	if host != "" {

		// we have a URL, so use it

		_, err := controllerutil.CreateOrUpdate(ctx, r.client, &consoleLink, func() error {
			applyConsoleLink(&consoleLink, host, consoleservice.Name)
			if err := controllerutil.SetControllerReference(consoleservice, route, r.scheme); err != nil {
				return err
			}

			return nil
		})

		return reconcile.Result{}, err

	} else {

		// we have no url, so delete the console link

		err := install.DeleteIgnoreNotFound(ctx, r.client, &consoleLink)
		return reconcile.Result{}, err

	}
}

func applyConsoleLink(consoleLink *unstructured.Unstructured, host string, name string) {

	consoleLink.Object["spec"] = map[string]interface{}{
		"text":     consoleLinkName,
		"location": "ApplicationMenu",
		"applicationMenu": map[string]interface{}{
			"section":  consoleLinkSectionName,
			"imageURL": consoleLinkImageUrl,
		},
		"href": host,
	}
	consoleLink.SetLabels(install.CreateDefaultLabels(consoleLink.GetLabels(), "console", name))
}

func (r *ReconcileConsoleService) reconcileSsoCookieSecret(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, error) {
	secretref := consoleservice.Spec.SsoCookieSecret

	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{Namespace: consoleservice.Namespace, Name: secretref.Name},
	}
	_, err := controllerutil.CreateOrUpdate(ctx, r.client, secret, func() error {
		if err := controllerutil.SetControllerReference(consoleservice, secret, r.scheme); err != nil {
			return err
		}

		if getBooleanAnnotationValue(consoleservice.Annotations, "enmasse.io/disable-sso-cookie") {
			secret.Data = nil
			return nil
		} else {
			return applySsoCookieSecret(secret)
		}
	})

	if err != nil {
		log.Error(err, "Failed reconciling SSO Cookie Secret")
		return reconcile.Result{}, err
	}

	return reconcile.Result{}, nil
}

func (r *ReconcileConsoleService) reconcileDeployment(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, error) {

	key := client.ObjectKey{Namespace: consoleservice.Namespace, Name: getTrustedCabundleConfigMapName(consoleservice)}
	caBundle := &corev1.ConfigMap{}
	err := r.client.Get(ctx, key, caBundle)
	if err != nil && !k8errors.IsNotFound(err) {
		return reconcile.Result{}, err
	}

	caBundleKeys := make([]string, 0)
	for key, _ := range caBundle.Data {
		caBundleKeys = append(caBundleKeys, key)
	}
	sort.Strings(caBundleKeys)

	deployment := &appsv1.Deployment{
		ObjectMeta: metav1.ObjectMeta{Namespace: consoleservice.Namespace, Name: consoleservice.Name},
	}
	_, err = controllerutil.CreateOrUpdate(ctx, r.client, deployment, func() error {
		if err := controllerutil.SetControllerReference(consoleservice, deployment, r.scheme); err != nil {
			return err
		}

		return applyDeployment(consoleservice, deployment, caBundleKeys)
	})

	if err != nil {
		log.Error(err, "Failed reconciling Deployment")
		return reconcile.Result{}, err
	}
	return reconcile.Result{}, nil
}

func applyDeployment(consoleservice *v1beta1.ConsoleService, deployment *appsv1.Deployment, caBundleKeys []string) error {

	install.ApplyDeploymentDefaults(deployment, "consoleservice", consoleservice.Name)

	if consoleservice.Spec.Replicas != nil {
		deployment.Spec.Replicas = consoleservice.Spec.Replicas
	}

	install.ApplyEmptyDirVolume(&deployment.Spec.Template.Spec, "apps")
	install.ApplySecretVolume(&deployment.Spec.Template.Spec, "console-tls", consoleservice.Spec.CertificateSecret.Name)

	if err := install.ApplyInitContainerWithError(deployment, "console-init", func(container *corev1.Container) error {
		if err := install.ApplyContainerImage(container, "console-init", nil); err != nil {
			return err
		}

		install.ApplyEnvSimple(container, "OPENSHIFT_AVAILABLE", strconv.FormatBool(util.IsOpenshift()))

		if consoleservice.ObjectMeta.GetAnnotations() != nil {
			install.ApplyEnv(container, "ITEM_REFRESH_RATE", func(envvar *corev1.EnvVar) {
				envvar.Value = consoleservice.ObjectMeta.GetAnnotations()["enmasse.io/console-refresh-rate"]
			})
		} else {
			install.RemoveEnv(container, "ITEM_REFRESH_RATE")
		}

		if consoleservice.Spec.Scope != nil {
			install.ApplyEnv(container, "OAUTH2_SCOPE", func(envvar *corev1.EnvVar) {
				envvar.Value = *consoleservice.Spec.Scope
			})
		} else {
			install.RemoveEnv(container, "OAUTH2_SCOPE")
		}

		if consoleservice.Spec.DiscoveryMetadataURL != nil {
			install.ApplyEnv(container, "DISCOVERY_METADATA_URL", func(envvar *corev1.EnvVar) {
				envvar.Value = *consoleservice.Spec.DiscoveryMetadataURL
			})
		} else {
			install.RemoveEnv(container, "DISCOVERY_METADATA_URL")
		}

		if consoleservice.Spec.SsoCookieDomain != nil {
			install.ApplyEnv(container, "SSO_COOKIE_DOMAIN", func(envvar *corev1.EnvVar) {
				envvar.Value = *consoleservice.Spec.SsoCookieDomain
			})
		} else {
			install.RemoveEnv(container, "SSO_COOKIE_DOMAIN")
		}

		if consoleservice.Spec.SsoCookieSecret != nil {
			b := true
			install.ApplyEnvOptionalSecret(container, "SSO_COOKIE_SECRET", "cookie-secret", consoleservice.Spec.SsoCookieSecret.Name, &b)
		} else {
			install.RemoveEnv(container, "SSO_COOKIE_SECRET")
		}

		install.ApplyVolumeMountSimple(container, "apps", "/apps", false)
		return nil
	}); err != nil {
		return err
	}

	if util.IsOpenshift() {
		install.ApplyConfigMapVolume(&deployment.Spec.Template.Spec, "trusted-ca-bundle", getTrustedCabundleConfigMapName(consoleservice))

		imageName := "console-proxy-openshift3"
		caBundleContainerPath := ca_bundle.ServiceCaPathOcp311
		if util.IsOpenshift4() {
			imageName = "console-proxy-openshift"
			caBundleContainerPath = ca_bundle.ServiceCaPathOcp4
			install.ApplyConfigMapVolumeItems(&deployment.Spec.Template.Spec, ca_bundle.CaBundleVolumeName, ca_bundle.CaBundleConfigmapName, []corev1.KeyToPath{
				{
					Key:  ca_bundle.CaBundleKey,
					Path: ca_bundle.ServiceCaFilename,
				},
			})
		}

		if err := install.ApplyDeploymentContainerWithError(deployment, "console-proxy", func(container *corev1.Container) error {
			if err := install.ApplyContainerImage(container, imageName, nil); err != nil {
				return err
			}
			container.Args = []string{
				"-config=/apps/cfg/oauth-proxy-openshift.cfg",
				fmt.Sprintf("--upstream-ca=%s/%s", caBundleContainerPath, ca_bundle.ServiceCaFilename),
				"-openshift-ca=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt",
				"-openshift-ca=/etc/ssl/certs/ca-bundle.crt",
			}

			for _, key := range caBundleKeys {
				container.Args = append(container.Args, fmt.Sprintf("-openshift-ca=/etc/pki/trusted-ca-bundle/%s", key))
			}
			applyOauthProxyContainer(container, consoleservice, "/oauth/healthz")

			install.ApplyVolumeMountSimple(container, "trusted-ca-bundle", "/etc/pki/trusted-ca-bundle/", true)
			if util.IsOpenshift4() {
				install.ApplyVolumeMountSimple(container, ca_bundle.CaBundleVolumeName, ca_bundle.ServiceCaPathOcp4, true)
			}
			return nil
		}); err != nil {
			return err
		}

		// Remove the old container console-httpd (if present)
		install.DropContainer(deployment, "console-httpd")
	} else {
		if err := install.ApplyDeploymentContainerWithError(deployment, "console-proxy", func(container *corev1.Container) error {
			if err := install.ApplyContainerImage(container, "console-proxy-kubernetes", nil); err != nil {
				return err
			}

			container.Args = []string{"-config=/apps/cfg/oauth-proxy-kubernetes.cfg"}

			applyOauthProxyContainer(container, consoleservice, "/ping")

			// https://github.com/golang/go/issues/35325 SSL_CERT_DIR does not accept a path (yet)
			install.ApplyEnv(container, "SSL_CERT_DIR", func(envvar *corev1.EnvVar) {
				envvar.Value = "/var/run/secrets/kubernetes.io/serviceaccount/"
			})

			return nil
		}); err != nil {
			return err
		}
	}

	if err := install.ApplyDeploymentContainerWithError(deployment, "console-server", func(container *corev1.Container) error {
		if err := install.ApplyContainerImage(container, "console-server", nil); err != nil {
			return err
		}

		port := int32(9090)
		metricsPort := int32(9089)
		install.ApplyEnv(container, "PORT", func(envvar *corev1.EnvVar) {
			envvar.Value = strconv.Itoa(int(port))
		})

		install.ApplyEnv(container, "METRICS_PORT", func(envvar *corev1.EnvVar) {
			envvar.Value = strconv.Itoa(int(metricsPort))
		})

		container.Ports = []corev1.ContainerPort{}

		namespace, err := util.GetInfrastructureNamespace()
		if err == nil {
			install.ApplyEnv(container, "NAMESPACE", func(envvar *corev1.EnvVar) {
				envvar.Value = namespace
			})
		}

		value, ok := os.LookupEnv(util.EnMasseOpenshiftEnvVar)
		if ok {
			install.ApplyEnvSimple(container, util.EnMasseOpenshiftEnvVar, value)
		}

		// TODO use https

		probeHandler := corev1.Handler{
			Exec: &corev1.ExecAction{
				Command: []string{
					"sh",
					"-c",
					"/probe.sh",
				},
			},
		}

		consoleServer := consoleservice.Spec.ConsoleServer

		container.ReadinessProbe = &corev1.Probe{
			InitialDelaySeconds: 30,
			Handler:             probeHandler,
		}

		if consoleServer != nil && consoleServer.ReadinessProbe != nil {
			install.OverrideProbe(consoleservice.Spec.ConsoleServer.ReadinessProbe, container.ReadinessProbe)
		}

		container.LivenessProbe = &corev1.Probe{
			InitialDelaySeconds: 30,
			Handler:             probeHandler,
		}

		if consoleServer != nil && consoleServer.LivenessProbe != nil {
			install.OverrideProbe(consoleservice.Spec.ConsoleServer.LivenessProbe, container.LivenessProbe)
		}

		container.Ports = []corev1.ContainerPort{{
			ContainerPort: port,
			Name:          "http",
		}, {
			ContainerPort: metricsPort,
			Name:          "metrics",
		}}

		if consoleServer != nil && consoleServer.Resources != nil {
			container.Resources = *consoleServer.Resources
		} else {
			container.Resources = corev1.ResourceRequirements{}
		}

		if consoleServer != nil && consoleServer.Session != nil && consoleServer.Session.Lifetime != nil && *consoleServer.Session.Lifetime != "" {
			install.ApplyEnv(container, "HTTP_SESSION_LIFETIME", func(envvar *corev1.EnvVar) {
				envvar.Value = *consoleServer.Session.Lifetime
			})
		} else {
			install.RemoveEnv(container, "HTTP_SESSION_LIFETIME")
		}

		if consoleServer != nil && consoleServer.Session != nil && consoleServer.Session.IdleTimeout != nil && *consoleServer.Session.IdleTimeout != "" {
			install.ApplyEnv(container, "HTTP_SESSION_IDLE_TIMEOUT", func(envvar *corev1.EnvVar) {
				envvar.Value = *consoleServer.Session.IdleTimeout
			})
		} else {
			install.RemoveEnv(container, "HTTP_SESSION_IDLE_TIMEOUT")
		}

		if consoleservice.Spec.Impersonation != nil {
			install.ApplyEnvSimple(container, "IMPERSONATION_ENABLE", "true")
			if consoleservice.Spec.Impersonation.UserHeader != "" {
				install.ApplyEnvSimple(container, "IMPERSONATION_USER_HEADER", consoleservice.Spec.Impersonation.UserHeader)
			} else {
				install.RemoveEnv(container, "IMPERSONATION_USER_HEADER")
			}
		} else {
			install.RemoveEnv(container, "IMPERSONATION_ENABLE")
			install.RemoveEnv(container, "IMPERSONATION_USER_HEADER")
		}

		if value := util.GetBooleanEnvOrDefault("ENABLE_MONITORING_ANNOTATIONS", false); value {
			deployment.ObjectMeta.Annotations["prometheus.io/scrape"] = "true"
			deployment.ObjectMeta.Annotations["prometheus.io/path"] = "/metrics"
			deployment.ObjectMeta.Annotations["prometheus.io/port"] = strconv.Itoa(int(metricsPort))
		}

		return nil
	}); err != nil {
		return err
	}

	deployment.Spec.Template.Spec.ServiceAccountName = "console-server"

	return nil
}

func getTrustedCabundleConfigMapName(consoleservice *v1beta1.ConsoleService) string {
	return consoleservice.Name + "-trusted-ca-bundle"
}

func applyOauthProxyContainer(container *corev1.Container, consoleservice *v1beta1.ConsoleService, path string) {
	install.ApplyVolumeMountSimple(container, "apps", "/apps", false)
	install.ApplyVolumeMountSimple(container, "console-tls", "/etc/tls/private", true)
	if consoleservice.Spec.OauthClientSecret != nil {
		install.ApplyEnvSecret(container, "OAUTH2_PROXY_CLIENT_ID", "client-id", consoleservice.Spec.OauthClientSecret.Name)
		install.ApplyEnvSecret(container, "OAUTH2_PROXY_CLIENT_SECRET", "client-secret", consoleservice.Spec.OauthClientSecret.Name)
	}

	container.Ports = []corev1.ContainerPort{{
		ContainerPort: 8443,
		Name:          "https",
	}}
	container.ReadinessProbe = &corev1.Probe{
		InitialDelaySeconds: 60,
		Handler: corev1.Handler{
			HTTPGet: &corev1.HTTPGetAction{
				Port:   intstr.FromString("https"),
				Path:   path,
				Scheme: "HTTPS",
			},
		},
	}
	container.LivenessProbe = &corev1.Probe{
		InitialDelaySeconds: 120,
		Handler: corev1.Handler{
			HTTPGet: &corev1.HTTPGetAction{
				Port:   intstr.FromString("https"),
				Path:   path,
				Scheme: "HTTPS",
			},
		},
	}
	if consoleservice.Spec.OauthProxy != nil {
		if consoleservice.Spec.OauthProxy.Resources != nil {
			container.Resources = *consoleservice.Spec.OauthProxy.Resources
		} else {
			container.Resources = corev1.ResourceRequirements{}
		}
		container.Args = append(container.Args, consoleservice.Spec.OauthProxy.ExtraArgs...)
	}
}

func (r *ReconcileConsoleService) reconcileOauthClient(ctx context.Context, consoleservice *v1beta1.ConsoleService) (reconcile.Result, []url.URL, error) {
	if util.IsOpenshift() {

		secretref := consoleservice.Spec.OauthClientSecret

		secret := &corev1.Secret{
			ObjectMeta: metav1.ObjectMeta{Namespace: consoleservice.Namespace, Name: secretref.Name},
		}
		_, err := controllerutil.CreateOrUpdate(ctx, r.client, secret, func() error {
			if err := controllerutil.SetControllerReference(consoleservice, secret, r.scheme); err != nil {
				return err
			}
			return applyOauthSecret(secret)
		})

		if err != nil {
			log.Error(err, "Failed reconciling OAuth Secret")
			return reconcile.Result{}, nil, err
		}

		key := client.ObjectKey{Namespace: consoleservice.Namespace, Name: consoleservice.Name}
		route := &routev1.Route{}
		err = r.client.Get(ctx, key, route)
		if err != nil {
			return reconcile.Result{}, nil, err
		}

		if len(route.Status.Ingress) == 0 {
			log.Info("Console route has no ingress, can't set up OAuth redirects yet.", "routeName", consoleservice.Name)
			return reconcile.Result{Requeue: true}, nil, nil
		}

		// Redirect for the global console itself.
		redirects := make([]url.URL, 0)
		redirects, err = buildRedirectsForRoute(*route, redirects)
		if err != nil {
			return reconcile.Result{}, nil, err
		}

		oauth := &oauthv1.OAuthClient{
			ObjectMeta: metav1.ObjectMeta{Name: secret.Name},
		}

		_, err = controllerutil.CreateOrUpdate(ctx, r.client, oauth, func() error {
			err = applyOauthClient(oauth, secret, redirects)
			if err != nil {
				return err
			}
			return nil
		})

		if err != nil {
			log.Error(err, "Failed reconciling OAuth")
			return reconcile.Result{}, nil, err
		}

		return reconcile.Result{}, redirects, nil
	}
	return reconcile.Result{}, nil, nil
}

func buildRedirectsForRoute(route routev1.Route, redirect []url.URL) ([]url.URL, error) {
	scheme := "http"
	if route.Spec.TLS != nil {
		scheme = "https"
	}
	var err error
	for _, ingress := range route.Status.Ingress {
		redirect, err = appendRedirect(scheme, ingress.Host, redirect)
		if err != nil {
			return []url.URL{}, err
		}
	}
	return redirect, nil
}

func appendRedirect(scheme string, host string, redirects []url.URL) ([]url.URL, error) {
	redirect, err := url.Parse(fmt.Sprintf("%s://%s", scheme, host))
	if err != nil {
		return []url.URL{}, err
	}
	redirects = append(redirects, *redirect)
	return redirects, nil
}

func applyOauthClient(oauth *oauthv1.OAuthClient, secret *corev1.Secret, redirects []url.URL) error {
	install.ApplyDefaultLabels(&oauth.ObjectMeta, "oauthclient", oauth.Name)
	bytes := secret.Data["client-secret"]
	oauth.Secret = string(bytes[:])

	oauth.GrantMethod = oauthv1.GrantHandlerAuto
	str_redirects := make([]string, len(redirects))
	for i, v := range redirects {
		str_redirects[i] = v.String()
	}
	oauth.RedirectURIs = str_redirects
	return nil
}

func applyOauthSecret(secret *corev1.Secret) error {

	if secret.Data == nil {
		secret.Data = make(map[string][]byte)
	}

	if _, hassecret := secret.Data["client-secret"]; !hassecret {
		password, err := util.GeneratePassword(32)
		if err != nil {
			return err
		}

		secret.Data["client-secret"] = []byte(password)
	}

	if _, hasid := secret.Data["client-id"]; !hasid {
		secret.Data["client-id"] = []byte(secret.Name)
	}

	return nil
}

func applySsoCookieSecret(secret *corev1.Secret) error {

	if secret.Data == nil {
		secret.Data = make(map[string][]byte)
	}

	if _, hassecret := secret.Data["cookie-secret"]; !hassecret {
		password, err := util.GeneratePassword(32)
		if err != nil {
			return err
		}

		secret.Data["cookie-secret"] = []byte(password)
	}

	return nil
}

func ensureSingletonConsoleService(ctx context.Context, objectMeta metav1.ObjectMeta, c client.Client, r client.Reader) error {

	consoleservice := &v1beta1.ConsoleService{
		ObjectMeta: objectMeta,
	}

	err := r.Get(ctx,
		types.NamespacedName{
			Name:      objectMeta.Name,
			Namespace: objectMeta.Namespace,
		}, consoleservice)
	if err != nil && k8errors.IsNotFound(err) {
		return c.Create(ctx, consoleservice)
	}
	return err
}

func getBooleanAnnotationValue(Annotations map[string]string, name string) bool {
	if Annotations != nil {
		if sval, ok := Annotations[name]; ok {
			if bval, ok := strconv.ParseBool(sval); ok == nil && bval {
				return bval
			}
		}
	}
	return false
}
