/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2020 All Rights Reserved.
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
package cn.taketoday.jdbc.config;

import java.util.List;

import javax.sql.DataSource;

import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.annotation.MissingBean;
import cn.taketoday.context.event.ApplicationListener;
import cn.taketoday.context.event.LoadingMissingBeanEvent;
import cn.taketoday.context.logger.LoggerFactory;
import cn.taketoday.context.utils.OrderUtils;
import cn.taketoday.jdbc.JdbcOperations;

/**
 * @author TODAY <br>
 * 2019-08-24 12:01
 */
public class JdbcAutoConfiguration implements ApplicationListener<LoadingMissingBeanEvent> {

  @Override
  public void onApplicationEvent(LoadingMissingBeanEvent event) {
    LoggerFactory.getLogger(getClass()).info("Preparing TODAY Jdbc Environment");
    final ApplicationContext applicationContext = event.getApplicationContext();
    final JdbcConfiguration jdbcConfiguration = getJdbcConfiguration(applicationContext);
    configureResultResolver(applicationContext, jdbcConfiguration);
  }

  protected void configureResultResolver(ApplicationContext applicationContext, JdbcConfiguration jdbcConfiguration) {

  }

  protected JdbcConfiguration getJdbcConfiguration(ApplicationContext applicationContext) {
    return new CompositeJdbcConfiguration(applicationContext.getBeans(JdbcConfiguration.class));
  }

  @MissingBean
  JdbcOperations jdbcOperations(DataSource dataSource) {
    return new JdbcOperations(dataSource);
  }

  /**
   * All {@link JdbcConfiguration} beans
   *
   * @author TODAY <br>
   * 2019-08-24 13:27
   */
  protected static class CompositeJdbcConfiguration implements JdbcConfiguration {

    private final List<JdbcConfiguration> jdbcConfigurations;

    public CompositeJdbcConfiguration(List<JdbcConfiguration> jdbcConfigurations) {
      OrderUtils.reversedSort(jdbcConfigurations);
      this.jdbcConfigurations = jdbcConfigurations;
    }

    protected List<JdbcConfiguration> getJdbcConfigurations() {
      return jdbcConfigurations;
    }

  }
}
