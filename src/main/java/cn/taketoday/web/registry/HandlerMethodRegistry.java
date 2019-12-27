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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.taketoday.web.registry;

import static cn.taketoday.context.exception.ConfigurationException.nonNull;
import static cn.taketoday.context.utils.ContextUtils.resolveValue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import cn.taketoday.context.AnnotationAttributes;
import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.Ordered;
import cn.taketoday.context.PathMatcher;
import cn.taketoday.context.annotation.MissingBean;
import cn.taketoday.context.annotation.Order;
import cn.taketoday.context.bean.BeanDefinition;
import cn.taketoday.context.env.Environment;
import cn.taketoday.context.exception.BeanDefinitionStoreException;
import cn.taketoday.context.exception.ConfigurationException;
import cn.taketoday.context.exception.ContextException;
import cn.taketoday.context.factory.AbstractBeanFactory;
import cn.taketoday.context.factory.AbstractBeanFactory.Prototypes;
import cn.taketoday.context.loader.BeanDefinitionLoader;
import cn.taketoday.context.utils.ClassUtils;
import cn.taketoday.context.utils.ObjectUtils;
import cn.taketoday.context.utils.StringUtils;
import cn.taketoday.web.Constant;
import cn.taketoday.web.RequestContext;
import cn.taketoday.web.RequestMethod;
import cn.taketoday.web.WebApplicationContext;
import cn.taketoday.web.annotation.ActionMapping;
import cn.taketoday.web.annotation.Interceptor;
import cn.taketoday.web.annotation.RootController;
import cn.taketoday.web.config.WebApplicationInitializer;
import cn.taketoday.web.handler.HandlerMethod;
import cn.taketoday.web.handler.MethodParameter;
import cn.taketoday.web.handler.PatternMapping;
import cn.taketoday.web.interceptor.HandlerInterceptor;

/**
 * Store {@link HandlerMethod}
 * 
 * @author TODAY <br>
 *         2018-07-1 20:47:06
 */
@MissingBean
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HandlerMethodRegistry extends MappedHandlerRegistry implements HandlerRegistry, WebApplicationInitializer {

    private Properties variables;
    private AbstractBeanFactory beanFactory;
    private BeanDefinitionLoader beanDefinitionLoader;

    public HandlerMethodRegistry() {
        this(new HashMap<>(512));
    }

    public HandlerMethodRegistry(int initialCapacity) {
        this(new HashMap<>(initialCapacity));
    }

    public HandlerMethodRegistry(Map<String, Object> handlers) {
        super(handlers);
    }

    public HandlerMethodRegistry(Map<String, Object> handlers, int order) {
        super(handlers, order);
    }

    // MappedHandlerRegistry
    // --------------------------

    @Override
    protected String computeKey(RequestContext context) {
        return context.method().concat(context.requestURI());
    }

    @Override
    protected Object matchingPatternHandler(String handlerKey, PatternMapping[] patternMappings) {
        //StringUtils.decodeUrl(key)

        final PathMatcher pathMatcher = getPathMatcher();

        for (final PatternMapping mapping : patternMappings) {
            if (pathMatcher.match(mapping.getPattern(), handlerKey)) {
                return mapping.getHandler();
            }
        }
        return null;
    }

    public void registerHandler(RequestMethod method, String patternPath, Object handler) {
        super.registerHandler(method.name().concat(patternPath), handler);
    }

    // 

    /**
     * Initialize All Action or Handler
     */
    @Override
    public void onStartup(WebApplicationContext applicationContext) throws Throwable {

        log.info("Initializing Controllers");
        startConfiguration();
    }

    @Override
    protected void initApplicationContext(ApplicationContext context) throws ContextException {
        this.beanFactory = nonNull(context.getBean(AbstractBeanFactory.class));

        final Environment environment = context.getEnvironment();
        this.variables = environment.getProperties();
        this.beanDefinitionLoader = environment.getBeanDefinitionLoader();

        super.initApplicationContext(context);
    }

    /**
     * Start config
     * 
     * @throws Exception
     *             If any {@link Exception} occurred
     */
    protected void startConfiguration() throws Exception {
        final ApplicationContext beanFactory = obtainApplicationContext();

        // @since 2.3.3
        for (final Entry<String, BeanDefinition> entry : beanFactory.getBeanDefinitions().entrySet()) {

            final BeanDefinition def = entry.getValue();

            if (!def.isAbstract() && (def.isAnnotationPresent(RootController.class)
                                      || def.isAnnotationPresent(ActionMapping.class))) { // ActionMapping on the class is ok
                buildHandlerMethod(def.getBeanClass());
            }
        }
    }

    /**
     * Build {@link HandlerMethod}
     * 
     * @param beanClass
     *            Bean class
     * @throws Exception
     *             If any {@link Exception} occurred
     * @since 2.3.7
     */
    public void buildHandlerMethod(final Class<?> beanClass) throws Exception {

        final Set<String> namespaces = new HashSet<>(4, 1.0f); // name space
        final Set<RequestMethod> methodsOnClass = new HashSet<>(8, 1.0f); // method

        final AnnotationAttributes controllerMapping = // find mapping on class
                ClassUtils.getAnnotationAttributes(ActionMapping.class, beanClass);

        if (ObjectUtils.isNotEmpty(controllerMapping)) {
            for (final String value : controllerMapping.getStringArray(Constant.VALUE)) {
                namespaces.add(StringUtils.checkUrl(value));
            }
            Collections.addAll(methodsOnClass, controllerMapping.getAttribute("method", RequestMethod[].class));
        }

        for (final Method method : beanClass.getDeclaredMethods()) {
            buildHandlerMethod(beanClass, method, namespaces, methodsOnClass);
        }
    }

    /**
     * Set Action Mapping
     * 
     * @param beanClass
     *            Controller
     * @param method
     *            Action or Handler
     * @param namespaces
     *            Name space is that path mapping on the class level
     * @param methodsOnClass
     *            request method on class
     * @throws Exception
     *             If any {@link Exception} occurred
     */
    protected void buildHandlerMethod(final Class<?> beanClass,
                                      final Method method,
                                      final Set<String> namespaces,
                                      final Set<RequestMethod> methodsOnClass) throws Exception //
    {
        final AnnotationAttributes[] annotationAttributes = // find mapping on method
                ClassUtils.getAnnotationAttributesArray(method, ActionMapping.class);

        if (ObjectUtils.isNotEmpty(annotationAttributes)) {
            // do mapping url
            mappingHandlerMethod(createHandlerMethod(beanClass, method), // create HandlerMapping
                                 namespaces,
                                 methodsOnClass,
                                 annotationAttributes);
        }
    }

    /**
     * create a hash set
     * 
     * @param elements
     *            Elements instance
     */
    @SafeVarargs
    public static <E> Set<E> newHashSet(E... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

    /**
     * Mapping given HandlerMapping to {@link HandlerMethodRegistry}
     * 
     * @param handler
     *            current {@link HandlerMethod}
     * @param namespaces
     *            path on class
     * @param classRequestMethods
     *            methods on class
     * @param annotationAttributes
     *            {@link ActionMapping} Attributes
     */
    protected void mappingHandlerMethod(final HandlerMethod handler,
                                        final Set<String> namespaces,
                                        final Set<RequestMethod> classRequestMethods,
                                        final AnnotationAttributes[] annotationAttributes) // TODO 
    {
        final boolean emptyNamespaces = namespaces.isEmpty();
        final boolean emptyClassRequestMethods = classRequestMethods.isEmpty();

        for (final AnnotationAttributes handlerMethodMapping : annotationAttributes) {

            final boolean exclude = handlerMethodMapping.getBoolean("exclude"); // exclude name space on class ?
            final Set<RequestMethod> requestMethods = // http request method on method(action/handler)
                    newHashSet(handlerMethodMapping.getAttribute("method", RequestMethod[].class));

            if (!emptyClassRequestMethods) requestMethods.addAll(classRequestMethods);

            for (final String urlOnMethod : handlerMethodMapping.getStringArray("value")) { // url on method
                // splice urls and request methods
                // ---------------------------------
                for (final RequestMethod requestMethod : requestMethods) {

                    final String checkedUrl = StringUtils.checkUrl(urlOnMethod);
                    if (exclude || emptyNamespaces) {
                        mappingHandlerMethod(checkedUrl, requestMethod, handler);
                        continue;
                    }
                    for (final String namespace : namespaces) {
                        mappingHandlerMethod(namespace.concat(checkedUrl), requestMethod, handler);
                    }
                }
            }
        }
    }

    /**
     * Mapping to {@link HandlerMethodRegistry}
     * 
     * @param handlerMethod
     *            {@link HandlerMethod}
     * @param urlOnMethod
     *            Method url mapping
     * @param requestMethod
     *            HTTP request method
     * @see RequestMethod
     */
    protected void mappingHandlerMethod(String urlOnMethod, RequestMethod requestMethod, HandlerMethod handlerMethod) {

        // GET/blog/users/1 GET/blog/#{key}/1
        final String key = getContextPath().concat(resolveValue(urlOnMethod, String.class, variables));
        if (urlOnMethod.indexOf('{') > -1 && urlOnMethod.indexOf('}') > -1) {
            handlerMethod.setPathPattern(key);
            mappingPathVariable(key, handlerMethod);
        }
        registerHandler(requestMethod, key, handlerMethod);
    }

    /**
     * Mapping path variable.
     */
    protected void mappingPathVariable(final String pathPattern, final HandlerMethod handlerMethod) {

        final Map<String, MethodParameter> parameterMapping = new HashMap<>();
        for (MethodParameter methodParameter : handlerMethod.getParameters()) {
            parameterMapping.put(pathPattern, methodParameter);
        }

        int i = 0;
        for (final String variable : getPathMatcher().extractVariableNames(pathPattern)) {
            final MethodParameter parameter = parameterMapping.get(variable);
            if (parameter == null) {
                throw new ConfigurationException("There isn't a variable named: ["
                        + variable + "] in the parameter list at method: [" + handlerMethod.getMethod() + "]");
            }
            parameter.setPathIndex(i++);
        }
    }

    /**
     * Create {@link HandlerMethod}.
     * 
     * @param beanClass
     *            Controller class
     * @param method
     *            Action or Handler
     * @return A new {@link HandlerMethod}
     * @throws Exception
     *             If any {@link Throwable} occurred
     */
    protected HandlerMethod createHandlerMethod(final Class<?> beanClass, final Method method) throws Exception {

        final AbstractBeanFactory beanFactory = this.beanFactory;
        final BeanDefinition def = beanFactory.getBeanDefinition(beanClass);

        final Object bean = def.isSingleton()
                ? beanFactory.getBean(def)
                : Prototypes.newProxyInstance(beanClass, def, beanFactory);

        if (bean == null) {
            throw new ConfigurationException("An unexpected exception occurred: [Can't get bean with given type: ["
                    + beanClass.getName() + "]]"//
            );
        }

        return new HandlerMethod(bean, method, getInterceptors(beanClass, method));
    }

    /**
     * Add intercepter to handler .
     * 
     * @param controllerClass
     *            controller class
     * @param action
     *            method
     */
    protected List<HandlerInterceptor> getInterceptors(final Class<?> controllerClass, final Method action) {

        final List<HandlerInterceptor> ret = new ArrayList<>();

        // 设置类拦截器
        final Interceptor controllerInterceptors = controllerClass.getAnnotation(Interceptor.class);
        if (controllerInterceptors != null) {
            Collections.addAll(ret, addInterceptors(controllerInterceptors.value()));
        }
        // HandlerInterceptor on a method
        final Interceptor actionInterceptors = action.getAnnotation(Interceptor.class);

        if (actionInterceptors != null) {
            Collections.addAll(ret, addInterceptors(actionInterceptors.value()));
            final ApplicationContext beanFactory = obtainApplicationContext();
            for (Class<? extends HandlerInterceptor> interceptor : actionInterceptors.exclude()) {
                ret.remove(beanFactory.getBean(interceptor));
            }
        }

        return ret;
    }

    /***
     * Register intercepter object list
     * 
     * @param interceptors
     *            {@link HandlerInterceptor} class
     * @return A list of {@link HandlerInterceptor} objects
     */
    public HandlerInterceptor[] addInterceptors(Class<? extends HandlerInterceptor>[] interceptors) {

        if (ObjectUtils.isEmpty(interceptors)) {
            return Constant.EMPTY_HANDLER_INTERCEPTOR;
        }
        final ApplicationContext beanFactory = obtainApplicationContext();

        int i = 0;
        final HandlerInterceptor[] ret = new HandlerInterceptor[interceptors.length];
        for (Class<? extends HandlerInterceptor> interceptor : interceptors) {

            if (!beanFactory.containsBeanDefinition(interceptor, true)) {
                try {
                    beanFactory.registerBean(beanDefinitionLoader.createBeanDefinition(interceptor));
                }
                catch (BeanDefinitionStoreException e) {
                    throw new ConfigurationException("Interceptor: [" + interceptor.getName() + "] register error", e);
                }
            }
            final HandlerInterceptor instance = beanFactory.getBean(interceptor);
            ret[i++] = Objects.requireNonNull(instance, "Can't get target interceptor bean");
        }
        return ret;
    }

    /**
     * Rebuild Controllers
     * 
     * @throws Throwable
     *             If any {@link Throwable} occurred
     */
    public void reBuiltControllers() throws Throwable {

        log.info("Rebuilding Controllers");
        clear();
        startConfiguration();
    }
}
