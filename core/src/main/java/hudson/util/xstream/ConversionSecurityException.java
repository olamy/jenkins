package hudson.util.xstream;

import com.thoughtworks.xstream.converters.ConversionException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
/**
 * Thrown during XStream conversion to indicate a security violation.
 *
 * @since TODO
 */
public class ConversionSecurityException extends ConversionException {
    private static final long serialVersionUID = 1L;

    public ConversionSecurityException(String msg) {
        super(msg);
    }
}
