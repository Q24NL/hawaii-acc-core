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

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.EventFiringWebDriver;

/**
 * Because the new geckodriver (v 0.16.1) does not support Actions (e.g. mouse clicks) we needed a
 * modified Actions class that remaps the mouse-clicks to javascript code when using the geckodriver
 *
 * Please remove this class once the geckodriver is properly supporting Actions:
 *
 * http://stackoverflow.com/questions/39104732/selenium-web-driver-movetoelement-actions-throwing-error-with-marionette-drive
 */
public class Actions extends org.openqa.selenium.interactions.Actions {
    private boolean isMarionette = false;
    private WebElement element;
    private final WebDriver driver;

    public Actions(WebDriver driver) {
        super(driver);
        this.driver = driver;
    }

    public Actions moveToElement(WebElement target) {
        if (isMarionette()) {
            element = target;
            return this;
        }
        return (Actions) super.moveToElement(target);

    }

    public Actions click() {
        if (isMarionette()) {
            return this;
        }
        return (Actions) super.click();
    }

    public void perform() {
        if (isMarionette()) {
            clickPerform();
        } else {
            super.perform();
        }
    }

    public boolean isMarionette() {
        return isMarionette;
    }

    public void setMarionette(boolean marionette) {
        isMarionette = marionette;
    }

    private void clickPerform() {
        //rove, may 2017: hacky workaround, needed until geckodriver properly supports the moveto-actions:
        if (driver instanceof EventFiringWebDriver) {
            if (((EventFiringWebDriver) driver).getWrappedDriver() instanceof JavascriptExecutor) {
                ((JavascriptExecutor) ((EventFiringWebDriver) driver).getWrappedDriver())
                        .executeScript("arguments[0].click();", element);
                return;
            } else {
                throw new RuntimeException("no javascriptExecutor");
            }
        }
        throw new RuntimeException("unexpected driver found");
    }
}
