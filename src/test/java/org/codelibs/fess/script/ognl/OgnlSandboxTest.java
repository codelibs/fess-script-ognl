/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.script.ognl;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class OgnlSandboxTest extends UnitScriptTestCase {

    @Test
    public void test_memberAccess_deniesByDeclaringClassPrefix() throws Exception {
        final FessMemberAccess memberAccess = new FessMemberAccess(new String[] { "java.lang.System", "java.io" });

        final Method toUpperCase = String.class.getMethod("toUpperCase");
        assertTrue("String methods must stay accessible", memberAccess.isAccessible(null, "a", toUpperCase, null));

        final Method getProperty = System.class.getMethod("getProperty", String.class);
        assertFalse("System must be denied", memberAccess.isAccessible(null, null, getProperty, null));

        final Method fileExists = java.io.File.class.getMethod("exists");
        assertFalse("java.io must be denied", memberAccess.isAccessible(null, null, fileExists, null));
    }

    @Test
    public void test_memberAccess_deniesNonPublicMembers() throws Exception {
        final FessMemberAccess memberAccess = new FessMemberAccess(new String[0]);
        final Method clone = Object.class.getDeclaredMethod("clone");
        assertFalse("Non-public members must be denied", memberAccess.isAccessible(null, null, clone, null));
    }
}
