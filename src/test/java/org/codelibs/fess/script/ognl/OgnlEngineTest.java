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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.exception.JobProcessingException;
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

    // ========================================
    // Basic Tests
    // ========================================

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

    public void test_evaluate_nullParamMap() {
        // Should not throw exception with null paramMap
        try {
            ognlEngine.evaluate("123", null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected behavior - the constructor new HashMap<>(paramMap) will throw NPE
        }
    }

    public void test_evaluate_emptyParamMap() {
        final Map<String, Object> params = new HashMap<>();

        assertEquals(100, ognlEngine.evaluate("100", params));
        assertEquals("test", ognlEngine.evaluate("'test'", params));
    }

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

    public void test_evaluate_invalidExpression() {
        final Map<String, Object> params = new HashMap<>();

        // Invalid OGNL syntax should return null and log warning
        assertNull(ognlEngine.evaluate("this is not valid ognl @#$%", params));
        assertNull(ognlEngine.evaluate("a +", params));
        assertNull(ognlEngine.evaluate("(unclosed parenthesis", params));
    }

    public void test_evaluate_undefinedVariable() {
        final Map<String, Object> params = new HashMap<>();

        // Accessing undefined variable should return null and log warning
        assertNull(ognlEngine.evaluate("undefinedVariable", params));
        assertNull(ognlEngine.evaluate("foo.bar.baz", params));
    }

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

    public void test_evaluate_divisionByZero() {
        final Map<String, Object> params = new HashMap<>();

        // Division by zero should return null and log warning
        assertNull(ognlEngine.evaluate("10 / 0", params));
    }

    // ========================================
    // Special Characters and Unicode Tests
    // ========================================

    public void test_evaluate_specialCharacters() {
        final Map<String, Object> params = new HashMap<>();
        params.put("special", "Hello\nWorld\t!");

        assertEquals("Hello\nWorld\t!", ognlEngine.evaluate("special", params));
    }

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
    // getName() Test
    // ========================================

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
