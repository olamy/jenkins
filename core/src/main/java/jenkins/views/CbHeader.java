/*
 * Copyright 2020 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */
package jenkins.views;

import hudson.ExtensionPoint;

/**
 * Abstract class to be extended from the SDA UI plugin to include the new header
 */
public class CbHeader implements ExtensionPoint {

    /**
     * Checks if CB header is enabled. By default it is if installed, but the logic is deferred in the plugins.
     * @return
     */
    public boolean isCbHeaderEnabled() {
        return true;
    }
}
