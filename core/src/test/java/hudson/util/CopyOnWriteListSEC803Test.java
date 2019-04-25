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
import org.junit.Test;

public class CopyOnWriteListSEC803Test {

    @Test(expected = ConversionException.class)
    public void verifyExceptionOnInvalidJep200Deserialization() {
        XStream2 xs = new XStream2();
        String xmlString =
                "<hudson.util.CopyOnWriteListTest_-TestData>\n" +
                        "  <list1>\n" +
                        "    <com.google.common.base.Optional_-Present>\n" +
                        "      <referenceclass=\"int\">123</reference>\n" +
                        "    </com.google.common.base.Optional_-Present>\n" +
                        "  </list1>\n" +
                        "  <list2/>\n" +
                        "</hudson.util.CopyOnWriteListTest_-TestData>";

        Object object = xs.fromXML(xmlString);
    }

}
