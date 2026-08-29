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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Caches parsed OGNL expression trees keyed by their source text.
 * <p>
 * OGNL itself does not cache parse results, so without this cache every evaluation
 * re-runs the parser. Parsed trees are safe to share across threads for evaluation.
 */
public class OgnlExpressionCache {

    private final Cache<String, CachedExpression> cache;

    /**
     * Creates a cache holding at most the given number of expressions.
     *
     * @param maximumSize the maximum number of cached expressions
     */
    public OgnlExpressionCache(final int maximumSize) {
        cache = CacheBuilder.newBuilder().maximumSize(maximumSize).build();
    }

    /**
     * Returns the cached expression for the given template, parsing it on a cache miss.
     *
     * @param template the OGNL expression text
     * @param parser the parser used on a cache miss
     * @return the cached expression
     * @throws Exception if parsing fails
     */
    public CachedExpression get(final String template, final ExpressionParser parser) throws Exception {
        try {
            return cache.get(template, () -> new CachedExpression(parser.parse(template)));
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    /**
     * Returns the number of cached expressions after pending evictions are applied.
     *
     * @return the number of cached expressions
     */
    public long size() {
        cache.cleanUp();
        return cache.size();
    }

    /**
     * Removes every cached expression.
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * Parses an OGNL expression.
     */
    @FunctionalInterface
    public interface ExpressionParser {
        /**
         * Parses the given expression text.
         *
         * @param template the OGNL expression text
         * @return the parsed expression tree
         * @throws Exception if parsing fails
         */
        Object parse(String template) throws Exception;
    }

    /**
     * A parsed expression together with flags recording whether it has already been audited.
     */
    public static class CachedExpression {

        private final Object node;

        private final AtomicBoolean successAudited = new AtomicBoolean();

        private final AtomicBoolean failureAudited = new AtomicBoolean();

        CachedExpression(final Object node) {
            this.node = node;
        }

        /**
         * Returns the parsed expression tree.
         *
         * @return the parsed expression tree
         */
        public Object getNode() {
            return node;
        }

        /**
         * Marks this expression as audited for a successful evaluation.
         *
         * @return true on the first call only
         */
        public boolean markSuccessAudited() {
            return successAudited.compareAndSet(false, true);
        }

        /**
         * Marks this expression as audited for a failed evaluation.
         *
         * @return true on the first call only
         */
        public boolean markFailureAudited() {
            return failureAudited.compareAndSet(false, true);
        }
    }
}
