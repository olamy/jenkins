/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.crypto.fips;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import org.bouncycastle.crypto.CryptoServicesRegistrar;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class BouncyCastleFIPSIntaller {

    private static final Logger LOG = Logger.getLogger(BouncyCastleFIPSIntaller.class.getName());
    private static final String propertyName = "org.bouncycastle.fips.approved_only";

    /**
     * Holder to set a boolean flag when we have configured FIPS on the JVM. Agents can be disconnected re-connected and
     * we need to only run the registration once.
     */
    //private static volatile boolean bcInstalled = false;

    /**
     * Configure the java.security Providers to use bouncycastle, for use by the controllers JVM only do not use for an
     * agent. This method allows the use of not approved algorithms unless the System property @{code
     * org.bouncycastle.fips.approved_only} is set.
     */
    public static void install() {
        install(isApprovedOnlyMode());
    }

    /**
     * Configure the java.security Providers to use bouncycastle in permissive or restricted mode.
     * 
     * @param fipsOnlyAlgorithms {@code true} if we only allow FIPS compliant usage, {@code false} to allow the use of
     *            non FIPS compliant algorithms.
     */
    public static synchronized void install(boolean fipsOnlyAlgorithms) {
        // only install it once per JVM (ideally)!
        // due to tests and funky classloaders over remoting check if we actually have the same class not something that no longer exists.
        if (!(Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME) instanceof BouncyCastleFipsProvider)) {
            LOG.log(Level.CONFIG, "Configuring java.security providers...");

            if (fipsOnlyAlgorithms) {
                LOG.log(Level.CONFIG, "Using BouncyCastle in FIPS approved only mode.");
                // Ensure the default approved mode is true for every thread
                System.setProperty(propertyName, "true");
            }

            // https://issues.redhat.com/browse/ELY-1622?workflowName=GIT+Pull+Request+workflow+&stepId=4
            Security.setProperty("ssl.KeyManagerFactory.algorithm","X509");

            final BouncyCastleFipsProvider bcFipsProvider = new BouncyCastleFipsProvider();
            //Security.insertProviderAt(bcFipsProvider, 0);
            final BouncyCastleJsseProvider jseeProvider = new BouncyCastleJsseProvider(fipsOnlyAlgorithms, bcFipsProvider);
            //Security.insertProviderAt(jseeProvider, 1);
            // We still need the Sun providers as per https://downloads.bouncycastle.org/fips-java/BC-FJA-UserGuide-1.0.2.pdf
            final Provider sunProvider = new sun.security.provider.Sun();

            sun.security.jca.ProviderList providers = sun.security.jca.ProviderList.newList(bcFipsProvider, jseeProvider, sunProvider);
            sun.security.jca.Providers.setProviderList(providers);
            // there is no atomic set so we need to add ours then remove the others otherwise some things that should be found won't be :( 
            
            assert KeyManagerFactory.getDefaultAlgorithm().equals("X509") : "incorrect default algorithm: " + KeyManagerFactory.getDefaultAlgorithm();

            /*
            final Provider[] providers = Security.getProviders();
            for (Provider p : providers) {
                if (p )
                LOG.log(Level.FINE, "Removing provider with name {0}.", p.getName());
                Security.removeProvider(p.getName());
            }
            */

            // to better understand the behaviour and detect the fallback
            // warning, during startup there seems to be some false positives, to be double checked
            // TODO if we want to run in compliance mode then add the logging provider then add back all the other
            // providers.
            // Security.addProvider(new LogOnlyProvider());

            LOG.log(Level.FINE,
                    () -> Arrays.asList(Security.getProviders()).stream().map(p -> p.getName())
                                .collect(Collectors.joining(",",
                                                            "Configured java.security providers, current installed providers are: ",
                                                            ".")));

            LOG.log(Level.CONFIG, "(regular) FIPS approved mode status: {0}",
                    CryptoServicesRegistrar.isInApprovedOnlyMode() ? "enabled" : "disabled");
            LOG.log(Level.CONFIG, "(JSSE) FIPS approved mode status: {0}",
                    jseeProvider.isFipsMode() ? "enabled" : "disabled");

            // install a security manager to prevent accidental tampering
            if (fipsOnlyAlgorithms) {
                LOG.log(Level.CONFIG, "installing FIPSSecurityManager");
                System.setSecurityManager(new FIPSSecurityManager());
            }
        } else {
            LOG.log(Level.CONFIG, "java.security providers already configured, skipping request");
        }
    }

    public static boolean isApprovedOnlyMode() {
        String sysprop = System.getProperty(propertyName);
        if (sysprop != null) {
            return Boolean.valueOf(sysprop);
        }
        // TODO can we expose this as some config?
        return true;
    }
}
