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

package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * @author Spencer Gibb
 */
public class LoadBalancerClientFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(LoadBalancerClientFilter.class);
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10100;

	private final LoadBalancerClient loadBalancer;

	public LoadBalancerClientFilter(LoadBalancerClient loadBalancer) {
		this.loadBalancer = loadBalancer;
	}

	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
	    // 获得 URL
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		if (url == null || !url.getScheme().equals("lb")) {
			return chain.filter(exchange);
		}
        // 添加 原始请求URI 到 GATEWAY_ORIGINAL_REQUEST_URL_ATTR
		//preserve the original url
		addOriginalRequestUrl(exchange, url);

		log.trace("LoadBalancerClientFilter url before: " + url);

		// 获取 服务实例
		final ServiceInstance instance = loadBalancer.choose(url.getHost());
		if (instance == null) {
			throw new NotFoundException("Unable to find instance for " + url.getHost());
		}

		/*URI uri = exchange.getRequest().getURI();
		URI requestUrl = loadBalancer.reconstructURI(instance, uri);*/
		//
		URI requestUrl = UriComponentsBuilder.fromUri(url)
				.scheme(instance.isSecure()? "https" : "http") //TODO: support websockets
				.host(instance.getHost())
				.port(instance.getPort())
				.build(true)
				.toUri();
		log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);

        // 添加 请求URI 到 GATEWAY_REQUEST_URL_ATTR
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);

        // 提交过滤器链继续过滤
		return chain.filter(exchange);
	}

}
