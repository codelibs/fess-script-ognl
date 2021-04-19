package org.codelibs.fess.script.ognl;

import java.util.HashMap;
import java.util.Map;

import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;

public class OgnlEngineTest extends LastaFluteTestCase {
    public OgnlEngine ognlEngine;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ognlEngine = new OgnlEngine();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    public void test_evaluate() {
        final Map<String, Object> params = new HashMap<>();
        assertNull(ognlEngine.evaluate("", params));
        assertEquals("", ognlEngine.evaluate("''", params));
        assertEquals(1, ognlEngine.evaluate("1", params));

        params.put("test", "123");
        assertEquals("123", ognlEngine.evaluate("test", params));
        assertEquals("test123", ognlEngine.evaluate("\"test\" + test", params));
    }

    public void test_getName() {
        assertEquals("ognl", ognlEngine.getName());
    }
}
