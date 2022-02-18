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

package cn.taketoday.format.support;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.taketoday.context.expression.EmbeddedValueResolverAware;
import cn.taketoday.core.DecoratingProxy;
import cn.taketoday.core.GenericTypeResolver;
import cn.taketoday.core.StringValueResolver;
import cn.taketoday.core.TypeDescriptor;
import cn.taketoday.core.conversion.ConditionalGenericConverter;
import cn.taketoday.core.conversion.ConversionService;
import cn.taketoday.core.conversion.GenericConverter;
import cn.taketoday.core.conversion.support.GenericConversionService;
import cn.taketoday.core.i18n.LocaleContextHolder;
import cn.taketoday.format.AnnotationFormatterFactory;
import cn.taketoday.format.Formatter;
import cn.taketoday.format.FormatterRegistry;
import cn.taketoday.format.Parser;
import cn.taketoday.format.Printer;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.StringUtils;

/**
 * A {@link cn.taketoday.core.conversion.ConversionService} implementation
 * designed to be configured as a {@link FormatterRegistry}.
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 4.0
 */
public class FormattingConversionService extends GenericConversionService
        implements FormatterRegistry, EmbeddedValueResolverAware {

  @Nullable
  private StringValueResolver embeddedValueResolver;

  private final Map<AnnotationConverterKey, GenericConverter> cachedPrinters = new ConcurrentHashMap<>(64);

  private final Map<AnnotationConverterKey, GenericConverter> cachedParsers = new ConcurrentHashMap<>(64);

  @Override
  public void setEmbeddedValueResolver(@Nullable StringValueResolver resolver) {
    this.embeddedValueResolver = resolver;
  }

  @Override
  public void addPrinter(Printer<?> printer) {
    Class<?> fieldType = getFieldType(printer, Printer.class);
    addConverter(new PrinterConverter(fieldType, printer, this));
  }

  @Override
  public void addParser(Parser<?> parser) {
    Class<?> fieldType = getFieldType(parser, Parser.class);
    addConverter(new ParserConverter(fieldType, parser, this));
  }

  @Override
  public void addFormatter(Formatter<?> formatter) {
    addFormatterForFieldType(getFieldType(formatter), formatter);
  }

  @Override
  public void addFormatterForFieldType(Class<?> fieldType, Formatter<?> formatter) {
    addConverter(new PrinterConverter(fieldType, formatter, this));
    addConverter(new ParserConverter(fieldType, formatter, this));
  }

  @Override
  public void addFormatterForFieldType(Class<?> fieldType, Printer<?> printer, Parser<?> parser) {
    addConverter(new PrinterConverter(fieldType, printer, this));
    addConverter(new ParserConverter(fieldType, parser, this));
  }

  @Override
  public void addFormatterForFieldAnnotation(AnnotationFormatterFactory<? extends Annotation> annotationFormatterFactory) {
    Class<? extends Annotation> annotationType = getAnnotationType(annotationFormatterFactory);
    if (this.embeddedValueResolver != null && annotationFormatterFactory instanceof EmbeddedValueResolverAware) {
      ((EmbeddedValueResolverAware) annotationFormatterFactory).setEmbeddedValueResolver(this.embeddedValueResolver);
    }
    Set<Class<?>> fieldTypes = annotationFormatterFactory.getFieldTypes();
    for (Class<?> fieldType : fieldTypes) {
      addConverter(new AnnotationPrinterConverter(annotationType, annotationFormatterFactory, fieldType));
      addConverter(new AnnotationParserConverter(annotationType, annotationFormatterFactory, fieldType));
    }
  }

  static Class<?> getFieldType(Formatter<?> formatter) {
    return getFieldType(formatter, Formatter.class);
  }

  private static <T> Class<?> getFieldType(T instance, Class<T> genericInterface) {
    Class<?> fieldType = GenericTypeResolver.resolveTypeArgument(instance.getClass(), genericInterface);
    if (fieldType == null && instance instanceof DecoratingProxy) {
      fieldType = GenericTypeResolver.resolveTypeArgument(
              ((DecoratingProxy) instance).getDecoratedClass(), genericInterface);
    }
    Assert.notNull(fieldType, () -> "Unable to extract the parameterized field type from " +
            ClassUtils.getShortName(genericInterface) + " [" + instance.getClass().getName() +
            "]; does the class parameterize the <T> generic type?");
    return fieldType;
  }

  static Class<? extends Annotation> getAnnotationType(AnnotationFormatterFactory<? extends Annotation> factory) {
    Class<? extends Annotation> annotationType = GenericTypeResolver.resolveTypeArgument(factory.getClass(), AnnotationFormatterFactory.class);
    if (annotationType == null) {
      throw new IllegalArgumentException("Unable to extract parameterized Annotation type argument from " +
              "AnnotationFormatterFactory [" + factory.getClass().getName() +
              "]; does the factory parameterize the <A extends Annotation> generic type?");
    }
    return annotationType;
  }

  private static class PrinterConverter implements GenericConverter {

    private final Class<?> fieldType;

    private final TypeDescriptor printerObjectType;

    @SuppressWarnings("rawtypes")
    private final Printer printer;

    private final ConversionService conversionService;

    public PrinterConverter(Class<?> fieldType, Printer<?> printer, ConversionService conversionService) {
      this.fieldType = fieldType;
      this.printerObjectType = TypeDescriptor.valueOf(resolvePrinterObjectType(printer));
      this.printer = printer;
      this.conversionService = conversionService;
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(this.fieldType, String.class));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (!sourceType.isAssignableTo(this.printerObjectType)) {
        source = this.conversionService.convert(source, sourceType, this.printerObjectType);
      }
      if (source == null) {
        return "";
      }
      return this.printer.print(source, LocaleContextHolder.getLocale());
    }

    @Nullable
    private Class<?> resolvePrinterObjectType(Printer<?> printer) {
      return GenericTypeResolver.resolveTypeArgument(printer.getClass(), Printer.class);
    }

    @Override
    public String toString() {
      return (this.fieldType.getName() + " -> " + String.class.getName() + " : " + this.printer);
    }
  }

  private record ParserConverter(Class<?> fieldType, Parser<?> parser, ConversionService conversionService)
          implements GenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, this.fieldType));
    }

    @Override
    @Nullable
    public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      String text = (String) source;
      if (!StringUtils.hasText(text)) {
        return null;
      }
      Object result;
      try {
        result = this.parser.parse(text, LocaleContextHolder.getLocale());
      }
      catch (IllegalArgumentException ex) {
        throw ex;
      }
      catch (Throwable ex) {
        throw new IllegalArgumentException("Parse attempt failed for value [" + text + "]", ex);
      }
      TypeDescriptor resultType = TypeDescriptor.valueOf(result.getClass());
      if (!resultType.isAssignableTo(targetType)) {
        result = this.conversionService.convert(result, resultType, targetType);
      }
      return result;
    }

    @Override
    public String toString() {
      return (String.class.getName() + " -> " + this.fieldType.getName() + ": " + this.parser);
    }
  }

  private class AnnotationPrinterConverter implements ConditionalGenericConverter {

    private final Class<? extends Annotation> annotationType;

    @SuppressWarnings("rawtypes")
    private final AnnotationFormatterFactory annotationFormatterFactory;

    private final Class<?> fieldType;

    public AnnotationPrinterConverter(Class<? extends Annotation> annotationType,
                                      AnnotationFormatterFactory<?> annotationFormatterFactory, Class<?> fieldType) {

      this.annotationType = annotationType;
      this.annotationFormatterFactory = annotationFormatterFactory;
      this.fieldType = fieldType;
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(this.fieldType, String.class));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return sourceType.hasAnnotation(this.annotationType);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      Annotation ann = sourceType.getAnnotation(this.annotationType);
      if (ann == null) {
        throw new IllegalStateException(
                "Expected [" + this.annotationType.getName() + "] to be present on " + sourceType);
      }
      AnnotationConverterKey converterKey = new AnnotationConverterKey(ann, sourceType.getObjectType());
      GenericConverter converter = cachedPrinters.get(converterKey);
      if (converter == null) {
        Printer<?> printer = this.annotationFormatterFactory.getPrinter(
                converterKey.getAnnotation(), converterKey.getFieldType());
        converter = new PrinterConverter(this.fieldType, printer, FormattingConversionService.this);
        cachedPrinters.put(converterKey, converter);
      }
      return converter.convert(source, sourceType, targetType);
    }

    @Override
    public String toString() {
      return ("@" + this.annotationType.getName() + " " + this.fieldType.getName() + " -> " +
              String.class.getName() + ": " + this.annotationFormatterFactory);
    }
  }

  private class AnnotationParserConverter implements ConditionalGenericConverter {

    private final Class<? extends Annotation> annotationType;

    @SuppressWarnings("rawtypes")
    private final AnnotationFormatterFactory annotationFormatterFactory;

    private final Class<?> fieldType;

    public AnnotationParserConverter(Class<? extends Annotation> annotationType,
                                     AnnotationFormatterFactory<?> annotationFormatterFactory, Class<?> fieldType) {

      this.annotationType = annotationType;
      this.annotationFormatterFactory = annotationFormatterFactory;
      this.fieldType = fieldType;
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
      return Collections.singleton(new ConvertiblePair(String.class, this.fieldType));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
      return targetType.hasAnnotation(this.annotationType);
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      Annotation ann = targetType.getAnnotation(this.annotationType);
      if (ann == null) {
        throw new IllegalStateException(
                "Expected [" + this.annotationType.getName() + "] to be present on " + targetType);
      }
      AnnotationConverterKey converterKey = new AnnotationConverterKey(ann, targetType.getObjectType());
      GenericConverter converter = cachedParsers.get(converterKey);
      if (converter == null) {
        Parser<?> parser = this.annotationFormatterFactory.getParser(
                converterKey.getAnnotation(), converterKey.getFieldType());
        converter = new ParserConverter(this.fieldType, parser, FormattingConversionService.this);
        cachedParsers.put(converterKey, converter);
      }
      return converter.convert(source, sourceType, targetType);
    }

    @Override
    public String toString() {
      return (String.class.getName() + " -> @" + this.annotationType.getName() + " " +
              this.fieldType.getName() + ": " + this.annotationFormatterFactory);
    }
  }

  private record AnnotationConverterKey(Annotation annotation, Class<?> fieldType) {

    public Annotation getAnnotation() {
      return this.annotation;
    }

    public Class<?> getFieldType() {
      return this.fieldType;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof AnnotationConverterKey otherKey)) {
        return false;
      }
      return (this.fieldType == otherKey.fieldType && this.annotation.equals(otherKey.annotation));
    }

    @Override
    public int hashCode() {
      return (this.fieldType.hashCode() * 29 + this.annotation.hashCode());
    }
  }

}
