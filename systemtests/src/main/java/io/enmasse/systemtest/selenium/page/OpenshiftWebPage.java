/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.selenium.page;


import io.enmasse.systemtest.CustomLogger;
import io.enmasse.systemtest.KeycloakCredentials;
import io.enmasse.systemtest.selenium.SeleniumProvider;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class OpenshiftWebPage {

    private static Logger log = CustomLogger.getLogger();

    SeleniumProvider selenium;
    String ocRoute;
    KeycloakCredentials credentials;
    OpenshiftLoginWebPage loginPage;

    public OpenshiftWebPage(SeleniumProvider selenium, String ocRoute, KeycloakCredentials credentials) {
        this.selenium = selenium;
        this.ocRoute = ocRoute;
        this.credentials = credentials;
        this.loginPage = new OpenshiftLoginWebPage(selenium);
    }

    public void openOpenshiftPage() throws Exception {
        log.info("Opening openshift web page on route {}", ocRoute);
        selenium.getDriver().get(ocRoute);
        selenium.getDriverWait().until(ExpectedConditions.titleContains("Login"));
        selenium.getAngularDriver().waitForAngularRequestsToFinish();
        selenium.takeScreenShot();
        if (!login())
            throw new IllegalAccessException(loginPage.getAlertMessage());
        selenium.getAngularDriver().waitForAngularRequestsToFinish();
        selenium.getDriverWait().until(ExpectedConditions.visibilityOfElementLocated(By.className("services-no-sub-categories")));
    }

    public boolean login() throws Exception {
        return loginPage.login(credentials.getUsername(), credentials.getPassword());
    }

    public WebElement getCatalog() {
        return selenium.getDriver().findElement(By.className("services-no-sub-categories"));
    }

    private String getTitleFromService(WebElement element) {
        return element.findElement(By.className("services-item-name")).getAttribute("title");
    }

    public WebElement getServiceFromCatalog(String name) {
        List<WebElement> services = getServicesFromCatalog();
        return services.stream().filter(item -> name.equals(getTitleFromService(item))).collect(Collectors.toList()).get(0);
    }

    public List<WebElement> getServicesFromCatalog() {
        List<WebElement> services = selenium.getDriver().findElements(By.className("services-item"));
        services.forEach(item -> log.info("Got service item from catalog: {}", getTitleFromService(item)));
        return services;
    }

    public void clickOnCreateBrokered() throws Exception {
        selenium.clickOnItem(getServiceFromCatalog("EnMasse (brokered)"));
    }

    public void clickOnCreateStandard() throws Exception {
        selenium.clickOnItem(getServiceFromCatalog("EnMasse (standard)"));
    }
}
