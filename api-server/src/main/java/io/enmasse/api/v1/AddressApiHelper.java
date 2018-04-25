/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.api.v1;

import java.util.*;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.SecurityContext;

import io.enmasse.address.model.*;
import io.enmasse.api.common.Exceptions;
import io.enmasse.api.common.SchemaProvider;
import io.enmasse.api.auth.RbacSecurityContext;
import io.enmasse.api.auth.ResourceVerb;
import io.enmasse.k8s.api.AddressApi;
import io.enmasse.k8s.api.AddressSpaceApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a handler for doing operations on the addressing manager that works independent of AMQP and HTTP.
 */
public class AddressApiHelper {
    private static final Logger log = LoggerFactory.getLogger(AddressApiHelper.class.getName());
    private final AddressSpaceApi addressSpaceApi;
    private final SchemaProvider schemaProvider;

    public AddressApiHelper(AddressSpaceApi addressSpaceApi, SchemaProvider schemaProvider) {
        this.addressSpaceApi = addressSpaceApi;
        this.schemaProvider = schemaProvider;
    }

    public AddressList getAddresses(String namespace) {
        AddressList list = new AddressList();
        for (AddressSpace addressSpace : addressSpaceApi.listAddressSpaces(namespace)) {
            list.addAll(addressSpaceApi.withAddressSpace(addressSpace).listAddresses(namespace));
        }
        return list;
    }

    private void validateAddress(AddressSpace addressSpace, Address address) {
        Schema schema = schemaProvider.getSchema();
        AddressSpaceType type = schema.findAddressSpaceType(addressSpace.getType()).orElseThrow(() -> new UnresolvedAddressSpaceException("Unable to resolve address space type " + addressSpace.getType()));

        AddressResolver addressResolver = new AddressResolver(schema, type);
        Set<Address> existingAddresses = addressSpaceApi.withAddressSpace(addressSpace).listAddresses(address.getNamespace());
        addressResolver.validate(address);
        for (Address existing : existingAddresses) {
            if (address.getAddress().equals(existing.getAddress()) && !address.getName().equals(existing.getName())) {
                throw new BadRequestException("Address '" + address.getAddress() + "' already exists with resource name '" + existing.getName() + "'");
            }
        }
    }

    private AddressSpace getAddressSpace(String namespace, String addressSpaceId) throws Exception {
        return addressSpaceApi.getAddressSpaceWithName(namespace, addressSpaceId)
                .orElseThrow(() -> new NotFoundException("Address space " + addressSpaceId + " not found"));
    }

    public Optional<Address> getAddress(String namespace, String address) throws Exception {
        for (AddressSpace addressSpace : addressSpaceApi.listAddressSpaces(namespace)) {
            Optional<Address> object = addressSpaceApi.withAddressSpace(addressSpace).getAddressWithName(namespace, address);
            if (object.isPresent()) {
                return object;
            }
        }
        return Optional.empty();
    }

    public void deleteAddress(String namespace, String name) throws Exception {
        for (AddressSpace addressSpace : addressSpaceApi.listAddressSpaces(namespace)) {
            AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
            for (Address address : addressApi.listAddresses(namespace)) {
                if (address.getNamespace().equals(namespace) && address.getName().equals(name)) {
                    addressApi.deleteAddress(address);
                    return;
                }
            }
        }
    }

    public Address createAddress(Address address) throws Exception {
        AddressSpace addressSpace = getAddressSpace(address.getNamespace(), address.getAddressSpace());
        validateAddress(addressSpace, address);
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
        addressApi.createAddress(address);
        return address;
    }

    public Address replaceAddress(Address address) throws Exception {
        AddressSpace addressSpace = getAddressSpace(address.getNamespace(), address.getAddressSpace());
        validateAddress(addressSpace, address);
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
        addressApi.replaceAddress(address);
        return address;
    }

    public static Map<String,String> parseLabelSelector(String labelSelector) {
        Map<String, String> labels = new HashMap<>();
        String [] pairs = labelSelector.split(",");
        for (String pair : pairs) {
            String elements[] = pair.split("=");
            if (elements.length > 2) {
                labels.put(elements[0], elements[1]);
            }
        }
        return labels;
    }

    public AddressList getAddressesWithLabels(String namespace, Map<String, String> labels) {
        AddressList list = new AddressList();
        for (AddressSpace addressSpace : addressSpaceApi.listAddressSpaces(namespace)) {
            list.addAll(addressSpaceApi.withAddressSpace(addressSpace).listAddressesWithLabels(namespace, labels));
        }
        return list;
    }
}
