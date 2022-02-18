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

package cn.taketoday.beans;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collections;

import cn.taketoday.beans.factory.support.BeanUtils;
import cn.taketoday.util.ObjectUtils;
import cn.taketoday.util.ReflectionUtils;
import cn.taketoday.util.StringUtils;

/**
 * Helper class for calculating property matches, according to a configurable
 * distance. Provide the list of potential matches and an easy way to generate
 * an error message. Works for both java bean properties and fields.
 *
 * <p>Mainly for use within the framework and in particular the binding facility.
 *
 * @author Alef Arendsen
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see #forProperty(String, Class)
 * @see #forField(String, Class)
 * @since 4.0 2022/2/17 17:43
 */
public abstract class PropertyMatches {

  /** Default maximum property distance: 2. */
  public static final int DEFAULT_MAX_DISTANCE = 2;

  // Static factory methods

  /**
   * Create PropertyMatches for the given bean property.
   *
   * @param propertyName the name of the property to find possible matches for
   * @param beanClass the bean class to search for matches
   */
  public static PropertyMatches forProperty(String propertyName, Class<?> beanClass) {
    return forProperty(propertyName, beanClass, DEFAULT_MAX_DISTANCE);
  }

  /**
   * Create PropertyMatches for the given bean property.
   *
   * @param propertyName the name of the property to find possible matches for
   * @param beanClass the bean class to search for matches
   * @param maxDistance the maximum property distance allowed for matches
   */
  public static PropertyMatches forProperty(String propertyName, Class<?> beanClass, int maxDistance) {
    return new BeanPropertyMatches(propertyName, beanClass, maxDistance);
  }

  /**
   * Create PropertyMatches for the given field property.
   *
   * @param propertyName the name of the field to find possible matches for
   * @param beanClass the bean class to search for matches
   */
  public static PropertyMatches forField(String propertyName, Class<?> beanClass) {
    return forField(propertyName, beanClass, DEFAULT_MAX_DISTANCE);
  }

  /**
   * Create PropertyMatches for the given field property.
   *
   * @param propertyName the name of the field to find possible matches for
   * @param beanClass the bean class to search for matches
   * @param maxDistance the maximum property distance allowed for matches
   */
  public static PropertyMatches forField(String propertyName, Class<?> beanClass, int maxDistance) {
    return new FieldPropertyMatches(propertyName, beanClass, maxDistance);
  }

  // Instance state

  private final String propertyName;

  private final String[] possibleMatches;

  /**
   * Create a new PropertyMatches instance for the given property and possible matches.
   */
  private PropertyMatches(String propertyName, String[] possibleMatches) {
    this.propertyName = propertyName;
    this.possibleMatches = possibleMatches;
  }

  /**
   * Return the name of the requested property.
   */
  public String getPropertyName() {
    return this.propertyName;
  }

  /**
   * Return the calculated possible matches.
   */
  public String[] getPossibleMatches() {
    return this.possibleMatches;
  }

  /**
   * Build an error message for the given invalid property name,
   * indicating the possible property matches.
   */
  public abstract String buildErrorMessage();

  // Implementation support for subclasses

  protected void appendHintMessage(StringBuilder msg) {
    msg.append("Did you mean ");
    for (int i = 0; i < this.possibleMatches.length; i++) {
      msg.append('\'');
      msg.append(this.possibleMatches[i]);
      if (i < this.possibleMatches.length - 2) {
        msg.append("', ");
      }
      else if (i == this.possibleMatches.length - 2) {
        msg.append("', or ");
      }
    }
    msg.append("'?");
  }

  /**
   * Calculate the distance between the given two Strings
   * according to the Levenshtein algorithm.
   *
   * @param s1 the first String
   * @param s2 the second String
   * @return the distance value
   */
  private static int calculateStringDistance(String s1, String s2) {
    if (s1.isEmpty()) {
      return s2.length();
    }
    if (s2.isEmpty()) {
      return s1.length();
    }

    int[][] d = new int[s1.length() + 1][s2.length() + 1];
    for (int i = 0; i <= s1.length(); i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= s2.length(); j++) {
      d[0][j] = j;
    }

    for (int i = 1; i <= s1.length(); i++) {
      char c1 = s1.charAt(i - 1);
      for (int j = 1; j <= s2.length(); j++) {
        int cost;
        char c2 = s2.charAt(j - 1);
        if (c1 == c2) {
          cost = 0;
        }
        else {
          cost = 1;
        }
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
      }
    }

    return d[s1.length()][s2.length()];
  }

  // Concrete subclasses

  private static class BeanPropertyMatches extends PropertyMatches {

    public BeanPropertyMatches(String propertyName, Class<?> beanClass, int maxDistance) {
      super(propertyName,
              calculateMatches(propertyName, BeanUtils.getPropertyDescriptors(beanClass), maxDistance));
    }

    /**
     * Generate possible property alternatives for the given property and class.
     * Internally uses the {@code getStringDistance} method, which in turn uses
     * the Levenshtein algorithm to determine the distance between two Strings.
     *
     * @param descriptors the JavaBeans property descriptors to search
     * @param maxDistance the maximum distance to accept
     */
    private static String[] calculateMatches(String name, PropertyDescriptor[] descriptors, int maxDistance) {
      ArrayList<String> candidates = new ArrayList<>();
      for (PropertyDescriptor pd : descriptors) {
        if (pd.getWriteMethod() != null) {
          String possibleAlternative = pd.getName();
          if (calculateStringDistance(name, possibleAlternative) <= maxDistance) {
            candidates.add(possibleAlternative);
          }
        }
      }
      Collections.sort(candidates);
      return StringUtils.toStringArray(candidates);
    }

    @Override
    public String buildErrorMessage() {
      StringBuilder msg = new StringBuilder(160);
      msg.append("Bean property '").append(getPropertyName()).append(
              "' is not writable or has an invalid setter method. ");
      if (ObjectUtils.isNotEmpty(getPossibleMatches())) {
        appendHintMessage(msg);
      }
      else {
        msg.append("Does the parameter type of the setter match the return type of the getter?");
      }
      return msg.toString();
    }
  }

  private static class FieldPropertyMatches extends PropertyMatches {

    public FieldPropertyMatches(String propertyName, Class<?> beanClass, int maxDistance) {
      super(propertyName, calculateMatches(propertyName, beanClass, maxDistance));
    }

    private static String[] calculateMatches(final String name, Class<?> clazz, final int maxDistance) {
      ArrayList<String> candidates = new ArrayList<>();
      ReflectionUtils.doWithFields(clazz, field -> {
        String possibleAlternative = field.getName();
        if (calculateStringDistance(name, possibleAlternative) <= maxDistance) {
          candidates.add(possibleAlternative);
        }
      });
      Collections.sort(candidates);
      return StringUtils.toStringArray(candidates);
    }

    @Override
    public String buildErrorMessage() {
      StringBuilder msg = new StringBuilder(80);
      msg.append("Bean property '").append(getPropertyName()).append("' has no matching field.");
      if (ObjectUtils.isNotEmpty(getPossibleMatches())) {
        msg.append(' ');
        appendHintMessage(msg);
      }
      return msg.toString();
    }
  }

}
