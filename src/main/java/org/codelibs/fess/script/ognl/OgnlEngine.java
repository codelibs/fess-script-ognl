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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.exception.JobProcessingException;
import org.codelibs.fess.script.AbstractScriptEngine;
import org.codelibs.fess.script.ognl.OgnlExpressionCache.CachedExpression;
import org.lastaflute.di.core.factory.SingletonLaContainerFactory;

import jakarta.annotation.PostConstruct;
import ognl.Ognl;

/**
 * Script engine that evaluates OGNL (Object-Graph Navigation Language) expressions.
 * <p>
 * The Fess DI container is exposed to the expression context under the {@code container} key,
 * allowing scripts to access registered components.
 */
public class OgnlEngine extends AbstractScriptEngine {
    private static final Logger logger = LogManager.getLogger(OgnlEngine.class);

    /** Maximum length of script text included in warning log messages. Configurable via DI. */
    protected int maxScriptLogLength = 200;

    /** Maximum number of parsed expressions to cache. Configurable via DI. */
    protected int expressionCacheSize = 1000;

    private OgnlExpressionCache expressionCache = new OgnlExpressionCache(1000);

    /**
     * Creates a new {@link OgnlEngine}.
     */
    public OgnlEngine() {
        super();
    }

    /**
     * Rebuilds internal state after DI property injection.
     */
    @PostConstruct
    public void init() {
        expressionCache = new OgnlExpressionCache(expressionCacheSize);
    }

    /**
     * Sets the maximum length of script text included in log messages.
     *
     * @param maxScriptLogLength the maximum length
     */
    public void setMaxScriptLogLength(final int maxScriptLogLength) {
        this.maxScriptLogLength = maxScriptLogLength;
    }

    /**
     * Sets the maximum number of parsed expressions to cache.
     *
     * @param expressionCacheSize the maximum number of cached expressions
     */
    public void setExpressionCacheSize(final int expressionCacheSize) {
        this.expressionCacheSize = expressionCacheSize;
    }

    /**
     * Returns the parsed-expression cache.
     *
     * @return the parsed-expression cache
     */
    protected OgnlExpressionCache getExpressionCache() {
        return expressionCache;
    }

    /**
     * Truncates the given script so that it is safe to write to a log.
     *
     * @param script the script text, may be null
     * @return the truncated script, or {@code "-"} when the script is null
     */
    protected String abbreviateScript(final String script) {
        if (script == null) {
            return "-";
        }
        if (script.length() <= maxScriptLogLength) {
            return script;
        }
        return script.substring(0, maxScriptLogLength - 3) + "...";
    }

    @Override
    public Object evaluate(final String template, final Map<String, Object> paramMap) {
        if (StringUtil.isBlank(template)) {
            return null;
        }
        final Map<String, Object> safeParamMap = paramMap != null ? paramMap : Collections.emptyMap();
        final Map<String, Object> bindingMap = new HashMap<>(safeParamMap);
        bindingMap.put("container", SingletonLaContainerFactory.getContainer());
        try {
            final CachedExpression expression = expressionCache.get(template, Ognl::parseExpression);
            return Ognl.getValue(expression.getNode(), bindingMap);
        } catch (final JobProcessingException e) {
            throw e;
        } catch (final Exception e) {
            logger.warn("Failed to evaluate ognl script: {} => {}", abbreviateScript(template), safeParamMap.keySet(), e);
            return null;
        }
    }

    @Override
    protected String getName() {
        return "ognl";
    }

}
