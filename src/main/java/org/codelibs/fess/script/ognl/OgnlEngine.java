package org.codelibs.fess.script.ognl;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.exception.JobProcessingException;
import org.codelibs.fess.script.AbstractScriptEngine;
import org.lastaflute.di.core.factory.SingletonLaContainerFactory;

import ognl.Ognl;

public class OgnlEngine extends AbstractScriptEngine {
    private static final Logger logger = LogManager.getLogger(OgnlEngine.class);

    @Override
    public Object evaluate(String template, Map<String, Object> paramMap) {
        if (StringUtil.isBlank(template)) {
            return null;
        }
        final Map<String, Object> bindingMap = new HashMap<>(paramMap);
        bindingMap.put("container", SingletonLaContainerFactory.getContainer());
        try {
            final Object exp = Ognl.parseExpression(template);
            return Ognl.getValue(exp, bindingMap);
        } catch (final JobProcessingException e) {
            throw e;
        } catch (final Exception e) {
            logger.warn("Failed to evalue groovy script: {} => {}", template, paramMap, e);
            return null;
        }
    }

    @Override
    protected String getName() {
        return "ognl";
    }

}
