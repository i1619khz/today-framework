/*
 * Copyright 2017 - 2024 the original author or authors.
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
 * along with this program. If not, see [https://www.gnu.org/licenses/]
 */

package cn.taketoday.expression.spel.ast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import cn.taketoday.bytecode.MethodVisitor;
import cn.taketoday.bytecode.Opcodes;
import cn.taketoday.bytecode.core.CodeFlow;
import cn.taketoday.expression.EvaluationException;
import cn.taketoday.expression.TypedValue;
import cn.taketoday.expression.common.ExpressionUtils;
import cn.taketoday.expression.spel.ExpressionState;
import cn.taketoday.expression.spel.SpelEvaluationException;
import cn.taketoday.expression.spel.SpelMessage;
import cn.taketoday.expression.spel.SpelNode;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ObjectUtils;

/**
 * The common supertype of all AST nodes in a parsed Spring Expression Language
 * format expression.
 *
 * @author Andy Clement
 * @author Juergen Hoeller
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
public abstract class SpelNodeImpl implements SpelNode, Opcodes {

  private static final SpelNodeImpl[] NO_CHILDREN = new SpelNodeImpl[0];

  private final int startPos;

  private final int endPos;

  protected SpelNodeImpl[] children = SpelNodeImpl.NO_CHILDREN;

  @Nullable
  private SpelNodeImpl parent;

  /**
   * Indicates the type descriptor for the result of this expression node.
   * This is set as soon as it is known. For a literal node it is known immediately.
   * For a property access or method invocation it is known after one evaluation of
   * that node.
   * <p>The descriptor is like the bytecode form but is slightly easier to work with.
   * It does not include the trailing semicolon (for non array reference types).
   * Some examples: Ljava/lang/String, I, [I
   */
  @Nullable
  protected volatile String exitTypeDescriptor;

  public SpelNodeImpl(int startPos, int endPos) {
    this.startPos = startPos;
    this.endPos = endPos;
  }

  public SpelNodeImpl(int startPos, int endPos, SpelNodeImpl... operands) {
    this.startPos = startPos;
    this.endPos = endPos;
    if (ObjectUtils.isNotEmpty(operands)) {
      this.children = operands;
      for (SpelNodeImpl operand : operands) {
        Assert.notNull(operand, "Operand is required");
        operand.parent = this;
      }
    }
  }

  /**
   * Return {@code true} if the next child is one of the specified classes.
   */
  protected boolean nextChildIs(Class<?>... classes) {
    if (this.parent != null) {
      SpelNodeImpl[] peers = this.parent.children;
      for (int i = 0, max = peers.length; i < max; i++) {
        if (this == peers[i]) {
          if (i + 1 >= max) {
            return false;
          }
          Class<?> peerClass = peers[i + 1].getClass();
          for (Class<?> desiredClass : classes) {
            if (peerClass == desiredClass) {
              return true;
            }
          }
          return false;
        }
      }
    }
    return false;
  }

  @Override
  @Nullable
  public final Object getValue(ExpressionState expressionState) throws EvaluationException {
    return getValueInternal(expressionState).getValue();
  }

  @Override
  public final TypedValue getTypedValue(ExpressionState expressionState) throws EvaluationException {
    return getValueInternal(expressionState);
  }

  // by default Ast nodes are not writable
  @Override
  public boolean isWritable(ExpressionState expressionState) throws EvaluationException {
    return false;
  }

  @Override
  public void setValue(ExpressionState expressionState, @Nullable Object newValue) throws EvaluationException {
    setValueInternal(expressionState, () -> new TypedValue(newValue));
  }

  /**
   * Evaluate the expression to a node and then set the new value created by the
   * specified {@link Supplier} on that node.
   * <p>For example, if the expression evaluates to a property reference, then the
   * property will be set to the new value.
   * <p>Favor this method over {@link #setValue(ExpressionState, Object)} when
   * the value should be lazily computed.
   * <p>By default, this method throws a {@link SpelEvaluationException},
   * effectively disabling this feature. Subclasses may override this method to
   * provide an actual implementation.
   *
   * @param state the current expression state (includes the context)
   * @param valueSupplier a supplier of the new value
   * @throws EvaluationException if any problem occurs evaluating the expression or
   * setting the new value
   */
  public TypedValue setValueInternal(ExpressionState state, Supplier<TypedValue> valueSupplier) throws EvaluationException {
    throw new SpelEvaluationException(getStartPosition(), SpelMessage.SETVALUE_NOT_SUPPORTED, getClass().getName());
  }

  @Override
  public SpelNode getChild(int index) {
    return this.children[index];
  }

  @Override
  public int getChildCount() {
    return this.children.length;
  }

  @Override
  @Nullable
  public Class<?> getObjectClass(@Nullable Object obj) {
    if (obj == null) {
      return null;
    }
    return (obj instanceof Class ? ((Class<?>) obj) : obj.getClass());
  }

  @Override
  public int getStartPosition() {
    return this.startPos;
  }

  @Override
  public int getEndPosition() {
    return this.endPos;
  }

  /**
   * Determine if this node is the target of a null-safe navigation operation.
   * <p>The default implementation returns {@code false}.
   *
   * @return {@code true} if this node is the target of a null-safe operation
   */
  public boolean isNullSafe() {
    return false;
  }

  /**
   * Check whether a node can be compiled to bytecode. The reasoning in each node may
   * be different but will typically involve checking whether the exit type descriptor
   * of the node is known and any relevant child nodes are compilable.
   *
   * @return {@code true} if this node can be compiled to bytecode
   */
  public boolean isCompilable() {
    return false;
  }

  /**
   * Generate the bytecode for this node into the supplied visitor. Context info about
   * the current expression being compiled is available in the codeflow object, e.g.
   * including information about the type of the object currently on the stack.
   *
   * @param mv the ASM MethodVisitor into which code should be generated
   * @param cf a context object with info about what is on the stack
   */
  public void generateCode(MethodVisitor mv, CodeFlow cf) {
    throw new IllegalStateException(getClass().getName() + " has no generateCode(..) method");
  }

  @Nullable
  public String getExitDescriptor() {
    return this.exitTypeDescriptor;
  }

  @Nullable
  protected final <T> T getValue(ExpressionState state, Class<T> desiredReturnType) throws EvaluationException {
    return ExpressionUtils.convertTypedValue(state.getEvaluationContext(), getValueInternal(state), desiredReturnType);
  }

  protected ValueRef getValueRef(ExpressionState state) throws EvaluationException {
    throw new SpelEvaluationException(getStartPosition(), SpelMessage.NOT_ASSIGNABLE, toStringAST());
  }

  public abstract TypedValue getValueInternal(ExpressionState expressionState) throws EvaluationException;

  /**
   * Generate code that handles building the argument values for the specified method.
   * <p>This method will take into account whether the invoked method is a varargs method,
   * and if it is then the argument values will be appropriately packaged into an array.
   *
   * @param mv the method visitor where code should be generated
   * @param cf the current codeflow
   * @param member the method or constructor for which arguments are being set up
   * @param arguments the expression nodes for the expression supplied argument values
   */
  protected static void generateCodeForArguments(MethodVisitor mv, CodeFlow cf, Member member, SpelNodeImpl[] arguments) {
    String[] paramDescriptors;
    boolean isVarargs;
    if (member instanceof Constructor<?> ctor) {
      paramDescriptors = CodeFlow.toDescriptors(ctor.getParameterTypes());
      isVarargs = ctor.isVarArgs();
    }
    else { // Method
      Method method = (Method) member;
      paramDescriptors = CodeFlow.toDescriptors(method.getParameterTypes());
      isVarargs = method.isVarArgs();
    }
    if (isVarargs) {
      // The final parameter may or may not need packaging into an array, or nothing may
      // have been passed to satisfy the varargs and so something needs to be built.
      int p = 0; // Current supplied argument being processed
      int childCount = arguments.length;

      // Fulfill all the parameter requirements except the last one
      for (p = 0; p < paramDescriptors.length - 1; p++) {
        cf.generateCodeForArgument(mv, arguments[p], paramDescriptors[p]);
      }

      SpelNodeImpl lastChild = (childCount == 0 ? null : arguments[childCount - 1]);
      String arrayType = paramDescriptors[paramDescriptors.length - 1];
      // Determine if the final passed argument is already suitably packaged in array
      // form to be passed to the method
      if (lastChild != null && arrayType.equals(lastChild.getExitDescriptor())) {
        cf.generateCodeForArgument(mv, lastChild, paramDescriptors[p]);
      }
      else {
        arrayType = arrayType.substring(1); // trim the leading '[', may leave other '['
        // build array big enough to hold remaining arguments
        CodeFlow.insertNewArrayCode(mv, childCount - p, arrayType);
        // Package up the remaining arguments into the array
        int arrayindex = 0;
        while (p < childCount) {
          SpelNodeImpl child = arguments[p];
          mv.visitInsn(DUP);
          CodeFlow.insertOptimalLoad(mv, arrayindex++);
          cf.generateCodeForArgument(mv, child, arrayType);
          CodeFlow.insertArrayStore(mv, arrayType);
          p++;
        }
      }
    }
    else {
      for (int i = 0; i < paramDescriptors.length; i++) {
        cf.generateCodeForArgument(mv, arguments[i], paramDescriptors[i]);
      }
    }
  }

}
