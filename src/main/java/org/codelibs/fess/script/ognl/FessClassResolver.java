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

import ognl.DefaultClassResolver;

/**
 * Resolves only the classes named in an allow list.
 * <p>
 * This limits what {@code @some.Class@method(...)} can reach from an OGNL expression.
 * {@link DefaultClassResolver} retries an unqualified name under {@code java.lang.}, and this
 * class hooks the lookup itself so that both forms are checked against the allow list.
 */
public class FessClassResolver extends DefaultClassResolver {

    private final String[] allowedPrefixes;

    /**
     * Creates a resolver that accepts only the given class names and their nested classes.
     *
     * @param allowedPrefixes fully qualified class or package name prefixes to allow
     */
    public FessClassResolver(final String[] allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes.clone();
    }

    @Override
    protected Class<?> toClassForName(final String className) throws ClassNotFoundException {
        for (final String prefix : allowedPrefixes) {
            if (className.equals(prefix) || className.startsWith(prefix + ".")) {
                return super.toClassForName(className);
            }
        }
        throw new ClassNotFoundException("The class is not allowed in strict mode: " + className);
    }
}
