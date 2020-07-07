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

package org.springframework.cloud.gateway.handler;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Spencer Gibb
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	private final FilteringWebHandler webHandler;
	private final RouteLocator routeLocator;

	public RoutePredicateHandlerMapping(FilteringWebHandler webHandler, RouteLocator routeLocator) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		setOrder(1); // RequestMappingHandlerMapping 之后
	}

	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
	    // 设置 GATEWAY_HANDLER_MAPPER_ATTR 为 RoutePredicateHandlerMapping
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getClass().getSimpleName());

		return lookupRoute(exchange) // 匹配 Route
				// .log("route-predicate-handler-mapping", Level.FINER) //name this
				.flatMap((Function<Route, Mono<?>>) r -> { // 返回 FilteringWebHandler
					if (logger.isDebugEnabled()) {
						logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
					}

					// 设置 GATEWAY_ROUTE_ATTR 为 匹配的 Route
					exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
					// 返回
					return Mono.just(webHandler);
				}).switchIfEmpty(Mono.empty().then(Mono.fromRunnable(() -> { // 匹配不到 Route
					if (logger.isTraceEnabled()) {
						logger.trace("No RouteDefinition found for [" + getExchangeDesc(exchange) + "]");
					}
				})));
	}

	//TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}

	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		return this.routeLocator.getRoutes()
				.filter(route -> route.getPredicate().test(exchange))
				// .defaultIfEmpty() put a static Route not found
				// or .switchIfEmpty()
				// .switchIfEmpty(Mono.<Route>empty().log("noroute"))
				.next()
				//TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("RouteDefinition matched: " + route.getId());
					}
					validateRoute(route, exchange);
					return route;
				});

		/* TODO: trace logging
			if (logger.isTraceEnabled()) {
				logger.trace("RouteDefinition did not match: " + routeDefinition.getId());
			}*/
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>The default implementation is empty. Can be overridden in subclasses,
	 * for example to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

}
