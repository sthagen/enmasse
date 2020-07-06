/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 *
 */

package resolvers

import (
	"github.com/enmasseproject/enmasse/pkg/apis/enmasse/v1"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/agent"
	"github.com/google/uuid"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"
)

func getMetric(name string, metrics []*consolegraphql.Metric) *consolegraphql.Metric {
	for _, m := range metrics {
		if m.Name == name {
			return m
		}
	}
	return nil
}

type messagingProjectHolderOption func(*consolegraphql.MessagingProjectHolder)

func createMessagingProject(addressspace, namespace string, messagingProjectHolderOptions ...messagingProjectHolderOption) *consolegraphql.MessagingProjectHolder {
	ash := &consolegraphql.MessagingProjectHolder{
		MessagingProject: v1.MessagingProject{
			TypeMeta: metav1.TypeMeta{
				Kind: "MessagingProject",
			},
			ObjectMeta: metav1.ObjectMeta{
				Name:      addressspace,
				Namespace: namespace,
				UID:       types.UID(uuid.New().String()),
			},
		},
	}
	for _, holderOptions := range messagingProjectHolderOptions {
		holderOptions(ash)
	}
	return ash
}

func withMessagingProjectAnnotation(name, value string) messagingProjectHolderOption {
	return func(ash *consolegraphql.MessagingProjectHolder) {

		if ash.Annotations == nil {
			ash.Annotations = make(map[string]string)
		}
		ash.Annotations[name] = value
	}
}

func createConnection(host, namespace string, metrics ...*consolegraphql.Metric) *consolegraphql.Connection {
	return &consolegraphql.Connection{
		TypeMeta: metav1.TypeMeta{
			Kind: "Connection",
		},
		ObjectMeta: metav1.ObjectMeta{
			Name:      host,
			Namespace: namespace,
			UID:       types.UID(uuid.New().String()),
		},
		Spec: consolegraphql.ConnectionSpec{
			Namespace: namespace,
		},
		Metrics: metrics,
	}
}

type addressHolderOption func(*consolegraphql.AddressHolder)

func withAddressAnnotation(name, value string) addressHolderOption {
	return func(ah *consolegraphql.AddressHolder) {

		if ah.Annotations == nil {
			ah.Annotations = make(map[string]string)
		}
		ah.Annotations[name] = value
	}
}

func withAddressMetrics(metrics ...*consolegraphql.Metric) addressHolderOption {
	return func(ah *consolegraphql.AddressHolder) {
		if ah.Metrics == nil {
			ah.Metrics = metrics
		} else {
			ah.Metrics = append(ah.Metrics, metrics...)
		}
	}
}

func withAddress(address string) addressHolderOption {
	return func(ah *consolegraphql.AddressHolder) {
		ah.Spec.Address = &address
	}
}

func withAddressType(t string) addressHolderOption {
	return func(ah *consolegraphql.AddressHolder) {
		switch t {
		case "queue":
			ah.Spec.Queue = &v1.MessagingAddressSpecQueue{}
		case "topic":
			ah.Spec.Topic = &v1.MessagingAddressSpecTopic{}
		case "subscription":
			ah.Spec.Subscription = &v1.MessagingAddressSpecSubscription{}
		case "deadLetter":
			ah.Spec.DeadLetter = &v1.MessagingAddressSpecDeadLetter{}
		case "anycast":
			ah.Spec.Anycast = &v1.MessagingAddressSpecAnycast{}
		case "multicast":
			ah.Spec.Multicast = &v1.MessagingAddressSpecMulticast{}
		}
	}
}

func createAddress(namespace, name string, addressHolderOptions ...addressHolderOption) *consolegraphql.AddressHolder {
	ah := &consolegraphql.AddressHolder{
		MessagingAddress: v1.MessagingAddress{
			TypeMeta: metav1.TypeMeta{
				Kind: "MessagingAddress",
			},
			ObjectMeta: metav1.ObjectMeta{
				Name:      name,
				Namespace: namespace,
				UID:       types.UID(uuid.New().String()),
			},
		},
	}

	for _, holderOptions := range addressHolderOptions {
		holderOptions(ah)
	}
	return ah
}

type mockCollector struct {
	delegates map[string]agent.CommandDelegate
}

type mockCommandDelegate struct {
	purged []metav1.ObjectMeta
	closed []metav1.ObjectMeta
}

func (mcd *mockCommandDelegate) PurgeAddress(a metav1.ObjectMeta) error {
	mcd.purged = append(mcd.purged, a)
	return nil
}

func (mcd *mockCommandDelegate) CloseConnection(c metav1.ObjectMeta) error {
	mcd.closed = append(mcd.closed, c)
	return nil
}

func (mcd *mockCommandDelegate) Shutdown() {
	panic("unused")
}

func (mc *mockCollector) CommandDelegate(bearerToken string, impersonateUser string) (agent.CommandDelegate, error) {
	if delegate, present := mc.delegates[bearerToken]; present {
		return delegate, nil
	} else {
		mc.delegates[bearerToken] = &mockCommandDelegate{}
		return mc.delegates[bearerToken], nil
	}
}

func (mc *mockCollector) Collect(handler agent.EventHandler) error {
	panic("unused")
}

func (mc *mockCollector) Shutdown() {
}

var collectors = make(map[string]agent.Delegate, 0)

func getCollector(infraUuid string) agent.Delegate {
	if collector, present := collectors[infraUuid]; present {
		return collector
	} else {
		collectors[infraUuid] = &mockCollector{
			delegates: make(map[string]agent.CommandDelegate, 0),
		}
		return collectors[infraUuid]
	}
}
