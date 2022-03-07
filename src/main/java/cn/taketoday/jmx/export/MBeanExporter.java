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

package cn.taketoday.jmx.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.DynamicMBean;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import cn.taketoday.aop.proxy.ProxyFactory;
import cn.taketoday.aop.scope.ScopedProxyUtils;
import cn.taketoday.aop.support.AopUtils;
import cn.taketoday.aop.target.LazyInitTargetSource;
import cn.taketoday.beans.factory.BeanClassLoadFailedException;
import cn.taketoday.beans.factory.BeanClassLoaderAware;
import cn.taketoday.beans.factory.BeanFactory;
import cn.taketoday.beans.factory.BeanFactoryAware;
import cn.taketoday.beans.factory.BeanFactoryUtils;
import cn.taketoday.beans.factory.DisposableBean;
import cn.taketoday.beans.factory.InitializingBean;
import cn.taketoday.beans.factory.SmartInitializingSingleton;
import cn.taketoday.beans.factory.support.BeanDefinition;
import cn.taketoday.beans.factory.support.ConfigurableBeanFactory;
import cn.taketoday.core.Constants;
import cn.taketoday.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler;
import cn.taketoday.jmx.export.assembler.MBeanInfoAssembler;
import cn.taketoday.jmx.export.assembler.MetadataMBeanInfoAssembler;
import cn.taketoday.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;
import cn.taketoday.jmx.export.naming.KeyNamingStrategy;
import cn.taketoday.jmx.export.naming.MetadataNamingStrategy;
import cn.taketoday.jmx.export.naming.ObjectNamingStrategy;
import cn.taketoday.jmx.export.naming.SelfNaming;
import cn.taketoday.jmx.export.notification.ModelMBeanNotificationPublisher;
import cn.taketoday.jmx.export.notification.NotificationPublisher;
import cn.taketoday.jmx.export.notification.NotificationPublisherAware;
import cn.taketoday.jmx.support.JmxUtils;
import cn.taketoday.jmx.support.MBeanRegistrationSupport;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.CollectionUtils;
import cn.taketoday.util.ObjectUtils;

/**
 * JMX exporter that allows for exposing any <i>Spring-managed bean</i> to a
 * JMX {@link MBeanServer}, without the need to define any
 * JMX-specific information in the bean classes.
 *
 * <p>If a bean implements one of the JMX management interfaces, MBeanExporter can
 * simply register the MBean with the server through its autodetection process.
 *
 * <p>If a bean does not implement one of the JMX management interfaces, MBeanExporter
 * will create the management information using the supplied {@link MBeanInfoAssembler}.
 *
 * <p>A list of {@link MBeanExporterListener MBeanExporterListeners} can be registered
 * via the {@link #setListeners(MBeanExporterListener[]) listeners} property, allowing
 * application code to be notified of MBean registration and unregistration events.
 *
 * <p>This exporter is compatible with MBeans as well as MXBeans.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @see #setBeans
 * @see #setAutodetect
 * @see #setAssembler
 * @see #setListeners
 * @see MBeanInfoAssembler
 * @see MBeanExporterListener
 * @since 4.0
 */
public class MBeanExporter extends MBeanRegistrationSupport implements MBeanExportOperations,
        BeanClassLoaderAware, BeanFactoryAware, InitializingBean, SmartInitializingSingleton, DisposableBean {

  /**
   * Autodetection mode indicating that no autodetection should be used.
   */
  public static final int AUTODETECT_NONE = 0;

  /**
   * Autodetection mode indicating that only valid MBeans should be autodetected.
   */
  public static final int AUTODETECT_MBEAN = 1;

  /**
   * Autodetection mode indicating that only the {@link MBeanInfoAssembler} should be able
   * to autodetect beans.
   */
  public static final int AUTODETECT_ASSEMBLER = 2;

  /**
   * Autodetection mode indicating that all autodetection mechanisms should be used.
   */
  public static final int AUTODETECT_ALL = AUTODETECT_MBEAN | AUTODETECT_ASSEMBLER;

  /**
   * Wildcard used to map a {@link NotificationListener}
   * to all MBeans registered by the {@code MBeanExporter}.
   */
  private static final String WILDCARD = "*";

  /** Constant for the JMX {@code mr_type} "ObjectReference". */
  private static final String MR_TYPE_OBJECT_REFERENCE = "ObjectReference";

  /** Prefix for the autodetect constants defined in this class. */
  private static final String CONSTANT_PREFIX_AUTODETECT = "AUTODETECT_";

  /** Constants instance for this class. */
  private static final Constants constants = new Constants(MBeanExporter.class);

  /** The beans to be exposed as JMX managed resources, with JMX names as keys. */
  @Nullable
  private Map<String, Object> beans;

  /** The autodetect mode to use for this MBeanExporter. */
  @Nullable
  private Integer autodetectMode;

  /** Whether to eagerly initialize candidate beans when autodetecting MBeans. */
  private boolean allowEagerInit = false;

  /** Stores the MBeanInfoAssembler to use for this exporter. */
  private MBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();

  /** The strategy to use for creating ObjectNames for an object. */
  private ObjectNamingStrategy namingStrategy = new KeyNamingStrategy();

  /** Indicates whether Spring should modify generated ObjectNames. */
  private boolean ensureUniqueRuntimeObjectNames = true;

  /** Indicates whether Spring should expose the managed resource ClassLoader in the MBean. */
  private boolean exposeManagedResourceClassLoader = true;

  /** A set of bean names that should be excluded from autodetection. */
  private final Set<String> excludedBeans = new HashSet<>();

  /** The MBeanExporterListeners registered with this exporter. */
  @Nullable
  private MBeanExporterListener[] listeners;

  /** The NotificationListeners to register for the MBeans registered by this exporter. */
  @Nullable
  private NotificationListenerBean[] notificationListeners;

  /** Map of actually registered NotificationListeners. */
  private final Map<NotificationListenerBean, ObjectName[]> registeredNotificationListeners = new LinkedHashMap<>();

  /** Stores the ClassLoader to use for generating lazy-init proxies. */
  @Nullable
  private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

  /** Stores the BeanFactory for use in autodetection process. */
  @Nullable
  private BeanFactory beanFactory;

  /**
   * Supply a {@code Map} of beans to be registered with the JMX
   * {@code MBeanServer}.
   * <p>The String keys are the basis for the creation of JMX object names.
   * By default, a JMX {@code ObjectName} will be created straight
   * from the given key. This can be customized through specifying a
   * custom {@code NamingStrategy}.
   * <p>Both bean instances and bean names are allowed as values.
   * Bean instances are typically linked in through bean references.
   * Bean names will be resolved as beans in the current factory, respecting
   * lazy-init markers (that is, not triggering initialization of such beans).
   *
   * @param beans a Map with JMX names as keys and bean instances or bean names
   * as values
   * @see #setNamingStrategy
   * @see KeyNamingStrategy
   * @see ObjectName#ObjectName(String)
   */
  public void setBeans(Map<String, Object> beans) {
    this.beans = beans;
  }

  /**
   * Set whether to autodetect MBeans in the bean factory that this exporter
   * runs in. Will also ask an {@code AutodetectCapableMBeanInfoAssembler}
   * if available.
   * <p>This feature is turned off by default. Explicitly specify
   * {@code true} here to enable autodetection.
   *
   * @see #setAssembler
   * @see AutodetectCapableMBeanInfoAssembler
   * @see #isMBean
   */
  public void setAutodetect(boolean autodetect) {
    this.autodetectMode = (autodetect ? AUTODETECT_ALL : AUTODETECT_NONE);
  }

  /**
   * Set the autodetection mode to use.
   *
   * @throws IllegalArgumentException if the supplied value is not
   * one of the {@code AUTODETECT_} constants
   * @see #setAutodetectModeName(String)
   * @see #AUTODETECT_ALL
   * @see #AUTODETECT_ASSEMBLER
   * @see #AUTODETECT_MBEAN
   * @see #AUTODETECT_NONE
   */
  public void setAutodetectMode(int autodetectMode) {
    if (!constants.getValues(CONSTANT_PREFIX_AUTODETECT).contains(autodetectMode)) {
      throw new IllegalArgumentException("Only values of autodetect constants allowed");
    }
    this.autodetectMode = autodetectMode;
  }

  /**
   * Set the autodetection mode to use by name.
   *
   * @throws IllegalArgumentException if the supplied value is not resolvable
   * to one of the {@code AUTODETECT_} constants or is {@code null}
   * @see #setAutodetectMode(int)
   * @see #AUTODETECT_ALL
   * @see #AUTODETECT_ASSEMBLER
   * @see #AUTODETECT_MBEAN
   * @see #AUTODETECT_NONE
   */
  public void setAutodetectModeName(String constantName) {
    if (!constantName.startsWith(CONSTANT_PREFIX_AUTODETECT)) {
      throw new IllegalArgumentException("Only autodetect constants allowed");
    }
    this.autodetectMode = (Integer) constants.asNumber(constantName);
  }

  /**
   * Specify whether to allow eager initialization of candidate beans
   * when autodetecting MBeans in the Spring application context.
   * <p>Default is "false", respecting lazy-init flags on bean definitions.
   * Switch this to "true" in order to search lazy-init beans as well,
   * including FactoryBean-produced objects that haven't been initialized yet.
   */
  public void setAllowEagerInit(boolean allowEagerInit) {
    this.allowEagerInit = allowEagerInit;
  }

  /**
   * Set the implementation of the {@code MBeanInfoAssembler} interface to use
   * for this exporter. Default is a {@code SimpleReflectiveMBeanInfoAssembler}.
   * <p>The passed-in assembler can optionally implement the
   * {@code AutodetectCapableMBeanInfoAssembler} interface, which enables it
   * to participate in the exporter's MBean autodetection process.
   *
   * @see SimpleReflectiveMBeanInfoAssembler
   * @see AutodetectCapableMBeanInfoAssembler
   * @see MetadataMBeanInfoAssembler
   * @see #setAutodetect
   */
  public void setAssembler(MBeanInfoAssembler assembler) {
    this.assembler = assembler;
  }

  /**
   * Set the implementation of the {@code ObjectNamingStrategy} interface
   * to use for this exporter. Default is a {@code KeyNamingStrategy}.
   *
   * @see KeyNamingStrategy
   * @see MetadataNamingStrategy
   */
  public void setNamingStrategy(ObjectNamingStrategy namingStrategy) {
    this.namingStrategy = namingStrategy;
  }

  /**
   * Indicates whether Spring should ensure that {@link ObjectName ObjectNames}
   * generated by the configured {@link ObjectNamingStrategy} for
   * runtime-registered MBeans ({@link #registerManagedResource}) should get
   * modified: to ensure uniqueness for every instance of a managed {@code Class}.
   * <p>The default value is {@code true}.
   *
   * @see #registerManagedResource
   * @see JmxUtils#appendIdentityToObjectName(ObjectName, Object)
   */
  public void setEnsureUniqueRuntimeObjectNames(boolean ensureUniqueRuntimeObjectNames) {
    this.ensureUniqueRuntimeObjectNames = ensureUniqueRuntimeObjectNames;
  }

  /**
   * Indicates whether or not the managed resource should be exposed on the
   * {@link Thread#getContextClassLoader() thread context ClassLoader} before
   * allowing any invocations on the MBean to occur.
   * <p>The default value is {@code true}, exposing a {@link SpringModelMBean}
   * which performs thread context ClassLoader management. Switch this flag off to
   * expose a standard JMX {@link RequiredModelMBean}.
   */
  public void setExposeManagedResourceClassLoader(boolean exposeManagedResourceClassLoader) {
    this.exposeManagedResourceClassLoader = exposeManagedResourceClassLoader;
  }

  /**
   * Set the list of names for beans that should be excluded from autodetection.
   */
  public void setExcludedBeans(String... excludedBeans) {
    this.excludedBeans.clear();
    Collections.addAll(this.excludedBeans, excludedBeans);
  }

  /**
   * Add the name of bean that should be excluded from autodetection.
   */
  public void addExcludedBean(String excludedBean) {
    Assert.notNull(excludedBean, "ExcludedBean must not be null");
    excludedBeans.add(excludedBean);
  }

  /**
   * Set the {@code MBeanExporterListener}s that should be notified
   * of MBean registration and unregistration events.
   *
   * @see MBeanExporterListener
   */
  public void setListeners(MBeanExporterListener... listeners) {
    this.listeners = listeners;
  }

  /**
   * Set the {@link NotificationListenerBean NotificationListenerBeans}
   * containing the
   * {@link NotificationListener NotificationListeners}
   * that will be registered with the {@link MBeanServer}.
   *
   * @see #setNotificationListenerMappings(Map)
   * @see NotificationListenerBean
   */
  public void setNotificationListeners(NotificationListenerBean... notificationListeners) {
    this.notificationListeners = notificationListeners;
  }

  /**
   * Set the {@link NotificationListener NotificationListeners} to register
   * with the {@link MBeanServer}.
   * <P>The key of each entry in the {@code Map} is a {@link String}
   * representation of the {@link ObjectName} or the bean
   * name of the MBean the listener should be registered for. Specifying an
   * asterisk ({@code *}) for a key will cause the listener to be
   * associated with all MBeans registered by this class at startup time.
   * <p>The value of each entry is the
   * {@link NotificationListener} to register. For more
   * advanced options such as registering
   * {@link javax.management.NotificationFilter NotificationFilters} and
   * handback objects see {@link #setNotificationListeners(NotificationListenerBean[])}.
   */
  public void setNotificationListenerMappings(Map<?, ? extends NotificationListener> listeners) {
    Assert.notNull(listeners, "'listeners' must not be null");
    List<NotificationListenerBean> notificationListeners =
            new ArrayList<>(listeners.size());

    listeners.forEach((key, listener) -> {
      // Get the listener from the map value.
      NotificationListenerBean bean = new NotificationListenerBean(listener);
      // Get the ObjectName from the map key.
      if (key != null && !WILDCARD.equals(key)) {
        // This listener is mapped to a specific ObjectName.
        bean.setMappedObjectName(key);
      }
      notificationListeners.add(bean);
    });

    this.notificationListeners = notificationListeners.toArray(new NotificationListenerBean[0]);
  }

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    this.beanClassLoader = classLoader;
  }

  /**
   * This callback is only required for resolution of bean names in the
   * {@link #setBeans(Map) "beans"} {@link Map} and for
   * autodetection of MBeans (in the latter case, a
   * {@code ListableBeanFactory} is required).
   *
   * @see #setBeans
   * @see #setAutodetect
   */
  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  //---------------------------------------------------------------------
  // Lifecycle in bean factory: automatically register/unregister beans
  //---------------------------------------------------------------------

  @Override
  public void afterPropertiesSet() {
    // If no server was provided then try to find one. This is useful in an environment
    // where there is already an MBeanServer loaded.
    if (server == null) {
      this.server = JmxUtils.locateMBeanServer();
    }
  }

  /**
   * Kick off bean registration automatically after the regular singleton instantiation phase.
   *
   * @see #registerBeans()
   */
  @Override
  public void afterSingletonsInstantiated() {
    try {
      log.debug("Registering beans for JMX exposure on startup");
      registerBeans();
      registerNotificationListeners();
    }
    catch (RuntimeException ex) {
      // Unregister beans already registered by this exporter.
      unregisterNotificationListeners();
      unregisterBeans();
      throw ex;
    }
  }

  /**
   * Unregisters all beans that this exported has exposed via JMX
   * when the enclosing {@code ApplicationContext} is destroyed.
   */
  @Override
  public void destroy() {
    log.debug("Unregistering JMX-exposed beans on shutdown");
    unregisterNotificationListeners();
    unregisterBeans();
  }

  //---------------------------------------------------------------------
  // Implementation of MBeanExportOperations interface
  //---------------------------------------------------------------------

  @Override
  public ObjectName registerManagedResource(Object managedResource) throws MBeanExportException {
    Assert.notNull(managedResource, "Managed resource must not be null");
    ObjectName objectName;
    try {
      objectName = getObjectName(managedResource, null);
      if (ensureUniqueRuntimeObjectNames) {
        objectName = JmxUtils.appendIdentityToObjectName(objectName, managedResource);
      }
    }
    catch (Throwable ex) {
      throw new MBeanExportException("Unable to generate ObjectName for MBean [" + managedResource + "]", ex);
    }
    registerManagedResource(managedResource, objectName);
    return objectName;
  }

  @Override
  public void registerManagedResource(Object managedResource, ObjectName objectName) throws MBeanExportException {
    Assert.notNull(managedResource, "Managed resource must not be null");
    Assert.notNull(objectName, "ObjectName must not be null");
    try {
      if (isMBean(managedResource.getClass())) {
        doRegister(managedResource, objectName);
      }
      else {
        ModelMBean mbean = createAndConfigureMBean(managedResource, managedResource.getClass().getName());
        doRegister(mbean, objectName);
        injectNotificationPublisherIfNecessary(managedResource, mbean, objectName);
      }
    }
    catch (JMException ex) {
      throw new UnableToRegisterMBeanException(
              "Unable to register MBean [" + managedResource + "] with object name [" + objectName + "]", ex);
    }
  }

  @Override
  public void unregisterManagedResource(ObjectName objectName) {
    Assert.notNull(objectName, "ObjectName must not be null");
    doUnregister(objectName);
  }

  //---------------------------------------------------------------------
  // Exporter implementation
  //---------------------------------------------------------------------

  /**
   * Register the defined beans with the {@link MBeanServer}.
   * <p>Each bean is exposed to the {@code MBeanServer} via a
   * {@code ModelMBean}. The actual implementation of the
   * {@code ModelMBean} interface used depends on the implementation of
   * the {@code ModelMBeanProvider} interface that is configured. By
   * default the {@code RequiredModelMBean} class that is supplied with
   * all JMX implementations is used.
   * <p>The management interface produced for each bean is dependent on the
   * {@code MBeanInfoAssembler} implementation being used. The
   * {@code ObjectName} given to each bean is dependent on the
   * implementation of the {@code ObjectNamingStrategy} interface being used.
   */
  protected void registerBeans() {
    // The beans property may be null, for example if we are relying solely on autodetection.
    if (beans == null) {
      this.beans = new HashMap<>();
      // Use AUTODETECT_ALL as default in no beans specified explicitly.
      if (autodetectMode == null) {
        this.autodetectMode = AUTODETECT_ALL;
      }
    }

    // Perform autodetection, if desired.
    int mode = autodetectMode != null ? autodetectMode : AUTODETECT_NONE;
    if (mode != AUTODETECT_NONE) {
      if (beanFactory == null) {
        throw new MBeanExportException("Cannot autodetect MBeans if not running in a BeanFactory");
      }
      if (mode == AUTODETECT_MBEAN || mode == AUTODETECT_ALL) {
        // Autodetect any beans that are already MBeans.
        log.debug("Autodetecting user-defined JMX MBeans");
        autodetect(beans, (beanClass, beanName) -> isMBean(beanClass));
      }
      // Allow the assembler a chance to vote for bean inclusion.
      if ((mode == AUTODETECT_ASSEMBLER || mode == AUTODETECT_ALL)
              && assembler instanceof AutodetectCapableMBeanInfoAssembler assembler) {
        autodetect(beans, assembler::includeBean);
      }
    }

    if (!beans.isEmpty()) {
      for (Map.Entry<String, Object> entry : beans.entrySet()) {
        String beanName = entry.getKey();
        Object instance = entry.getValue();
        registerBeanNameOrInstance(instance, beanName);
      }
    }
  }

  /**
   * Return whether the specified bean definition should be considered as lazy-init.
   *
   * @param beanFactory the bean factory that is supposed to contain the bean definition
   * @param beanName the name of the bean to check
   * @see cn.taketoday.beans.factory.support.ConfigurableBeanFactory#getBeanDefinition
   * @see cn.taketoday.beans.factory.support.BeanDefinition#isLazyInit
   */
  protected boolean isBeanDefinitionLazyInit(BeanFactory beanFactory, String beanName) {
    if (beanFactory instanceof ConfigurableBeanFactory) {
      BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
      if (beanDefinition != null) {
        return beanDefinition.isLazyInit();
      }
    }
    return false;
  }

  /**
   * Register an individual bean with the {@link #setServer MBeanServer}.
   * <p>This method is responsible for deciding <strong>how</strong> a bean
   * should be exposed to the {@code MBeanServer}. Specifically, if the
   * supplied {@code mapValue} is the name of a bean that is configured
   * for lazy initialization, then a proxy to the resource is registered with
   * the {@code MBeanServer} so that the lazy load behavior is
   * honored. If the bean is already an MBean then it will be registered
   * directly with the {@code MBeanServer} without any intervention. For
   * all other beans or bean names, the resource itself is registered with
   * the {@code MBeanServer} directly.
   *
   * @param mapValue the value configured for this bean in the beans map;
   * may be either the {@code String} name of a bean, or the bean itself
   * @param beanKey the key associated with this bean in the beans map
   * @return the {@code ObjectName} under which the resource was registered
   * @throws MBeanExportException if the export failed
   * @see #setBeans
   * @see #registerBeanInstance
   * @see #registerLazyInit
   */
  protected ObjectName registerBeanNameOrInstance(Object mapValue, String beanKey) throws MBeanExportException {
    try {
      if (mapValue instanceof String beanName) {
        // Bean name pointing to a potentially lazy-init bean in the factory.
        if (beanFactory == null) {
          throw new MBeanExportException("Cannot resolve bean names if not running in a BeanFactory");
        }
        if (isBeanDefinitionLazyInit(beanFactory, beanName)) {
          ObjectName objectName = registerLazyInit(beanName, beanKey);
          replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
          return objectName;
        }
        else {
          Object bean = BeanFactoryUtils.requiredBean(beanFactory, beanName);
          ObjectName objectName = registerBeanInstance(bean, beanKey);
          replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
          return objectName;
        }
      }
      else {
        // Plain bean instance -> register it directly.
        if (beanFactory != null) {
          var beansOfSameType = beanFactory.getBeansOfType(mapValue.getClass(), false, allowEagerInit);
          for (Map.Entry<String, ?> entry : beansOfSameType.entrySet()) {
            if (entry.getValue() == mapValue) {
              String beanName = entry.getKey();
              ObjectName objectName = registerBeanInstance(mapValue, beanKey);
              replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
              return objectName;
            }
          }
        }
        return registerBeanInstance(mapValue, beanKey);
      }
    }
    catch (Throwable ex) {
      throw new UnableToRegisterMBeanException(
              "Unable to register MBean [" + mapValue + "] with key '" + beanKey + "'", ex);
    }
  }

  /**
   * Replace any bean names used as keys in the {@code NotificationListener}
   * mappings with their corresponding {@code ObjectName} values.
   *
   * @param beanName the name of the bean to be registered
   * @param objectName the {@code ObjectName} under which the bean will be registered
   * with the {@code MBeanServer}
   */
  private void replaceNotificationListenerBeanNameKeysIfNecessary(String beanName, ObjectName objectName) {
    if (notificationListeners != null) {
      for (NotificationListenerBean notificationListener : notificationListeners) {
        notificationListener.replaceObjectName(beanName, objectName);
      }
    }
  }

  /**
   * Registers an existing MBean or an MBean adapter for a plain bean
   * with the {@code MBeanServer}.
   *
   * @param bean the bean to register, either an MBean or a plain bean
   * @param beanKey the key associated with this bean in the beans map
   * @return the {@code ObjectName} under which the bean was registered
   * with the {@code MBeanServer}
   */
  private ObjectName registerBeanInstance(Object bean, String beanKey) throws JMException {
    ObjectName objectName = getObjectName(bean, beanKey);
    Object mbeanToExpose = null;
    if (isMBean(bean.getClass())) {
      mbeanToExpose = bean;
    }
    else {
      DynamicMBean adaptedBean = adaptMBeanIfPossible(bean);
      if (adaptedBean != null) {
        mbeanToExpose = adaptedBean;
      }
    }

    if (mbeanToExpose != null) {
      if (log.isDebugEnabled()) {
        log.debug("Located MBean '{}': registering with JMX server as MBean [{}]", beanKey, objectName);
      }
      doRegister(mbeanToExpose, objectName);
    }
    else {
      if (log.isDebugEnabled()) {
        log.debug("Located managed bean '{}': registering with JMX server as MBean [{}]", beanKey, objectName);
      }
      ModelMBean mbean = createAndConfigureMBean(bean, beanKey);
      doRegister(mbean, objectName);
      injectNotificationPublisherIfNecessary(bean, mbean, objectName);
    }

    return objectName;
  }

  /**
   * Register beans that are configured for lazy initialization with the
   * {@code MBeanServer} indirectly through a proxy.
   *
   * @param beanName the name of the bean in the {@code BeanFactory}
   * @param beanKey the key associated with this bean in the beans map
   * @return the {@code ObjectName} under which the bean was registered
   * with the {@code MBeanServer}
   */
  private ObjectName registerLazyInit(String beanName, String beanKey) throws JMException {
    Assert.state(beanFactory != null, "No BeanFactory set");

    ProxyFactory proxyFactory = new ProxyFactory();
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.setFrozen(true);

    if (isMBean(beanFactory.getType(beanName))) {
      // A straight MBean... Let's create a simple lazy-init CGLIB proxy for it.
      LazyInitTargetSource targetSource = new LazyInitTargetSource();
      targetSource.setTargetBeanName(beanName);
      targetSource.setBeanFactory(beanFactory);
      proxyFactory.setTargetSource(targetSource);

      Object proxy = proxyFactory.getProxy(beanClassLoader);
      ObjectName objectName = getObjectName(proxy, beanKey);
      if (log.isDebugEnabled()) {
        log.debug("Located MBean '{}': registering with JMX server as lazy-init MBean [{}]", beanKey, objectName);
      }
      doRegister(proxy, objectName);
      return objectName;
    }

    else {
      // A simple bean... Let's create a lazy-init ModelMBean proxy with notification support.
      NotificationPublisherAwareLazyTargetSource targetSource = new NotificationPublisherAwareLazyTargetSource();
      targetSource.setTargetBeanName(beanName);
      targetSource.setBeanFactory(beanFactory);
      proxyFactory.setTargetSource(targetSource);

      Object proxy = proxyFactory.getProxy(beanClassLoader);
      ObjectName objectName = getObjectName(proxy, beanKey);
      if (log.isDebugEnabled()) {
        log.debug("Located simple bean '{}': registering with JMX server as lazy-init MBean [{}]", beanKey, objectName);
      }
      ModelMBean mbean = createAndConfigureMBean(proxy, beanKey);
      targetSource.setModelMBean(mbean);
      targetSource.setObjectName(objectName);
      doRegister(mbean, objectName);
      return objectName;
    }
  }

  /**
   * Retrieve the {@code ObjectName} for a bean.
   * <p>If the bean implements the {@code SelfNaming} interface, then the
   * {@code ObjectName} will be retrieved using {@code SelfNaming.getObjectName()}.
   * Otherwise, the configured {@code ObjectNamingStrategy} is used.
   *
   * @param bean the name of the bean in the {@code BeanFactory}
   * @param beanKey the key associated with the bean in the beans map
   * @return the {@code ObjectName} for the supplied bean
   * @throws MalformedObjectNameException if the retrieved {@code ObjectName} is malformed
   */
  protected ObjectName getObjectName(Object bean, @Nullable String beanKey) throws MalformedObjectNameException {
    if (bean instanceof SelfNaming) {
      return ((SelfNaming) bean).getObjectName();
    }
    else {
      return namingStrategy.getObjectName(bean, beanKey);
    }
  }

  /**
   * Determine whether the given bean class qualifies as an MBean as-is.
   * <p>The default implementation delegates to {@link JmxUtils#isMBean},
   * which checks for {@link DynamicMBean} classes as well
   * as classes with corresponding "*MBean" interface (Standard MBeans)
   * or corresponding "*MXBean" interface (Java 6 MXBeans).
   *
   * @param beanClass the bean class to analyze
   * @return whether the class qualifies as an MBean
   * @see JmxUtils#isMBean(Class)
   */
  protected boolean isMBean(@Nullable Class<?> beanClass) {
    return JmxUtils.isMBean(beanClass);
  }

  /**
   * Build an adapted MBean for the given bean instance, if possible.
   * <p>The default implementation builds a JMX @since 4.0 StandardMBean
   * for the target's MBean/MXBean interface in case of an AOP proxy,
   * delegating the interface's management operations to the proxy.
   *
   * @param bean the original bean instance
   * @return the adapted MBean, or {@code null} if not possible
   */
  @SuppressWarnings("unchecked")
  @Nullable
  protected DynamicMBean adaptMBeanIfPossible(Object bean) throws JMException {
    Class<?> targetClass = AopUtils.getTargetClass(bean);
    if (targetClass != bean.getClass()) {
      Class<?> ifc = JmxUtils.getMXBeanInterface(targetClass);
      if (ifc != null) {
        if (!ifc.isInstance(bean)) {
          throw new NotCompliantMBeanException("Managed bean [" + bean +
                  "] has a target class with an MXBean interface but does not expose it in the proxy");
        }
        return new StandardMBean(bean, ((Class<Object>) ifc), true);
      }
      else {
        ifc = JmxUtils.getMBeanInterface(targetClass);
        if (ifc != null) {
          if (!ifc.isInstance(bean)) {
            throw new NotCompliantMBeanException("Managed bean [" + bean +
                    "] has a target class with an MBean interface but does not expose it in the proxy");
          }
          return new StandardMBean(bean, ((Class<Object>) ifc));
        }
      }
    }
    return null;
  }

  /**
   * Creates an MBean that is configured with the appropriate management
   * interface for the supplied managed resource.
   *
   * @param managedResource the resource that is to be exported as an MBean
   * @param beanKey the key associated with the managed bean
   * @see #createModelMBean()
   * @see #getMBeanInfo(Object, String)
   */
  protected ModelMBean createAndConfigureMBean(Object managedResource, String beanKey)
          throws MBeanExportException {
    try {
      ModelMBean mbean = createModelMBean();
      mbean.setModelMBeanInfo(getMBeanInfo(managedResource, beanKey));
      mbean.setManagedResource(managedResource, MR_TYPE_OBJECT_REFERENCE);
      return mbean;
    }
    catch (Throwable ex) {
      throw new MBeanExportException("Could not create ModelMBean for managed resource [" +
              managedResource + "] with key '" + beanKey + "'", ex);
    }
  }

  /**
   * Create an instance of a class that implements {@code ModelMBean}.
   * <p>This method is called to obtain a {@code ModelMBean} instance to
   * use when registering a bean. This method is called once per bean during the
   * registration phase and must return a new instance of {@code ModelMBean}
   *
   * @return a new instance of a class that implements {@code ModelMBean}
   * @throws MBeanException if creation of the ModelMBean failed
   */
  protected ModelMBean createModelMBean() throws MBeanException {
    return exposeManagedResourceClassLoader ? new SpringModelMBean() : new RequiredModelMBean();
  }

  /**
   * Gets the {@code ModelMBeanInfo} for the bean with the supplied key
   * and of the supplied type.
   */
  private ModelMBeanInfo getMBeanInfo(Object managedBean, String beanKey) throws JMException {
    ModelMBeanInfo info = assembler.getMBeanInfo(managedBean, beanKey);
    if (log.isInfoEnabled() && ObjectUtils.isEmpty(info.getAttributes()) &&
            ObjectUtils.isEmpty(info.getOperations())) {
      log.info("Bean with key '{}' has been registered as an MBean but has no exposed attributes or operations", beanKey);
    }
    return info;
  }

  //---------------------------------------------------------------------
  // Autodetection process
  //---------------------------------------------------------------------

  /**
   * Performs the actual autodetection process, delegating to an
   * {@code AutodetectCallback} instance to vote on the inclusion of a
   * given bean.
   *
   * @param callback the {@code AutodetectCallback} to use when deciding
   * whether to include a bean or not
   */
  private void autodetect(Map<String, Object> beans, AutodetectCallback callback) {
    Assert.state(beanFactory != null, "No BeanFactory set");
    Set<String> beanNames = new LinkedHashSet<>(beanFactory.getBeanDefinitionCount());
    Collections.addAll(beanNames, beanFactory.getBeanDefinitionNames());
    if (beanFactory instanceof ConfigurableBeanFactory) {
      Collections.addAll(beanNames, ((ConfigurableBeanFactory) beanFactory).getSingletonNames());
    }

    for (String beanName : beanNames) {
      if (!isExcluded(beanName) && !isBeanDefinitionAbstract(beanFactory, beanName)) {
        try {
          Class<?> beanClass = beanFactory.getType(beanName);
          if (beanClass != null && callback.include(beanClass, beanName)) {
            boolean lazyInit = isBeanDefinitionLazyInit(beanFactory, beanName);
            Object beanInstance = null;
            if (!lazyInit) {
              beanInstance = beanFactory.getBean(beanName);
              if (!beanClass.isInstance(beanInstance)) {
                continue;
              }
            }
            if (!ScopedProxyUtils.isScopedTarget(beanName)
                    && !beans.containsValue(beanName)
                    && (beanInstance == null || !CollectionUtils.containsInstance(beans.values(), beanInstance))) {
              // Not already registered for JMX exposure.
              beans.put(beanName, (beanInstance != null ? beanInstance : beanName));
              if (log.isDebugEnabled()) {
                log.debug("Bean with name '{}' has been autodetected for JMX exposure", beanName);
              }
            }
            else {
              if (log.isTraceEnabled()) {
                log.trace("Bean with name '{}' is already registered for JMX exposure", beanName);
              }
            }
          }
        }
        catch (BeanClassLoadFailedException ex) {
          if (allowEagerInit) {
            throw ex;
          }
          // otherwise ignore beans where the class is not resolvable
        }
      }
    }
  }

  /**
   * Indicates whether or not a particular bean name is present in the excluded beans list.
   */
  private boolean isExcluded(String beanName) {
    return excludedBeans.contains(beanName)
            || (
            beanName.startsWith(BeanFactory.FACTORY_BEAN_PREFIX)
                    && excludedBeans.contains(beanName.substring(BeanFactory.FACTORY_BEAN_PREFIX.length()))
    );
  }

  /**
   * Return whether the specified bean definition should be considered as abstract.
   */
  private boolean isBeanDefinitionAbstract(BeanFactory beanFactory, String beanName) {
    if (beanFactory instanceof ConfigurableBeanFactory) {
      BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
      if (beanDefinition != null) {
        return beanDefinition.isAbstract();
      }
    }
    return false;
  }

  //---------------------------------------------------------------------
  // Notification and listener management
  //---------------------------------------------------------------------

  /**
   * If the supplied managed resource implements the {@link NotificationPublisherAware} an instance of
   * {@link NotificationPublisher} is injected.
   */
  private void injectNotificationPublisherIfNecessary(
          Object managedResource, @Nullable ModelMBean modelMBean, @Nullable ObjectName objectName) {

    if (managedResource instanceof NotificationPublisherAware && modelMBean != null && objectName != null) {
      ((NotificationPublisherAware) managedResource).setNotificationPublisher(
              new ModelMBeanNotificationPublisher(modelMBean, objectName, managedResource));
    }
  }

  /**
   * Register the configured {@link NotificationListener NotificationListeners}
   * with the {@link MBeanServer}.
   */
  private void registerNotificationListeners() throws MBeanExportException {
    if (notificationListeners != null) {
      Assert.state(server != null, "No MBeanServer available");
      for (NotificationListenerBean bean : notificationListeners) {
        try {
          ObjectName[] mappedObjectNames = bean.getResolvedObjectNames();
          if (mappedObjectNames == null) {
            // Mapped to all MBeans registered by the MBeanExporter.
            mappedObjectNames = getRegisteredObjectNames();
          }
          if (registeredNotificationListeners.put(bean, mappedObjectNames) == null) {
            for (ObjectName mappedObjectName : mappedObjectNames) {
              server.addNotificationListener(mappedObjectName, bean.getNotificationListener(),
                      bean.getNotificationFilter(), bean.getHandback());
            }
          }
        }
        catch (Throwable ex) {
          throw new MBeanExportException("Unable to register NotificationListener", ex);
        }
      }
    }
  }

  /**
   * Unregister the configured {@link NotificationListener NotificationListeners}
   * from the {@link MBeanServer}.
   */
  private void unregisterNotificationListeners() {
    if (server != null) {
      registeredNotificationListeners.forEach((bean, mappedObjectNames) -> {
        for (ObjectName mappedObjectName : mappedObjectNames) {
          try {
            server.removeNotificationListener(mappedObjectName, bean.getNotificationListener(),
                    bean.getNotificationFilter(), bean.getHandback());
          }
          catch (Throwable ex) {
            if (log.isDebugEnabled()) {
              log.debug("Unable to unregister NotificationListener", ex);
            }
          }
        }
      });
    }
    registeredNotificationListeners.clear();
  }

  /**
   * Called when an MBean is registered. Notifies all registered
   * {@link MBeanExporterListener MBeanExporterListeners} of the registration event.
   * <p>Please note that if an {@link MBeanExporterListener} throws a (runtime)
   * exception when notified, this will essentially interrupt the notification process
   * and any remaining listeners that have yet to be notified will not (obviously)
   * receive the {@link MBeanExporterListener#mbeanRegistered(ObjectName)}
   * callback.
   *
   * @param objectName the {@code ObjectName} of the registered MBean
   */
  @Override
  protected void onRegister(ObjectName objectName) {
    notifyListenersOfRegistration(objectName);
  }

  /**
   * Called when an MBean is unregistered. Notifies all registered
   * {@link MBeanExporterListener MBeanExporterListeners} of the unregistration event.
   * <p>Please note that if an {@link MBeanExporterListener} throws a (runtime)
   * exception when notified, this will essentially interrupt the notification process
   * and any remaining listeners that have yet to be notified will not (obviously)
   * receive the {@link MBeanExporterListener#mbeanUnregistered(ObjectName)}
   * callback.
   *
   * @param objectName the {@code ObjectName} of the unregistered MBean
   */
  @Override
  protected void onUnregister(ObjectName objectName) {
    notifyListenersOfUnregistration(objectName);
  }

  /**
   * Notifies all registered {@link MBeanExporterListener MBeanExporterListeners} of the
   * registration of the MBean identified by the supplied {@link ObjectName}.
   */
  private void notifyListenersOfRegistration(ObjectName objectName) {
    if (listeners != null) {
      for (MBeanExporterListener listener : listeners) {
        listener.mbeanRegistered(objectName);
      }
    }
  }

  /**
   * Notifies all registered {@link MBeanExporterListener MBeanExporterListeners} of the
   * unregistration of the MBean identified by the supplied {@link ObjectName}.
   */
  private void notifyListenersOfUnregistration(ObjectName objectName) {
    if (listeners != null) {
      for (MBeanExporterListener listener : listeners) {
        listener.mbeanUnregistered(objectName);
      }
    }
  }

  //---------------------------------------------------------------------
  // Inner classes for internal use
  //---------------------------------------------------------------------

  /**
   * Internal callback interface for the autodetection process.
   */
  @FunctionalInterface
  private interface AutodetectCallback {

    /**
     * Called during the autodetection process to decide whether
     * or not a bean should be included.
     *
     * @param beanClass the class of the bean
     * @param beanName the name of the bean
     */
    boolean include(Class<?> beanClass, String beanName);
  }

  /**
   * Extension of {@link LazyInitTargetSource} that will inject a
   * {@link NotificationPublisher}
   * into the lazy resource as it is created if required.
   */
  @SuppressWarnings("serial")
  private class NotificationPublisherAwareLazyTargetSource extends LazyInitTargetSource {

    @Nullable
    private ModelMBean modelMBean;

    @Nullable
    private ObjectName objectName;

    public void setModelMBean(ModelMBean modelMBean) {
      this.modelMBean = modelMBean;
    }

    public void setObjectName(ObjectName objectName) {
      this.objectName = objectName;
    }

    @Override
    @Nullable
    public Object getTarget() {
      try {
        return super.getTarget();
      }
      catch (RuntimeException ex) {
        if (logger.isInfoEnabled()) {
          logger.info("Failed to retrieve target for JMX-exposed bean [" + objectName + "]: " + ex);
        }
        throw ex;
      }
    }

    @Override
    protected void postProcessTargetObject(Object targetObject) {
      injectNotificationPublisherIfNecessary(targetObject, modelMBean, objectName);
    }
  }

}
