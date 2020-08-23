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

package org.springframework.cloud.gateway.config;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.RemoveNonProxyHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.validation.annotation.Validated;

import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterName;

/**
 * @author Spencer Gibb
 * 从配置文件读取
 * 1：路由配置
 * 2：默认过滤器配置。当 RouteDefinition => Route 时，会将过滤器配置添加到每个 Route
 */
//表明以 “spring.cloud.gateway” 前缀的 properties 会绑定 GatewayProperties。
@ConfigurationProperties("spring.cloud.gateway")
@Validated
public class GatewayProperties {

	/**
	 * List of Routes
	 * 路由配置。通过 spring.cloud.gateway.routes 配置
	 */
	/**
	 * spring:
	 *   cloud:
	 *     gateway:
	 *       routes:
	 *       # =====================================
	 *       - host_example_to_httpbin=${test.uri}, Host=**.example.org
	 *
	 *       # =====================================
	 *       - id: host_foo_path_headers_to_httpbin
	 *         uri: ${test.uri}
	 *         predicates:
	 *         - Host=**.foo.org
	 *         - Path=/headers
	 *         - Method=GET
	 *         - Header=X-Request-Id, \d+
	 *         - Query=foo, ba.
	 *         - Query=baz
	 *         - Cookie=chocolate, ch.p
	 *         - After=1900-01-20T17:42:47.789-07:00[America/Denver]
	 *         filters:
	 *         - AddResponseHeader=X-Response-Foo, Bar
	 *
	 *       # =====================================
	 *       - id: add_request_header_test
	 *         uri: ${test.uri}
	 *         predicates:
	 *         - Host=**.addrequestheader.org
	 *         - Path=/headers
	 *         filters:
	 *         - AddRequestHeader=X-Request-Foo, Bar
	 */
	@NotNull
	@Valid
	private List<RouteDefinition> routes = new ArrayList<>();

	/**
	 * List of filter definitions that are applied to every route.
	 * 默认过滤器配置。通过 spring.cloud.gateway.default-filters 配置
	 */
	/**
	 * spring:
	 *   cloud:
	 *     gateway:
	 *       default-filters:
	 *       - AddResponseHeader=X-Response-Default-Foo, Default-Bar
	 *       - PrefixPath=/httpbin
	 */
	private List<FilterDefinition> defaultFilters = loadDefaults();

	private ArrayList<FilterDefinition> loadDefaults() {
		ArrayList<FilterDefinition> defaults = new ArrayList<>();
		FilterDefinition definition = new FilterDefinition();
		definition.setName(normalizeFilterName(RemoveNonProxyHeadersGatewayFilterFactory.class));
		defaults.add(definition);
		return defaults;
	}

	public List<RouteDefinition> getRoutes() {
		return routes;
	}

	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
	}

	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}
}
