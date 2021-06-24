package jenkins.crypto.fips;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;

/**
 * {@link ComputerListener} that registers BouncyCastle on new Agents as they connect.
 */
@Restricted(NoExternalUse.class)
@Extension
public class BouncyCastleFIPSInstallerComputerListener extends ComputerListener {

    private static final Logger LOG = Logger.getLogger(BouncyCastleFIPSInstallerComputerListener.class.getName());


    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener)
            throws IOException, InterruptedException {
        try {
            // we should not need to preload the jars as the installer class instantiates objects from them so they should appear magically.
            //channel.preloadJar(BouncyCastleFipsProvider.class.getClassLoader(), BouncyCastleFipsProvider.class); // bc-fips
            //channel.preloadJar(BouncyCastleJsseProvider.class.getClassLoader(), BouncyCastleJsseProvider.class); // bc-tls

            channel.call(new BouncyCastleFIPSInstallerCallable());
        } catch (Exception e) {
            listener.error("Failed to configure java security extensions, see system logs for more details.");
            LOG.log(Level.WARNING, "Failed to configure java security extensions on " + c.getName(), e);
            if (BouncyCastleFIPSIntaller.isApprovedOnlyMode()) {
                // the agent is not FIPS compliant, throw the exception to prevent it being used.
                throw new IOException("Could not configure the computer for FIPS compliance", e);
            }
        }
    }
}

