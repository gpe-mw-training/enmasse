/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.api.v1.http;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressList;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.api.common.DefaultExceptionMapper;
import io.enmasse.api.server.TestSchemaProvider;
import io.enmasse.k8s.api.TestAddressApi;
import io.enmasse.k8s.api.TestAddressSpaceApi;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.concurrent.Callable;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class HttpAddressServiceTest {
    private HttpAddressService addressService;
    private TestAddressSpaceApi addressSpaceApi;
    private TestAddressApi addressApi;
    private Address q1;
    private Address a1;
    private DefaultExceptionMapper exceptionMapper = new DefaultExceptionMapper();

    @Before
    public void setup() {
        addressSpaceApi = new TestAddressSpaceApi();
        this.addressService = new HttpAddressService(addressSpaceApi, new TestSchemaProvider());

        AddressSpace addressSpace = new AddressSpace.Builder()
                .setName("myspace")
                .setType("type1")
                .setPlan("myplan")
                .build();

        addressSpaceApi.createAddressSpace(addressSpace);
        addressApi = (TestAddressApi) addressSpaceApi.withAddressSpace(addressSpace);
        q1 = new Address.Builder()
                .setName("q1")
                .setAddress("Q1")
                .setAddressSpace("myspace")
                .setNamespace("ns")
                .setType("queue")
                .build();
        a1 = new Address.Builder()
                .setName("a1")
                .setAddress("A1")
                .setAddressSpace("myspace")
                .setNamespace("ns")
                .setType("anycast")
                .build();
        addressApi.createAddress(q1);
        addressApi.createAddress(a1);
    }

    private Response invoke(Callable<Response> fn) {
        try {
            return fn.call();
        } catch (Exception e) {
            return exceptionMapper.toResponse(e);
        }
    }

    @Test
    public void testList() {
        Response response = invoke(() -> addressService.getAddressList(null, null));

        assertThat(response.getStatus(), is(200));
        AddressList list = (AddressList) response.getEntity();

        assertThat(list.size(), is(2));
        assertThat(list, hasItem(q1));
        assertThat(list, hasItem(a1));
    }

    @Test
    public void testGetByAddress() {
        Response response = invoke(() -> addressService.getAddressList(null, "A1"));

        assertThat(response.getStatus(), is(200));
        Address address = (Address) response.getEntity();

        assertThat(address, is(a1));
    }

    @Test
    public void testGetByAddressNotFound() {
        Response response = invoke(() -> addressService.getAddressList(null,"b1"));

        assertThat(response.getStatus(), is(404));
    }

    @Test
    public void testListException() {
        addressApi.throwException = true;
        Response response = invoke(() -> addressService.getAddressList(null, null));
        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void testGet() {
        Response response = invoke(() -> addressService.getAddress(null, "q1"));
        assertThat(response.getStatus(), is(200));
        Address address = (Address) response.getEntity();

        assertThat(address, is(q1));
    }

    @Test
    public void testGetException() {
        addressApi.throwException = true;
        Response response = invoke(() -> addressService.getAddress(null, "q1"));
        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void testGetUnknown() {
        Response response = invoke(() -> addressService.getAddress(null, "doesnotexist"));
        assertThat(response.getStatus(), is(404));
    }


    @Test
    public void testCreate() {
        Address a2 = new Address.Builder()
                .setAddress("a2")
                .setType("anycast")
                .setPlan("plan1")
                .setAddressSpace("myspace")
                .build();
        Response response = invoke(() -> addressService.createAddress(new ResteasyUriInfo("http://localhost:8443/", null, "/"), "ns", a2));
        assertThat(response.getStatus(), is(201));

        Address a2ns = new Address.Builder(a2).setNamespace("ns").build();
        assertThat(addressApi.listAddresses(null), hasItem(a2ns));
    }

    @Test
    public void testCreateException() {
        addressApi.throwException = true;
        Address a2 = new Address.Builder()
                .setAddress("a2")
                .setPlan("plan1")
                .setAddressSpace("myspace")
                .setType("anycast")
                .build();
        Response response = invoke(() -> addressService.createAddress(null, null, a2));
        assertThat(response.getStatus(), is(500));
    }

    @Test
    public void testDelete() {
        Response response = invoke(() -> addressService.deleteAddress("ns", "a1"));
        assertThat(response.getStatus(), is(200));

        assertThat(addressApi.listAddresses(null), hasItem(q1));
        assertThat(addressApi.listAddresses(null).size(), is(1));
    }

    @Test
    public void testDeleteException() {
        addressApi.throwException = true;
        Response response = invoke(() -> addressService.deleteAddress("ns", "a1"));
        assertThat(response.getStatus(), is(500));
    }
}
