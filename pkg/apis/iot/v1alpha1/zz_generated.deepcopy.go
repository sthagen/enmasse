// +build !ignore_autogenerated

/*
 * Copyright 2018-2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by deepcopy-gen. DO NOT EDIT.

package v1alpha1

import (
	json "encoding/json"

	v1beta1 "github.com/enmasseproject/enmasse/pkg/apis/enmasse/v1beta1"
	v1 "k8s.io/api/core/v1"
	runtime "k8s.io/apimachinery/pkg/runtime"
)

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AdapterConfig) DeepCopyInto(out *AdapterConfig) {
	*out = *in
	if in.Enabled != nil {
		in, out := &in.Enabled, &out.Enabled
		*out = new(bool)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AdapterConfig.
func (in *AdapterConfig) DeepCopy() *AdapterConfig {
	if in == nil {
		return nil
	}
	out := new(AdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AdapterEndpointConfig) DeepCopyInto(out *AdapterEndpointConfig) {
	*out = *in
	if in.EnableDefaultRoute != nil {
		in, out := &in.EnableDefaultRoute, &out.EnableDefaultRoute
		*out = new(bool)
		**out = **in
	}
	if in.SecretNameStrategy != nil {
		in, out := &in.SecretNameStrategy, &out.SecretNameStrategy
		*out = new(SecretNameStrategy)
		**out = **in
	}
	if in.KeyCertificateStrategy != nil {
		in, out := &in.KeyCertificateStrategy, &out.KeyCertificateStrategy
		*out = new(KeyCertificateStrategy)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AdapterEndpointConfig.
func (in *AdapterEndpointConfig) DeepCopy() *AdapterEndpointConfig {
	if in == nil {
		return nil
	}
	out := new(AdapterEndpointConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AdapterStatus) DeepCopyInto(out *AdapterStatus) {
	*out = *in
	out.CommonStatus = in.CommonStatus
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AdapterStatus.
func (in *AdapterStatus) DeepCopy() *AdapterStatus {
	if in == nil {
		return nil
	}
	out := new(AdapterStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AdaptersConfig) DeepCopyInto(out *AdaptersConfig) {
	*out = *in
	in.HttpAdapterConfig.DeepCopyInto(&out.HttpAdapterConfig)
	in.MqttAdapterConfig.DeepCopyInto(&out.MqttAdapterConfig)
	in.SigfoxAdapterConfig.DeepCopyInto(&out.SigfoxAdapterConfig)
	in.LoraWanAdapterConfig.DeepCopyInto(&out.LoraWanAdapterConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AdaptersConfig.
func (in *AdaptersConfig) DeepCopy() *AdaptersConfig {
	if in == nil {
		return nil
	}
	out := new(AdaptersConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AddressConfig) DeepCopyInto(out *AddressConfig) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AddressConfig.
func (in *AddressConfig) DeepCopy() *AddressConfig {
	if in == nil {
		return nil
	}
	out := new(AddressConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AddressSpaceConfig) DeepCopyInto(out *AddressSpaceConfig) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AddressSpaceConfig.
func (in *AddressSpaceConfig) DeepCopy() *AddressSpaceConfig {
	if in == nil {
		return nil
	}
	out := new(AddressSpaceConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AddressesConfig) DeepCopyInto(out *AddressesConfig) {
	*out = *in
	out.Telemetry = in.Telemetry
	out.Event = in.Event
	out.Command = in.Command
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AddressesConfig.
func (in *AddressesConfig) DeepCopy() *AddressesConfig {
	if in == nil {
		return nil
	}
	out := new(AddressesConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *AuthenticationServiceConfig) DeepCopyInto(out *AuthenticationServiceConfig) {
	*out = *in
	in.ServiceConfig.DeepCopyInto(&out.ServiceConfig)
	in.CommonServiceConfig.DeepCopyInto(&out.CommonServiceConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new AuthenticationServiceConfig.
func (in *AuthenticationServiceConfig) DeepCopy() *AuthenticationServiceConfig {
	if in == nil {
		return nil
	}
	out := new(AuthenticationServiceConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CollectorConfig) DeepCopyInto(out *CollectorConfig) {
	*out = *in
	if in.Container != nil {
		in, out := &in.Container, &out.Container
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CollectorConfig.
func (in *CollectorConfig) DeepCopy() *CollectorConfig {
	if in == nil {
		return nil
	}
	out := new(CollectorConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CommonAdapterConfig) DeepCopyInto(out *CommonAdapterConfig) {
	*out = *in
	in.ServiceConfig.DeepCopyInto(&out.ServiceConfig)
	in.AdapterConfig.DeepCopyInto(&out.AdapterConfig)
	in.Containers.DeepCopyInto(&out.Containers)
	if in.Java != nil {
		in, out := &in.Java, &out.Java
		*out = new(JavaContainerOptions)
		(*in).DeepCopyInto(*out)
	}
	if in.EndpointConfig != nil {
		in, out := &in.EndpointConfig, &out.EndpointConfig
		*out = new(AdapterEndpointConfig)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CommonAdapterConfig.
func (in *CommonAdapterConfig) DeepCopy() *CommonAdapterConfig {
	if in == nil {
		return nil
	}
	out := new(CommonAdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CommonAdapterContainers) DeepCopyInto(out *CommonAdapterContainers) {
	*out = *in
	if in.Adapter != nil {
		in, out := &in.Adapter, &out.Adapter
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	if in.Proxy != nil {
		in, out := &in.Proxy, &out.Proxy
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	if in.ProxyConfigurator != nil {
		in, out := &in.ProxyConfigurator, &out.ProxyConfigurator
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CommonAdapterContainers.
func (in *CommonAdapterContainers) DeepCopy() *CommonAdapterContainers {
	if in == nil {
		return nil
	}
	out := new(CommonAdapterContainers)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CommonCondition) DeepCopyInto(out *CommonCondition) {
	*out = *in
	in.LastTransitionTime.DeepCopyInto(&out.LastTransitionTime)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CommonCondition.
func (in *CommonCondition) DeepCopy() *CommonCondition {
	if in == nil {
		return nil
	}
	out := new(CommonCondition)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CommonServiceConfig) DeepCopyInto(out *CommonServiceConfig) {
	*out = *in
	if in.Container != nil {
		in, out := &in.Container, &out.Container
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	if in.Java != nil {
		in, out := &in.Java, &out.Java
		*out = new(JavaContainerOptions)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CommonServiceConfig.
func (in *CommonServiceConfig) DeepCopy() *CommonServiceConfig {
	if in == nil {
		return nil
	}
	out := new(CommonServiceConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *CommonStatus) DeepCopyInto(out *CommonStatus) {
	*out = *in
	out.Endpoint = in.Endpoint
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new CommonStatus.
func (in *CommonStatus) DeepCopy() *CommonStatus {
	if in == nil {
		return nil
	}
	out := new(CommonStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ConnectionInformation) DeepCopyInto(out *ConnectionInformation) {
	*out = *in
	out.Credentials = in.Credentials
	if in.Certificate != nil {
		in, out := &in.Certificate, &out.Certificate
		*out = make([]byte, len(*in))
		copy(*out, *in)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ConnectionInformation.
func (in *ConnectionInformation) DeepCopy() *ConnectionInformation {
	if in == nil {
		return nil
	}
	out := new(ConnectionInformation)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ContainerConfig) DeepCopyInto(out *ContainerConfig) {
	*out = *in
	if in.Resources != nil {
		in, out := &in.Resources, &out.Resources
		*out = new(v1.ResourceRequirements)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ContainerConfig.
func (in *ContainerConfig) DeepCopy() *ContainerConfig {
	if in == nil {
		return nil
	}
	out := new(ContainerConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *Credentials) DeepCopyInto(out *Credentials) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new Credentials.
func (in *Credentials) DeepCopy() *Credentials {
	if in == nil {
		return nil
	}
	out := new(Credentials)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *DeviceRegistryServiceConfig) DeepCopyInto(out *DeviceRegistryServiceConfig) {
	*out = *in
	in.ServiceConfig.DeepCopyInto(&out.ServiceConfig)
	if in.File != nil {
		in, out := &in.File, &out.File
		*out = new(FileBasedDeviceRegistry)
		(*in).DeepCopyInto(*out)
	}
	if in.Infinispan != nil {
		in, out := &in.Infinispan, &out.Infinispan
		*out = new(InfinispanDeviceRegistry)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new DeviceRegistryServiceConfig.
func (in *DeviceRegistryServiceConfig) DeepCopy() *DeviceRegistryServiceConfig {
	if in == nil {
		return nil
	}
	out := new(DeviceRegistryServiceConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *DownstreamStrategy) DeepCopyInto(out *DownstreamStrategy) {
	*out = *in
	if in.ExternalDownstreamStrategy != nil {
		in, out := &in.ExternalDownstreamStrategy, &out.ExternalDownstreamStrategy
		*out = new(ExternalDownstreamStrategy)
		(*in).DeepCopyInto(*out)
	}
	if in.ProvidedDownstreamStrategy != nil {
		in, out := &in.ProvidedDownstreamStrategy, &out.ProvidedDownstreamStrategy
		*out = new(ProvidedDownstreamStrategy)
		(*in).DeepCopyInto(*out)
	}
	if in.ManagedDownstreamStrategy != nil {
		in, out := &in.ManagedDownstreamStrategy, &out.ManagedDownstreamStrategy
		*out = new(ManagedDownstreamStrategy)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new DownstreamStrategy.
func (in *DownstreamStrategy) DeepCopy() *DownstreamStrategy {
	if in == nil {
		return nil
	}
	out := new(DownstreamStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *EndpointStatus) DeepCopyInto(out *EndpointStatus) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new EndpointStatus.
func (in *EndpointStatus) DeepCopy() *EndpointStatus {
	if in == nil {
		return nil
	}
	out := new(EndpointStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ExternalCacheNames) DeepCopyInto(out *ExternalCacheNames) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ExternalCacheNames.
func (in *ExternalCacheNames) DeepCopy() *ExternalCacheNames {
	if in == nil {
		return nil
	}
	out := new(ExternalCacheNames)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ExternalDownstreamStrategy) DeepCopyInto(out *ExternalDownstreamStrategy) {
	*out = *in
	in.ConnectionInformation.DeepCopyInto(&out.ConnectionInformation)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ExternalDownstreamStrategy.
func (in *ExternalDownstreamStrategy) DeepCopy() *ExternalDownstreamStrategy {
	if in == nil {
		return nil
	}
	out := new(ExternalDownstreamStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ExternalInfinispanServer) DeepCopyInto(out *ExternalInfinispanServer) {
	*out = *in
	out.Credentials = in.Credentials
	if in.CacheNames != nil {
		in, out := &in.CacheNames, &out.CacheNames
		*out = new(ExternalCacheNames)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ExternalInfinispanServer.
func (in *ExternalInfinispanServer) DeepCopy() *ExternalInfinispanServer {
	if in == nil {
		return nil
	}
	out := new(ExternalInfinispanServer)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *FileBasedDeviceRegistry) DeepCopyInto(out *FileBasedDeviceRegistry) {
	*out = *in
	if in.NumberOfDevicesPerTenant != nil {
		in, out := &in.NumberOfDevicesPerTenant, &out.NumberOfDevicesPerTenant
		*out = new(uint32)
		**out = **in
	}
	in.CommonServiceConfig.DeepCopyInto(&out.CommonServiceConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new FileBasedDeviceRegistry.
func (in *FileBasedDeviceRegistry) DeepCopy() *FileBasedDeviceRegistry {
	if in == nil {
		return nil
	}
	out := new(FileBasedDeviceRegistry)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *HttpAdapterConfig) DeepCopyInto(out *HttpAdapterConfig) {
	*out = *in
	in.CommonAdapterConfig.DeepCopyInto(&out.CommonAdapterConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new HttpAdapterConfig.
func (in *HttpAdapterConfig) DeepCopy() *HttpAdapterConfig {
	if in == nil {
		return nil
	}
	out := new(HttpAdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *InfinispanDeviceRegistry) DeepCopyInto(out *InfinispanDeviceRegistry) {
	*out = *in
	in.Server.DeepCopyInto(&out.Server)
	in.CommonServiceConfig.DeepCopyInto(&out.CommonServiceConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new InfinispanDeviceRegistry.
func (in *InfinispanDeviceRegistry) DeepCopy() *InfinispanDeviceRegistry {
	if in == nil {
		return nil
	}
	out := new(InfinispanDeviceRegistry)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *InfinispanServer) DeepCopyInto(out *InfinispanServer) {
	*out = *in
	if in.External != nil {
		in, out := &in.External, &out.External
		*out = new(ExternalInfinispanServer)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new InfinispanServer.
func (in *InfinispanServer) DeepCopy() *InfinispanServer {
	if in == nil {
		return nil
	}
	out := new(InfinispanServer)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *InterServiceCertificates) DeepCopyInto(out *InterServiceCertificates) {
	*out = *in
	if in.SecretCertificatesStrategy != nil {
		in, out := &in.SecretCertificatesStrategy, &out.SecretCertificatesStrategy
		*out = new(SecretCertificatesStrategy)
		(*in).DeepCopyInto(*out)
	}
	if in.ServiceCAStrategy != nil {
		in, out := &in.ServiceCAStrategy, &out.ServiceCAStrategy
		*out = new(ServiceCAStrategy)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new InterServiceCertificates.
func (in *InterServiceCertificates) DeepCopy() *InterServiceCertificates {
	if in == nil {
		return nil
	}
	out := new(InterServiceCertificates)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTConfig) DeepCopyInto(out *IoTConfig) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTConfig.
func (in *IoTConfig) DeepCopy() *IoTConfig {
	if in == nil {
		return nil
	}
	out := new(IoTConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject is an autogenerated deepcopy function, copying the receiver, creating a new runtime.Object.
func (in *IoTConfig) DeepCopyObject() runtime.Object {
	if c := in.DeepCopy(); c != nil {
		return c
	}
	return nil
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTConfigList) DeepCopyInto(out *IoTConfigList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	out.ListMeta = in.ListMeta
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]IoTConfig, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTConfigList.
func (in *IoTConfigList) DeepCopy() *IoTConfigList {
	if in == nil {
		return nil
	}
	out := new(IoTConfigList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject is an autogenerated deepcopy function, copying the receiver, creating a new runtime.Object.
func (in *IoTConfigList) DeepCopyObject() runtime.Object {
	if c := in.DeepCopy(); c != nil {
		return c
	}
	return nil
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTConfigSpec) DeepCopyInto(out *IoTConfigSpec) {
	*out = *in
	if in.EnableDefaultRoutes != nil {
		in, out := &in.EnableDefaultRoutes, &out.EnableDefaultRoutes
		*out = new(bool)
		**out = **in
	}
	if in.ImageOverrides != nil {
		in, out := &in.ImageOverrides, &out.ImageOverrides
		*out = make(map[string]v1beta1.ImageOverride, len(*in))
		for key, val := range *in {
			(*out)[key] = val
		}
	}
	if in.InterServiceCertificates != nil {
		in, out := &in.InterServiceCertificates, &out.InterServiceCertificates
		*out = new(InterServiceCertificates)
		(*in).DeepCopyInto(*out)
	}
	in.JavaDefaults.DeepCopyInto(&out.JavaDefaults)
	in.ServicesConfig.DeepCopyInto(&out.ServicesConfig)
	in.AdaptersConfig.DeepCopyInto(&out.AdaptersConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTConfigSpec.
func (in *IoTConfigSpec) DeepCopy() *IoTConfigSpec {
	if in == nil {
		return nil
	}
	out := new(IoTConfigSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTConfigStatus) DeepCopyInto(out *IoTConfigStatus) {
	*out = *in
	if in.AuthenticationServicePSK != nil {
		in, out := &in.AuthenticationServicePSK, &out.AuthenticationServicePSK
		*out = new(string)
		**out = **in
	}
	if in.Adapters != nil {
		in, out := &in.Adapters, &out.Adapters
		*out = make(map[string]AdapterStatus, len(*in))
		for key, val := range *in {
			(*out)[key] = val
		}
	}
	if in.Services != nil {
		in, out := &in.Services, &out.Services
		*out = make(map[string]ServiceStatus, len(*in))
		for key, val := range *in {
			(*out)[key] = val
		}
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTConfigStatus.
func (in *IoTConfigStatus) DeepCopy() *IoTConfigStatus {
	if in == nil {
		return nil
	}
	out := new(IoTConfigStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTProject) DeepCopyInto(out *IoTProject) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTProject.
func (in *IoTProject) DeepCopy() *IoTProject {
	if in == nil {
		return nil
	}
	out := new(IoTProject)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject is an autogenerated deepcopy function, copying the receiver, creating a new runtime.Object.
func (in *IoTProject) DeepCopyObject() runtime.Object {
	if c := in.DeepCopy(); c != nil {
		return c
	}
	return nil
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTProjectList) DeepCopyInto(out *IoTProjectList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	out.ListMeta = in.ListMeta
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]IoTProject, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTProjectList.
func (in *IoTProjectList) DeepCopy() *IoTProjectList {
	if in == nil {
		return nil
	}
	out := new(IoTProjectList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject is an autogenerated deepcopy function, copying the receiver, creating a new runtime.Object.
func (in *IoTProjectList) DeepCopyObject() runtime.Object {
	if c := in.DeepCopy(); c != nil {
		return c
	}
	return nil
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTProjectSpec) DeepCopyInto(out *IoTProjectSpec) {
	*out = *in
	in.DownstreamStrategy.DeepCopyInto(&out.DownstreamStrategy)
	if in.Configuration != nil {
		in, out := &in.Configuration, &out.Configuration
		*out = make(json.RawMessage, len(*in))
		copy(*out, *in)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTProjectSpec.
func (in *IoTProjectSpec) DeepCopy() *IoTProjectSpec {
	if in == nil {
		return nil
	}
	out := new(IoTProjectSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *IoTProjectStatus) DeepCopyInto(out *IoTProjectStatus) {
	*out = *in
	if in.DownstreamEndpoint != nil {
		in, out := &in.DownstreamEndpoint, &out.DownstreamEndpoint
		*out = new(ConnectionInformation)
		(*in).DeepCopyInto(*out)
	}
	if in.Managed != nil {
		in, out := &in.Managed, &out.Managed
		*out = new(ManagedStatus)
		(*in).DeepCopyInto(*out)
	}
	if in.Conditions != nil {
		in, out := &in.Conditions, &out.Conditions
		*out = make([]ProjectCondition, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new IoTProjectStatus.
func (in *IoTProjectStatus) DeepCopy() *IoTProjectStatus {
	if in == nil {
		return nil
	}
	out := new(IoTProjectStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *JavaContainerDefaults) DeepCopyInto(out *JavaContainerDefaults) {
	*out = *in
	if in.RequireNativeTls != nil {
		in, out := &in.RequireNativeTls, &out.RequireNativeTls
		*out = new(bool)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new JavaContainerDefaults.
func (in *JavaContainerDefaults) DeepCopy() *JavaContainerDefaults {
	if in == nil {
		return nil
	}
	out := new(JavaContainerDefaults)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *JavaContainerOptions) DeepCopyInto(out *JavaContainerOptions) {
	*out = *in
	if in.RequireNativeTls != nil {
		in, out := &in.RequireNativeTls, &out.RequireNativeTls
		*out = new(bool)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new JavaContainerOptions.
func (in *JavaContainerOptions) DeepCopy() *JavaContainerOptions {
	if in == nil {
		return nil
	}
	out := new(JavaContainerOptions)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *KeyCertificateStrategy) DeepCopyInto(out *KeyCertificateStrategy) {
	*out = *in
	if in.Key != nil {
		in, out := &in.Key, &out.Key
		*out = make([]byte, len(*in))
		copy(*out, *in)
	}
	if in.Certificate != nil {
		in, out := &in.Certificate, &out.Certificate
		*out = make([]byte, len(*in))
		copy(*out, *in)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new KeyCertificateStrategy.
func (in *KeyCertificateStrategy) DeepCopy() *KeyCertificateStrategy {
	if in == nil {
		return nil
	}
	out := new(KeyCertificateStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *LoraWanAdapterConfig) DeepCopyInto(out *LoraWanAdapterConfig) {
	*out = *in
	in.CommonAdapterConfig.DeepCopyInto(&out.CommonAdapterConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new LoraWanAdapterConfig.
func (in *LoraWanAdapterConfig) DeepCopy() *LoraWanAdapterConfig {
	if in == nil {
		return nil
	}
	out := new(LoraWanAdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ManagedDownstreamStrategy) DeepCopyInto(out *ManagedDownstreamStrategy) {
	*out = *in
	out.AddressSpace = in.AddressSpace
	out.Addresses = in.Addresses
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ManagedDownstreamStrategy.
func (in *ManagedDownstreamStrategy) DeepCopy() *ManagedDownstreamStrategy {
	if in == nil {
		return nil
	}
	out := new(ManagedDownstreamStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ManagedStatus) DeepCopyInto(out *ManagedStatus) {
	*out = *in
	in.PasswordTime.DeepCopyInto(&out.PasswordTime)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ManagedStatus.
func (in *ManagedStatus) DeepCopy() *ManagedStatus {
	if in == nil {
		return nil
	}
	out := new(ManagedStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *MqttAdapterConfig) DeepCopyInto(out *MqttAdapterConfig) {
	*out = *in
	in.CommonAdapterConfig.DeepCopyInto(&out.CommonAdapterConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new MqttAdapterConfig.
func (in *MqttAdapterConfig) DeepCopy() *MqttAdapterConfig {
	if in == nil {
		return nil
	}
	out := new(MqttAdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *OperatorConfig) DeepCopyInto(out *OperatorConfig) {
	*out = *in
	in.ServiceConfig.DeepCopyInto(&out.ServiceConfig)
	if in.Container != nil {
		in, out := &in.Container, &out.Container
		*out = new(ContainerConfig)
		(*in).DeepCopyInto(*out)
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new OperatorConfig.
func (in *OperatorConfig) DeepCopy() *OperatorConfig {
	if in == nil {
		return nil
	}
	out := new(OperatorConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ProjectCondition) DeepCopyInto(out *ProjectCondition) {
	*out = *in
	in.CommonCondition.DeepCopyInto(&out.CommonCondition)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ProjectCondition.
func (in *ProjectCondition) DeepCopy() *ProjectCondition {
	if in == nil {
		return nil
	}
	out := new(ProjectCondition)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ProvidedDownstreamStrategy) DeepCopyInto(out *ProvidedDownstreamStrategy) {
	*out = *in
	out.Credentials = in.Credentials
	if in.EndpointMode != nil {
		in, out := &in.EndpointMode, &out.EndpointMode
		*out = new(EndpointMode)
		**out = **in
	}
	if in.TLS != nil {
		in, out := &in.TLS, &out.TLS
		*out = new(bool)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ProvidedDownstreamStrategy.
func (in *ProvidedDownstreamStrategy) DeepCopy() *ProvidedDownstreamStrategy {
	if in == nil {
		return nil
	}
	out := new(ProvidedDownstreamStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *SecretCertificatesStrategy) DeepCopyInto(out *SecretCertificatesStrategy) {
	*out = *in
	if in.ServiceSecretNames != nil {
		in, out := &in.ServiceSecretNames, &out.ServiceSecretNames
		*out = make(map[string]string, len(*in))
		for key, val := range *in {
			(*out)[key] = val
		}
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new SecretCertificatesStrategy.
func (in *SecretCertificatesStrategy) DeepCopy() *SecretCertificatesStrategy {
	if in == nil {
		return nil
	}
	out := new(SecretCertificatesStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *SecretNameStrategy) DeepCopyInto(out *SecretNameStrategy) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new SecretNameStrategy.
func (in *SecretNameStrategy) DeepCopy() *SecretNameStrategy {
	if in == nil {
		return nil
	}
	out := new(SecretNameStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ServiceCAStrategy) DeepCopyInto(out *ServiceCAStrategy) {
	*out = *in
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ServiceCAStrategy.
func (in *ServiceCAStrategy) DeepCopy() *ServiceCAStrategy {
	if in == nil {
		return nil
	}
	out := new(ServiceCAStrategy)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ServiceConfig) DeepCopyInto(out *ServiceConfig) {
	*out = *in
	if in.Replicas != nil {
		in, out := &in.Replicas, &out.Replicas
		*out = new(int32)
		**out = **in
	}
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ServiceConfig.
func (in *ServiceConfig) DeepCopy() *ServiceConfig {
	if in == nil {
		return nil
	}
	out := new(ServiceConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ServiceStatus) DeepCopyInto(out *ServiceStatus) {
	*out = *in
	out.CommonStatus = in.CommonStatus
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ServiceStatus.
func (in *ServiceStatus) DeepCopy() *ServiceStatus {
	if in == nil {
		return nil
	}
	out := new(ServiceStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *ServicesConfig) DeepCopyInto(out *ServicesConfig) {
	*out = *in
	in.DeviceRegistry.DeepCopyInto(&out.DeviceRegistry)
	in.Authentication.DeepCopyInto(&out.Authentication)
	in.Tenant.DeepCopyInto(&out.Tenant)
	in.Collector.DeepCopyInto(&out.Collector)
	in.Operator.DeepCopyInto(&out.Operator)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new ServicesConfig.
func (in *ServicesConfig) DeepCopy() *ServicesConfig {
	if in == nil {
		return nil
	}
	out := new(ServicesConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *SigfoxAdapterConfig) DeepCopyInto(out *SigfoxAdapterConfig) {
	*out = *in
	in.CommonAdapterConfig.DeepCopyInto(&out.CommonAdapterConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new SigfoxAdapterConfig.
func (in *SigfoxAdapterConfig) DeepCopy() *SigfoxAdapterConfig {
	if in == nil {
		return nil
	}
	out := new(SigfoxAdapterConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto is an autogenerated deepcopy function, copying the receiver, writing into out. in must be non-nil.
func (in *TenantServiceConfig) DeepCopyInto(out *TenantServiceConfig) {
	*out = *in
	in.ServiceConfig.DeepCopyInto(&out.ServiceConfig)
	in.CommonServiceConfig.DeepCopyInto(&out.CommonServiceConfig)
	return
}

// DeepCopy is an autogenerated deepcopy function, copying the receiver, creating a new TenantServiceConfig.
func (in *TenantServiceConfig) DeepCopy() *TenantServiceConfig {
	if in == nil {
		return nil
	}
	out := new(TenantServiceConfig)
	in.DeepCopyInto(out)
	return out
}
