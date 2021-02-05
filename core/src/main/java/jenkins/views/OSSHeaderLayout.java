/*
 * Copyright 2020 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */
package jenkins.views;

import hudson.Extension;
import hudson.ExtensionPoint;

/**
 * Class to include the classic header in CloudBees products while the new SDA header lands
 *
 * TODO remove once the new plugins land in CAP
 */
@Extension
public class OSSHeaderLayout implements ExtensionPoint {
}
