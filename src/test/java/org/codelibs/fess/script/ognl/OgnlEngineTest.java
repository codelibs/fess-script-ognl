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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.exception.JobProcessingException;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.script.ognl.UnitScriptTestCase;

public class OgnlEngineTest extends UnitScriptTestCase {
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
    public void setUp(TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        ognlEngine = new OgnlEngine();
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    // ========================================
    // Basic Tests
    // ========================================

    @Test
    public void test_evaluate_basicLiterals() {
        final Map<String, Object> params = new HashMap<>();

        // Empty and null cases
        assertNull(ognlEngine.evaluate("", params));
        assertNull(ognlEngine.evaluate("   ", params));
        assertNull(ognlEngine.evaluate(null, params));

        // String literals
        assertEquals("", ognlEngine.evaluate("''", params));
        assertEquals("hello", ognlEngine.evaluate("'hello'", params));
        assertEquals("hello", ognlEngine.evaluate("\"hello\"", params));

        // Numeric literals
        assertEquals(1, ognlEngine.evaluate("1", params));
        assertEquals(123, ognlEngine.evaluate("123", params));
        assertEquals(-456, ognlEngine.evaluate("-456", params));
        assertEquals(3.14, ognlEngine.evaluate("3.14", params));

        // Boolean literals
        assertEquals(true, ognlEngine.evaluate("true", params));
        assertEquals(false, ognlEngine.evaluate("false", params));

        // Null literal
        assertNull(ognlEngine.evaluate("null", params));
    }

    @Test
    public void test_evaluate_variableSubstitution() {
        final Map<String, Object> params = new HashMap<>();

        params.put("test", "123");
        assertEquals("123", ognlEngine.evaluate("test", params));

        params.put("name", "John");
        assertEquals("John", ognlEngine.evaluate("name", params));

        params.put("count", 42);
        assertEquals(42, ognlEngine.evaluate("count", params));

        params.put("flag", true);
        assertEquals(true, ognlEngine.evaluate("flag", params));
    }

    @Test
    public void test_evaluate_nullParamMap() {
        // Should not throw exception with null paramMap
        try {
            ognlEngine.evaluate("123", null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected behavior - the constructor new HashMap<>(paramMap) will throw NPE
        }
    }

    @Test
    public void test_evaluate_emptyParamMap() {
        final Map<String, Object> params = new HashMap<>();

        assertEquals(100, ognlEngine.evaluate("100", params));
        assertEquals("test", ognlEngine.evaluate("'test'", params));
    }

    @Test
    public void test_evaluate_multipleParameters() {
        final Map<String, Object> params = new HashMap<>();
        params.put("firstName", "John");
        params.put("lastName", "Doe");
        params.put("age", 30);

        assertEquals("John", ognlEngine.evaluate("firstName", params));
        assertEquals("Doe", ognlEngine.evaluate("lastName", params));
        assertEquals(30, ognlEngine.evaluate("age", params));
    }

    // ========================================
    // String Operations Tests
    // ========================================

    @Test
    public void test_evaluate_stringOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("test", "123");
        params.put("str1", "Hello");
        params.put("str2", "World");

        // String concatenation
        assertEquals("test123", ognlEngine.evaluate("\"test\" + test", params));
        assertEquals("HelloWorld", ognlEngine.evaluate("str1 + str2", params));
        assertEquals("Hello World", ognlEngine.evaluate("str1 + ' ' + str2", params));
    }

    @Test
    public void test_evaluate_stringMethods() {
        final Map<String, Object> params = new HashMap<>();
        params.put("text", "Hello World");

        // String method invocations
        assertEquals(11, ognlEngine.evaluate("text.length()", params));
        assertEquals("HELLO WORLD", ognlEngine.evaluate("text.toUpperCase()", params));
        assertEquals("hello world", ognlEngine.evaluate("text.toLowerCase()", params));
        assertEquals("Hello", ognlEngine.evaluate("text.substring(0, 5)", params));
        assertEquals(true, ognlEngine.evaluate("text.startsWith('Hello')", params));
        assertEquals(true, ognlEngine.evaluate("text.endsWith('World')", params));
        assertEquals(true, ognlEngine.evaluate("text.contains('lo Wo')", params));
        assertEquals("Hi World", ognlEngine.evaluate("text.replace('Hello', 'Hi')", params));
    }

    // ========================================
    // Arithmetic Operations Tests
    // ========================================

    @Test
    public void test_evaluate_arithmeticOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("a", 10);
        params.put("b", 3);

        // Basic arithmetic
        assertEquals(13, ognlEngine.evaluate("a + b", params));
        assertEquals(7, ognlEngine.evaluate("a - b", params));
        assertEquals(30, ognlEngine.evaluate("a * b", params));
        assertEquals(3, ognlEngine.evaluate("a / b", params));
        assertEquals(1, ognlEngine.evaluate("a % b", params));

        // Complex expressions
        assertEquals(23, ognlEngine.evaluate("a + b * 4 + 1", params));
        assertEquals(52, ognlEngine.evaluate("(a + b) * 4", params));
    }

    @Test
    public void test_evaluate_mathOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("x", 16.0);
        params.put("y", -5.5);

        // Math class methods
        assertEquals(4.0, ognlEngine.evaluate("@Math@sqrt(x)", params));
        assertEquals(5.5, ognlEngine.evaluate("@Math@abs(y)", params));
        assertEquals(16, ognlEngine.evaluate("@Math@max(10, 16)", params));
        assertEquals(10, ognlEngine.evaluate("@Math@min(10, 16)", params));
    }

    // ========================================
    // Boolean and Comparison Tests
    // ========================================

    @Test
    public void test_evaluate_comparisonOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("a", 10);
        params.put("b", 20);
        params.put("c", 10);

        // Comparison operators
        assertEquals(true, ognlEngine.evaluate("a < b", params));
        assertEquals(false, ognlEngine.evaluate("a > b", params));
        assertEquals(true, ognlEngine.evaluate("a <= c", params));
        assertEquals(true, ognlEngine.evaluate("a >= c", params));
        assertEquals(true, ognlEngine.evaluate("a == c", params));
        assertEquals(false, ognlEngine.evaluate("a == b", params));
        assertEquals(true, ognlEngine.evaluate("a != b", params));
    }

    @Test
    public void test_evaluate_logicalOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("flag1", true);
        params.put("flag2", false);

        // Logical operators
        assertEquals(false, ognlEngine.evaluate("flag1 && flag2", params));
        assertEquals(true, ognlEngine.evaluate("flag1 || flag2", params));
        assertEquals(false, ognlEngine.evaluate("!flag1", params));
        assertEquals(true, ognlEngine.evaluate("!flag2", params));

        // Complex logical expressions
        assertEquals(true, ognlEngine.evaluate("flag1 && !flag2", params));
        assertEquals(false, ognlEngine.evaluate("!flag1 || flag2", params));
    }

    @Test
    public void test_evaluate_ternaryOperator() {
        final Map<String, Object> params = new HashMap<>();
        params.put("age", 25);
        params.put("score", 85);

        // Ternary conditional operator
        assertEquals("adult", ognlEngine.evaluate("age >= 20 ? 'adult' : 'minor'", params));
        assertEquals("pass", ognlEngine.evaluate("score >= 60 ? 'pass' : 'fail'", params));

        params.put("age", 15);
        params.put("score", 45);
        assertEquals("minor", ognlEngine.evaluate("age >= 20 ? 'adult' : 'minor'", params));
        assertEquals("fail", ognlEngine.evaluate("score >= 60 ? 'pass' : 'fail'", params));
    }

    // ========================================
    // Collection Operations Tests
    // ========================================

    @Test
    public void test_evaluate_listOperations() {
        final Map<String, Object> params = new HashMap<>();
        List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
        params.put("names", names);

        // List access
        assertEquals("Alice", ognlEngine.evaluate("names[0]", params));
        assertEquals("Bob", ognlEngine.evaluate("names[1]", params));
        assertEquals("Charlie", ognlEngine.evaluate("names[2]", params));

        // List size
        assertEquals(3, ognlEngine.evaluate("names.size", params));
    }

    @Test
    public void test_evaluate_arrayOperations() {
        final Map<String, Object> params = new HashMap<>();
        int[] numbers = { 10, 20, 30, 40, 50 };
        params.put("numbers", numbers);

        // Array access
        assertEquals(10, ognlEngine.evaluate("numbers[0]", params));
        assertEquals(30, ognlEngine.evaluate("numbers[2]", params));
        assertEquals(50, ognlEngine.evaluate("numbers[4]", params));

        // Array length
        assertEquals(5, ognlEngine.evaluate("numbers.length", params));
    }

    @Test
    public void test_evaluate_mapOperations() {
        final Map<String, Object> params = new HashMap<>();
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "John");
        userData.put("age", 30);
        userData.put("city", "Tokyo");
        params.put("user", userData);

        // Map access using brackets
        assertEquals("John", ognlEngine.evaluate("user['name']", params));
        assertEquals(30, ognlEngine.evaluate("user['age']", params));
        assertEquals("Tokyo", ognlEngine.evaluate("user['city']", params));

        // Map size
        assertEquals(3, ognlEngine.evaluate("user.size", params));
    }

    // ========================================
    // Object Property Access Tests
    // ========================================

    @Test
    public void test_evaluate_objectPropertyAccess() {
        final Map<String, Object> params = new HashMap<>();
        TestPerson person = new TestPerson("Alice", 28);
        params.put("person", person);

        // Property access
        assertEquals("Alice", ognlEngine.evaluate("person.name", params));
        assertEquals(28, ognlEngine.evaluate("person.age", params));

        // Method invocation
        assertEquals("Alice (28)", ognlEngine.evaluate("person.getInfo()", params));
    }

    @Test
    public void test_evaluate_nestedPropertyAccess() {
        final Map<String, Object> params = new HashMap<>();
        TestAddress address = new TestAddress("Tokyo", "Japan");
        TestPerson person = new TestPerson("Bob", 35);
        person.setAddress(address);
        params.put("person", person);

        // Nested property access
        assertEquals("Tokyo", ognlEngine.evaluate("person.address.city", params));
        assertEquals("Japan", ognlEngine.evaluate("person.address.country", params));
    }

    // ========================================
    // Container Integration Tests
    // ========================================

    @Test
    public void test_evaluate_containerAccess() {
        final Map<String, Object> params = new HashMap<>();

        // Verify that the container is accessible in expressions
        assertNotNull(ognlEngine.evaluate("container", params));

        // The container should be a LaContainer instance
        Object container = ognlEngine.evaluate("container", params);
        assertNotNull(container);
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    @Test
    public void test_evaluate_invalidExpression() {
        final Map<String, Object> params = new HashMap<>();

        // Invalid OGNL syntax should return null and log warning
        assertNull(ognlEngine.evaluate("this is not valid ognl @#$%", params));
        assertNull(ognlEngine.evaluate("a +", params));
        assertNull(ognlEngine.evaluate("(unclosed parenthesis", params));
    }

    @Test
    public void test_evaluate_undefinedVariable() {
        final Map<String, Object> params = new HashMap<>();

        // Accessing undefined variable should return null and log warning
        assertNull(ognlEngine.evaluate("undefinedVariable", params));
        assertNull(ognlEngine.evaluate("foo.bar.baz", params));
    }

    @Test
    public void test_evaluate_jobProcessingException() {
        final Map<String, Object> params = new HashMap<>();

        // Put a special object that throws JobProcessingException when accessed
        params.put("errorObj", new Object() {
            @Override
            public String toString() {
                throw new JobProcessingException("Test job processing error");
            }
        });

        // JobProcessingException should be re-thrown, not caught
        try {
            ognlEngine.evaluate("errorObj.toString()", params);
            fail("Should throw JobProcessingException");
        } catch (JobProcessingException e) {
            assertEquals("Test job processing error", e.getMessage());
        }
    }

    @Test
    public void test_evaluate_divisionByZero() {
        final Map<String, Object> params = new HashMap<>();

        // Division by zero should return null and log warning
        assertNull(ognlEngine.evaluate("10 / 0", params));
    }

    // ========================================
    // Special Characters and Unicode Tests
    // ========================================

    @Test
    public void test_evaluate_specialCharacters() {
        final Map<String, Object> params = new HashMap<>();
        params.put("special", "Hello\nWorld\t!");

        assertEquals("Hello\nWorld\t!", ognlEngine.evaluate("special", params));
    }

    @Test
    public void test_evaluate_unicodeCharacters() {
        final Map<String, Object> params = new HashMap<>();
        params.put("japanese", "こんにちは");
        params.put("emoji", "😀🎉");
        params.put("chinese", "你好");

        assertEquals("こんにちは", ognlEngine.evaluate("japanese", params));
        assertEquals("😀🎉", ognlEngine.evaluate("emoji", params));
        assertEquals("你好", ognlEngine.evaluate("chinese", params));

        // Unicode in expressions
        assertEquals("こんにちは世界", ognlEngine.evaluate("japanese + '世界'", params));
    }

    // ========================================
    // Complex Expression Tests
    // ========================================

    @Test
    public void test_evaluate_complexExpression() {
        final Map<String, Object> params = new HashMap<>();
        params.put("price", 1000);
        params.put("quantity", 5);
        params.put("taxRate", 0.1);
        params.put("discount", 100);

        // Complex calculation
        Object result = ognlEngine.evaluate("(price * quantity * (1 + taxRate)) - discount", params);
        assertEquals(5400.0, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    public void test_evaluate_nestedExpressions() {
        final Map<String, Object> params = new HashMap<>();
        params.put("a", 5);
        params.put("b", 10);
        params.put("c", 15);

        // Nested ternary operators - a=5, b=10, c=15: a > b is false, b > c is false, so result is "low"
        assertEquals("low", ognlEngine.evaluate("a > b ? 'high' : (b > c ? 'medium' : 'low')", params));

        // a=12, b=10, c=15: a > b is true, so result is "high"
        params.put("a", 12);
        assertEquals("high", ognlEngine.evaluate("a > b ? 'high' : (b > c ? 'medium' : 'low')", params));
    }

    // ========================================
    // Collection Projection Tests (OGNL-specific)
    // ========================================

    @Test
    public void test_evaluate_collectionProjection() {
        final Map<String, Object> params = new HashMap<>();
        List<TestPerson> people = Arrays.asList(new TestPerson("Alice", 25), new TestPerson("Bob", 30), new TestPerson("Charlie", 35));
        params.put("people", people);

        // Projection: extract names from list
        Object result = ognlEngine.evaluate("people.{name}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> names = (List<?>) result;
        assertEquals(3, names.size());
        assertEquals("Alice", names.get(0));
        assertEquals("Bob", names.get(1));
        assertEquals("Charlie", names.get(2));

        // Projection: extract ages
        Object ages = ognlEngine.evaluate("people.{age}", params);
        assertNotNull(ages);
        assertTrue(ages instanceof List);
        List<?> ageList = (List<?>) ages;
        assertEquals(3, ageList.size());
        assertEquals(25, ageList.get(0));
        assertEquals(30, ageList.get(1));
        assertEquals(35, ageList.get(2));
    }

    @Test
    public void test_evaluate_collectionSelection() {
        final Map<String, Object> params = new HashMap<>();
        List<TestPerson> people = Arrays.asList(new TestPerson("Alice", 25), new TestPerson("Bob", 30), new TestPerson("Charlie", 35));
        params.put("people", people);

        // Selection: filter people with age > 28
        Object result = ognlEngine.evaluate("people.{? #this.age > 28}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> filtered = (List<?>) result;
        assertEquals(2, filtered.size());

        // First selection: get first match
        Object first = ognlEngine.evaluate("people.{^ #this.age > 28}", params);
        assertNotNull(first);
        assertTrue(first instanceof List);
        List<?> firstList = (List<?>) first;
        assertEquals(1, firstList.size());
        assertEquals("Bob", ((TestPerson) firstList.get(0)).getName());

        // Last selection: get last match
        Object last = ognlEngine.evaluate("people.{$ #this.age > 28}", params);
        assertNotNull(last);
        assertTrue(last instanceof List);
        List<?> lastList = (List<?>) last;
        assertEquals(1, lastList.size());
        assertEquals("Charlie", ((TestPerson) lastList.get(0)).getName());
    }

    // ========================================
    // Constructor Invocation Tests
    // ========================================

    @Test
    public void test_evaluate_constructorInvocation() {
        final Map<String, Object> params = new HashMap<>();

        // Create new objects via OGNL
        Object result = ognlEngine.evaluate("new java.util.ArrayList()", params);
        assertNotNull(result);
        assertTrue(result instanceof java.util.ArrayList);

        // Create with arguments
        Object hashMap = ognlEngine.evaluate("new java.util.HashMap()", params);
        assertNotNull(hashMap);
        assertTrue(hashMap instanceof java.util.HashMap);

        // StringBuilder
        Object sb = ognlEngine.evaluate("new java.lang.StringBuilder('hello')", params);
        assertNotNull(sb);
        assertEquals("hello", sb.toString());
    }

    // ========================================
    // Static Field Access Tests
    // ========================================

    @Test
    public void test_evaluate_staticFieldAccess() {
        final Map<String, Object> params = new HashMap<>();

        // Access static fields
        assertEquals(Integer.MAX_VALUE, ognlEngine.evaluate("@Integer@MAX_VALUE", params));
        assertEquals(Integer.MIN_VALUE, ognlEngine.evaluate("@Integer@MIN_VALUE", params));
        assertEquals(Boolean.TRUE, ognlEngine.evaluate("@Boolean@TRUE", params));
        assertEquals(Boolean.FALSE, ognlEngine.evaluate("@Boolean@FALSE", params));
    }

    @Test
    public void test_evaluate_staticMethodCalls() {
        final Map<String, Object> params = new HashMap<>();

        // Static method calls
        assertEquals("123", ognlEngine.evaluate("@String@valueOf(123)", params));
        assertEquals(42, ognlEngine.evaluate("@Integer@parseInt('42')", params));
        assertEquals("HELLO", ognlEngine.evaluate("@String@valueOf('hello').toUpperCase()", params));
    }

    // ========================================
    // Chained Method Calls Tests
    // ========================================

    @Test
    public void test_evaluate_chainedMethodCalls() {
        final Map<String, Object> params = new HashMap<>();
        params.put("text", "  Hello World  ");

        // Method chaining
        assertEquals("hello world", ognlEngine.evaluate("text.trim().toLowerCase()", params));
        assertEquals("HELLO WORLD", ognlEngine.evaluate("text.trim().toUpperCase()", params));
        assertEquals("Hi World", ognlEngine.evaluate("text.trim().replace('Hello', 'Hi')", params));

        // Chain with substring
        assertEquals("Hello", ognlEngine.evaluate("text.trim().substring(0, 5)", params));
        assertEquals(5, ognlEngine.evaluate("text.trim().substring(0, 5).length()", params));
    }

    // ========================================
    // Type Conversion Tests
    // ========================================

    @Test
    public void test_evaluate_typeConversion() {
        final Map<String, Object> params = new HashMap<>();
        params.put("intVal", 42);
        params.put("strNum", "123");
        params.put("doubleVal", 3.14);

        // Integer to string via method
        assertEquals("42", ognlEngine.evaluate("@String@valueOf(intVal)", params));

        // String to integer via static method
        assertEquals(123, ognlEngine.evaluate("@Integer@parseInt(strNum)", params));

        // Double to int
        assertEquals(3, ognlEngine.evaluate("doubleVal.intValue()", params));

        // Integer arithmetic with mixed types
        Object result = ognlEngine.evaluate("intVal + doubleVal", params);
        assertNotNull(result);
        assertEquals(45.14, ((Number) result).doubleValue(), 0.001);
    }

    // ========================================
    // OGNL Context Variable Tests (#variable)
    // ========================================

    @Test
    public void test_evaluate_contextVariables() {
        final Map<String, Object> params = new HashMap<>();
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        params.put("numbers", numbers);

        // #this in projection
        Object result = ognlEngine.evaluate("numbers.{#this * 2}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> doubled = (List<?>) result;
        assertEquals(5, doubled.size());
        assertEquals(2, doubled.get(0));
        assertEquals(4, doubled.get(1));
        assertEquals(6, doubled.get(2));
        assertEquals(8, doubled.get(3));
        assertEquals(10, doubled.get(4));

        // #this in selection
        Object filtered = ognlEngine.evaluate("numbers.{? #this > 3}", params);
        assertNotNull(filtered);
        assertTrue(filtered instanceof List);
        List<?> filteredList = (List<?>) filtered;
        assertEquals(2, filteredList.size());
        assertEquals(4, filteredList.get(0));
        assertEquals(5, filteredList.get(1));
    }

    // ========================================
    // Comma (Sequence) Expression Tests
    // ========================================

    @Test
    public void test_evaluate_commaExpression() {
        final Map<String, Object> params = new HashMap<>();

        // Comma expression returns the last value
        Object result = ognlEngine.evaluate("1, 2, 3", params);
        assertEquals(3, result);

        // Comma with string operations
        Object strResult = ognlEngine.evaluate("'hello', 'world'", params);
        assertEquals("world", strResult);
    }

    // ========================================
    // In Operator Tests
    // ========================================

    @Test
    public void test_evaluate_inOperator() {
        final Map<String, Object> params = new HashMap<>();
        params.put("value", 3);

        // in operator
        assertEquals(true, ognlEngine.evaluate("value in {1, 2, 3, 4, 5}", params));
        assertEquals(false, ognlEngine.evaluate("value in {1, 2, 4, 5}", params));

        // not in operator
        assertEquals(true, ognlEngine.evaluate("value not in {1, 2, 4, 5}", params));
        assertEquals(false, ognlEngine.evaluate("value not in {1, 2, 3, 4, 5}", params));
    }

    // ========================================
    // Assignment Expression Tests
    // ========================================

    @Test
    public void test_evaluate_assignmentExpression() {
        final Map<String, Object> params = new HashMap<>();
        TestPerson person = new TestPerson("Alice", 25);
        params.put("person", person);

        // Modify property via OGNL
        ognlEngine.evaluate("person.name = 'Bob'", params);
        assertEquals("Bob", person.getName());

        ognlEngine.evaluate("person.age = 30", params);
        assertEquals(30, person.getAge());
    }

    // ========================================
    // List and Map Literal Tests
    // ========================================

    @Test
    public void test_evaluate_listLiteral() {
        final Map<String, Object> params = new HashMap<>();

        // OGNL list literal
        Object result = ognlEngine.evaluate("{1, 2, 3}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> list = (List<?>) result;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));

        // String list literal (OGNL treats single-quoted chars as Character)
        Object strList = ognlEngine.evaluate("{'a', 'b', 'c'}", params);
        assertNotNull(strList);
        assertTrue(strList instanceof List);
        List<?> strings = (List<?>) strList;
        assertEquals(3, strings.size());
        assertEquals('a', strings.get(0));
    }

    @Test
    public void test_evaluate_mapLiteral() {
        final Map<String, Object> params = new HashMap<>();

        // OGNL map literal
        Object result = ognlEngine.evaluate("#{'key1': 'val1', 'key2': 'val2'}", params);
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(2, map.size());
        assertEquals("val1", map.get("key1"));
        assertEquals("val2", map.get("key2"));
    }

    // ========================================
    // Null Handling Tests
    // ========================================

    @Test
    public void test_evaluate_nullValueHandling() {
        final Map<String, Object> params = new HashMap<>();
        params.put("nullVal", null);
        params.put("nonNull", "hello");

        // Null check
        assertEquals(true, ognlEngine.evaluate("nullVal == null", params));
        assertEquals(false, ognlEngine.evaluate("nonNull == null", params));
        assertEquals(true, ognlEngine.evaluate("nonNull != null", params));

        // Ternary with null check
        assertEquals("default", ognlEngine.evaluate("nullVal == null ? 'default' : nullVal", params));
        assertEquals("hello", ognlEngine.evaluate("nonNull == null ? 'default' : nonNull", params));
    }

    @Test
    public void test_evaluate_nullPropertyAccess() {
        final Map<String, Object> params = new HashMap<>();
        TestPerson person = new TestPerson("Alice", 25);
        // address is null
        params.put("person", person);

        // Accessing property of null should return null (error handled)
        assertNull(ognlEngine.evaluate("person.address.city", params));
    }

    // ========================================
    // Large/Edge Case Expression Tests
    // ========================================

    @Test
    public void test_evaluate_deeplyNestedExpressions() {
        final Map<String, Object> params = new HashMap<>();
        params.put("x", 10);

        // Deeply nested parentheses
        assertEquals(10, ognlEngine.evaluate("((((x))))", params));

        // Deeply nested ternary
        assertEquals("ten", ognlEngine.evaluate("x == 1 ? 'one' : (x == 5 ? 'five' : (x == 10 ? 'ten' : 'other'))", params));
    }

    @Test
    public void test_evaluate_largeCollectionProjection() {
        final Map<String, Object> params = new HashMap<>();
        List<Integer> numbers = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            numbers.add(i);
        }
        params.put("numbers", numbers);

        // Projection on a larger collection
        Object result = ognlEngine.evaluate("numbers.{? #this >= 90}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> filtered = (List<?>) result;
        assertEquals(10, filtered.size());
        assertEquals(90, filtered.get(0));
        assertEquals(99, filtered.get(9));
    }

    @Test
    public void test_evaluate_combinedProjectionAndSelection() {
        final Map<String, Object> params = new HashMap<>();
        List<TestPerson> people = Arrays.asList(new TestPerson("Alice", 20), new TestPerson("Bob", 30), new TestPerson("Charlie", 40),
                new TestPerson("Diana", 25));
        params.put("people", people);

        // Select people over 25, then project their names
        Object result = ognlEngine.evaluate("people.{? #this.age > 25}.{name}", params);
        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> names = (List<?>) result;
        assertEquals(2, names.size());
        assertEquals("Bob", names.get(0));
        assertEquals("Charlie", names.get(1));
    }

    // ========================================
    // Bitwise Operations Tests
    // ========================================

    @Test
    public void test_evaluate_bitwiseOperations() {
        final Map<String, Object> params = new HashMap<>();
        params.put("a", 0b1010); // 10
        params.put("b", 0b1100); // 12

        // Bitwise AND
        assertEquals(0b1000, ognlEngine.evaluate("a & b", params)); // 8

        // Bitwise OR
        assertEquals(0b1110, ognlEngine.evaluate("a | b", params)); // 14

        // Bitwise XOR
        assertEquals(0b0110, ognlEngine.evaluate("a ^ b", params)); // 6

        // Bit shift
        assertEquals(20, ognlEngine.evaluate("a << 1", params));
        assertEquals(5, ognlEngine.evaluate("a >> 1", params));
    }

    // ========================================
    // getName() Test
    // ========================================

    @Test
    public void test_getName() {
        assertEquals("ognl", ognlEngine.getName());
    }

    // ========================================
    // Helper Classes for Testing
    // ========================================

    public static class TestPerson {
        private String name;
        private int age;
        private TestAddress address;

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public TestAddress getAddress() {
            return address;
        }

        public void setAddress(TestAddress address) {
            this.address = address;
        }

        public String getInfo() {
            return name + " (" + age + ")";
        }
    }

    public static class TestAddress {
        private String city;
        private String country;

        public TestAddress(String city, String country) {
            this.city = city;
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }
    }
}
