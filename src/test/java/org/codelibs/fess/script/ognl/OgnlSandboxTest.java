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

import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.lastaflute.di.core.factory.SingletonLaContainerFactory;

public class OgnlSandboxTest extends UnitScriptTestCase {

    // test_mode_readFromConfig_normalizesWhitespaceAndCase registers "systemHelper" directly
    // into the real LastaDi container (SingletonLaContainerFactory.getContainer()) so that
    // ComponentUtil.available() is true and OgnlEngine.getConfigValue() takes its real,
    // config-reading branch instead of the identity fallback it takes when unavailable. That
    // container is a JVM-lifetime singleton with no unregister API, so without isolation the
    // registration would leak into every test that runs afterward in the same fork. Declaring
    // isUseOneTimeContainer() rebuilds (and destroys) a container scoped to each test method
    // here, so the registration cannot escape this class - the same idiom already used
    // elsewhere in Fess for tests that register components directly (see FessPropTest,
    // ComponentUtilTest).
    @Override
    protected boolean isUseOneTimeContainer() {
        return true;
    }

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
        // getName() is declared on java.lang.Class, which is on the deny list. Deliberately not
        // getClassLoader(): String is loaded by the bootstrap loader, so getClassLoader() returns
        // null in compat mode too, which would make this assertion pass for the wrong reason.
        assertNull("Class metadata access must be blocked", engine.evaluate("value.getClass().getName()", params));
        assertNull("container must not be exposed", engine.evaluate("container", params));
    }

    @Test
    public void test_mode_readFromConfig_normalizesWhitespaceAndCase() {
        // script.ognl.mode is a security control's on-switch, so a silent fall-back to compat
        // mode is the worst failure mode here - this guards the .trim() and equalsIgnoreCase()
        // in OgnlEngine.getConfigValue()/init() against regressing, e.g. a config line reading
        // "strict " (with a trailing space, as system.properties preserves it verbatim)
        // silently disabling the sandbox instead of enabling it.
        //
        // OgnlEngine.getConfigValue() only trims when ComponentUtil.available() is true; with
        // no FessConfig/systemHelper wired (the case everywhere else in this test suite) it
        // takes an identity-fallback branch that does NOT trim, so a plain setMode(" STRICT ")
        // + init() would not actually exercise the .trim()/equalsIgnoreCase() code this test
        // guards. Registering a minimal systemHelper directly makes available() true; the
        // FessConfig below simulates "no override configured for this key" by echoing the
        // default straight back, so getConfigValue() must trim/case-normalize it exactly as it
        // would a real system.properties value of the same shape.
        SingletonLaContainerFactory.getContainer().register(new Object(), "systemHelper");
        final FessConfig fessConfig = new FessConfig.SimpleImpl() {
            @Override
            public String getSystemProperty(final String key, final String defaultValue) {
                return defaultValue;
            }

            @Override
            public boolean isScriptAuditLogEnabled() {
                // init() also reads this; FessConfig.SimpleImpl backs only overridden getters
                // and throws NPE on any other one (no properties are loaded in a bare
                // SimpleImpl), so it must be stubbed too even though this test does not
                // exercise audit logging.
                return false;
            }
        };
        ComponentUtil.setFessConfig(fessConfig);

        final OgnlEngine engine = new OgnlEngine();
        engine.setMode(" STRICT ");
        engine.init();

        final Map<String, Object> params = new HashMap<>();
        params.put("value", "Hello");

        assertNull("mode \" STRICT \" resolved through the real config-reading path must still apply the sandbox",
                engine.evaluate("@java.lang.System@getProperty(\"user.name\")", params));
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
        // Compat-mode counterparts of the two expressions blocked in
        // test_strictMode_blocksDangerousExpressions, pinning the contrast that proves the
        // strict-mode assertions are not vacuous: these must return non-null here.
        assertNotNull("java.io stays reachable in compat mode", engine.evaluate("new java.io.File(\"/etc/hosts\").exists()", params));
        assertEquals("java.lang.String", engine.evaluate("value.getClass().getName()", params));
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
