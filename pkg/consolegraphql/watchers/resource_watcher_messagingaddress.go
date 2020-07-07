/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

// Code generated by go generate; DO NOT EDIT.

package watchers

import (
	"fmt"
	tp "github.com/enmasseproject/enmasse/pkg/apis/enmasse/v1"
	cp "github.com/enmasseproject/enmasse/pkg/client/clientset/versioned/typed/enmasse/v1"
	"github.com/enmasseproject/enmasse/pkg/consolegraphql/cache"
	"k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/watch"
	"k8s.io/client-go/rest"
	"log"
	"math/rand"
	"reflect"
	"time"
)

type MessagingAddressWatcher struct {
	Namespace string
	cache.Cache
	ClientInterface cp.EnmasseV1Interface
	watching        chan struct{}
	watchingStarted bool
	stopchan        chan struct{}
	stoppedchan     chan struct{}
	create          func(*tp.MessagingAddress) interface{}
	update          func(*tp.MessagingAddress, interface{}) bool
	restartCounter  int32
	resyncInterval  *time.Duration
}

func NewMessagingAddressWatcher(c cache.Cache, resyncInterval *time.Duration, options ...WatcherOption) (ResourceWatcher, error) {

	kw := &MessagingAddressWatcher{
		Namespace:      v1.NamespaceAll,
		Cache:          c,
		watching:       make(chan struct{}),
		stopchan:       make(chan struct{}),
		stoppedchan:    make(chan struct{}),
		resyncInterval: resyncInterval,
		create: func(v *tp.MessagingAddress) interface{} {
			return v
		},
		update: func(v *tp.MessagingAddress, e interface{}) bool {
			if !reflect.DeepEqual(v, e) {
				*e.(*tp.MessagingAddress) = *v
				return true
			} else {
				return false
			}
		},
	}

	for _, option := range options {
		option(kw)
	}

	if kw.ClientInterface == nil {
		return nil, fmt.Errorf("Client must be configured using the MessagingAddressWatcherConfig or MessagingAddressWatcherClient")
	}
	return kw, nil
}

func MessagingAddressWatcherFactory(create func(*tp.MessagingAddress) interface{}, update func(*tp.MessagingAddress, interface{}) bool) WatcherOption {
	return func(watcher ResourceWatcher) error {
		w := watcher.(*MessagingAddressWatcher)
		w.create = create
		w.update = update
		return nil
	}
}

func MessagingAddressWatcherConfig(config *rest.Config) WatcherOption {
	return func(watcher ResourceWatcher) error {
		w := watcher.(*MessagingAddressWatcher)

		var cl interface{}
		cl, _ = cp.NewForConfig(config)

		client, ok := cl.(cp.EnmasseV1Interface)
		if !ok {
			return fmt.Errorf("unexpected type %T", cl)
		}

		w.ClientInterface = client
		return nil
	}
}

// Used to inject the fake client set for testing purposes
func MessagingAddressWatcherClient(client cp.EnmasseV1Interface) WatcherOption {
	return func(watcher ResourceWatcher) error {
		w := watcher.(*MessagingAddressWatcher)
		w.ClientInterface = client
		return nil
	}
}

func (kw *MessagingAddressWatcher) Watch() error {
	go func() {
		defer close(kw.stoppedchan)
		defer func() {
			if !kw.watchingStarted {
				close(kw.watching)
			}
		}()
		resource := kw.ClientInterface.MessagingAddresses(kw.Namespace)
		log.Printf("MessagingAddress - Watching")
		running := true
		for running {
			err := kw.doWatch(resource)
			if err != nil {
				log.Printf("MessagingAddress - Restarting watch - %v", err)
				atomicInc(&kw.restartCounter)
			} else {
				running = false
			}
		}
		log.Printf("MessagingAddress - Watching stopped")
	}()

	return nil
}

func (kw *MessagingAddressWatcher) AwaitWatching() {
	<-kw.watching
}

func (kw *MessagingAddressWatcher) Shutdown() {
	close(kw.stopchan)
	<-kw.stoppedchan
}

func (kw *MessagingAddressWatcher) GetRestartCount() int32 {
	return atomicGet(&kw.restartCounter)
}

func (kw *MessagingAddressWatcher) doWatch(resource cp.MessagingAddressInterface) error {
	resourceList, err := resource.List(v1.ListOptions{})
	if err != nil {
		return err
	}

	keyCreator, err := kw.Cache.GetKeyCreator(cache.PrimaryObjectIndex)
	if err != nil {
		return err
	}
	curr := make(map[string]interface{}, 0)
	_, err = kw.Cache.Get(cache.PrimaryObjectIndex, "MessagingAddress/", func(obj interface{}) (bool, bool, error) {
		gen, key, err := keyCreator(obj)
		if err != nil {
			return false, false, err
		} else if !gen {
			return false, false, fmt.Errorf("failed to generate key for existing object %+v", obj)
		}
		curr[key] = obj
		return false, true, nil
	})

	var added = 0
	var updated = 0
	var unchanged = 0
	for _, res := range resourceList.Items {
		copy := res.DeepCopy()
		kw.updateGroupVersionKind(copy)

		candidate := kw.create(copy)
		gen, key, err := keyCreator(candidate)
		if err != nil {
			return err
		} else if !gen {
			return fmt.Errorf("failed to generate key for new object %+v", copy)
		}
		if existing, ok := curr[key]; ok {
			err = kw.Cache.Update(func(target interface{}) (interface{}, error) {
				if kw.update(copy, target) {
					updated++
					return target, nil
				} else {
					unchanged++
					return nil, nil
				}
			}, existing)
			if err != nil {
				return err
			}
			delete(curr, key)
		} else {
			err = kw.Cache.Add(candidate)
			if err != nil {
				return err
			}
			added++
		}
	}

	// Now remove any stale
	for _, stale := range curr {
		err = kw.Cache.Delete(stale)
		if err != nil {
			return err
		}
	}
	var stale = len(curr)
	log.Printf("MessagingAddress - Cache initialised population added %d, updated %d, unchanged %d, stale %d", added, updated, unchanged, stale)

	watchOptions := v1.ListOptions{
		ResourceVersion: resourceList.ResourceVersion,
	}
	if kw.resyncInterval != nil {
		ts := int64(kw.resyncInterval.Seconds() * (rand.Float64() + 1.0))
		watchOptions.TimeoutSeconds = &ts
	}
	resourceWatch, err := resource.Watch(watchOptions)
	if err != nil {
		return err
	}
	defer resourceWatch.Stop()

	if !kw.watchingStarted {
		close(kw.watching)
		kw.watchingStarted = true
	}

	ch := resourceWatch.ResultChan()
	for {
		select {
		case event, chok := <-ch:
			if !chok {
				return fmt.Errorf("watch ended due to channel error")
			} else if event.Type == watch.Error {
				return fmt.Errorf("watch ended in error")
			}

			var err error
			log.Printf("MessagingAddress - Received event type %s", event.Type)
			res, ok := event.Object.(*tp.MessagingAddress)
			if !ok {
				err = fmt.Errorf("Watch error - object of unexpected type, %T, received", event.Object)
			} else {
				copy := res.DeepCopy()
				kw.updateGroupVersionKind(copy)
				switch event.Type {
				case watch.Added:
					err = kw.Cache.Add(kw.create(copy))
				case watch.Modified:
					updatingKey := kw.create(copy)
					err = kw.Cache.Update(func(target interface{}) (interface{}, error) {
						if kw.update(copy, target) {
							return target, nil
						} else {
							return nil, nil
						}
					}, updatingKey)
				case watch.Deleted:
					err = kw.Cache.Delete(kw.create(copy))
				}
			}

			if err != nil {
				return err
			}
		case <-kw.stopchan:
			log.Printf("MessagingAddress - Shutdown received")
			return nil
		}
	}
}

// KubernetesRBACAccessController relies on the GVK information to be set on objects.
// List provides GVK (https://github.com/kubernetes/kubernetes/pull/63972) but Watch does not not so we set it ourselves.
func (kw *MessagingAddressWatcher) updateGroupVersionKind(o *tp.MessagingAddress) {
	if o.TypeMeta.Kind == "" || o.TypeMeta.APIVersion == "" {
		o.TypeMeta.SetGroupVersionKind(tp.SchemeGroupVersion.WithKind("MessagingAddress"))
	}
}
