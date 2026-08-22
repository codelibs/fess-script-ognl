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
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class OgnlSandboxTest extends UnitScriptTestCase {

    private OgnlEngine strictEngine() {
        final OgnlEngine engine = new OgnlEngine();
        engine.setMode("strict");
        engine.init();
        return engine;
    }

    private OgnlEngine compatEngine() {
        final OgnlEngine engine = new OgnlEngine();
        engine.setMode("compat");
        engine.init();
        return engine;
    }

    @Test
    public void test_strictMode_blocksDangerousExpressions() {
        final OgnlEngine engine = strictEngine();
        final Map<String, Object> params = new HashMap<>();
        params.put("value", "Hello");

        assertNull("System must be blocked", engine.evaluate("@java.lang.System@getProperty(\"user.name\")", params));
        assertNull("java.io must be blocked", engine.evaluate("new java.io.File(\"/etc/hosts\").exists()", params));
        assertNull("Class loader must be blocked", engine.evaluate("value.getClass().getClassLoader()", params));
        assertNull("container must not be exposed", engine.evaluate("container", params));
    }

    @Test
    public void test_strictMode_allowsOrdinaryExpressions() {
        final OgnlEngine engine = strictEngine();
        final Map<String, Object> params = new HashMap<>();
        params.put("value", "Hello");
        params.put("count", 3);

        assertEquals("Hello", engine.evaluate("value", params));
        assertEquals("HELLO", engine.evaluate("value.toUpperCase()", params));
        assertEquals(5, engine.evaluate("value.length()", params));
        assertEquals("Hello-3", engine.evaluate("value + \"-\" + count", params));
        assertEquals(4.0, engine.evaluate("@java.lang.Math@sqrt(16)", params));
        assertEquals("Hello", engine.evaluate("#value", params));
    }

    @Test
    public void test_compatMode_keepsHistoricalBehaviour() {
        final OgnlEngine engine = compatEngine();
        final Map<String, Object> params = new HashMap<>();
        params.put("value", "Hello");

        assertNotNull("container stays exposed in compat mode", engine.evaluate("container", params));
        assertNotNull("System stays reachable in compat mode", engine.evaluate("@java.lang.System@getProperty(\"user.name\")", params));
        assertEquals("HELLO", engine.evaluate("value.toUpperCase()", params));
    }

    @Test
    public void test_strictMode_dataStoreReadmeSamples() {
        final OgnlEngine engine = strictEngine();
        final Map<String, Object> params = new HashMap<>();
        params.put("url", "https://example.com/a");
        params.put("name", "a.txt");
        params.put("content", "hello");
        params.put("path", "src/a.txt");
        params.put("contentLength", 5L);
        params.put("mimetype", "text/plain");

        assertEquals("https://example.com/a", engine.evaluate("url", params));
        assertEquals("github.com", engine.evaluate("\"github.com\"", params));
        assertEquals("github.com/codelibs/fess-ds-git/src/a.txt", engine.evaluate("\"github.com/codelibs/fess-ds-git/\" + path", params));
        assertEquals("a.txt", engine.evaluate("name", params));
        assertEquals(5, engine.evaluate("content.length()", params));
        assertEquals("", engine.evaluate("\"\"", params));
        assertEquals(5L, engine.evaluate("contentLength", params));
        assertEquals("text/plain", engine.evaluate("mimetype", params));
    }

    @Test
    public void test_context_paramMapKeyNamedContextIsRetrievableInBothModes() {
        final Map<String, Object> params = new HashMap<>();
        params.put("context", "crawler-transformer-value");

        final OgnlEngine strict = strictEngine();
        assertEquals("context key must be reachable as a root property in strict mode", "crawler-transformer-value",
                strict.evaluate("context", params));
        assertEquals("context key must be reachable as a context variable in strict mode", "crawler-transformer-value",
                strict.evaluate("#context", params));

        final OgnlEngine compat = compatEngine();
        assertEquals("context key must be reachable as a root property in compat mode", "crawler-transformer-value",
                compat.evaluate("context", params));
        assertEquals("context key must be reachable as a context variable in compat mode", "crawler-transformer-value",
                compat.evaluate("#context", params));
    }

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

    @Test
    public void test_classResolver_allowsOnlyListedPrefixes() {
        final FessClassResolver resolver = new FessClassResolver(
                new String[] { "java.lang.Math", "java.lang.String", "java.util.Date", "java.time", "java.lang.Thread", "java.lang.ref" });

        assertEquals(Math.class, resolveOrNull(resolver, "java.lang.Math"));
        assertEquals(String.class, resolveOrNull(resolver, "String"));
        assertEquals(Math.class, resolveOrNull(resolver, "Math"));
        assertEquals(java.util.Date.class, resolveOrNull(resolver, "java.util.Date"));
        assertEquals(java.time.LocalDate.class, resolveOrNull(resolver, "java.time.LocalDate"));
        assertEquals(Thread.class, resolveOrNull(resolver, "java.lang.Thread"));
        assertEquals(java.lang.ref.WeakReference.class, resolveOrNull(resolver, "java.lang.ref.WeakReference"));

        assertNull("System must not resolve", resolveOrNull(resolver, "java.lang.System"));
        assertNull("Unqualified System must not resolve", resolveOrNull(resolver, "System"));
        assertNull("java.io must not resolve", resolveOrNull(resolver, "java.io.File"));
        assertNull("Runtime must not resolve", resolveOrNull(resolver, "java.lang.Runtime"));
        assertNull("ThreadGroup must not resolve despite Thread prefix", resolveOrNull(resolver, "java.lang.ThreadGroup"));
        assertNull("reflect.Method must not resolve despite ref prefix", resolveOrNull(resolver, "java.lang.reflect.Method"));
    }

    private Class<?> resolveOrNull(final FessClassResolver resolver, final String className) {
        try {
            return resolver.classForName(className, null);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }
}
