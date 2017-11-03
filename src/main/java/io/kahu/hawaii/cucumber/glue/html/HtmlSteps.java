/**
 * Copyright 2015 Q24
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kahu.hawaii.cucumber.glue.html;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.invisibilityOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElement;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElementValue;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleContains;
import static org.openqa.selenium.support.ui.ExpectedConditions.titleIs;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import com.assertthat.selenium_shutterbug.core.Shutterbug;
import com.assertthat.selenium_shutterbug.utils.web.ScrollStrategy;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.opera.OperaDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ISelect;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.mdimension.jchronic.Chronic;
import com.mdimension.jchronic.Options;
import com.mdimension.jchronic.utils.Span;

import cucumber.api.DataTable;
import cucumber.api.Scenario;
import cucumber.api.Transform;
import cucumber.api.Transformer;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import javax.imageio.ImageIO;

public class HtmlSteps {

    private static final String MARIONETTE = "marionette";

    private static final String PROXY_HOST_KEY = "test.proxyHost";
    private static final String PROXY_PORT_KEY = "test.proxyPort";
    private final String browser;
    private final boolean remote;
    private final String baseUrl;
    private final String relativeUrl;
    private final int timeout;

    private final String seleniumHub;
    private final boolean embedScreenshot;
    private EventFiringWebDriver webDriver;
    private boolean acceptCookies;

    public HtmlSteps() {
        Properties properties = System.getProperties();
        this.browser = properties.containsKey("test.browser") ? System.getProperty("test.browser") : "chrome";
        this.remote = properties.containsKey("test.remote") ? Boolean.parseBoolean(System.getProperty("test.remote")) : false;
        this.baseUrl = properties.containsKey("test.base.url") ? System.getProperty("test.base.url") : "http://target.kahuna.loc:8888";
        this.seleniumHub = properties.containsKey("test.selenium.hub") ? System.getProperty("test.selenium.hub") : "http://localhost:4444/wd/hub";
        this.relativeUrl = properties.containsKey("test.relative.url") ? System.getProperty("test.relative.url") : "";
        this.timeout = properties.containsKey("test.timeout") ? Integer.parseInt(System.getProperty("test.timeout")) : 10;
        this.acceptCookies = properties.containsKey("test.disable.accept.cookies") ? !Boolean.parseBoolean(System.getProperty("test.disable.accept.cookies"))
                : true;
        this.embedScreenshot = properties.containsKey("test.embed.screenshot") ? Boolean.parseBoolean(System.getProperty("test.embed.screenshot")) : true;
    }

    public WebDriver getWebDriver() {
        return this.webDriver;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * An expectation for checking the current url of a page.
     *
     * @param url
     *            the expected url, which must be an exact match
     * @return true when the url matches, false otherwise
     * @see https://code.google.com/p/selenium/issues/detail?id=6842
     */
    public static ExpectedCondition<Boolean> currentUrlIs(final String url) {
        return driver -> {
            String currentUrl = driver.getCurrentUrl();
            return currentUrl == null ? false : (removeQueryString(currentUrl).equals(url) || currentUrl.equals(url + '/'));
        };
    }

    private static String removeQueryString(String url) {
        //remove all starting from the first ?
        return url == null ?  url : url.replaceAll("^([^\\?]+)\\?.*$", "$1");
    }

    /**
     * An expectation for checking that the current url contains a
     * case-sensitive substring
     *
     * @param url
     *            the fragment of url expected
     * @return true when the url matches, false otherwise
     * @see https://code.google.com/p/selenium/issues/detail?id=6842
     */
    public static ExpectedCondition<Boolean> currentUrlContains(final String url) {
        return driver -> {
            String currentUrl = driver.getCurrentUrl();
            return currentUrl == null ? false : currentUrl.contains(url);
        };
    }

    @Before("@web")
    public void beforeScenario() throws Exception {
        WebDriver driver;
        if (StringUtils.containsIgnoreCase(browser, "chrome")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.chrome();
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new ChromeDriver();
            }
        } else if (StringUtils.containsIgnoreCase(browser, "opera")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.operaBlink();
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new OperaDriver();
            }
        } else if (StringUtils.containsIgnoreCase(browser, "marionette")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.firefox();
                capabilities.setCapability("marionette", true);
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new FirefoxDriver();
            }
        } else if (StringUtils.containsIgnoreCase(browser, "firefox")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.firefox();
                capabilities.setCapability("marionette", false);
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new FirefoxDriver();
            }
        } else if (StringUtils.containsIgnoreCase(browser, "htmlunit")) {
            driver = new HtmlUnitDriver(BrowserVersion.CHROME);
        } else if (StringUtils.containsIgnoreCase(browser, "iexplore")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.internetExplorer();
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new InternetExplorerDriver();
            }

        } else if (StringUtils.containsIgnoreCase(browser, "phantom")) {
            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, getOsSpecificPhantomDriverPath());
            Proxy proxy = getHttpProxy();
            if (proxy != null) {
                capabilities.setCapability(CapabilityType.PROXY, getHttpProxy());
            }
            driver = new PhantomJSDriver(capabilities);
        } else if (StringUtils.containsIgnoreCase(browser, "safari")) {
            if (remote) {
                DesiredCapabilities capabilities = DesiredCapabilities.safari();
                driver = createRemoteWebDriverForCapabilities(capabilities);
            } else {
                driver = new SafariDriver();
            }
        } else {
            throw new IllegalStateException("Unsupported browser specified");
        }
        webDriver = new EventFiringWebDriver(driver);
        webDriver.manage().deleteAllCookies();
        turnOnImplicitWaits();
    }

    private WebDriver createRemoteWebDriverForCapabilities(DesiredCapabilities capabilities) throws Exception {
        WebDriver driver;
        Proxy proxy = getHttpProxy();
        if (proxy != null) {
            capabilities.setCapability(CapabilityType.PROXY, getHttpProxy());
        }
        driver = new RemoteWebDriver(new URL(seleniumHub), capabilities);
        return driver;
    }

    private String getOsSpecificPhantomDriverPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String returnPath = "bin/linux/phantomjs";
        if (osName.contains("windows")) {
            returnPath = "bin/windows/phantomjs";
        } else if (osName.contains("mac")) {
            returnPath = "bin/mac/phantomjs";
        }
        return returnPath;
    }

    @After("@web")
    public void afterScenario(Scenario scenario) throws IOException {
        if (scenario.isFailed() && embedScreenshot) {
            try {
                //byte[] screenshot = webDriver.getScreenshotAs(OutputType.BYTES);

                BufferedImage image = Shutterbug.shootPage(webDriver, ScrollStrategy.BOTH_DIRECTIONS).getImage();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                baos.flush();
                byte[] screenshot = baos.toByteArray();

                scenario.embed(screenshot, "image/png");
            } catch (WebDriverException somePlatformsDontSupportScreenshots) {
                System.err.println(somePlatformsDontSupportScreenshots.getMessage());
            }
        }
        webDriver.quit();
        webDriver = null;
    }

    @When("^I visit page \"([^\"]*)\"$")
    public void I_visit_page(String path) throws Throwable {
        visitPage(path, false);
    }

    @When("^I visit page \"([^\"]*)\" and accept cookies$")
    public void I_visit_page_and_accept_cookies(String path) throws Throwable {
        visitPage(path, true);
    }

    @When("^I visit page \"([^\"]*)\" expecting to end up at \"([^\"]*)\"$")
    public void I_visit_page(String path, String expectedPath) throws Throwable {
        visitPage(path, false, expectedPath);
    }

    @When("^I visit page \"([^\"]*)\" and accept cookies expecting to end up at \"([^\"]*)\"$")
    public void I_visit_page_and_accept_cookies(String path, String expectedPath) throws Throwable {
        visitPage(path, true, expectedPath);
    }

    private void visitPage(String path, boolean acceptCookies) throws Throwable {
        visitPage(path, acceptCookies, path);
    }
    private void visitPage(String path, boolean acceptCookies, String expectedPath) throws Throwable {
        webDriver.get(getUrl() + path);
        if (acceptCookies) {
            acceptCookies();
        }
        waitForLoad();

        current_path_should_be(expectedPath);
    }

    public void waitForLoad() {
        ExpectedCondition<Boolean> pageLoadCondition = driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete");
        WebDriverWait wait = new WebDriverWait(webDriver, 30);
        wait.until(pageLoadCondition);
    }
    
    public void waitForJQueryToFinish() {
    	try {
	        ExpectedCondition<Boolean> ajaxCondition = driver -> (Boolean)((JavascriptExecutor) driver).executeScript("return window.jQuery != undefined && jQuery.active === 0");
	        WebDriverWait wait = new WebDriverWait(webDriver, 30);
	        wait.until(ajaxCondition);
    	}
    	catch (TimeoutException e) {
    		// this is a timeout (obviously) but since we were supposed to wait anyway, swallow the exception
    	}
    }
    
    @When("^I wait for jQuery to finish$")
    public void I_wait_for_JQuery_to_finish() throws Throwable {
    	waitForJQueryToFinish();
    }
    
    public void waitForAngularJSToFinish() {
    	try {
	        ExpectedCondition<Boolean> ajaxCondition = driver -> (Boolean)((JavascriptExecutor) driver).executeScript("return window.angular != undefined && angular.element(document.body).injector().get('$http').pendingRequests.length === 0");
	        WebDriverWait wait = new WebDriverWait(webDriver, 30);
	        wait.until(ajaxCondition);
    	}
    	catch (TimeoutException e) {
    		// this is a timeout (obviously) but since we were supposed to wait anyway, swallow the exception
    	}
    }
    
    @When("^I wait for AngularJS to finish$")
    public void I_wait_for_AngularJS_to_finish() throws Throwable {
    	waitForAngularJSToFinish();
    }

    @When("^I accept cookies$")
    public void I_accept_cookies() throws Throwable {
        acceptCookies();
    }

    @When("^I fill \"([^\"]*)\" in field \"([^\"]*)\"$")
    public void I_fill_in_field(String value, String id) throws Throwable {
        findVisibleElementById(id).sendKeys(value);
    }

    @When("^I clear field \"([^\"]*)\"$")
    public void I_clear_field(String id) throws Throwable {
        findVisibleElementById(id).clear();
    }

    @When("^I fill date (.*) in field \"([^\"]*)\"$")
    public void I_fill_date_in_field(@Transform(ChronicConverter.class) Calendar cal, String id) throws Throwable {
        findVisibleElementById(id).sendKeys(formatDate(cal));
    }

    @When("^I fill time (.*) in field \"([^\"]*)\"$")
    public void I_fill_time_in_field(@Transform(ChronicConverter.class) Calendar cal, String id) throws Throwable {
        findVisibleElementById(id).sendKeys(formatTime(cal));
    }

    @When("^I choose radio button \"([^\"]*)\"$")
    public void I_choose_radio_button(String id) throws Throwable {
        I_check_checkbox(id);
    }

    @When("^I check checkbox \"([^\"]*)\"$")
    /**
     * Checkboxes are not visible themselves because of the CSS used. Instead, we click on the label belonging to them with the label'for'
     * Note: because of this approach this method is tight to the implementation I will not just click on checkbox; don't like it; needs refactoring.
     * @param id, the 'label for' identifier
     * @throws Throwable
     */
    public void I_check_checkbox(String id) throws Throwable {
        List<WebElement> elements = webDriver.findElements(By.cssSelector("label[for='" + id + "']"));
        WebElement element = null;
        if (elements != null) {
            if (elements.size() > 1) {
                element = findVisibleElement(By.cssSelector("label[for='" + id + "']:first-child"));
            } else {
                element = findVisibleElement(By.cssSelector("label[for='" + id + "']"));
            }
        } else {
            fail("Checkbox element label[for='" + id + "'] not found");
        }
        scrollToElement(element);
        moveTo(element).click().perform();

        // While fixing SUPDEV-1903 I refactored above code.
        // However above code worked as after fixing the issues with the quotes.
        // I keep code below just for reference if we look into this in the future.

//        WebElement element = findElement(By.id(id));
//        // find label
//        if (element != null) {
//            WebElement labelElement = element.findElement(By.xpath(".//ancestor::label[@for='" + id + "']"));
//            scrollToElement(labelElement);
//            labelElement.click();
//        } else {
//            fail("Checkbox element \"" + id + "\" not found");
//        }
    }

    @When("^I select \"([^\"]*)\" from drop-down list \"([^\"]*)\"$")
    public void I_select_from_drop_down_list(String value, String id) throws Throwable {
        I_select_from_drop_down_list(value, id, false);
    }

    public void I_select_from_drop_down_list(String value, String id, boolean byValue) throws Throwable {
        WebElement element = findVisibleAndClickableElementById(id);
        if (byValue) {
            selectItemInDropdownByValue(element, value);
        } else {
            selectItemInDropdown(element, value);
        }
    }

    @When("^I fill in the form?$")
    public void I_fill_in_the_form(DataTable data) throws Throwable {
        I_fill_in_the_fields(data);
    }

    @When("^I fill in the fields?$")
    public void I_fill_in_the_fields(DataTable data) throws Throwable {
        for (List<String> row : data.raw()) {
            String id = row.get(0);
            String value = row.get(1);
            WebElement element = findVisibleAndClickableElementById(id);
            String tagName = element.getTagName();
            String type = element.getAttribute("type");
            if ("input".equalsIgnoreCase(tagName)) {
                if ("checkbox".equalsIgnoreCase(type)) {
                    moveTo(element).click().perform();
                } else if ("radio".equalsIgnoreCase(type)) {
                    moveTo(element).click().perform();
                } else {
                    if (value != null) {
                        if (value.startsWith("date:")) {
                            String chronic = value.replaceFirst("date:", "").trim();
                            Calendar cal = parseChronic(chronic);
                            element.sendKeys(formatDate(cal));
                        } else if (value.startsWith("time:")) {
                            String chronic = value.replaceFirst("time:", "").trim();
                            Calendar cal = parseChronic(chronic);
                            element.sendKeys(formatTime(cal));
                        } else {
                            element.sendKeys(value);
                        }
                    }
                }
            } else if ("select".equalsIgnoreCase(tagName)) {
                selectItemInDropdown(element, value);
            } else if ("label".equalsIgnoreCase(tagName)) {
                moveTo(element).click().perform();
            } else {
                element.sendKeys(value);
            }
        }
    }

    @When("^I click on button \"([^\"]*)\"$")
    public void I_click_on_button(String id) throws Throwable {
        WebElement element = findVisibleAndClickableElementById(id);
        moveTo(element).click().perform();
    }

    @When("^I click on button with text \"([^\"]*)\"$")
    public void I_click_on_button_with_text(String text) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.xpath("//button[text()='" + text + "']"));
        moveTo(element).click().perform();
    }

    @When("^I click on button with text containing \"([^\"]*)\"$")
    public void I_click_on_button_with_text_containing(String text) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.xpath("//button[contains(text(), '" + text + "')]"));
        moveTo(element).click().perform();
    }

    @When("^I click on element \"([^\"]*)\"$")
    public void I_click_on_element(String id) throws Throwable {
        WebElement element = findVisibleAndClickableElementById(id);
        moveTo(element).click().perform();
    }

    @When("^I click on element with id \"([^\"]*)\"$")
    public void I_click_on_element_with_id(String id) throws Throwable {
        WebElement element = findVisibleAndClickableElementById(id);
        moveTo(element).click().perform();
    }

    @When("^I click on link \"([^\"]*)\"$")
    public void I_click_on_link(String id) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.cssSelector("a#" + id));
        moveTo(element).click().perform();
    }

    @When("^I click on link with text \"([^\"]*)\"$")
    public void I_click_on_link_with_text(String linkText) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.linkText(linkText));
        moveTo(element).click().perform();
    }

    @When("^I click on link with text containing \"([^\"]*)\"$")
    public void I_click_on_link_with_text_containing(String linkText) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.partialLinkText(linkText));
        moveTo(element).click().perform();
    }

    @When("^I wait (\\d+) seconds?$")
    public void I_wait_seconds(int seconds) throws Throwable {
        if (seconds > 0) {
            int millis = seconds * 1000;
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait(millis);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    @When("^I wait some milliseconds$")
    public void I_wait_some_milliseconds() throws Throwable {
        int millis = 1500;
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @When("^I select an iframe with name \"([^\"]*)\"$")
    public void I_select_an_iframe_with_name(String text) throws Throwable {
        webDriver.switchTo().frame(text);
    }

    @When("^I select the parent window")
    public void I_select_the_parent_window() throws Throwable {
        webDriver.switchTo().defaultContent();
    }

    @When("^I click on input with value \"([^\"]*)\"$")
    public void I_click_on_input_with_value(String text) throws Throwable {
        WebElement element = findVisibleAndClickableElement(By.xpath("//input[contains(@value,'" + text + "')]"));
        moveTo(element).click().perform();
    }

    @Then("^current url should be \"([^\"]*)\"$")
    public void current_url_should_be(String url) throws Throwable {
        try {
            waitUntil(currentUrlIs(url));
        } catch (TimeoutException e) {
            assertThat(removeQueryString(webDriver.getCurrentUrl()), is(either(equalTo(url)).or(equalTo(url + '/'))));
        }
    }

    @Then("^current path should be \"([^\"]*)\"$")
    public void current_path_should_be(String path) throws Throwable {
        String url = this.getUrl() + path;
        try {
            waitUntil(currentUrlIs(url));
        } catch (TimeoutException e) {
            assertThat(removeQueryString(webDriver.getCurrentUrl()), is(either(equalTo(url)).or(equalTo(url + '/'))));
        }
    }

    @Then("^current url should not be \"([^\"]*)\"$")
    public void current_url_should_not_be(String url) throws Throwable {
        try {
            waitUntil(not(currentUrlIs(url)));
        } catch (TimeoutException e) {
            assertThat(removeQueryString(webDriver.getCurrentUrl()), is(Matchers.not(equalTo(url))));
        }
    }

    @Then("^current url should contain \"([^\"]*)\"$")
    public void current_url_should_contain(final String url) throws Throwable {
        try {
            waitUntil(currentUrlContains(url));
        } catch (TimeoutException e) {
            assertThat(webDriver.getCurrentUrl(), containsString(url));
        }
    }

    @Then("^current url should not contain \"([^\"]*)\"$")
    public void current_url_should_not_contain(String url) throws Throwable {
        try {
            waitUntil(not(currentUrlContains(url)));
        } catch (TimeoutException e) {
            assertThat(webDriver.getCurrentUrl(), Matchers.not(containsString(url)));
        }
    }

    @Then("^page title should be \"([^\"]*)\"$")
    public void page_title_should_be(String title) throws Throwable {
        try {
            waitUntil(titleIs(title));
        } catch (TimeoutException e) {
            assertThat(webDriver.getTitle(), is(equalTo(title)));
        }
    }

    @Then("^page title should not be \"([^\"]*)\"$")
    public void page_title_should_not_be(String title) throws Throwable {
        try {
            waitUntil(not(titleIs(title)));
        } catch (TimeoutException e) {
            assertThat(webDriver.getTitle(), is(Matchers.not(equalTo(title))));
        }
    }

    @Then("^page title should contain \"([^\"]*)\"$")
    public void page_title_should_contain(String title) throws Throwable {
        try {
            waitUntil(titleContains(title));
        } catch (TimeoutException e) {
            assertThat(webDriver.getTitle(), containsString(title));
        }
    }

    @Then("^page title should not contain \"([^\"]*)\"$")
    public void page_title_should_not_contain(String title) throws Throwable {
        try {
            waitUntil(not(titleContains(title)));
        } catch (TimeoutException e) {
            assertThat(webDriver.getTitle(), Matchers.not(containsString(title)));
        }
    }

    @Then("^page should contain element \"([^\"]*)\"$")
    public void page_should_contain_element(String id) throws Throwable {
        try {
            findElement(By.id(id));
        } catch (NoSuchElementException e) {
            fail("page did not contain element with id \"" + id + "\"");
        }
    }

    @Then("^page should not contain element \"([^\"]*)\"$")
    public void page_should_not_contain_element(String id) throws Throwable {
        try {
            findElement(By.id(id));
            fail("page did contain element with id \"" + id + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page should contain element with id \"([^\"]*)\"$")
    public void page_should_contain_element_with_id(String id) throws Throwable {
        try {
            findElement(By.id(id));
        } catch (NoSuchElementException e) {
            fail("page did not contain element with id \"" + id + "\"");
        }
    }

    @Then("^page should not contain element with id \"([^\"]*)\"$")
    public void page_should_not_contain_element_with_id(String id) throws Throwable {
        try {
            findElement(By.id(id));
            fail("page did contain element with id \"" + id + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page should contain element with class name \"([^\"]*)\"$")
    public void page_should_contain_element_with_class_name(String className) throws Throwable {
        try {
            findElement(By.className(className));
        } catch (NoSuchElementException e) {
            fail("page did not contain element with class name \"" + className + "\"");
        }
    }

    @Then("^page should not contain element with class name \"([^\"]*)\"$")
    public void page_should_not_contain_element_with_class_name(String className) throws Throwable {
        try {
            findElement(By.className(className));
            fail("page did contain element with class name \"" + className + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page element \"([^\"]*)\" should be visible$")
    public void page_element_should_be_visible(String id) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.id(id)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" was not visible");
        }
    }

    @Then("^page element \"([^\"]*)\" should not be visible$")
    public void page_element_should_not_be_visible(String id) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.id(id)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" was visible");
        }
    }

    @Then("^page element \"([^\"]*)\" should contain text \"([^\"]*)\"$")
    public void page_element_should_contain_text(String id, String text) throws Throwable {
        try {
            waitUntil(textToBePresentInElementLocated(By.id(id), text));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("element with id \"" + id + "\" did not contain text \"" + text + "\"; visible content of element was: " + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with id \"" + id + "\" did not contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element \"([^\"]*)\" should not contain text \"([^\"]*)\"$")
    public void page_element_should_not_contain_text(String id, String text) throws Throwable {
        try {
            waitUntil(not(textToBePresentInElementLocated(By.id(id), text)));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("element with id \"" + id + "\" did contain text \"" + text + "\"; visible content of element was: " + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with id \"" + id + "\" did contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element \"([^\"]*)\" should contain class name \"([^\"]*)\"$")
    public void page_element_should_contain_class_name(String id, String className) throws Throwable {
        try {
            WebElement element = findElementById(id);
            if (!element.getAttribute("class").contains(className)) {
                fail("element with id \"" + id + "\" did not contain element with class name \"" + className + "\"");
            }
        } catch (NoSuchElementException e) {
            fail("element with id \"" + id + "\" not found");
        }
    }

    @Then("^page element \"([^\"]*)\" should contain element with class name \"([^\"]*)\"$")
    public void page_element_should_contain_element_with_class_name(String id, String className) throws Throwable {
        try {
            findElement(By.cssSelector("#" + id + " ." + className));
        } catch (NoSuchElementException e) {
            fail("element with id \"" + id + "\" not found");
        }
    }

    @Then("^page element \"([^\"]*)\" should not contain element with class name \"([^\"]*)\"$")
    public void page_element_should_not_contain_element_with_class_name(String id, String className) throws Throwable {
        try {
            findElement(By.cssSelector("#" + id + " ." + className));
            fail("element with id \"" + id + "\" did contain element with class name \"" + className + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page element \"([^\"]*)\" should contain visible element with class name \"([^\"]*)\"$")
    public void page_element_should_contain_visible_element_with_class_name(String id, String className) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.cssSelector("#" + id + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" did not contain visible element with class name \"" + className + "\"");
        }
    }

    @Then("^page element \"([^\"]*)\" should contain invisible element with class name \"([^\"]*)\"$")
    public void page_element_should_contain_invisible_element_with_class_name(String id, String className) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.cssSelector("#" + id + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" did not contain invisible element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should be visible$")
    public void page_element_with_id_should_be_visible(String id) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.id(id)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" was not visible");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should not be visible$")
    public void page_element_with_id_should_not_be_visible(String id) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.id(id)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" was visible");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should contain text \"([^\"]*)\"$")
    public void page_element_with_id_should_contain_text(String id, String text) throws Throwable {
        try {
            waitUntil(textToBePresentInElementLocated(By.id(id), text));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("element with id \"" + id + "\" did not contain text \"" + text + "\"; visible content of element was: " + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with id \"" + id + "\" did not contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element with id \"([^\"]*)\" should not contain text \"([^\"]*)\"$")
    public void page_element_with_id_should_not_contain_text(String id, String text) throws Throwable {
        try {
            waitUntil(not(textToBePresentInElementLocated(By.id(id), text)));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("element with id \"" + id + "\" did contain text \"" + text + "\"; visible content of element was: " + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with id \"" + id + "\" did contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element with id \"([^\"]*)\" should contain element with class name \"([^\"]*)\"$")
    public void page_element_with_id_should_contain_element_with_class_name(String id, String className) throws Throwable {
        try {
            findElement(By.cssSelector("#" + id + " ." + className));
        } catch (NoSuchElementException e) {
            fail("element with id \"" + id + "\" did not contain element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should not contain element with class name \"([^\"]*)\"$")
    public void page_element_with_id_should_not_contain_element_with_class_name(String id, String className) throws Throwable {
        try {
            findElement(By.cssSelector("#" + id + " ." + className));
            fail("element with id \"" + id + "\" did contain element with class name \"" + className + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page element with id \"([^\"]*)\" should contain visible element with class name \"([^\"]*)\"$")
    public void page_elemen_with_id_should_contain_visible_element_with_class_name(String id, String className) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.cssSelector("#" + id + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" did not contain visible element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should contain invisible element with class name \"([^\"]*)\"$")
    public void page_element_with_id_should_contain_invisible_element_with_class_name(String id, String className) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.cssSelector("#" + id + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with id \"" + id + "\" did not contain invisible element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with id \"([^\"]*)\" should have class \"([^\"]*)\"$")
    public void page_element_with_id_should_have_class(String id, String className) throws Throwable {
        try {
            findElement(By.cssSelector("#" + id + "." + className));
        } catch (NoSuchElementException e) {
            fail("element with id \"" + id + "\" did not have class name \"" + className + "\"");
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should be visible$")
    public void page_element_with_class_name_should_be_visible(String className) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.className(className)));
        } catch (TimeoutException e) {
            fail("element with class name \"" + className + "\" was not visible");
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should not be visible$")
    public void page_element_with_class_name_should_not_be_visible(String className) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.className(className)));
        } catch (TimeoutException e) {
            fail("element with class name \"" + className + "\" was visible");
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should contain text \"([^\"]*)\"$")
    public void page_element_with_class_name_should_contain_text(String className, String text) throws Throwable {
        try {
            waitUntil(textToBePresentInElementLocated(By.className(className), text));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElement(By.className(className));
                fail("element with class name \"" + className + "\" did not contain text \"" + text + "\"; visible content of element was: "
                        + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with class name \"" + className + "\" did not contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should not contain text \"([^\"]*)\"$")
    public void page_element_with_class_name_should_not_contain_text(String className, String text) throws Throwable {
        try {
            waitUntil(not(textToBePresentInElementLocated(By.className(className), text)));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElement(By.className(className));
                fail("element with class name \"" + className + "\" did contain text \"" + text + "\"; visible content of element was: " + element.getText());
            } catch (NoSuchElementException e2) {
                fail("element with class name \"" + className + "\" did contain text \"" + text + "\"; element not found");
            }
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should contain element with class name \"([^\"]*)\"$")
    public void page_element_with_class_name_should_contain_element_with_class_name(String parent, String className) throws Throwable {
        try {
            findElement(By.cssSelector("." + parent + " ." + className));
        } catch (NoSuchElementException e) {
            fail("element with class name \"" + parent + "\" did not contain element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should not contain element with class name \"([^\"]*)\"$")
    public void page_element_with_class_name_should_not_contain_element_with_class_name(String parent, String className) throws Throwable {
        try {
            findElement(By.cssSelector("." + parent + " ." + className));
            fail("element with class name \"" + parent + "\" did contain element with class name \"" + className + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should contain visible element with class name \"([^\"]*)\"$")
    public void page_elemen_with_class_name_should_contain_visible_element_with_class_name(String parent, String className) throws Throwable {
        try {
            waitUntil(visibilityOfElementLocated(By.cssSelector("." + parent + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with class name \"" + parent + "\" did not contain visible element with class name \"" + className + "\"");
        }
    }

    @Then("^page element with class name \"([^\"]*)\" should contain invisible element with class name \"([^\"]*)\"$")
    public void page_element_with_class_name_should_contain_invisible_element_with_class_name(String parent, String className) throws Throwable {
        try {
            waitUntil(invisibilityOfElementLocated(By.cssSelector("." + parent + " ." + className)));
        } catch (TimeoutException e) {
            fail("element with class name \"" + parent + "\" did not contain invisible element with class name \"" + className + "\"");
        }
    }

    @Then("^field \"([^\"]*)\" should contain value \"([^\"]*)\"$")
    public void field_should_contain_value(String id, String value) throws Throwable {
        try {
            waitUntil(textToBePresentInElementValue(By.id(id), value));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("field with id \"" + id + "\" did not contain value \"" + value + "\"; value of field was: " + element.getAttribute("value"));
            } catch (NoSuchElementException e2) {
                fail("field with id \"" + id + "\" did not contain value \"" + value + "\"; field not found");
            }
        }
    }

    @Then("^field \"([^\"]*)\" should not contain value \"([^\"]*)\"$")
    public void field_should_not_contain_value(String id, String value) throws Throwable {
        try {
            waitUntil(not(textToBePresentInElementValue(By.id(id), value)));
        } catch (TimeoutException e) {
            try {
                WebElement element = findElementById(id);
                fail("field with id \"" + id + "\" did contain value \"" + value + "\"; value of field was: " + element.getAttribute("value"));
            } catch (NoSuchElementException e2) {
                fail("field with id \"" + id + "\" did contain value \"" + value + "\"; field not found");
            }
        }
    }

    @Then("^body should contain text \"([^\"]*)\"$")
    public void body_should_contain_text(String text) throws Throwable {
        // assertThat(findVisibleElement(By.tagName("body")).getText().contains(text),
        // is(equalTo(true)));
        try {
            WebElement element = webDriver.findElement(By.tagName("body"));
            waitUntil(textToBePresentInElement(element, text));
        } catch (TimeoutException e) {
            fail("body did not contain text \"" + text + "\"; text not found");
        }
    }

    /**
     * Undocumented feature for testing purpose.
     */
    @Then("^page should contain element with xpath expression \"([^\"]*)\"$")
    public void page_should_contain_element_with_xpath_expression(String xpath) throws Throwable {
        try {
            findElement(By.xpath(xpath));
        } catch (NoSuchElementException e) {
            fail("page did not contain element with xpath expression \"" + xpath + "\"");
        }
    }

    /**
     * Undocumented feature for testing purpose.
     */
    @Then("^page should not contain element with xpath expression \"([^\"]*)\"$")
    public void page_should_not_contain_element_with_xpath_expression(String xpath) throws Throwable {
        try {
            findElement(By.xpath(xpath));
            fail("page did contain element with xpath expression \"" + xpath + "\"");
        } catch (NoSuchElementException e) {
            // pass
        }
    }

    public String getUrl() {
        return baseUrl + relativeUrl;
    }

    private Proxy getHttpProxy() {
        Proxy proxy = null;
        String proxyHost = System.getProperty(PROXY_HOST_KEY);
        String proxyPort = System.getProperty(PROXY_PORT_KEY);
        if (proxyHost != null && proxyPort != null) {
            proxy = new Proxy();
            proxy.setHttpProxy(proxyHost + ":" + proxyPort);
            proxy.setProxyType(Proxy.ProxyType.MANUAL);
            proxy.setSslProxy(proxyHost + ":" + proxyPort);
        }
        return proxy;
    }

    /**
     * Find the first {@link org.openqa.selenium.WebElement} using the given
     * method.
     *
     * @param by
     *            The locating mechanism
     * @return The first matching element on the current page
     * @throws org.openqa.selenium.NoSuchElementException
     *             If no matching elements are found
     * @see org.openqa.selenium.WebDriver#findElement(org.openqa.selenium.By)
     */
    public WebElement findElement(By by) {
        return webDriver.findElement(by);
    }

    /**
     * Find the first {@link org.openqa.selenium.WebElement} using the given
     * method.
     *
     * @param by
     *            The locating mechanism
     * @return The first matching element on the current page
     * @throws org.openqa.selenium.NoSuchElementException
     *             If no matching elements are found
     * @see org.openqa.selenium.WebDriver#findElement(org.openqa.selenium.By)
     */
    public WebElement findVisibleElement(By by) {
        waitUntil(visibilityOfElementLocated(by));
        return findElement(by);
    }

    public WebElement findVisibleAndClickableElementById(String id) {
        return findVisibleAndClickableElement(By.id(id));
    }

    public WebElement findVisibleAndClickableElement(By by) {
        // waitUntil(elementToBeClickable(by));
        waitUntil(visibilityOfElementLocated(by));
        waitUntil(elementToBeClickable(by));
        return findElement(by);
    }

    public WebElement findVisibleElementById(String id) {
        return findVisibleElement(By.id(id));
    }

    /**
     * Find the first {@link org.openqa.selenium.WebElement} for the given id.
     *
     * @param id
     *            The value of the "id" attribute to search for
     * @return The first matching element on the current page
     * @throws AssertionError
     *             If no matching elements are found
     * @see org.openqa.selenium.WebDriver#findElement(org.openqa.selenium.By)
     * @see org.openqa.selenium.By.ById
     */
    public WebElement findElementById(String id) {
        try {
            waitUntil(presenceOfElementLocated(By.id(id)));
            WebElement element = findElement(By.id(id));

            // Scroll to the element, this due to some dirty radiobutton tricks.
            // And force it a bit more to to center
            // And yes, do this for all elements. ( This is a known ChromeDriver
            // V2.12 bug, if the element is not in the
            // visible area then you 'can' have some troubles. ). Works for all
            // elements too ;-)
            scrollToElement(element);

            return element;
        } catch (NoSuchElementException e) {
            throw new AssertionError("Element with id  <" + id + "> not found");
        }
    }

    /**
     * Scroll to the element, this due to some dirty radiobutton tricks. And
     * force it a bit more to to center // And yes, do this for all elements. (
     * This is a known ChromeDriver V2.12 bug, if the element is not in the //
     * visible area then you 'can' have some troubles. ). Works for all elements
     * too ;-)
     *
     * @param element
     */
    public void scrollToElement(WebElement element) {
        int y_coor = 0;

        try {
            y_coor = element.getLocation().y - 100;
            ((JavascriptExecutor) webDriver).executeScript("window.scrollTo(0," + y_coor + ")");
        } catch (Exception e) {
            throw new AssertionError("Could not scroll to element");
        }
    }

    /**
     * Scroll to classname. This is a known ChromeDriver V2.12 bug, if the
     * element is not in the // visible area then you 'can' have some troubles.
     */
    public void scrollToClass(String classname) {
        WebElement element = findElement(By.className(classname));
        scrollToElement(element);
    }

    /**
     * Scroll to id. This is a known ChromeDriver V2.12 bug, if the element is
     * not in the // visible area then you 'can' have some troubles.
     */
    public void scrollToID(String id) {
        WebElement element = findElementById(id);
        scrollToElement(element);
    }

    /**
     * Repeatedly applies this instance's input value to the given function
     * until one of the following occurs:
     * <ol>
     * <li>the function returns neither null nor false,</li>
     * <li>the function throws an unignored exception,</li>
     * <li>the timeout expires,
     * <li>
     * <li>the current thread is interrupted</li>
     * </ol>
     *
     * @param isTrue
     *            the parameter to pass to the
     *            {@link org.openqa.selenium.support.ui.ExpectedCondition}
     * @param <V>
     *            The function's expected return type.
     * @return The functions' return value if the function returned something
     *         different from null or false before the timeout expired.
     * @throws org.openqa.selenium.TimeoutException
     *             If the timeout expires.
     */
    public <V> V waitUntil(Function<? super WebDriver, V> isTrue) {
        WebDriverWait wait = new WebDriverWait(webDriver, timeout);
        return wait.until(isTrue);
    }

    /**
     * Repeatedly applies this instance's input value to the given predicate
     * until the timeout expires or the predicate evaluates to true.
     *
     * @param isTrue
     *            The predicate to wait on.
     * @throws org.openqa.selenium.TimeoutException
     *             If the timeout expires.
     */
    public void waitUntil(Predicate<WebDriver> isTrue) {
        waitUntil(
            new Function<WebDriver, Boolean>() {
                @Override
                public Boolean apply(WebDriver input) {
                    return isTrue.test(input);
                }
                @Override
                public String toString() {
                    return isTrue.toString();
                }
            }
        );
    }

    /**
     * Parses a chronic string to a Calendar object.
     */
    public Calendar parseChronic(String value) {
        ChronicConverter chronicConverter = new ChronicConverter();
        return chronicConverter.transform(value);
    }

    /**
     * Formats a calendar to a date string.
     */
    public String formatDate(Calendar cal) {
        DateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        return sdf.format(cal.getTime());
    }

    /**
     * Formats a calendar to a time string.
     */
    public String formatTime(Calendar cal) {
        DateFormat sdf = new SimpleDateFormat("hh:mm");
        return sdf.format(cal.getTime());
    }

    public void acceptCookies() {
        // accept cookies popup
        if (acceptCookies) {
            try {
                WebDriverWait wait = new WebDriverWait(webDriver, 2); // wait
                // max 2
                // seconds
                wait.until(elementToBeClickable(By.className("cookie-yes")));
                turnOffImplicitWaits();
                WebElement element = findElement(By.className("cookie-yes"));
                moveTo(element).click().perform();
                Object lock = new Object();
                synchronized (lock) {
                    try {
                        lock.wait(1000); // wait 1 second to allow cookie popup
                        // to be closed
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

            } catch (NoSuchElementException e) {
                // ignore, cookie popup not displayed
            } catch (TimeoutException e) {
                // ignore, cookie popup not displayed
            }
            turnOnImplicitWaits();
            acceptCookies = false;
        }
    }

    private void turnOnImplicitWaits() {
        webDriver.manage().timeouts().implicitlyWait(timeout, SECONDS);
    }

    private void turnOffImplicitWaits() {
        webDriver.manage().timeouts().implicitlyWait(0, SECONDS);
    }

    /**
     * Transformer for chronic values.
     */
    public static class ChronicConverter extends Transformer<Calendar> {

        private static Options options = new Options();

        @Override
        public Calendar transform(String value) {
            Span span = Chronic.parse(value, options);
            return span.getEndCalendar();
        }
    }
    
    public Actions moveTo(WebElement element) {
        Actions actions = new Actions(webDriver);
        actions.setMarionette(StringUtils.containsIgnoreCase(browser, MARIONETTE));
        return actions.moveToElement(element);
    }

    public void selectItemInDropdownByValue(WebElement element, String value) {
        ISelect select = new Select(element);
        try {
            select.selectByValue(value);
        } catch (NoSuchElementException e) {
            fail(format("Select-value '%s' not found for element with id '%d'"));
        }
    }

    public void selectItemInDropdown(WebElement element, String value) {
        ISelect select = new Select(element);
        try {
            select.selectByVisibleText(value);
        } catch (NoSuchElementException e) {
            //try by values
            selectItemInDropdownByValue(element, value);
        }
    }

}
