package jenkins.crypto;

import jenkins.crypto.fips.BouncyCastleFIPSIntaller;
import jenkins.model.Jenkins;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

public class LdapTest {

    // just to ensure we load the class and install BouncyCastleFIPS
    private Jenkins jenkins;

    static {
        BouncyCastleFIPSIntaller.install();
    }

    @Test
    public void testConnect() throws Exception {

        Hashtable<String,Object> props = new Hashtable<>();

        // normal
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, "ldap url");

        props.put("java.naming.referral", "follow");
        // generate issue with fips
        // because with this property jdk code try sslSocket.startHandshake()
        // but not if this connect timeout <= 0 and the connection go tru
        props.put("com.sun.jndi.ldap.connect.timeout", "30000");


        props.put("com.sun.jndi.ldap.connect.pool", "true");
        //props.put("org.springframework.ldap.base.path", new LdapName("the cn"));
        props.put("com.sun.jndi.ldap.read.timeout", "60000");
        //props.put("java.naming.factory.object", "org.springframework.ldap.core.support.DefaultDirObjectFactory");

        LdapContext ctx = new InitialLdapContext(props,  null);
        //ctx.getAttributes("");
        if (ctx!=null) ctx.close();
        // NamingManager.getInitialContext(props);

    }

}
