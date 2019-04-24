/*

 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
package hudson.util;

import com.thoughtworks.xstream.converters.ConversionException;
import org.acegisecurity.AccessDeniedException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;

public class RobustReflectionConverterSEC803Test {

    @Before
    public void setup(){
        System.clearProperty(RobustReflectionConverter.DISABLE_CRITICAL_EXCEPTIONS_PROPERTY_NAME);
        RobustReflectionConverter.loadInitialCriticalExceptions();
    }

    @Test(expected = ConversionException.class)
    public void verifyCriticalExceptionInReadResolveIsThrown() {
        XStream2 xStream = new XStream2();
        PropertyContainer container = new PropertyContainer();
        AccessDeniedReadResolve object = new AccessDeniedReadResolve();
        container.properties.add(object);
        String objectXml = xStream.toXML(container);
        xStream.fromXML(objectXml);
    }

    @Test
    public void verifyNonCriticalExceptionInReadResolveIsSwallowed() {
        XStream2 xStream = new XStream2();
        PropertyContainer container = new PropertyContainer();
        IOExceptionReadResolve object = new IOExceptionReadResolve();
        container.properties.add(object);
        String objectXml = xStream.toXML(container);
        PropertyContainer deserializedContainer = (PropertyContainer) xStream.fromXML(objectXml);
        assertTrue(deserializedContainer.properties.isEmpty());
    }

    @Test(expected = ConversionException.class)
    public void verifyAddedCriticalExceptionInReadResolveIsThrown() {
        XStream2 xStream = new XStream2();
        xStream.addCriticalException(IOException.class);
        PropertyContainer container = new PropertyContainer();
        IOExceptionReadResolve object = new IOExceptionReadResolve();
        container.properties.add(object);
        String objectXml = xStream.toXML(container);
        xStream.fromXML(objectXml);
    }

    @Test
    public void verifyExceptionIsNotThrownWithCriticalExceptionsDisabled() {
        System.setProperty(RobustReflectionConverter.DISABLE_CRITICAL_EXCEPTIONS_PROPERTY_NAME, "true");
        RobustReflectionConverter.loadInitialCriticalExceptions();
        XStream2 xStream = new XStream2();
        PropertyContainer container = new PropertyContainer();
        AccessDeniedReadResolve object = new AccessDeniedReadResolve();
        container.properties.add(object);
        String objectXml = xStream.toXML(container);
        PropertyContainer deserializedContainer = (PropertyContainer) xStream.fromXML(objectXml);
        assertTrue(deserializedContainer.properties.isEmpty());
    }

    private static class PropertyContainer {
        CopyOnWriteList properties = new CopyOnWriteList();
    }

    private static class AccessDeniedReadResolve {

        public Object readResolve() {
            throw new AccessDeniedException("expected failure");
        }

    }

    private static class IOExceptionReadResolve {

        public Object readResolve() throws IOException {
            throw new IOException("expected failure");
        }

    }

}
