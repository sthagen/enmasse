/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import io.enmasse.common.model.DefaultCustomResource;
import io.fabric8.kubernetes.client.CustomResourceList;

/**
 * Type for address space list
 */
@DefaultCustomResource
@SuppressWarnings("serial")
public class AddressSpaceList extends CustomResourceList<AddressSpace> {
    public static final String KIND = "AddressSpaceList";
}
