/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2021 All Rights Reserved.
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

package cn.taketoday.core.type.classreading;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.taketoday.core.DefaultMultiValueMap;
import cn.taketoday.core.MultiValueMap;
import cn.taketoday.core.annotation.AnnotationAttributes;
import cn.taketoday.core.annotation.AnnotationUtils;
import cn.taketoday.core.annotation.MergedAnnotations;
import cn.taketoday.core.bytecode.AnnotationVisitor;
import cn.taketoday.core.bytecode.MethodVisitor;
import cn.taketoday.core.bytecode.Opcodes;
import cn.taketoday.core.bytecode.Type;
import cn.taketoday.core.type.AnnotationMetadata;
import cn.taketoday.core.type.MethodMetadata;
import cn.taketoday.lang.Nullable;

/**
 * ASM class visitor which looks for the class name and implemented types as
 * well as for the annotations defined on the class, exposing them through
 * the {@link cn.taketoday.core.type.AnnotationMetadata} interface.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Costin Leau
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 4.0
 */
public class AnnotationMetadataReadingVisitor extends ClassMetadataReadingVisitor implements AnnotationMetadata {

  @Nullable
  protected final ClassLoader classLoader;

  protected final LinkedHashSet<String> annotationSet = new LinkedHashSet<>(4);

  protected final LinkedHashMap<String, Set<String>> metaAnnotationMap = new LinkedHashMap<>(4);

  /**
   * Declared as a {@link DefaultMultiValueMap} instead of a {@link MultiValueMap}
   * to ensure that the hierarchical ordering of the entries is preserved.
   *
   * @see AnnotationReadingVisitorUtils#getMergedAnnotationAttributes
   */
  protected final DefaultMultiValueMap<String, AnnotationAttributes> attributesMap = new DefaultMultiValueMap<>(3);

  protected final LinkedHashSet<MethodMetadata> methodMetadataSet = new LinkedHashSet<>(4);

  public AnnotationMetadataReadingVisitor(@Nullable ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public MergedAnnotations getAnnotations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    // Skip bridge methods - we're only interested in original annotation-defining user methods.
    // On JDK 8, we'd otherwise run into double detection of the same annotated method...
    if ((access & Opcodes.ACC_BRIDGE) != 0) {
      return super.visitMethod(access, name, desc, signature, exceptions);
    }
    return new MethodMetadataReadingVisitor(name, access, getClassName(),
            Type.forReturnType(desc).getClassName(), this.classLoader, this.methodMetadataSet);
  }

  @Override
  @Nullable
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    if (!visible) {
      return null;
    }
    String className = Type.fromDescriptor(desc).getClassName();
    if (AnnotationUtils.isInJavaLangAnnotationPackage(className)) {
      return null;
    }
    this.annotationSet.add(className);
    return new AnnotationAttributesReadingVisitor(
            className, this.attributesMap, this.metaAnnotationMap, this.classLoader);
  }

  @Override
  public Set<String> getAnnotationTypes() {
    return this.annotationSet;
  }

  @Override
  public Set<String> getMetaAnnotationTypes(String annotationName) {
    Set<String> metaAnnotationTypes = this.metaAnnotationMap.get(annotationName);
    return (metaAnnotationTypes != null ? metaAnnotationTypes : Collections.emptySet());
  }

  @Override
  public boolean hasMetaAnnotation(String metaAnnotationType) {
    if (AnnotationUtils.isInJavaLangAnnotationPackage(metaAnnotationType)) {
      return false;
    }
    Collection<Set<String>> allMetaTypes = this.metaAnnotationMap.values();
    for (Set<String> metaTypes : allMetaTypes) {
      if (metaTypes.contains(metaAnnotationType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isAnnotated(String annotationName) {
    return !AnnotationUtils.isInJavaLangAnnotationPackage(annotationName)
            && this.attributesMap.containsKey(annotationName);
  }

  @Override
  public boolean hasAnnotation(String annotationName) {
    return getAnnotationTypes().contains(annotationName);
  }

  @Override
  @Nullable
  public AnnotationAttributes getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
    AnnotationAttributes raw = AnnotationReadingVisitorUtils.getMergedAnnotationAttributes(
            this.attributesMap, this.metaAnnotationMap, annotationName);
    if (raw == null) {
      return null;
    }
    return AnnotationReadingVisitorUtils.convertClassValues(
            "class '" + getClassName() + "'", this.classLoader, raw, classValuesAsString);
  }

  @Override
  @Nullable
  public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
    DefaultMultiValueMap<String, Object> allAttributes = new DefaultMultiValueMap<>();
    List<AnnotationAttributes> attributes = this.attributesMap.get(annotationName);
    if (attributes == null) {
      return null;
    }
    String annotatedElement = "class '" + getClassName() + "'";
    for (AnnotationAttributes raw : attributes) {
      for (Map.Entry<String, Object> entry : AnnotationReadingVisitorUtils.convertClassValues(
              annotatedElement, this.classLoader, raw, classValuesAsString).entrySet()) {
        allAttributes.add(entry.getKey(), entry.getValue());
      }
    }
    return allAttributes;
  }

  @Override
  public boolean hasAnnotatedMethods(String annotationName) {
    for (MethodMetadata methodMetadata : this.methodMetadataSet) {
      if (methodMetadata.isAnnotated(annotationName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
    LinkedHashSet<MethodMetadata> annotatedMethods = new LinkedHashSet<>(4);
    for (MethodMetadata methodMetadata : this.methodMetadataSet) {
      if (methodMetadata.isAnnotated(annotationName)) {
        annotatedMethods.add(methodMetadata);
      }
    }
    return annotatedMethods;
  }

}
