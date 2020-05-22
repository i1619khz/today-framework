/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2019 All Rights Reserved.
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
package cn.taketoday.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.junit.Test;

import cn.taketoday.web.annotation.EnableViewController;
import cn.taketoday.web.annotation.GET;
import cn.taketoday.web.annotation.RestController;
import cn.taketoday.web.config.WebMvcConfiguration;
import cn.taketoday.web.handler.ViewController;
import cn.taketoday.web.registry.ViewControllerHandlerRegistry;
import cn.taketoday.web.utils.HttpUtils;

/**
 * @author TODAY <br>
 *         2020-04-28 15:39
 */
@RestController
@EnableViewController
public class HandlerMethodTest extends Base implements WebMvcConfiguration {

    @GET("/index/{q}")
    public String index(String q) {
        return q;
    }

    @GET("/index/query")
    public String query(String q) {
        return q;
    }

    @Override
    public void configureViewController(ViewControllerHandlerRegistry registry) {
        registry.addViewController("/view/controller/text").setResource("body:text");
        registry.addViewController("/view/controller/buffer", new StringBuilder("text"));
        registry.addViewController("/view/controller/null");
//        registry.addViewController("/view/controller/text").setResource("text");
    }

    public void testViewController(ViewControllerHandlerRegistry registry) {
        final Object defaultHandler = registry.getDefaultHandler();
        assertNull(defaultHandler);
        final ViewController viewController = registry.getViewController("/view/controller/null");
        assertNull(viewController.getStatus());
        assertNull(viewController.getResource());
        assertNull(viewController.getContentType());
        assertNull(viewController.getHandlerMethod());

        assertEquals(registry.getViewController("/view/controller/text").getResource(), "body:text");
        assertNull(registry.getViewController("/view/controller/text/123"));
    }

    @Test
    public void testRestController() throws IOException {
        assertEquals(HttpUtils.get("http://localhost:8080/index/123"), "123");
        assertEquals(HttpUtils.get("http://localhost:8080/index/query?q=123"), "123");
        assertEquals(HttpUtils.get("http://localhost:8080/view/controller/text"), "text");
        assertEquals(HttpUtils.get("http://localhost:8080/view/controller/buffer"), "text");
        assertEquals(HttpUtils.get("http://localhost:8080/view/controller/null"), "");
        try {
            HttpUtils.get("http://localhost:8080/index");
        }
        catch (FileNotFoundException e) {
            assert true;
        }
        testViewController(context.getBean(ViewControllerHandlerRegistry.class));
    }

}
