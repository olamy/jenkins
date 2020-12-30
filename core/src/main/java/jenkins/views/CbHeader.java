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
     * Setting the System property jenkins.views.CbHeader.disable to "true" or the Environment variable CB_HEADER_DISABLE to "true"
     * makes the new SDA header not visible.
     */
    public static final boolean CB_HEADER_DISABLED = System.getProperty(CbHeader.class.getName() + ".disable") != null ?
            "true".equalsIgnoreCase(System.getProperty(CbHeader.class.getName() + ".disable")) :
            "true".equalsIgnoreCase(System.getenv("CB_HEADER_DISABLE"));
}
