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

package cn.taketoday.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import cn.taketoday.core.env.CompositePropertySource;
import cn.taketoday.core.env.ConfigurableEnvironment;
import cn.taketoday.core.env.Environment;
import cn.taketoday.core.env.PropertySource;
import cn.taketoday.core.env.PropertySources;
import cn.taketoday.lang.Assert;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.util.ReflectionUtils;

/**
 * Contribute {@link PropertySource property sources} to the {@link Environment}.
 *
 * <p>This class is stateful and merges descriptors with the same name in a
 * single {@link PropertySource} rather than creating dedicated ones.
 *
 * @author Stephane Nicoll
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see PropertySourceDescriptor
 * @since 4.0
 */
public class PropertySourceProcessor {

  private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

  private static final Logger logger = LoggerFactory.getLogger(PropertySourceProcessor.class);

  private final ConfigurableEnvironment environment;

  private final ResourceLoader resourceLoader;

  private final List<String> propertySourceNames;

  public PropertySourceProcessor(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
    this.environment = environment;
    this.resourceLoader = resourceLoader;
    this.propertySourceNames = new ArrayList<>();
  }

  /**
   * Process the specified {@link PropertySourceDescriptor} against the
   * environment managed by this instance.
   *
   * @param descriptor the descriptor to process
   * @throws IOException if loading the properties failed
   */
  public void processPropertySource(PropertySourceDescriptor descriptor) throws IOException {
    String name = descriptor.name();
    String encoding = descriptor.encoding();
    List<String> locations = descriptor.locations();
    Assert.isTrue(locations.size() > 0, "At least one @PropertySource(value) location is required");
    boolean ignoreResourceNotFound = descriptor.ignoreResourceNotFound();
    PropertySourceFactory factory =
            descriptor.propertySourceFactory() != null ?
            instantiateClass(descriptor.propertySourceFactory()) : DEFAULT_PROPERTY_SOURCE_FACTORY;

    for (String location : locations) {
      try {
        String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
        Resource resource = this.resourceLoader.getResource(resolvedLocation);
        addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
      }
      catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
        // Placeholders not resolvable or resource not found when trying to open it
        if (ignoreResourceNotFound) {
          if (logger.isInfoEnabled()) {
            logger.info("Properties location [{}] not resolvable: {}", location, ex.getMessage());
          }
        }
        else {
          throw ex;
        }
      }
    }
  }

  private void addPropertySource(cn.taketoday.core.env.PropertySource<?> propertySource) {
    String name = propertySource.getName();
    PropertySources propertySources = this.environment.getPropertySources();

    if (this.propertySourceNames.contains(name)) {
      // We've already added a version, we need to extend it
      cn.taketoday.core.env.PropertySource<?> existing = propertySources.get(name);
      if (existing != null) {
        PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource rps ?
                                       rps.withResourceName() : propertySource);
        if (existing instanceof CompositePropertySource cps) {
          cps.addFirstPropertySource(newSource);
        }
        else {
          if (existing instanceof ResourcePropertySource rps) {
            existing = rps.withResourceName();
          }
          CompositePropertySource composite = new CompositePropertySource(name);
          composite.addPropertySource(newSource);
          composite.addPropertySource(existing);
          propertySources.replace(name, composite);
        }
        return;
      }
    }

    if (this.propertySourceNames.isEmpty()) {
      propertySources.addLast(propertySource);
    }
    else {
      String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
      propertySources.addBefore(firstProcessed, propertySource);
    }
    this.propertySourceNames.add(name);
  }

  private PropertySourceFactory instantiateClass(Class<? extends PropertySourceFactory> type) {
    try {
      Constructor<? extends PropertySourceFactory> constructor = type.getDeclaredConstructor();
      ReflectionUtils.makeAccessible(constructor);
      return constructor.newInstance();
    }
    catch (Exception ex) {
      throw new IllegalStateException("Failed to instantiate " + type, ex);
    }
  }

}