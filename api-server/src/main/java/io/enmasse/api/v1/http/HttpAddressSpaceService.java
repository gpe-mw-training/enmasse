/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.api.v1.http;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceList;
import io.enmasse.address.model.AddressSpaceResolver;
import io.enmasse.api.common.SchemaProvider;
import io.enmasse.k8s.api.AddressSpaceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.concurrent.Callable;

@Path(HttpAddressSpaceService.BASE_URI)
public class HttpAddressSpaceService {

    static final String BASE_URI = "/apis/enmasse.io/v1/namespaces/{namespace}/addressspaces";

    private static final Logger log = LoggerFactory.getLogger(HttpAddressSpaceService.class.getName());
    private final SchemaProvider schemaProvider;

    private final AddressSpaceApi addressSpaceApi;
    public HttpAddressSpaceService(AddressSpaceApi addressSpaceApi, SchemaProvider schemaProvider) {
        this.addressSpaceApi = addressSpaceApi;
        this.schemaProvider = schemaProvider;
    }

    private Response doRequest(String errorMessage, Callable<Response> request) throws Exception {
        try {
            return request.call();
        } catch (Exception e) {
            log.error(errorMessage, e);
            throw e;
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getAddressSpaceList(@PathParam("namespace") String namespace) throws Exception {
        return doRequest("Error getting address space list", () ->
                Response.ok(new AddressSpaceList(addressSpaceApi.listAddressSpaces(namespace))).build());
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{addressSpace}")
    public Response getAddressSpace(@PathParam("namespace") String namespace, @PathParam("addressSpace") String addressSpaceName) throws Exception {
        return doRequest("Error getting address space " + addressSpaceName, () ->
            addressSpaceApi.getAddressSpaceWithName(namespace, addressSpaceName)
                    .map(addressSpace -> Response.ok(addressSpace).build())
                    .orElseThrow(() -> new NotFoundException("Address space " + addressSpaceName + " not found")));
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response createAddressSpace(@Context UriInfo uriInfo, @PathParam("namespace") String namespace, @NotNull  AddressSpace input) throws Exception {
        return doRequest("Error creating address space " + input.getName(), () -> {
            AddressSpaceResolver addressSpaceResolver = new AddressSpaceResolver(schemaProvider.getSchema());
            addressSpaceResolver.validate(input);
            addressSpaceApi.createAddressSpace(input);
            AddressSpace created = addressSpaceApi.getAddressSpaceWithName(namespace, input.getName()).get();
            UriBuilder builder = uriInfo.getAbsolutePathBuilder();
            builder.path(created.getName());
            return Response.created(builder.build()).entity(created).build();
        });
    }

    @PUT
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    public Response replaceAddressSpace(@PathParam("namespace") String namespace, @NotNull  AddressSpace input) throws Exception {
        return doRequest("Error replacing address space " + input.getName(), () -> {
            AddressSpaceResolver addressSpaceResolver = new AddressSpaceResolver(schemaProvider.getSchema());
            addressSpaceResolver.validate(input);
            addressSpaceApi.replaceAddressSpace(input);
            AddressSpace replaced = addressSpaceApi.getAddressSpaceWithName(namespace, input.getName()).get();
            return Response.ok().entity(replaced).build();
        });
    }

    @DELETE
    @Path("{addressSpace}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response deleteAddressSpace(@PathParam("namespace") String namespace, @PathParam("addressSpace") String addressSpaceName) throws Exception {
        return doRequest("Error deleting address space " + addressSpaceName, () -> {
            AddressSpace addressSpace = addressSpaceApi.getAddressSpaceWithName(namespace, addressSpaceName)
                    .orElseThrow(() -> new NotFoundException("Unable to find address space " + addressSpaceName));
            addressSpaceApi.deleteAddressSpace(addressSpace);
            return Response.ok().build();
        });
    }
}
