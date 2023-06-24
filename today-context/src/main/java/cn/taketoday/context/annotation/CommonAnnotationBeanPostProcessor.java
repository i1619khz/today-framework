/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Harry Yang & 2017 - 2023 All Rights Reserved.
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

package cn.taketoday.context.annotation;

import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.taketoday.aop.TargetSource;
import cn.taketoday.aop.framework.ProxyFactory;
import cn.taketoday.beans.BeanUtils;
import cn.taketoday.beans.PropertyValues;
import cn.taketoday.beans.factory.BeanCreationException;
import cn.taketoday.beans.factory.BeanFactory;
import cn.taketoday.beans.factory.BeanFactoryAware;
import cn.taketoday.beans.factory.DependenciesBeanPostProcessor;
import cn.taketoday.beans.factory.NoSuchBeanDefinitionException;
import cn.taketoday.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import cn.taketoday.beans.factory.annotation.InjectionMetadata;
import cn.taketoday.beans.factory.config.AutowireCapableBeanFactory;
import cn.taketoday.beans.factory.config.BeanPostProcessor;
import cn.taketoday.beans.factory.config.ConfigurableBeanFactory;
import cn.taketoday.beans.factory.config.DependencyDescriptor;
import cn.taketoday.beans.factory.config.EmbeddedValueResolver;
import cn.taketoday.beans.factory.support.RootBeanDefinition;
import cn.taketoday.core.BridgeMethodResolver;
import cn.taketoday.core.MethodParameter;
import cn.taketoday.core.StringValueResolver;
import cn.taketoday.core.annotation.AnnotationUtils;
import cn.taketoday.jndi.support.SimpleJndiBeanFactory;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.ReflectionUtils;
import cn.taketoday.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;

/**
 * {@link BeanPostProcessor} implementation
 * that supports common Java annotations out of the box, in particular the common
 * annotations in the {@code jakarta.annotation} package. These common Java
 * annotations are supported in many Jakarta EE technologies (e.g. JSF and JAX-RS).
 *
 * <p>This post-processor includes support for the {@link jakarta.annotation.PostConstruct}
 * and {@link jakarta.annotation.PreDestroy} annotations - as init annotation
 * and destroy annotation, respectively - through inheriting from
 * {@link InitDestroyAnnotationBeanPostProcessor} with pre-configured annotation types.
 *
 * <p>The central element is the {@link jakarta.annotation.Resource} annotation
 * for annotation-driven injection of named beans, by default from the containing
 * Framework BeanFactory, with only {@code mappedName} references resolved in JNDI.
 * The {@link #setAlwaysUseJndiLookup "alwaysUseJndiLookup" flag} enforces JNDI lookups
 * equivalent to standard Jakarta EE resource injection for {@code name} references
 * and default names as well. The target beans can be simple POJOs, with no special
 * requirements other than the type having to match.
 *
 * <p>This post-processor also supports the EJB 3 {@link jakarta.ejb.EJB} annotation,
 * analogous to {@link jakarta.annotation.Resource}, with the capability to
 * specify both a local bean name and a global JNDI name for fallback retrieval.
 * The target beans can be plain POJOs as well as EJB 3 Session Beans in this case.
 *
 * <p>For default usage, resolving resource names as Framework bean names,
 * simply define the following in your application context:
 *
 * <pre class="code">
 * &lt;bean class="cn.taketoday.context.annotation.CommonAnnotationBeanPostProcessor"/&gt;</pre>
 *
 * For direct JNDI access, resolving resource names as JNDI resource references
 * within the Jakarta EE application's "java:comp/env/" namespace, use the following:
 *
 * <pre class="code">
 * &lt;bean class="cn.taketoday.context.annotation.CommonAnnotationBeanPostProcessor"&gt;
 *   &lt;property name="alwaysUseJndiLookup" value="true"/&gt;
 * &lt;/bean&gt;</pre>
 *
 * {@code mappedName} references will always be resolved in JNDI,
 * allowing for global JNDI names (including "java:" prefix) as well. The
 * "alwaysUseJndiLookup" flag just affects {@code name} references and
 * default names (inferred from the field name / property name).
 *
 * <p><b>NOTE:</b> A default CommonAnnotationBeanPostProcessor will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom CommonAnnotationBeanPostProcessor bean definition!
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection; thus
 * the latter configuration will override the former for properties wired through
 * both approaches.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see #setAlwaysUseJndiLookup
 * @see #setResourceFactory
 * @see cn.taketoday.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor
 * @see cn.taketoday.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
 * @since 4.0 2022/3/5 12:09
 */
public class CommonAnnotationBeanPostProcessor extends InitDestroyAnnotationBeanPostProcessor
        implements DependenciesBeanPostProcessor, BeanFactoryAware, Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  // Defensive reference to JNDI API for JDK 9+ (optional java.naming module)
  private static final boolean jndiPresent = ClassUtils.isPresent(
          "javax.naming.InitialContext", CommonAnnotationBeanPostProcessor.class.getClassLoader());

  private static final Set<Class<? extends Annotation>> resourceAnnotationTypes = new LinkedHashSet<>(4);

  @Nullable
  private static final Class<? extends Annotation> ejbClass = ClassUtils.load(
          "jakarta.ejb.EJB", CommonAnnotationBeanPostProcessor.class.getClassLoader());

  static {
    resourceAnnotationTypes.add(Resource.class);
    if (ejbClass != null) {
      resourceAnnotationTypes.add(ejbClass);
    }
  }

  private final HashSet<String> ignoredResourceTypes = new HashSet<>(1);

  private boolean fallbackToDefaultTypeMatch = true;

  private boolean alwaysUseJndiLookup = false;

  @Nullable
  private transient BeanFactory jndiFactory;

  @Nullable
  private transient BeanFactory resourceFactory;

  @Nullable
  private transient BeanFactory beanFactory;

  @Nullable
  private transient StringValueResolver embeddedValueResolver;

  private final transient ConcurrentHashMap<String, InjectionMetadata>
          injectionMetadataCache = new ConcurrentHashMap<>(256);

  /**
   * Create a new CommonAnnotationBeanPostProcessor,
   * with the init and destroy annotation types set to
   * {@link jakarta.annotation.PostConstruct} and {@link jakarta.annotation.PreDestroy},
   * respectively.
   */
  public CommonAnnotationBeanPostProcessor() {
    setOrder(LOWEST_PRECEDENCE - 3);
    setInitAnnotationType(PostConstruct.class);
    setDestroyAnnotationType(PreDestroy.class);

    // java.naming module present on JDK 9+?
    if (jndiPresent) {
      this.jndiFactory = new SimpleJndiBeanFactory();
    }
  }

  /**
   * Ignore the given resource type when resolving {@code @Resource} annotations.
   *
   * @param resourceType the resource type to ignore
   */
  public void ignoreResourceType(String resourceType) {
    Assert.notNull(resourceType, "Ignored resource type must not be null");
    this.ignoredResourceTypes.add(resourceType);
  }

  /**
   * Set whether to allow a fallback to a type match if no explicit name has been
   * specified. The default name (i.e. the field name or bean property name) will
   * still be checked first; if a bean of that name exists, it will be taken.
   * However, if no bean of that name exists, a by-type resolution of the
   * dependency will be attempted if this flag is "true".
   * <p>Default is "true". Switch this flag to "false" in order to enforce a
   * by-name lookup in all cases, throwing an exception in case of no name match.
   *
   * @see AutowireCapableBeanFactory#resolveDependency
   */
  public void setFallbackToDefaultTypeMatch(boolean fallbackToDefaultTypeMatch) {
    this.fallbackToDefaultTypeMatch = fallbackToDefaultTypeMatch;
  }

  /**
   * Set whether to always use JNDI lookups equivalent to standard Jakarta EE resource
   * injection, <b>even for {@code name} attributes and default names</b>.
   * <p>Default is "false": Resource names are used for Framework bean lookups in the
   * containing BeanFactory; only {@code mappedName} attributes point directly
   * into JNDI. Switch this flag to "true" for enforcing Jakarta EE style JNDI lookups
   * in any case, even for {@code name} attributes and default names.
   *
   * @see #setJndiFactory
   * @see #setResourceFactory
   */
  public void setAlwaysUseJndiLookup(boolean alwaysUseJndiLookup) {
    this.alwaysUseJndiLookup = alwaysUseJndiLookup;
  }

  /**
   * Specify the factory for objects to be injected into {@code @Resource} /
   * {@code @EJB} annotated fields and setter methods,
   * <b>for {@code mappedName} attributes that point directly into JNDI</b>.
   * This factory will also be used if "alwaysUseJndiLookup" is set to "true" in order
   * to enforce JNDI lookups even for {@code name} attributes and default names.
   * <p>The default is a {@link cn.taketoday.jndi.support.SimpleJndiBeanFactory}
   * for JNDI lookup behavior equivalent to standard Jakarta EE resource injection.
   *
   * @see #setResourceFactory
   * @see #setAlwaysUseJndiLookup
   */
  public void setJndiFactory(BeanFactory jndiFactory) {
    Assert.notNull(jndiFactory, "BeanFactory must not be null");
    this.jndiFactory = jndiFactory;
  }

  /**
   * Specify the factory for objects to be injected into {@code @Resource} /
   * {@code @EJB} annotated fields and setter methods,
   * <b>for {@code name} attributes and default names</b>.
   * <p>The default is the BeanFactory that this post-processor is defined in,
   * if any, looking up resource names as Framework bean names. Specify the resource
   * factory explicitly for programmatic usage of this post-processor.
   * <p>Specifying Framework's {@link cn.taketoday.jndi.support.SimpleJndiBeanFactory}
   * leads to JNDI lookup behavior equivalent to standard Jakarta EE resource injection,
   * even for {@code name} attributes and default names. This is the same behavior
   * that the "alwaysUseJndiLookup" flag enables.
   *
   * @see #setAlwaysUseJndiLookup
   */
  public void setResourceFactory(BeanFactory resourceFactory) {
    Assert.notNull(resourceFactory, "BeanFactory must not be null");
    this.resourceFactory = resourceFactory;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    super.setBeanFactory(beanFactory);
    this.beanFactory = beanFactory;
    if (this.resourceFactory == null) {
      this.resourceFactory = beanFactory;
    }
    if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
      this.embeddedValueResolver = new EmbeddedValueResolver(configurableBeanFactory);
    }
  }

  @Override
  public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Object bean, String beanName) {
    super.postProcessMergedBeanDefinition(beanDefinition, bean, beanName);
    InjectionMetadata metadata = findResourceMetadata(beanName, bean.getClass(), null);
    metadata.checkConfigMembers(beanDefinition);
  }

  @Override
  public void resetBeanDefinition(String beanName) {
    this.injectionMetadataCache.remove(beanName);
  }

  @Override
  public PropertyValues processDependencies(PropertyValues propertyValues, Object bean, String beanName) {
    InjectionMetadata metadata = findResourceMetadata(beanName, bean.getClass(), propertyValues);
    try {
      metadata.inject(bean, beanName, propertyValues);
      return propertyValues;
    }
    catch (Throwable ex) {
      throw new BeanCreationException(beanName, "Injection of resource dependencies failed", ex);
    }
  }

  private InjectionMetadata findResourceMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
    // Fall back to class name as cache key, for backwards compatibility with custom callers.
    String cacheKey = StringUtils.isNotEmpty(beanName) ? beanName : clazz.getName();
    // Quick check on the concurrent map first, with minimal locking.
    InjectionMetadata metadata = injectionMetadataCache.get(cacheKey);
    if (InjectionMetadata.needsRefresh(metadata, clazz)) {
      synchronized(injectionMetadataCache) {
        metadata = injectionMetadataCache.get(cacheKey);
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
          if (metadata != null) {
            metadata.clear(pvs);
          }
          metadata = buildResourceMetadata(clazz);
          this.injectionMetadataCache.put(cacheKey, metadata);
        }
      }
    }
    return metadata;
  }

  private InjectionMetadata buildResourceMetadata(Class<?> clazz) {
    if (!AnnotationUtils.isCandidateClass(clazz, resourceAnnotationTypes)) {
      return InjectionMetadata.EMPTY;
    }

    ArrayList<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
    Class<?> targetClass = clazz;

    do {
      final ArrayList<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();

      ReflectionUtils.doWithLocalFields(targetClass, field -> {
        if (ejbClass != null && field.isAnnotationPresent(ejbClass)) {
          if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("@EJB annotation is not supported on static fields");
          }
          currElements.add(new EjbRefElement(field, field, null));
        }
        else if (field.isAnnotationPresent(Resource.class)) {
          if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalStateException("@Resource annotation is not supported on static fields");
          }
          if (!this.ignoredResourceTypes.contains(field.getType().getName())) {
            currElements.add(new ResourceElement(field, field, null));
          }
        }
      });

      ReflectionUtils.doWithLocalMethods(targetClass, method -> {
        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
          return;
        }
        if (method.equals(ReflectionUtils.getMostSpecificMethod(method, clazz))) {
          if (ejbClass != null && bridgedMethod.isAnnotationPresent(ejbClass)) {
            if (Modifier.isStatic(method.getModifiers())) {
              throw new IllegalStateException("@EJB annotation is not supported on static methods");
            }
            if (method.getParameterCount() != 1) {
              throw new IllegalStateException("@EJB annotation requires a single-arg method: " + method);
            }
            PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
            currElements.add(new EjbRefElement(method, bridgedMethod, pd));
          }
          else if (bridgedMethod.isAnnotationPresent(Resource.class)) {
            if (Modifier.isStatic(method.getModifiers())) {
              throw new IllegalStateException("@Resource annotation is not supported on static methods");
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 1) {
              throw new IllegalStateException("@Resource annotation requires a single-arg method: " + method);
            }
            if (!this.ignoredResourceTypes.contains(paramTypes[0].getName())) {
              PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
              currElements.add(new ResourceElement(method, bridgedMethod, pd));
            }
          }
        }
      });

      elements.addAll(0, currElements);
      targetClass = targetClass.getSuperclass();
    }
    while (targetClass != null && targetClass != Object.class);

    return InjectionMetadata.forElements(elements, clazz);
  }

  /**
   * Obtain a lazily resolving resource proxy for the given name and type,
   * delegating to {@link #getResource} on demand once a method call comes in.
   *
   * @param element the descriptor for the annotated field/method
   * @param requestingBeanName the name of the requesting bean
   * @return the resource object (never {@code null})
   * @see #getResource
   * @see Lazy
   */
  protected Object buildLazyResourceProxy(final LookupElement element, final @Nullable String requestingBeanName) {
    TargetSource ts = new TargetSource() {
      @Override
      public Class<?> getTargetClass() {
        return element.lookupType;
      }

      @Override
      public boolean isStatic() {
        return false;
      }

      @Override
      public Object getTarget() {
        return getResource(element, requestingBeanName);
      }

      @Override
      public void releaseTarget(Object target) {
      }
    };

    ProxyFactory pf = new ProxyFactory();
    pf.setTargetSource(ts);
    if (element.lookupType.isInterface()) {
      pf.addInterface(element.lookupType);
    }
    ClassLoader classLoader = beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory
                              ? configurableBeanFactory.getBeanClassLoader() : null;
    return pf.getProxy(classLoader);
  }

  /**
   * Obtain the resource object for the given name and type.
   *
   * @param element the descriptor for the annotated field/method
   * @param requestingBeanName the name of the requesting bean
   * @return the resource object (never {@code null})
   * @throws NoSuchBeanDefinitionException if no corresponding target resource found
   */
  protected Object getResource(LookupElement element, @Nullable String requestingBeanName)
          throws NoSuchBeanDefinitionException {

    // JNDI lookup to perform?
    String jndiName = null;
    if (StringUtils.isNotEmpty(element.mappedName)) {
      jndiName = element.mappedName;
    }
    else if (this.alwaysUseJndiLookup) {
      jndiName = element.name;
    }
    if (jndiName != null) {
      if (this.jndiFactory == null) {
        throw new NoSuchBeanDefinitionException(element.lookupType,
                "No JNDI factory configured - specify the 'jndiFactory' property");
      }
      return this.jndiFactory.getBean(jndiName, element.lookupType);
    }

    // Regular resource autowiring
    if (this.resourceFactory == null) {
      throw new NoSuchBeanDefinitionException(element.lookupType,
              "No resource factory configured - specify the 'resourceFactory' property");
    }
    return autowireResource(this.resourceFactory, element, requestingBeanName);
  }

  /**
   * Obtain a resource object for the given name and type through autowiring
   * based on the given factory.
   *
   * @param factory the factory to autowire against
   * @param element the descriptor for the annotated field/method
   * @param requestingBeanName the name of the requesting bean
   * @return the resource object (never {@code null})
   * @throws NoSuchBeanDefinitionException if no corresponding target resource found
   */
  protected Object autowireResource(BeanFactory factory, LookupElement element, @Nullable String requestingBeanName)
          throws NoSuchBeanDefinitionException {

    Object resource;
    Set<String> autowiredBeanNames;
    String name = element.name;

    if (factory instanceof AutowireCapableBeanFactory autowireCapableBeanFactory) {
      DependencyDescriptor descriptor = element.getDependencyDescriptor();
      if (this.fallbackToDefaultTypeMatch && element.isDefaultName && !factory.containsBean(name)) {
        autowiredBeanNames = new LinkedHashSet<>();
        resource = autowireCapableBeanFactory.resolveDependency(descriptor, requestingBeanName, autowiredBeanNames, null);
        if (resource == null) {
          throw new NoSuchBeanDefinitionException(element.getLookupType(), "No resolvable resource object");
        }
      }
      else {
        resource = autowireCapableBeanFactory.resolveBeanByName(name, descriptor);
        autowiredBeanNames = Collections.singleton(name);
      }
    }
    else {
      resource = factory.getBean(name, element.lookupType);
      autowiredBeanNames = Collections.singleton(name);
    }

    if (factory instanceof ConfigurableBeanFactory configurableBeanFactory) {
      for (String autowiredBeanName : autowiredBeanNames) {
        if (requestingBeanName != null && configurableBeanFactory.containsBean(autowiredBeanName)) {
          configurableBeanFactory.registerDependentBean(autowiredBeanName, requestingBeanName);
        }
      }
    }

    return resource;
  }

  /**
   * Class representing generic injection information about an annotated field
   * or setter method, supporting @Resource and related annotations.
   */
  protected abstract static class LookupElement extends InjectionMetadata.InjectedElement {

    protected String name = "";

    protected boolean isDefaultName = false;

    protected Class<?> lookupType = Object.class;

    @Nullable
    protected String mappedName;

    public LookupElement(Member member, @Nullable PropertyDescriptor pd) {
      super(member, pd);
    }

    /**
     * Return the resource name for the lookup.
     */
    public final String getName() {
      return this.name;
    }

    /**
     * Return the desired type for the lookup.
     */
    public final Class<?> getLookupType() {
      return this.lookupType;
    }

    /**
     * Build a DependencyDescriptor for the underlying field/method.
     */
    public final DependencyDescriptor getDependencyDescriptor() {
      if (member instanceof Field field) {
        return new LookupDependencyDescriptor(field, lookupType);
      }
      else {
        return new LookupDependencyDescriptor((Method) member, lookupType);
      }
    }
  }

  /**
   * Class representing injection information about an annotated field
   * or setter method, supporting the @Resource annotation.
   */
  private class ResourceElement extends LookupElement {

    private final boolean lazyLookup;

    public ResourceElement(Member member, AnnotatedElement ae, @Nullable PropertyDescriptor pd) {
      super(member, pd);
      Resource resource = ae.getAnnotation(Resource.class);
      String resourceName = resource.name();
      Class<?> resourceType = resource.type();
      this.isDefaultName = StringUtils.isEmpty(resourceName);
      if (this.isDefaultName) {
        resourceName = this.member.getName();
        if (this.member instanceof Method && resourceName.startsWith("set") && resourceName.length() > 3) {
          resourceName = StringUtils.uncapitalizeAsProperty(resourceName.substring(3));
        }
      }
      else if (embeddedValueResolver != null) {
        resourceName = embeddedValueResolver.resolveStringValue(resourceName);
      }
      if (Object.class != resourceType) {
        checkResourceType(resourceType);
      }
      else {
        // No resource type specified... check field/method.
        resourceType = getResourceType();
      }
      this.name = (resourceName != null ? resourceName : "");
      this.lookupType = resourceType;
      String lookupValue = resource.lookup();
      this.mappedName = StringUtils.isNotEmpty(lookupValue) ? lookupValue : resource.mappedName();
      Lazy lazy = ae.getAnnotation(Lazy.class);
      this.lazyLookup = (lazy != null && lazy.value());
    }

    @Override
    protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
      return lazyLookup
             ? buildLazyResourceProxy(this, requestingBeanName)
             : getResource(this, requestingBeanName);
    }
  }

  /**
   * Class representing injection information about an annotated field
   * or setter method, supporting the @EJB annotation.
   */
  private class EjbRefElement extends LookupElement {

    private final String beanName;

    public EjbRefElement(Member member, AnnotatedElement ae, @Nullable PropertyDescriptor pd) {
      super(member, pd);
      EJB resource = ae.getAnnotation(EJB.class);
      String resourceBeanName = resource.beanName();
      String resourceName = resource.name();
      this.isDefaultName = StringUtils.isEmpty(resourceName);
      if (this.isDefaultName) {
        resourceName = this.member.getName();
        if (this.member instanceof Method && resourceName.startsWith("set") && resourceName.length() > 3) {
          resourceName = StringUtils.uncapitalizeAsProperty(resourceName.substring(3));
        }
      }
      Class<?> resourceType = resource.beanInterface();
      if (Object.class != resourceType) {
        checkResourceType(resourceType);
      }
      else {
        // No resource type specified... check field/method.
        resourceType = getResourceType();
      }
      this.beanName = resourceBeanName;
      this.name = resourceName;
      this.lookupType = resourceType;
      this.mappedName = resource.mappedName();
    }

    @Override
    protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
      if (StringUtils.isNotEmpty(this.beanName)) {
        if (beanFactory != null && beanFactory.containsBean(this.beanName)) {
          // Local match found for explicitly specified local bean name.
          Object bean = beanFactory.getBean(this.beanName, this.lookupType);
          if (requestingBeanName != null && beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
            configurableBeanFactory.registerDependentBean(this.beanName, requestingBeanName);
          }
          return bean;
        }
        else if (this.isDefaultName && StringUtils.isEmpty(this.mappedName)) {
          throw new NoSuchBeanDefinitionException(this.beanName,
                  "Cannot resolve 'beanName' in local BeanFactory. Consider specifying a general 'name' value instead.");
        }
      }
      // JNDI name lookup - may still go to a local BeanFactory.
      return getResource(this, requestingBeanName);
    }
  }

  /**
   * Extension of the DependencyDescriptor class,
   * overriding the dependency type with the specified resource type.
   */
  private static class LookupDependencyDescriptor extends DependencyDescriptor {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Class<?> lookupType;

    public LookupDependencyDescriptor(Field field, Class<?> lookupType) {
      super(field, true);
      this.lookupType = lookupType;
    }

    public LookupDependencyDescriptor(Method method, Class<?> lookupType) {
      super(new MethodParameter(method, 0), true);
      this.lookupType = lookupType;
    }

    @Override
    public Class<?> getDependencyType() {
      return this.lookupType;
    }
  }

}

