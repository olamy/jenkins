package jenkins.crypto.fips;

import java.security.Permission;
import java.security.Provider;
import java.security.Security;

/**
 * {@link SecurityManager} that prevents the installation of {@link Provider}s into the {@link Security} context.
 * This just prevents accidents, we do not prevent for example the changing of the SecurityManager to a more permissive one to enable registration.
 */
public class FIPSSecurityManager extends SecurityManager {

    @Override
    public void checkSecurityAccess(String target) {
        if (target.startsWith("insertProvider")) {
            throw new SecurityException("Registration of new security Providers is not supported when running in FIPS compliance mode");
        }
        // everything else goes!
    }
    
    @Override
    public void checkPermission(Permission perm) {
        // allowed
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        // allowed
    }

}
