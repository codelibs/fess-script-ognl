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

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

import ognl.AbstractMemberAccess;
import ognl.OgnlContext;

/**
 * Restricts which members an OGNL expression may touch.
 * <p>
 * Non-public members are always denied. In addition, a member is denied when the fully
 * qualified name of its declaring class starts with one of the configured prefixes.
 */
public class FessMemberAccess extends AbstractMemberAccess {

    private final String[] deniedPrefixes;

    /**
     * Creates a member access that denies the given declaring-class prefixes.
     *
     * @param deniedPrefixes fully qualified class or package name prefixes to deny
     */
    public FessMemberAccess(final String[] deniedPrefixes) {
        this.deniedPrefixes = deniedPrefixes.clone();
    }

    @Override
    public boolean isAccessible(final OgnlContext context, final Object target, final Member member, final String propertyName) {
        if (member == null || !Modifier.isPublic(member.getModifiers())) {
            return false;
        }
        final Class<?> declaringClass = member.getDeclaringClass();
        if (declaringClass == null) {
            return false;
        }
        final String name = declaringClass.getName();
        for (final String prefix : deniedPrefixes) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}
