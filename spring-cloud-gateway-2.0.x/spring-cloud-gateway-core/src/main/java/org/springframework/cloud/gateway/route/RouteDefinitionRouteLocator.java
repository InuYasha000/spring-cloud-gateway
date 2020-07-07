/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.route;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ArgumentHints;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.tuple.Tuple;
import org.springframework.tuple.TupleBuilder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}
 * @author Spencer Gibb
 */
public class RouteDefinitionRouteLocator implements RouteLocator, BeanFactoryAware {
	protected final Log logger = LogFactory.getLog(getClass());

	private final RouteDefinitionLocator routeDefinitionLocator;
    /**
     * RoutePredicateFactory 映射
     * key ：{@link RoutePredicateFactory#name()}
     */
	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();
    /**
     * GatewayFilterFactory 映射
     * key ：{@link GatewayFilterFactory#name()}
     */
	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();
	private final GatewayProperties gatewayProperties;
	private final SpelExpressionParser parser = new SpelExpressionParser();
	private BeanFactory beanFactory;

	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
									   List<RoutePredicateFactory> predicates,
									   List<GatewayFilterFactory> gatewayFilterFactories,
									   GatewayProperties gatewayProperties) {
		// 设置 RouteDefinitionLocator
	    this.routeDefinitionLocator = routeDefinitionLocator;
		// 初始化 RoutePredicateFactory
		initFactories(predicates);
		// 初始化 RoutePredicateFactory
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));
		// 设置 GatewayProperties
		this.gatewayProperties = gatewayProperties;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();
			if (this.predicates.containsKey(key)) { // KEY 冲突
				this.logger.warn("A RoutePredicateFactory named "+ key
						+ " already exists, class: " + this.predicates.get(key)
						+ ". It will be overwritten.");
			}
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	@Override
	public Flux<Route> getRoutes() {
		return this.routeDefinitionLocator.getRouteDefinitions()
				.map(this::convertToRoute) // RouteDefinition => Route
				//TODO: error handling
				.map(route -> { // 打印日志
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					return route;
				});


		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	private Route convertToRoute(RouteDefinition routeDefinition) {
	    // 合并 Predicate
		Predicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);
		// 获得 GatewayFilter
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);
		// 构建 Route
		return Route.builder(routeDefinition)
				.predicate(predicate)
				.gatewayFilters(gatewayFilters)
				.build();
	}

	private List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		List<GatewayFilter> filters = filterDefinitions.stream()
				.map(definition -> { // FilterDefinition => GatewayFilter
				    // 获得 GatewayFilterFactory
					GatewayFilterFactory filter = this.gatewayFilterFactories.get(definition.getName());
					if (filter == null) {
						throw new IllegalArgumentException("Unable to find GatewayFilterFactory with name " + definition.getName());
					}
					// 获得 Tuple
					Map<String, String> args = definition.getArgs();
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition " + id + " applying filter " + args + " to " + definition.getName());
					}
					Tuple tuple = getTuple(filter, args, this.parser, this.beanFactory);
					// 获得 GatewayFilter
					return filter.apply(tuple);
				})
				.collect(Collectors.toList()); // 转成 List
        // GatewayFilter => OrderedGatewayFilter
		ArrayList<GatewayFilter> ordered = new ArrayList<>(filters.size());
		for (int i = 0; i < filters.size(); i++) {
			ordered.add(new OrderedGatewayFilter(filters.get(i), i+1));
		}
		// 返回 GatewayFilter 数组
		return ordered;
	}

	//TODO: make argument resolving a strategy
	/* for testing */ static Tuple getTuple(ArgumentHints hasArguments, Map<String, String> args, SpelExpressionParser parser, BeanFactory beanFactory) {
		TupleBuilder builder = TupleBuilder.tuple();

		// 参数为空
		List<String> argNames = hasArguments.argNames();
		if (!argNames.isEmpty()) {
			// ensure size is the same for key replacement later
			if (hasArguments.validateArgs() && args.size() != argNames.size()) {
				throw new IllegalArgumentException("Wrong number of arguments. Expected " + argNames
						+ " " + argNames + ". Found " + args.size() + " " + args + "'");
			}
		}

		// 创建 Tuple
		int entryIdx = 0;
		for (Map.Entry<String, String> entry : args.entrySet()) {
		    // 获得参数 KEY
			String key = entry.getKey();
			// RoutePredicateFactory has name hints and this has a fake key name
			// replace with the matching key hint
			if (key.startsWith(NameUtils.GENERATED_NAME_PREFIX) && !argNames.isEmpty()
					&& entryIdx < args.size()) {
				key = argNames.get(entryIdx);
			}
			// 获得参数 VALUE
			Object value;
			String rawValue = entry.getValue();
			if (rawValue != null) {
				rawValue = rawValue.trim();
			}
			if (rawValue != null && rawValue.startsWith("#{") && entry.getValue().endsWith("}")) {
				// assume it's spel
				StandardEvaluationContext context = new StandardEvaluationContext();
				context.setBeanResolver(new BeanFactoryResolver(beanFactory));
				Expression expression = parser.parseExpression(entry.getValue(), new TemplateParserContext());
				value = expression.getValue(context);
			} else {
				value = entry.getValue();
			}
			// 添加 KEY / VALUE
			builder.put(key, value);
			entryIdx++;
		}
		Tuple tuple = builder.build();

		// 校验参数
		if (hasArguments.validateArgs()) {
			for (String name : argNames) {
				if (!tuple.hasFieldName(name)) {
					throw new IllegalArgumentException("Missing argument '" + name + "'. Given " + tuple);
				}
			}
		}
		return tuple;
	}

	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();
		// 添加 默认过滤器
		//TODO: support option to apply defaults after route specific filters?
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters("defaultFilters",
					this.gatewayProperties.getDefaultFilters()));
		}
		// 添加 配置的过滤器
		if (!routeDefinition.getFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), routeDefinition.getFilters()));
		}
		// 排序
		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	private Predicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {
	    // 寻找 Predicate
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();
		Predicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));
		// 拼接 Predicate
		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			Predicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);
			predicate = predicate.and(found);
		}
		// 返回 Predicate
		return predicate;
	}

	private Predicate<ServerWebExchange> lookup(RouteDefinition routeDefinition, PredicateDefinition predicate) {
	    // 获得 RoutePredicateFactory
		RoutePredicateFactory found = this.predicates.get(predicate.getName());
		if (found == null) {
			throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}
		// 获得 Tuple
		Map<String, String> args = predicate.getArgs();
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + routeDefinition.getId() + " applying "
					+ args + " to " + predicate.getName());
		}
		Tuple tuple = getTuple(found, args, this.parser, this.beanFactory);
		// 获得 Predicate
		return found.apply(tuple);
	}

}
