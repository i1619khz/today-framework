/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Today & 2017 - 2018 All Rights Reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.taketoday.test.context.aware;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.ClassPathApplicationContext;
import cn.taketoday.context.exception.BeanDefinitionStoreException;
import cn.taketoday.context.exception.NoSuchBeanDefinitionException;

/**
 * 
 * @author Today <br>
 * 
 *         2018-07-17 21:35:52
 */
public class AwareTest {
	
	private long start;

	@Before
	public void start() {
		start = System.currentTimeMillis();
	}
	
	@After
	public void end() {
		System.out.println("process takes " + (System.currentTimeMillis() - start) + "ms.");
	}
	
	
	
	@Test
	public void test_AwareBean() throws BeanDefinitionStoreException, NoSuchBeanDefinitionException {
		
		ApplicationContext applicationContext = new ClassPathApplicationContext();
		
		applicationContext.registerBean(AwareBean.class);
		applicationContext.onRefresh();
		
		AwareBean bean = applicationContext.getBean(AwareBean.class);
		assert bean.getApplicationContext() != null : "applicationContext == null";
		assert bean.getBeanFactory() != null : "bean factory == null";
		assert bean.getBeanName() != null : "bean name == null";
		assert bean.getClassLoader() != null : "class loader == null";
		
		System.out.println(bean);
		
		applicationContext.close();
		
	}

}
