/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.common.catalog;

import io.enmasse.systemtest.CustomLogger;
import io.enmasse.systemtest.KeycloakCredentials;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.selenium.ISeleniumProviderFirefox;
import io.enmasse.systemtest.selenium.SeleniumProvider;
import io.enmasse.systemtest.selenium.page.OpenshiftWebPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static io.enmasse.systemtest.TestTag.isolated;

@Tag(isolated)
class ServiceCatalogTest extends TestBase implements ISeleniumProviderFirefox {

    private static Logger log = CustomLogger.getLogger();
    SeleniumProvider selenium = new SeleniumProvider();

    //TODO: remove this (it is only for base test that oc console + selenium works)
    @Test
    void testOpenOCPage() throws Exception {
        selenium.setupDriver(environment, kubernetes, buildDriver());
        OpenshiftWebPage ocPage = new OpenshiftWebPage(selenium, getOCConsoleRoute(),
                new KeycloakCredentials("developer", "developer"));
        ocPage.openOpenshiftPage();
        ocPage.getCatalog();
        ocPage.getServicesFromCatalog();
        ocPage.clickOnCreateBrokered();
    }

}
