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

package cn.taketoday.expression.spel;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import cn.taketoday.core.TypeDescriptor;
import cn.taketoday.expression.EvaluationContext;
import cn.taketoday.expression.EvaluationException;
import cn.taketoday.expression.Operation;
import cn.taketoday.expression.OperatorOverloader;
import cn.taketoday.expression.PropertyAccessor;
import cn.taketoday.expression.TypeComparator;
import cn.taketoday.expression.TypeConverter;
import cn.taketoday.expression.TypedValue;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.CollectionUtils;

/**
 * An ExpressionState is for maintaining per-expression-evaluation state, any changes to
 * it are not seen by other expressions but it gives a place to hold local variables and
 * for component expressions in a compound expression to communicate state. This is in
 * contrast to the EvaluationContext, which is shared amongst expression evaluations, and
 * any changes to it will be seen by other expressions or any code that chooses to ask
 * questions of the context.
 *
 * <p>It also acts as a place for to define common utility routines that the various AST
 * nodes might need.
 *
 * @author Andy Clement
 * @author Juergen Hoeller
 * @since 4.0
 */
public class ExpressionState {

  private final EvaluationContext relatedContext;

  private final TypedValue rootObject;

  private final SpelParserConfiguration configuration;

  @Nullable
  private Deque<TypedValue> contextObjects;

  @Nullable
  private Deque<VariableScope> variableScopes;

  // When entering a new scope there is a new base object which should be used
  // for '#this' references (or to act as a target for unqualified references).
  // This ArrayDeque captures those objects at each nested scope level.
  // For example:
  // #list1.?[#list2.contains(#this)]
  // On entering the selection we enter a new scope, and #this is now the
  // element from list1
  @Nullable
  private ArrayDeque<TypedValue> scopeRootObjects;

  public ExpressionState(EvaluationContext context) {
    this(context, context.getRootObject(), new SpelParserConfiguration(false, false));
  }

  public ExpressionState(EvaluationContext context, SpelParserConfiguration configuration) {
    this(context, context.getRootObject(), configuration);
  }

  public ExpressionState(EvaluationContext context, TypedValue rootObject) {
    this(context, rootObject, new SpelParserConfiguration(false, false));
  }

  public ExpressionState(EvaluationContext context, TypedValue rootObject, SpelParserConfiguration configuration) {
    Assert.notNull(context, "EvaluationContext must not be null");
    Assert.notNull(configuration, "SpelParserConfiguration must not be null");
    this.relatedContext = context;
    this.rootObject = rootObject;
    this.configuration = configuration;
  }

  /**
   * The active context object is what unqualified references to properties/etc are resolved against.
   */
  public TypedValue getActiveContextObject() {
    if (CollectionUtils.isEmpty(this.contextObjects)) {
      return this.rootObject;
    }
    return this.contextObjects.element();
  }

  public void pushActiveContextObject(TypedValue obj) {
    if (this.contextObjects == null) {
      this.contextObjects = new ArrayDeque<>();
    }
    this.contextObjects.push(obj);
  }

  public void popActiveContextObject() {
    if (this.contextObjects == null) {
      this.contextObjects = new ArrayDeque<>();
    }
    try {
      this.contextObjects.pop();
    }
    catch (NoSuchElementException ex) {
      throw new IllegalStateException("Cannot pop active context object: stack is empty");
    }
  }

  public TypedValue getRootContextObject() {
    return this.rootObject;
  }

  public TypedValue getScopeRootContextObject() {
    if (CollectionUtils.isEmpty(this.scopeRootObjects)) {
      return this.rootObject;
    }
    return this.scopeRootObjects.element();
  }

  public void setVariable(String name, @Nullable Object value) {
    this.relatedContext.setVariable(name, value);
  }

  public TypedValue lookupVariable(String name) {
    Object value = this.relatedContext.lookupVariable(name);
    return (value != null ? new TypedValue(value) : TypedValue.NULL);
  }

  public TypeComparator getTypeComparator() {
    return this.relatedContext.getTypeComparator();
  }

  public Class<?> findType(String type) throws EvaluationException {
    return this.relatedContext.getTypeLocator().findType(type);
  }

  public Object convertValue(Object value, TypeDescriptor targetTypeDescriptor) throws EvaluationException {
    Object result = this.relatedContext.getTypeConverter().convertValue(
            value, TypeDescriptor.fromObject(value), targetTypeDescriptor);
    if (result == null) {
      throw new IllegalStateException("Null conversion result for value [" + value + "]");
    }
    return result;
  }

  public TypeConverter getTypeConverter() {
    return this.relatedContext.getTypeConverter();
  }

  @Nullable
  public Object convertValue(TypedValue value, TypeDescriptor targetTypeDescriptor) throws EvaluationException {
    Object val = value.getValue();
    return this.relatedContext.getTypeConverter().convertValue(
            val, TypeDescriptor.fromObject(val), targetTypeDescriptor);
  }

  /*
   * A new scope is entered when a function is invoked.
   */
  public void enterScope(Map<String, Object> argMap) {
    initVariableScopes().push(new VariableScope(argMap));
    initScopeRootObjects().push(getActiveContextObject());
  }

  public void enterScope() {
    initVariableScopes().push(new VariableScope(Collections.emptyMap()));
    initScopeRootObjects().push(getActiveContextObject());
  }

  public void enterScope(String name, Object value) {
    initVariableScopes().push(new VariableScope(name, value));
    initScopeRootObjects().push(getActiveContextObject());
  }

  public void exitScope() {
    initVariableScopes().pop();
    initScopeRootObjects().pop();
  }

  public void setLocalVariable(String name, Object value) {
    initVariableScopes().element().setVariable(name, value);
  }

  @Nullable
  public Object lookupLocalVariable(String name) {
    for (VariableScope scope : initVariableScopes()) {
      if (scope.definesVariable(name)) {
        return scope.lookupVariable(name);
      }
    }
    return null;
  }

  private Deque<VariableScope> initVariableScopes() {
    if (this.variableScopes == null) {
      this.variableScopes = new ArrayDeque<>();
      // top-level empty variable scope
      this.variableScopes.add(new VariableScope());
    }
    return this.variableScopes;
  }

  private Deque<TypedValue> initScopeRootObjects() {
    if (this.scopeRootObjects == null) {
      this.scopeRootObjects = new ArrayDeque<>();
    }
    return this.scopeRootObjects;
  }

  public TypedValue operate(Operation op, @Nullable Object left, @Nullable Object right) throws EvaluationException {
    OperatorOverloader overloader = this.relatedContext.getOperatorOverloader();
    if (overloader.overridesOperation(op, left, right)) {
      Object returnValue = overloader.operate(op, left, right);
      return new TypedValue(returnValue);
    }
    else {
      String leftType = (left == null ? "null" : left.getClass().getName());
      String rightType = (right == null ? "null" : right.getClass().getName());
      throw new SpelEvaluationException(SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES, op, leftType, rightType);
    }
  }

  public List<PropertyAccessor> getPropertyAccessors() {
    return this.relatedContext.getPropertyAccessors();
  }

  public EvaluationContext getEvaluationContext() {
    return this.relatedContext;
  }

  public SpelParserConfiguration getConfiguration() {
    return this.configuration;
  }

  /**
   * A new scope is entered when a function is called and it is used to hold the
   * parameters to the function call. If the names of the parameters clash with
   * those in a higher level scope, those in the higher level scope will not be
   * accessible whilst the function is executing. When the function returns,
   * the scope is exited.
   */
  private static class VariableScope {

    private final Map<String, Object> vars = new HashMap<>();

    public VariableScope() {
    }

    public VariableScope(@Nullable Map<String, Object> arguments) {
      if (arguments != null) {
        this.vars.putAll(arguments);
      }
    }

    public VariableScope(String name, Object value) {
      this.vars.put(name, value);
    }

    public Object lookupVariable(String name) {
      return this.vars.get(name);
    }

    public void setVariable(String name, Object value) {
      this.vars.put(name, value);
    }

    public boolean definesVariable(String name) {
      return this.vars.containsKey(name);
    }
  }

}