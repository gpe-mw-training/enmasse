/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.api.v1;

import java.io.IOException;
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

    private void verifyAuthorized(SecurityContext securityContext, AddressSpace addressSpace, ResourceVerb verb) {
        if (!securityContext.isUserInRole(RbacSecurityContext.rbacToRole(addressSpace.getNamespace(), verb))) {
            throw Exceptions.notAuthorizedException();
        }
    }

    public AddressList getAddresses(SecurityContext securityContext, String namespace) {
        AddressList addressList = new AddressList();
        Set<AddressSpace> addressSpaceList = addressSpaceApi.listAddressSpaces(namespace);
        for (AddressSpace addressSpace : addressSpaceList) {
            if (securityContext.isUserInRole(RbacSecurityContext.rbacToRole(addressSpace.getNamespace(), ResourceVerb.list))) {
                addressList.addAll(addressSpaceApi.withAddressSpace(addressSpace).listAddresses());
            }
        }
        return addressList;
    }

    public AddressList putAddresses(SecurityContext securityContext, String addressSpaceId, AddressList addressList) throws Exception {
        AddressSpace addressSpace = getAddressSpace(addressSpaceId);
        verifyAuthorized(securityContext, addressSpace, ResourceVerb.create);
        validateAddresses(addressSpace, addressList);
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);

        Set<Address> toRemove = new HashSet<>(addressApi.listAddresses());
        toRemove.removeAll(addressList);
        toRemove.forEach(addressApi::deleteAddress);
        addressList.forEach(addressApi::createAddress);
        return new AddressList(addressApi.listAddresses());
    }

    private void validateAddresses(AddressSpace addressSpace, AddressList addressList) {
        Schema schema = schemaProvider.getSchema();
        AddressSpaceType type = schema.findAddressSpaceType(addressSpace.getType()).orElseThrow(() -> new UnresolvedAddressSpaceException("Unable to resolve address space type " + addressSpace.getType()));

        AddressResolver addressResolver = new AddressResolver(schema, type);
        Set<Address> existingAddresses = addressSpaceApi.withAddressSpace(addressSpace).listAddresses();
        for (Address address : addressList) {
            addressResolver.validate(address);
            for (Address existing : existingAddresses) {
                if (address.getAddress().equals(existing.getAddress()) && !address.getName().equals(existing.getName())) {
                    throw new BadRequestException("Address '" + address.getAddress() + "' already exists with resource name '" + existing.getName() + "'");
                }
            }

            for (Address b : addressList) {
                if (address.getAddress().equals(b.getAddress()) && !address.getName().equals(b.getName())) {
                    throw new BadRequestException("Address '" + address.getAddress() + "' defined in resource names '" + address.getName() + "' and '" + b.getName() + "'");
                }
            }
        }
    }

    private AddressSpace getAddressSpace(String addressSpaceId) throws Exception {
        return addressSpaceApi.getAddressSpaceWithName(addressSpaceId)
                .orElseThrow(() -> new NotFoundException("Address space " + addressSpaceId + " not found"));
    }

    public Optional<Address> getAddress(SecurityContext securityContext, String namespace, String address) throws Exception {
        for (AddressSpace addressSpace : addressSpaceApi.listAddressSpaces(namespace)) {
            verifyAuthorized(securityContext, addressSpace, ResourceVerb.get);
            Optional<Address> object = addressSpaceApi.withAddressSpace(addressSpace).getAddressWithName(address);

    }

    public AddressList deleteAddress(SecurityContext securityContext, String addressSpaceId, String name) throws Exception {
        AddressSpace addressSpace = getAddressSpace(addressSpaceId);
        verifyAuthorized(securityContext, addressSpace, ResourceVerb.delete);
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
        addressApi.getAddressWithName(name).ifPresent(addressApi::deleteAddress);
        return new AddressList(addressApi.listAddresses());
    }

    public AddressList appendAddresses(SecurityContext securityContext, AddressList addressList) throws Exception {
        Map<String, AddressList> perAddressSpaceList = new HashMap<>();
        for (Address address : addressList) {
            if (address.getAddressSpace() == null) {
                throw new BadRequestException("Address '" + address.getAddress() + "' is missing addressSpace");
            }
            AddressList perList = perAddressSpaceList.get(address.getAddressSpace());
            if (perList == null) {
                perList = new AddressList();
                perAddressSpaceList.put(address.getAddressSpace(), perList);
            }
            perList.add(address);
        }

        for (Map.Entry<String, AddressList> entry : perAddressSpaceList.entrySet()) {
            appendAddresses(securityContext, entry.getKey(), entry.getValue());
        }
        return addressList;
    }

    public AddressList appendAddresses(SecurityContext securityContext, String addressSpaceId, AddressList addressList) throws Exception {
        AddressSpace addressSpace = getAddressSpace(addressSpaceId);
        verifyAuthorized(securityContext, addressSpace, ResourceVerb.create);
        validateAddresses(addressSpace, addressList);
        AddressApi addressApi = addressSpaceApi.withAddressSpace(addressSpace);
        for (Address address : addressList) {
            addressApi.createAddress(address);
        }
        return new AddressList(addressApi.listAddresses());
    }

}
