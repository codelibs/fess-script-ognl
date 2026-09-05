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

import java.util.Arrays;
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
import ognl.ClassResolver;
import ognl.MemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;

/**
 * Script engine that evaluates OGNL (Object-Graph Navigation Language) expressions.
 * <p>
 * In {@code compat} mode (the default), the Fess DI container is exposed to the expression
 * context under the {@code container} key, allowing scripts to access registered components,
 * and no member or class restrictions are applied. In {@code strict} mode the container is not
 * exposed, and a class allow list ({@link FessClassResolver}) and a declaring-class deny list
 * ({@link FessMemberAccess}) restrict what an expression can reach.
 */
public class OgnlEngine extends AbstractScriptEngine {
    private static final Logger logger = LogManager.getLogger(OgnlEngine.class);

    /** Maximum length of script text included in warning log messages. Configurable via DI. */
    protected int maxScriptLogLength = 200;

    /** Maximum number of parsed expressions to cache. Configurable via DI. */
    protected int expressionCacheSize = 1000;

    /** Maximum number of characters allowed in an expression. Configurable via DI. */
    protected int expressionMaxLength = 4000;

    /** Whether script execution is written to the audit log. Resolved in init(). */
    protected boolean scriptAuditLogEnabled;

    /** Mode name that applies the sandbox. */
    protected static final String MODE_STRICT = "strict";

    /** Mode name that keeps historical, unsandboxed behaviour. This is the default. */
    protected static final String MODE_COMPAT = "compat";

    /** Default class allow list applied in strict mode. */
    protected static final String DEFAULT_ALLOWED_CLASSES = "java.lang.Math,java.lang.String,java.lang.Boolean,"
            + "java.lang.Integer,java.lang.Long,java.lang.Float,java.lang.Double,java.lang.Number,"
            + "java.util.Date,java.util.Arrays,java.util.List,java.util.Map,java.util.Set,java.util.Collections,"
            + "java.math.BigDecimal,java.time," + "org.codelibs.core.lang.StringUtil,org.codelibs.fess.util.DocumentUtil,"
            + "org.codelibs.fess.taglib.FessFunctions";

    /** Default declaring-class deny list applied in strict mode. */
    protected static final String DEFAULT_DENIED_PACKAGES = "java.io,java.nio,java.net,java.lang.reflect,"
            + "java.lang.invoke,java.lang.System,java.lang.Class,java.lang.Runtime,java.lang.ProcessBuilder,"
            + "java.lang.Process,java.lang.Thread,java.lang.ClassLoader,javax.script,jdk.,sun.,org.lastaflute.di";

    /** Evaluation mode: "compat" (default) or "strict". Configurable via DI. */
    protected String mode = MODE_COMPAT;

    /** Comma separated class allow list used in strict mode. Configurable via DI. */
    protected String allowedClasses = DEFAULT_ALLOWED_CLASSES;

    /** Comma separated declaring-class deny list used in strict mode. Configurable via DI. */
    protected String deniedPackages = DEFAULT_DENIED_PACKAGES;

    // Written once by init() (on the DI-managed startup thread) and read on every evaluate()
    // call, which may run on a crawler worker thread. volatile so a strict=true / member /
    // class resolver written by init() is visible to those readers without extra locking.
    private volatile boolean strict;

    private volatile MemberAccess memberAccess;

    private volatile ClassResolver classResolver;

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

        expressionCacheSize = getConfigValueAsInt("script.ognl.cache.size", expressionCacheSize, 1);
        maxScriptLogLength = getConfigValueAsInt("script.ognl.max.log.length", maxScriptLogLength, 3);
        expressionMaxLength = getConfigValueAsInt("script.ognl.expression.max.length", expressionMaxLength, 1);
        mode = getConfigValue("script.ognl.mode", mode);
        allowedClasses = getConfigValue("script.ognl.allowed.classes", allowedClasses);
        deniedPackages = getConfigValue("script.ognl.denied.packages", deniedPackages);

        strict = MODE_STRICT.equalsIgnoreCase(mode);
        if (!strict && !MODE_COMPAT.equalsIgnoreCase(mode)) {
            logger.warn("Unknown ognl script engine mode \"{}\"; falling back to {} mode.", mode, MODE_COMPAT);
        }
        if (strict) {
            memberAccess = new FessMemberAccess(split(deniedPackages));
            classResolver = new FessClassResolver(split(allowedClasses));
        } else {
            memberAccess = null;
            classResolver = null;
        }

        expressionCache = new OgnlExpressionCache(expressionCacheSize);

        scriptAuditLogEnabled = ComponentUtil.available() && ComponentUtil.getFessConfig().isScriptAuditLogEnabled()
                && ComponentUtil.hasComponent("activityHelper");

        final String effectiveMode = strict ? MODE_STRICT : MODE_COMPAT;
        logger.info("ognl script engine: mode={}, cacheSize={}, expressionMaxLength={}", effectiveMode, expressionCacheSize,
                expressionMaxLength);
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
     * Sets the maximum number of characters allowed in an expression.
     *
     * @param expressionMaxLength the maximum expression length
     */
    public void setExpressionMaxLength(final int expressionMaxLength) {
        this.expressionMaxLength = expressionMaxLength;
    }

    /**
     * Sets the evaluation mode.
     *
     * @param mode {@code "compat"} or {@code "strict"}
     */
    public void setMode(final String mode) {
        this.mode = mode;
    }

    /**
     * Sets the comma separated class allow list used in strict mode.
     *
     * @param allowedClasses the allow list
     */
    public void setAllowedClasses(final String allowedClasses) {
        this.allowedClasses = allowedClasses;
    }

    /**
     * Sets the comma separated declaring-class deny list used in strict mode.
     *
     * @param deniedPackages the deny list
     */
    public void setDeniedPackages(final String deniedPackages) {
        this.deniedPackages = deniedPackages;
    }

    /**
     * Reads a plugin setting from system.properties, falling back to the given default.
     *
     * @param key the setting key, without the {@code fess.system.} prefix
     * @param defaultValue the value used when the setting is unavailable
     * @return the resolved value
     */
    protected String getConfigValue(final String key, final String defaultValue) {
        try {
            if (ComponentUtil.available()) {
                final String value = ComponentUtil.getFessConfig().getSystemProperty(key, defaultValue);
                // system.properties preserves trailing/leading whitespace verbatim (e.g. a
                // trailing space after "strict" on a config line), which would otherwise make
                // an exact mode comparison silently fail and fall back to the unsafe default.
                return value != null ? value.trim() : defaultValue;
            }
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to read {}", key, e);
            }
        }
        return defaultValue;
    }

    /**
     * Reads a plugin setting as an integer, falling back to the given default on a malformed
     * value and clamping to the given minimum on an out-of-range one.
     *
     * @param key the setting key, without the {@code fess.system.} prefix
     * @param defaultValue the value used when the setting is unavailable or malformed
     * @param minValue the smallest value this setting may safely take
     * @return the resolved value, at least {@code minValue}
     */
    private int getConfigValueAsInt(final String key, final int defaultValue, final int minValue) {
        final String value = getConfigValue(key, Integer.toString(defaultValue));
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            logger.warn("Invalid value for {}: {}. Using {}.", key, value, defaultValue);
            return defaultValue;
        }
        if (parsed < minValue) {
            logger.warn("Value for {} is below the minimum {}: {}. Using {}.", key, minValue, parsed, minValue);
            return minValue;
        }
        return parsed;
    }

    private static String[] split(final String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
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
        return script.substring(0, Math.max(0, maxScriptLogLength - 3)) + "...";
    }

    /**
     * Creates the evaluation context. The binding map is used both as the OGNL root object
     * and as the context variables, so that {@code name} and {@code #name} resolve alike.
     *
     * @param bindingMap the evaluation parameters
     * @return the evaluation context
     */
    protected OgnlContext createContext(final Map<String, Object> bindingMap) {
        if (strict) {
            return Ognl.createDefaultContext(bindingMap, memberAccess, classResolver, null).withValues(bindingMap);
        }
        return Ognl.createDefaultContext(bindingMap).withValues(bindingMap);
    }

    @Override
    public Object evaluate(final String template, final Map<String, Object> paramMap) {
        if (StringUtil.isBlank(template)) {
            return null;
        }
        if (template.length() > expressionMaxLength) {
            logger.warn("The ognl expression exceeds the maximum length {}: job={}, {}", expressionMaxLength, describeCurrentJob(),
                    abbreviateScript(template));
            return null;
        }
        final Map<String, Object> safeParamMap = paramMap != null ? paramMap : Collections.emptyMap();
        final Map<String, Object> bindingMap = new HashMap<>(safeParamMap);
        if (!strict) {
            bindingMap.put("container", SingletonLaContainerFactory.getContainer());
        }
        CachedExpression expression = null;
        try {
            expression = expressionCache.get(template, Ognl::parseExpression);
            final Object value = Ognl.getValue(expression.getNode(), createContext(bindingMap), bindingMap);
            if (expression.markSuccessAudited()) {
                logScriptExecution(template, "success");
            }
            if (value == null && logger.isDebugEnabled()) {
                logger.debug("The ognl script evaluated to null: {} => {}", abbreviateScript(template), safeParamMap.keySet());
            }
            return value;
        } catch (final JobProcessingException e) {
            auditFailure(expression, template, e);
            throw e;
        } catch (final Exception e) {
            auditFailure(expression, template, e);
            logger.warn("Failed to evaluate ognl script: job={}, {} => {}", describeCurrentJob(), abbreviateScript(template),
                    safeParamMap.keySet(), e);
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
     * Describes the scheduled job the current evaluation belongs to, for the warnings in
     * {@link #evaluate(String, Map)}.
     *
     * <p>An expression that is rejected or that fails returns null instead of propagating, so a
     * scheduler job whose script cannot be evaluated is still recorded with a successful status.
     * Naming the job here is what ties that job log entry back to the warning; the expression text
     * on its own does not say which job produced it.</p>
     *
     * @return the job name and id, or "none" when the evaluation is not part of a scheduled job
     */
    protected String describeCurrentJob() {
        final ScheduledJob job = getCurrentScheduledJob();
        if (job == null) {
            return "none";
        }
        return job.getName() + "(id=" + job.getId() + ")";
    }

    /**
     * Writes one audit log entry for a script execution.
     * <p>
     * Unlike the Groovy engine, this is called only on the first evaluation of a given
     * expression since the expression was last cached, because OGNL expressions are evaluated
     * once per document per field.
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
