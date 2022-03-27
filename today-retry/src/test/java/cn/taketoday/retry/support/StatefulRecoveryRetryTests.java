/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2022 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.retry.support;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.taketoday.classify.BinaryExceptionClassifier;
import cn.taketoday.dao.DataAccessException;
import cn.taketoday.retry.ExhaustedRetryException;
import cn.taketoday.retry.RecoveryCallback;
import cn.taketoday.retry.RetryCallback;
import cn.taketoday.retry.RetryContext;
import cn.taketoday.retry.RetryException;
import cn.taketoday.retry.RetryPolicy;
import cn.taketoday.retry.RetryState;
import cn.taketoday.retry.policy.MapRetryContextCache;
import cn.taketoday.retry.policy.NeverRetryPolicy;
import cn.taketoday.retry.policy.SimpleRetryPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StatefulRecoveryRetryTests {

  private RetryTemplate retryTemplate = new RetryTemplate();

  private int count = 0;

  private List<String> list = new ArrayList<String>();

  @Test
  public void testOpenSunnyDay() throws Exception {
    RetryContext context = this.retryTemplate.open(new NeverRetryPolicy(), new DefaultRetryState("foo"));
    assertNotNull(context);
    // we haven't called the processor yet...
    assertEquals(0, this.count);
  }

  @Test
  public void testRegisterThrowable() {
    NeverRetryPolicy retryPolicy = new NeverRetryPolicy();
    RetryState state = new DefaultRetryState("foo");
    RetryContext context = this.retryTemplate.open(retryPolicy, state);
    assertNotNull(context);
    this.retryTemplate.registerThrowable(retryPolicy, state, context, new Exception());
    assertFalse(retryPolicy.canRetry(context));
  }

  @Test
  public void testClose() throws Exception {
    NeverRetryPolicy retryPolicy = new NeverRetryPolicy();
    RetryState state = new DefaultRetryState("foo");
    RetryContext context = this.retryTemplate.open(retryPolicy, state);
    assertNotNull(context);
    this.retryTemplate.registerThrowable(retryPolicy, state, context, new Exception());
    assertFalse(retryPolicy.canRetry(context));
    this.retryTemplate.close(retryPolicy, context, state, true);
    // still can't retry, even if policy is closed
    // (not that this would happen in practice)...
    assertFalse(retryPolicy.canRetry(context));
  }

  @Test
  public void testRecover() throws Throwable {
    this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
    final String input = "foo";
    RetryState state = new DefaultRetryState(input);
    RetryCallback<String, Exception> callback = new RetryCallback<String, Exception>() {
      @Override
      public String doWithRetry(RetryContext context) throws Exception {
        throw new RuntimeException("Barf!");
      }
    };
    RecoveryCallback<String> recoveryCallback = new RecoveryCallback<String>() {
      @Override
      public String recover(RetryContext context) {
        StatefulRecoveryRetryTests.this.count++;
        StatefulRecoveryRetryTests.this.list.add(input);
        return input;
      }
    };
    Object result = null;
    try {
      result = this.retryTemplate.execute(callback, recoveryCallback, state);
      fail("Expected exception on first try");
    }
    catch (Exception e) {
      // expected...
    }
    // On the second retry, the recovery path is taken...
    result = this.retryTemplate.execute(callback, recoveryCallback, state);
    assertEquals(input, result); // default result is the item
    assertEquals(1, this.count);
    assertEquals(input, this.list.get(0));
  }

  @Test
  public void testSwitchToStatelessForNoRollback() throws Throwable {
    this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
    // Roll back for these:
    BinaryExceptionClassifier classifier = new BinaryExceptionClassifier(
            Collections.<Class<? extends Throwable>>singleton(DataAccessException.class));
    // ...but not these:
    assertFalse(classifier.classify(new RuntimeException()));
    final String input = "foo";
    RetryState state = new DefaultRetryState(input, classifier);
    RetryCallback<String, Exception> callback = new RetryCallback<String, Exception>() {
      @Override
      public String doWithRetry(RetryContext context) throws Exception {
        throw new RuntimeException("Barf!");
      }
    };
    RecoveryCallback<String> recoveryCallback = new RecoveryCallback<String>() {
      @Override
      public String recover(RetryContext context) {
        StatefulRecoveryRetryTests.this.count++;
        StatefulRecoveryRetryTests.this.list.add(input);
        return input;
      }
    };
    Object result = null;
    // On the second retry, the recovery path is taken...
    result = this.retryTemplate.execute(callback, recoveryCallback, state);
    assertEquals(input, result); // default result is the item
    assertEquals(1, this.count);
    assertEquals(input, this.list.get(0));
  }

  @Test
  public void testExhaustedClearsHistoryAfterLastAttempt() throws Throwable {
    RetryPolicy retryPolicy = new SimpleRetryPolicy(1);
    this.retryTemplate.setRetryPolicy(retryPolicy);

    final String input = "foo";
    RetryState state = new DefaultRetryState(input);
    RetryCallback<String, Exception> callback = new RetryCallback<String, Exception>() {
      @Override
      public String doWithRetry(RetryContext context) throws Exception {
        throw new RuntimeException("Barf!");
      }
    };

    try {
      this.retryTemplate.execute(callback, state);
      fail("Expected ExhaustedRetryException");
    }
    catch (RuntimeException e) {
      assertEquals("Barf!", e.getMessage());
    }

    try {
      this.retryTemplate.execute(callback, state);
      fail("Expected ExhaustedRetryException");
    }
    catch (ExhaustedRetryException e) {
      // expected
    }

    RetryContext context = this.retryTemplate.open(retryPolicy, state);
    // True after exhausted - the history is reset...
    assertTrue(retryPolicy.canRetry(context));
  }

  @Test
  public void testKeyGeneratorNotConsistentAfterFailure() throws Throwable {

    RetryPolicy retryPolicy = new SimpleRetryPolicy(3);
    this.retryTemplate.setRetryPolicy(retryPolicy);
    final StringHolder item = new StringHolder("bar");
    RetryState state = new DefaultRetryState(item);

    RetryCallback<StringHolder, Exception> callback = new RetryCallback<StringHolder, Exception>() {
      @Override
      public StringHolder doWithRetry(RetryContext context) throws Exception {
        // This simulates what happens if someone uses a primary key
        // for hashCode and equals and then relies on default key
        // generator
        item.string = item.string + (StatefulRecoveryRetryTests.this.count++);
        throw new RuntimeException("Barf!");
      }
    };

    try {
      this.retryTemplate.execute(callback, state);
      fail("Expected RuntimeException");
    }
    catch (RuntimeException ex) {
      String message = ex.getMessage();
      assertEquals("Barf!", message);
    }
    // Only fails second attempt because the algorithm to detect
    // inconsistent has codes relies on the cache having been used for this
    // item already...
    try {
      this.retryTemplate.execute(callback, state);
      fail("Expected RetryException");
    }
    catch (RetryException ex) {
      String message = ex.getNestedMessage();
      assertTrue("Message doesn't contain 'inconsistent': " + message, message.contains("inconsistent"));
    }

    RetryContext context = this.retryTemplate.open(retryPolicy, state);
    // True after exhausted - the history is reset...
    assertEquals(0, context.getRetryCount());

  }

  @Test
  public void testCacheCapacity() throws Throwable {

    this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
    this.retryTemplate.setRetryContextCache(new MapRetryContextCache(1));

    RetryCallback<Object, Exception> callback = context -> {
      StatefulRecoveryRetryTests.this.count++;
      throw new RuntimeException("Barf!");
    };

    try {
      this.retryTemplate.execute(callback, new DefaultRetryState("foo"));
      fail("Expected RuntimeException");
    }
    catch (RuntimeException e) {
      assertEquals("Barf!", e.getMessage());
    }

    try {
      this.retryTemplate.execute(callback, new DefaultRetryState("bar"));
      fail("Expected RetryException");
    }
    catch (RetryException e) {
      String message = e.getNestedMessage();
      assertTrue("Message does not contain 'capacity': " + message, message.contains("capacity"));
    }
  }

  @Test
  public void testCacheCapacityNotReachedIfRecovered() throws Throwable {

    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(1);
    this.retryTemplate.setRetryPolicy(retryPolicy);
    this.retryTemplate.setRetryContextCache(new MapRetryContextCache(2));
    final StringHolder item = new StringHolder("foo");
    RetryState state = new DefaultRetryState(item);

    RetryCallback<Object, Exception> callback = new RetryCallback<Object, Exception>() {
      @Override
      public Object doWithRetry(RetryContext context) throws Exception {
        StatefulRecoveryRetryTests.this.count++;
        throw new RuntimeException("Barf!");
      }
    };
    RecoveryCallback<Object> recoveryCallback = new RecoveryCallback<Object>() {
      @Override
      public Object recover(RetryContext context) throws Exception {
        return null;
      }
    };

    try {
      this.retryTemplate.execute(callback, recoveryCallback, state);
      fail("Expected RuntimeException");
    }
    catch (RuntimeException e) {
      assertEquals("Barf!", e.getMessage());
    }
    this.retryTemplate.execute(callback, recoveryCallback, state);

    RetryContext context = this.retryTemplate.open(retryPolicy, state);
    // True after exhausted - the history is reset...
    assertEquals(0, context.getRetryCount());

  }

  private static class StringHolder {

    private String string;

    public StringHolder(String string) {
      this.string = string;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || !(obj instanceof StringHolder)) {
        return false;
      }
      return this.string.equals(((StringHolder) obj).string);
    }

    @Override
    public int hashCode() {
      return this.string.hashCode();
    }

    @Override
    public String toString() {
      return "String: " + this.string + " (hash = " + hashCode() + ")";
    }

  }

}
