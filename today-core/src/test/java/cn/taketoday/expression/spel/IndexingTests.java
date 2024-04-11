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

package cn.taketoday.expression.spel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.taketoday.expression.EvaluationContext;
import cn.taketoday.expression.Expression;
import cn.taketoday.expression.IndexAccessor;
import cn.taketoday.expression.PropertyAccessor;
import cn.taketoday.expression.TypedValue;
import cn.taketoday.expression.spel.standard.SpelExpressionParser;
import cn.taketoday.expression.spel.support.StandardEvaluationContext;
import cn.taketoday.expression.spel.testresources.Person;
import cn.taketoday.lang.Nullable;

import static cn.taketoday.expression.spel.SpelMessage.EXCEPTION_DURING_INDEX_READ;
import static cn.taketoday.expression.spel.SpelMessage.EXCEPTION_DURING_INDEX_WRITE;
import static cn.taketoday.expression.spel.SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE;
import static cn.taketoday.expression.spel.SpelMessage.UNABLE_TO_GROW_COLLECTION_UNKNOWN_ELEMENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@SuppressWarnings("rawtypes")
class IndexingTests {

  @Test
  @SuppressWarnings("unchecked")
  void indexIntoArrays() {
    SpelExpressionParser parser = new SpelExpressionParser();

    // One-dimensional
    this.property = new int[] { 1, 2, 3, 4 };
    Expression expression = parser.parseExpression("property[2]");
    assertThat(expression.getValue(this)).isEqualTo(3);

    // Multi-dimensional
    this.property = new int[][] { { 1, 2 }, { 3, 4 } };
    expression = parser.parseExpression("property[0]");
    assertThat(expression.getValue(this)).isEqualTo(new int[] { 1, 2 });
    expression = parser.parseExpression("property[1][1]");
    assertThat(expression.getValue(this)).isEqualTo(4);
  }

  @Test
  @SuppressWarnings("unchecked")
  void indexIntoGenericPropertyContainingMap() {
    Map<String, String> property = new HashMap<>();
    property.put("foo", "bar");
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.HashMap<?, ?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);
    assertThat(expression.getValue(this, Map.class)).isEqualTo(property);
    expression = parser.parseExpression("property['foo']");
    assertThat(expression.getValue(this)).isEqualTo("bar");
  }

  @Test
  @SuppressWarnings("unchecked")
  void indexIntoGenericPropertyContainingMapObject() {
    Map<String, Map<String, String>> property = new HashMap<>();
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    property.put("property", map);
    SpelExpressionParser parser = new SpelExpressionParser();
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.addPropertyAccessor(new MapAccessor());
    context.setRootObject(property);
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(context)).asString()
            .isEqualTo("java.util.HashMap<?, ?>");
    assertThat(expression.getValue(context)).isEqualTo(map);
    assertThat(expression.getValue(context, Map.class)).isEqualTo(map);
    expression = parser.parseExpression("property['foo']");
    assertThat(expression.getValue(context)).isEqualTo("bar");
  }

  @Test
  void setGenericPropertyContainingMap() {
    Map<String, String> property = new HashMap<>();
    property.put("foo", "bar");
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.HashMap<?, ?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("property['foo']");
    assertThat(expression.getValue(this)).isEqualTo("bar");
    expression.setValue(this, "baz");
    assertThat(expression.getValue(this)).isEqualTo("baz");
  }

  @Test
  void setPropertyContainingMap() {
    Map<Integer, Integer> property = new HashMap<>();
    property.put(9, 3);
    this.parameterizedMap = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("parameterizedMap");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("java.util.HashMap<java.lang.Integer, java.lang.Integer>");
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("parameterizedMap['9']");
    assertThat(expression.getValue(this)).isEqualTo(3);
    expression.setValue(this, "37");
    assertThat(expression.getValue(this)).isEqualTo(37);
  }

  @Test
  void setPropertyContainingMapAutoGrow() {
    SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, false));
    Expression expression = parser.parseExpression("parameterizedMap");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("java.util.Map<java.lang.Integer, java.lang.Integer>");
    assertThat(expression.getValue(this)).isNull();
    expression = parser.parseExpression("parameterizedMap['9']");
    assertThat(expression.getValue(this)).isNull();
    expression.setValue(this, "37");
    assertThat(expression.getValue(this)).isEqualTo(37);
  }

  @Test
  void indexIntoGenericPropertyContainingList() {
    List<String> property = new ArrayList<>();
    property.add("bar");
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.ArrayList<?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("property[0]");
    assertThat(expression.getValue(this)).isEqualTo("bar");
  }

  @Test
  void setGenericPropertyContainingList() {
    List<Integer> property = new ArrayList<>();
    property.add(3);
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.ArrayList<?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("property[0]");
    assertThat(expression.getValue(this)).isEqualTo(3);
    expression.setValue(this, "4");
    assertThat(expression.getValue(this)).isEqualTo("4");
  }

  @Test
  void setGenericPropertyContainingListAutogrow() {
    List<Integer> property = new ArrayList<>();
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.ArrayList<?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);

    Expression indexExpression = parser.parseExpression("property[0]");
    assertThatExceptionOfType(SpelEvaluationException.class)
            .isThrownBy(() -> indexExpression.getValue(this))
            .satisfies(ex -> assertThat(ex.getMessageCode()).isEqualTo(UNABLE_TO_GROW_COLLECTION_UNKNOWN_ELEMENT_TYPE));
  }

  @Test
  void autoGrowListOfElementsWithoutDefaultConstructor() {
    this.decimals = new ArrayList<>();
    SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));
    parser.parseExpression("decimals[0]").setValue(this, "123.4");
    assertThat(decimals).containsExactly(BigDecimal.valueOf(123.4));
  }

  @Test
  void indexIntoPropertyContainingListContainingNullElement() {
    this.decimals = new ArrayList<>();
    this.decimals.add(null);
    this.decimals.add(BigDecimal.ONE);
    SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));
    parser.parseExpression("decimals[0]").setValue(this, "9876.5");
    assertThat(decimals).containsExactly(BigDecimal.valueOf(9876.5), BigDecimal.ONE);
  }

  @Test
  void indexIntoPropertyContainingList() {
    List<Integer> property = new ArrayList<>();
    property.add(3);
    this.parameterizedList = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("parameterizedList");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("java.util.ArrayList<java.lang.Integer>");
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("parameterizedList[0]");
    assertThat(expression.getValue(this)).isEqualTo(3);
  }

  @Test
  void indexIntoPropertyContainingListOfList() {
    List<List<Integer>> property = new ArrayList<>();
    property.add(Arrays.asList(3));
    this.parameterizedListOfList = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("parameterizedListOfList[0]");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("java.util.Arrays$ArrayList<java.lang.Integer>");
    assertThat(expression.getValue(this)).isEqualTo(property.get(0));
    expression = parser.parseExpression("parameterizedListOfList[0][0]");
    assertThat(expression.getValue(this)).isEqualTo(3);
  }

  @Test
  void setPropertyContainingList() {
    List<Integer> property = new ArrayList<>();
    property.add(3);
    this.parameterizedList = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("parameterizedList");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("java.util.ArrayList<java.lang.Integer>");
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("parameterizedList[0]");
    assertThat(expression.getValue(this)).isEqualTo(3);
    expression.setValue(this, "4");
    assertThat(expression.getValue(this)).isEqualTo(4);
  }

  @Test
  void indexIntoGenericPropertyContainingNullList() {
    SpelParserConfiguration configuration = new SpelParserConfiguration(true, true);
    SpelExpressionParser parser = new SpelExpressionParser(configuration);
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.lang.Object", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isNull();

    Expression indexExpression = parser.parseExpression("property[0]");
    assertThatExceptionOfType(SpelEvaluationException.class)
            .isThrownBy(() -> indexExpression.getValue(this))
            .satisfies(ex -> assertThat(ex.getMessageCode()).isEqualTo(INDEXING_NOT_SUPPORTED_FOR_TYPE));
  }

  @Test
  void indexIntoGenericPropertyContainingGrowingList() {
    List<String> property = new ArrayList<>();
    this.property = property;
    SpelParserConfiguration configuration = new SpelParserConfiguration(true, true);
    SpelExpressionParser parser = new SpelExpressionParser(configuration);
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.ArrayList<?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);

    Expression indexExpression = parser.parseExpression("property[0]");
    assertThatExceptionOfType(SpelEvaluationException.class)
            .isThrownBy(() -> indexExpression.getValue(this))
            .satisfies(ex -> assertThat(ex.getMessageCode()).isEqualTo(UNABLE_TO_GROW_COLLECTION_UNKNOWN_ELEMENT_TYPE));
  }

  @Test
  void indexIntoGenericPropertyContainingGrowingList2() {
    List<String> property2 = new ArrayList<>();
    this.property2 = property2;
    SpelParserConfiguration configuration = new SpelParserConfiguration(true, true);
    SpelExpressionParser parser = new SpelExpressionParser(configuration);
    Expression expression = parser.parseExpression("property2");
    assertThat(expression.getValueTypeDescriptor(this)).asString().isEqualTo("java.util.ArrayList<?>");
    assertThat(expression.getValue(this)).isEqualTo(property2);

    Expression indexExpression = parser.parseExpression("property2[0]");
    assertThatExceptionOfType(SpelEvaluationException.class)
            .isThrownBy(() -> indexExpression.getValue(this))
            .satisfies(ex -> assertThat(ex.getMessageCode()).isEqualTo(UNABLE_TO_GROW_COLLECTION_UNKNOWN_ELEMENT_TYPE));
  }

  @Test
  void indexIntoGenericPropertyContainingArray() {
    String[] property = { "bar" };
    this.property = property;
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("property");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.lang.String[]", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this)).isEqualTo(property);
    expression = parser.parseExpression("property[0]");
    assertThat(expression.getValue(this)).isEqualTo("bar");
  }

  @Test
  void emptyList() {
    listOfScalarNotGeneric = new ArrayList();
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("listOfScalarNotGeneric");
    assertThat(expression.getValueTypeDescriptor(this)).asString().isEqualTo("java.util.ArrayList<?>");
    assertThat(expression.getValue(this, String.class)).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolveCollectionElementType() {
    listNotGeneric = new ArrayList(2);
    listNotGeneric.add(5);
    listNotGeneric.add(6);
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("listNotGeneric");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.ArrayList<?>", FieldAnnotation.class.getCanonicalName());
    assertThat(expression.getValue(this, String.class)).isEqualTo("5,6");
  }

  @Test
  void resolveCollectionElementTypeNull() {
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("listNotGeneric");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.List<?>", FieldAnnotation.class.getCanonicalName());
  }

  @SuppressWarnings("unchecked")
  @Test
  void resolveMapKeyValueTypes() {
    mapNotGeneric = new HashMap();
    mapNotGeneric.put("baseAmount", 3.11);
    mapNotGeneric.put("bonusAmount", 7.17);
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("mapNotGeneric");
    assertThat(expression.getValueTypeDescriptor(this)).asString()
            .isEqualTo("@%s java.util.HashMap<?, ?>", FieldAnnotation.class.getCanonicalName());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listOfScalars() {
    listOfScalarNotGeneric = new ArrayList(1);
    listOfScalarNotGeneric.add("5");
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("listOfScalarNotGeneric[0]");
    assertThat(expression.getValue(this, Integer.class)).isEqualTo(5);
  }

  @Test
  @SuppressWarnings("unchecked")
  void listOfMaps() {
    listOfMapsNotGeneric = new ArrayList();
    Map map = new HashMap();
    map.put("fruit", "apple");
    listOfMapsNotGeneric.add(map);
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("listOfMapsNotGeneric[0]['fruit']");
    assertThat(expression.getValue(this, String.class)).isEqualTo("apple");
  }

  @Nested
  class NullSafeIndexTests {  // gh-29847

    private final RootContextWithIndexedProperties rootContext = new RootContextWithIndexedProperties();

    private final StandardEvaluationContext context = new StandardEvaluationContext(rootContext);

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private Expression expression;

    @Test
    void nullSafeIndexIntoArray() {
      expression = parser.parseExpression("array?.[0]");
      assertThat(expression.getValue(context)).isNull();
      rootContext.array = new int[] { 42 };
      assertThat(expression.getValue(context)).isEqualTo(42);
    }

    @Test
    void nullSafeIndexIntoList() {
      expression = parser.parseExpression("list?.[0]");
      assertThat(expression.getValue(context)).isNull();
      rootContext.list = List.of(42);
      assertThat(expression.getValue(context)).isEqualTo(42);
    }

    @Test
    void nullSafeIndexIntoSet() {
      expression = parser.parseExpression("set?.[0]");
      assertThat(expression.getValue(context)).isNull();
      rootContext.set = Set.of(42);
      assertThat(expression.getValue(context)).isEqualTo(42);
    }

    @Test
    void nullSafeIndexIntoString() {
      expression = parser.parseExpression("string?.[0]");
      assertThat(expression.getValue(context)).isNull();
      rootContext.string = "XYZ";
      assertThat(expression.getValue(context)).isEqualTo("X");
    }

    @Test
    void nullSafeIndexIntoMap() {
      expression = parser.parseExpression("map?.['enigma']");
      assertThat(expression.getValue(context)).isNull();
      rootContext.map = Map.of("enigma", 42);
      assertThat(expression.getValue(context)).isEqualTo(42);
    }

    @Test
    void nullSafeIndexIntoObject() {
      expression = parser.parseExpression("person?.['name']");
      assertThat(expression.getValue(context)).isNull();
      rootContext.person = new Person("Jane");
      assertThat(expression.getValue(context)).isEqualTo("Jane");
    }

    static class RootContextWithIndexedProperties {
      public int[] array;
      public List<Integer> list;
      public Set<Integer> set;
      public String string;
      public Map<String, Integer> map;
      public Person person;
    }

  }

  @Nested
  class IndexAccessorTests {  // gh-26478

    @Test
    void addingAndRemovingIndexAccessors() {
      ObjectMapper objectMapper = new ObjectMapper();
      IndexAccessor accessor1 = new JacksonArrayNodeIndexAccessor(objectMapper);
      IndexAccessor accessor2 = new JacksonArrayNodeIndexAccessor(objectMapper);

      StandardEvaluationContext context = new StandardEvaluationContext();
      List<IndexAccessor> indexAccessors = context.getIndexAccessors();
      assertThat(indexAccessors).isEmpty();

      context.addIndexAccessor(accessor1);
      assertThat(indexAccessors).containsExactly(accessor1);

      context.addIndexAccessor(accessor2);
      assertThat(indexAccessors).containsExactly(accessor1, accessor2);

      List<IndexAccessor> copy = new ArrayList<>(indexAccessors);
      assertThat(context.removeIndexAccessor(accessor1)).isTrue();
      assertThat(context.removeIndexAccessor(accessor1)).isFalse();
      assertThat(indexAccessors).containsExactly(accessor2);

      context.setIndexAccessors(copy);
      assertThat(context.getIndexAccessors()).containsExactly(accessor1, accessor2);
    }

    @Test
    void noSuitableIndexAccessorResultsInException() {
      StandardEvaluationContext context = new StandardEvaluationContext();
      assertThat(context.getIndexAccessors()).isEmpty();

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThatExceptionOfType(SpelEvaluationException.class)
              .isThrownBy(() -> expr.getValue(context, this))
              .withMessageEndingWith("Indexing into type '%s' is not supported", getClass().getName())
              .extracting(SpelEvaluationException::getMessageCode).isEqualTo(INDEXING_NOT_SUPPORTED_FOR_TYPE);
    }

    @Test
    void canReadThrowsException() throws Exception {
      StandardEvaluationContext context = new StandardEvaluationContext();
      RuntimeException exception = new RuntimeException("Boom!");

      IndexAccessor mock = mock();
      given(mock.getSpecificTargetClasses()).willReturn(null);
      given(mock.canRead(any(), eq(this), any())).willThrow(exception);
      context.addIndexAccessor(mock);

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThatExceptionOfType(SpelEvaluationException.class)
              .isThrownBy(() -> expr.getValue(context, this))
              .withMessageEndingWith("A problem occurred while attempting to read index '%d' in '%s'",
                      0, getClass().getName())
              .withCause(exception)
              .extracting(SpelEvaluationException::getMessageCode).isEqualTo(EXCEPTION_DURING_INDEX_READ);

      verify(mock, times(1)).getSpecificTargetClasses();
      verify(mock, times(1)).canRead(any(), any(), any());
      verifyNoMoreInteractions(mock);
    }

    @Test
    void readThrowsException() throws Exception {
      StandardEvaluationContext context = new StandardEvaluationContext();
      RuntimeException exception = new RuntimeException("Boom!");

      IndexAccessor mock = mock();
      given(mock.getSpecificTargetClasses()).willReturn(null);
      given(mock.canRead(any(), eq(this), any())).willReturn(true);
      given(mock.read(any(), eq(this), any())).willThrow(exception);
      context.addIndexAccessor(mock);

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThatExceptionOfType(SpelEvaluationException.class)
              .isThrownBy(() -> expr.getValue(context, this))
              .withMessageEndingWith("A problem occurred while attempting to read index '%d' in '%s'",
                      0, getClass().getName())
              .withCause(exception)
              .extracting(SpelEvaluationException::getMessageCode).isEqualTo(EXCEPTION_DURING_INDEX_READ);

      verify(mock, times(2)).getSpecificTargetClasses();
      verify(mock, times(2)).canRead(any(), any(), any());
      verify(mock, times(1)).read(any(), any(), any());
      verifyNoMoreInteractions(mock);
    }

    @Test
    void canWriteThrowsException() throws Exception {
      StandardEvaluationContext context = new StandardEvaluationContext();
      RuntimeException exception = new RuntimeException("Boom!");

      IndexAccessor mock = mock();
      given(mock.getSpecificTargetClasses()).willReturn(null);
      given(mock.canWrite(eq(context), eq(this), eq(0))).willThrow(exception);
      context.addIndexAccessor(mock);

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThatExceptionOfType(SpelEvaluationException.class)
              .isThrownBy(() -> expr.setValue(context, this, 999))
              .withMessageEndingWith("A problem occurred while attempting to write index '%d' in '%s'",
                      0, getClass().getName())
              .withCause(exception)
              .extracting(SpelEvaluationException::getMessageCode).isEqualTo(EXCEPTION_DURING_INDEX_WRITE);

      verify(mock, times(1)).getSpecificTargetClasses();
      verify(mock, times(1)).canWrite(any(), any(), any());
      verifyNoMoreInteractions(mock);
    }

    @Test
    void writeThrowsException() throws Exception {
      StandardEvaluationContext context = new StandardEvaluationContext();
      RuntimeException exception = new RuntimeException("Boom!");

      IndexAccessor mock = mock();
      given(mock.getSpecificTargetClasses()).willReturn(null);
      given(mock.canWrite(eq(context), eq(this), eq(0))).willReturn(true);
      doThrow(exception).when(mock).write(any(), any(), any(), any());
      context.addIndexAccessor(mock);

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThatExceptionOfType(SpelEvaluationException.class)
              .isThrownBy(() -> expr.setValue(context, this, 999))
              .withMessageEndingWith("A problem occurred while attempting to write index '%d' in '%s'",
                      0, getClass().getName())
              .withCause(exception)
              .extracting(SpelEvaluationException::getMessageCode).isEqualTo(EXCEPTION_DURING_INDEX_WRITE);

      verify(mock, times(2)).getSpecificTargetClasses();
      verify(mock, times(2)).canWrite(any(), any(), any());
      verify(mock, times(1)).write(any(), any(), any(), any());
      verifyNoMoreInteractions(mock);
    }

    @Test
    void readAndWriteIndex() {
      StandardEvaluationContext context = new StandardEvaluationContext();

      ObjectMapper objectMapper = new ObjectMapper();
      context.addIndexAccessor(new JacksonArrayNodeIndexAccessor(objectMapper));

      TextNode node0 = new TextNode("node0");
      TextNode node1 = new TextNode("node1");
      ArrayNode arrayNode = objectMapper.createArrayNode();
      arrayNode.addAll(List.of(node0, node1));

      SpelExpressionParser parser = new SpelExpressionParser();
      Expression expr = parser.parseExpression("[0]");
      assertThat(expr.getValue(context, arrayNode)).isSameAs(node0);

      TextNode nodeX = new TextNode("nodeX");
      expr.setValue(context, arrayNode, nodeX);
      // We use isEqualTo() instead of isSameAs(), since ObjectMapper.convertValue()
      // converts the supplied TextNode to an equivalent JsonNode.
      assertThat(expr.getValue(context, arrayNode)).isEqualTo(nodeX);

      NullNode nullNode = NullNode.getInstance();
      expr.setValue(context, arrayNode, nullNode);
      assertThat(expr.getValue(context, arrayNode)).isSameAs(nullNode);

      expr = parser.parseExpression("[1]");
      assertThat(expr.getValue(context, arrayNode)).isSameAs(node1);

      expr = parser.parseExpression("[-1]");
      // Jackson's ArrayNode returns null for a non-existent index instead
      // of throwing an ArrayIndexOutOfBoundsException or similar.
      assertThat(expr.getValue(context, arrayNode)).isNull();
    }

    /**
     * {@link IndexAccessor} that knows how to read and write indexes in a
     * Jackson {@link ArrayNode}.
     */
    private static class JacksonArrayNodeIndexAccessor implements IndexAccessor {

      private final ObjectMapper objectMapper;

      JacksonArrayNodeIndexAccessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
      }

      @Override
      public Class<?>[] getSpecificTargetClasses() {
        return new Class[] { ArrayNode.class };
      }

      @Override
      public boolean canRead(EvaluationContext context, Object target, Object index) {
        return (target instanceof ArrayNode && index instanceof Integer);
      }

      @Override
      public TypedValue read(EvaluationContext context, Object target, Object index) {
        ArrayNode arrayNode = (ArrayNode) target;
        Integer intIndex = (Integer) index;
        return new TypedValue(arrayNode.get(intIndex));
      }

      @Override
      public boolean canWrite(EvaluationContext context, Object target, Object index) {
        return canRead(context, target, index);
      }

      @Override
      public void write(EvaluationContext context, Object target, Object index, @Nullable Object newValue) {
        ArrayNode arrayNode = (ArrayNode) target;
        Integer intIndex = (Integer) index;
        arrayNode.set(intIndex, this.objectMapper.convertValue(newValue, JsonNode.class));
      }
    }
  }

  @Target({ ElementType.FIELD })
  @Retention(RetentionPolicy.RUNTIME)
  @interface FieldAnnotation {
  }

  @FieldAnnotation
  public Object property;

  public List property2;

  public Map<Integer, Integer> parameterizedMap;

  public List<BigDecimal> decimals;

  public List<Integer> parameterizedList;

  public List<List<Integer>> parameterizedListOfList;

  @FieldAnnotation
  public List listNotGeneric;

  @FieldAnnotation
  public Map mapNotGeneric;

  public List listOfScalarNotGeneric;

  public List listOfMapsNotGeneric;

  private static class MapAccessor implements PropertyAccessor {

    @Override
    public boolean canRead(EvaluationContext context, Object target, String name) {
      return (((Map<?, ?>) target).containsKey(name));
    }

    @Override
    public TypedValue read(EvaluationContext context, Object target, String name) {
      return new TypedValue(((Map<?, ?>) target).get(name));
    }

    @Override
    public boolean canWrite(EvaluationContext context, Object target, String name) {
      return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(EvaluationContext context, Object target, String name, Object newValue) {
      ((Map) target).put(name, newValue);
    }

    @Override
    public Class<?>[] getSpecificTargetClasses() {
      return new Class<?>[] { Map.class };
    }

  }

}
