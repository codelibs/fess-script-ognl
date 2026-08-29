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
import org.codelibs.fess.Constants;
import org.codelibs.fess.exception.JobProcessingException;
import org.codelibs.fess.opensearch.config.exentity.ScheduledJob;
import org.codelibs.fess.script.AbstractScriptEngine;
import org.codelibs.fess.script.ognl.OgnlExpressionCache.CachedExpression;
import org.codelibs.fess.util.ComponentUtil;
import org.lastaflute.di.core.factory.SingletonLaContainerFactory;
import org.lastaflute.job.LaJobRuntime;

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

    /** Whether script execution is written to the audit log. Resolved in init(). */
    protected boolean scriptAuditLogEnabled;

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
        if (!isExpressionCompilerAvailable()) {
            logger.error("javassist is not available. The ognl script engine cannot evaluate any expression."
                    + " javassist is normally provided by org.lastaflute:lasta-di in WEB-INF/lib.");
        }
        expressionCache = new OgnlExpressionCache(expressionCacheSize);
        scriptAuditLogEnabled = ComponentUtil.available() && ComponentUtil.getFessConfig().isScriptAuditLogEnabled()
                && ComponentUtil.hasComponent("activityHelper");
    }

    /**
     * Returns whether javassist, which ognl requires for its expression compiler, is on the classpath.
     *
     * @return true when javassist is available
     */
    protected static boolean isExpressionCompilerAvailable() {
        try {
            Class.forName("javassist.ClassPool");
            return true;
        } catch (final Throwable t) {
            return false;
        }
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
        CachedExpression expression = null;
        try {
            expression = expressionCache.get(template, Ognl::parseExpression);
            final Object value = Ognl.getValue(expression.getNode(), bindingMap);
            if (expression.markSuccessAudited()) {
                logScriptExecution(template, "success");
            }
            return value;
        } catch (final JobProcessingException e) {
            auditFailure(expression, template, e);
            throw e;
        } catch (final Exception e) {
            auditFailure(expression, template, e);
            logger.warn("Failed to evaluate ognl script: {} => {}", abbreviateScript(template), safeParamMap.keySet(), e);
            return null;
        }
    }

    @Override
    protected String getName() {
        return "ognl";
    }

    /**
     * Returns the scheduled job that is running on the current thread, if any.
     *
     * @return the running scheduled job, or null
     */
    protected ScheduledJob getCurrentScheduledJob() {
        try {
            if (!ComponentUtil.hasComponent("jobHelper")) {
                return null;
            }
            final LaJobRuntime runtime = ComponentUtil.getJobHelper().getJobRuntime();
            if (runtime != null) {
                final Object job = runtime.getParameterMap().get(Constants.SCHEDULED_JOB);
                if (job instanceof ScheduledJob) {
                    return (ScheduledJob) job;
                }
            }
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get scheduled job from thread local", e);
            }
        }
        return null;
    }

    /**
     * Writes one audit log entry for a script execution.
     * <p>
     * Unlike the Groovy engine, this is called only on the first evaluation of a given
     * expression, because OGNL expressions are evaluated once per document per field.
     *
     * @param script the script content that was executed
     * @param result the execution result, such as {@code "success"} or {@code "failure:ArithmeticException"}
     */
    protected void logScriptExecution(final String script, final String result) {
        if (!scriptAuditLogEnabled) {
            return;
        }
        try {
            String source = "unknown";
            String user = "system";

            final ScheduledJob job = getCurrentScheduledJob();
            if (job != null) {
                source = "scheduler:" + job.getName();
                if (job.getCreatedBy() != null) {
                    user = job.getCreatedBy();
                }
            } else {
                try {
                    user = ComponentUtil.getSystemHelper().getUsername();
                } catch (final Exception e) {
                    // Ignore - background job context
                }
            }

            ComponentUtil.getActivityHelper().scriptExecution(getName(), script, source, user, result);
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to log script execution", e);
            }
        }
    }

    private void auditFailure(final CachedExpression expression, final String template, final Exception e) {
        if (expression != null && expression.markFailureAudited()) {
            logScriptExecution(template, "failure:" + e.getClass().getSimpleName());
        }
    }

}
