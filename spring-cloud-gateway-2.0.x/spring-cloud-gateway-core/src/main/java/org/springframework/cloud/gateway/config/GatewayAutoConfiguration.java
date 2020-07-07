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

import com.netflix.hystrix.HystrixObservableCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayWebfluxEndpoint;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.cloud.gateway.filter.factory.*;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientOptions;
import reactor.ipc.netty.resources.PoolResources;
import rx.RxReactiveStreams;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true) // 默认开启
@EnableConfigurationProperties
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter({GatewayLoadBalancerClientAutoConfiguration.class, GatewayClassPathWarningAutoConfiguration.class})
@ConditionalOnClass(DispatcherHandler.class)
public class GatewayAutoConfiguration {

	@Configuration
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {
		@Bean // 1.2
		@ConditionalOnMissingBean
		public HttpClient httpClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return HttpClient.create(options);
		}

		@Bean // 1.1
		public Consumer<? super HttpClientOptions.Builder> nettyClientOptions() {
			return opts -> {
				opts.poolResources(PoolResources.elastic("proxy"));
				// opts.disablePool(); //TODO: why do I need this again?
			};
		}

//		@Bean // 1.3
//		public NettyRoutingFilter routingFilter(HttpClient httpClient) {
//			return new NettyRoutingFilter(httpClient);
//		}
//
//		@Bean // 1.4
//		public NettyWriteResponseFilter nettyWriteResponseFilter() {
//			return new NettyWriteResponseFilter();
//		}

		@Bean // 1.5 {@link org.springframework.cloud.gateway.filter.WebsocketRoutingFilter}
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return new ReactorNettyWebSocketClient(options);
		}
	}

	@Bean // 4.1
	@ConditionalOnMissingBean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean // 4.2
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	@Bean // 4.3
	@Primary // 优先被注入
	public RouteDefinitionLocator routeDefinitionLocator(List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(Flux.fromIterable(routeDefinitionLocators));
	}

	@Bean // 4.4
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
												   List<GatewayFilterFactory> GatewayFilters,
												   List<RoutePredicateFactory> predicates,
												   RouteDefinitionLocator routeDefinitionLocator) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates, GatewayFilters, properties);
	}

	@Bean // 4.5 // TODO 芋艿，where are you 【1】AdditionalRoutes 【2】customRouteLocator 【3】上面 routeDefinitionRouteLocator
	@Primary
	public RouteLocator routeLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	@Bean // 2.6
	public FilteringWebHandler filteringWebHandler(List<GlobalFilter> globalFilters) {
		return new FilteringWebHandler(globalFilters);
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
																	   RouteLocator routeLocator) {
		return new RoutePredicateHandlerMapping(webHandler, routeLocator);
	}

	// ConfigurationProperty beans

	@Bean // 2.7
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean // 3.11 {@link SecureHeadersGatewayFilterFactory}
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	// GlobalFilter beans

	@Bean // 2.1
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean // 2.2
	@ConditionalOnBean(DispatcherHandler.class)
	public ForwardRoutingFilter forwardRoutingFilter(DispatcherHandler dispatcherHandler) {
		return new ForwardRoutingFilter(dispatcherHandler);
	}

	@Bean // 2.3
	public WebSocketService webSocketService() {
		return new HandshakeWebSocketService();
	}

	@Bean // 2.4
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient, WebSocketService webSocketService) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService);
	}

	@Bean // TODO 芋艿，需要确认下原因
	//TODO: default over netty? configurable
	public WebClientHttpRoutingFilter webClientHttpRoutingFilter() {
		//TODO: WebClient bean
		return new WebClientHttpRoutingFilter(WebClient.builder().build());
	}

	@Bean
	public WebClientWriteResponseFilter webClientWriteResponseFilter() {
		return new WebClientWriteResponseFilter();
	}

	// Predicate Factory beans

	@Bean // 3.15
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	@Bean // 3.16
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	@Bean // 3.17
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	@Bean // 3.18
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	@Bean // 3.19
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	@Bean // 3.20
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	@Bean // 3.21
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	@Bean // 3.22
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	@Bean // 3.23
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	@Bean // 3.24
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}

	// GatewayFilter Factory beans

	@Bean // 3.1
	public AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
		return new AddRequestHeaderGatewayFilterFactory();
	}

	@Bean // 3.2
	public AddRequestParameterGatewayFilterFactory addRequestParameterGatewayFilterFactory() {
		return new AddRequestParameterGatewayFilterFactory();
	}

	@Bean // 3.3
	public AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
		return new AddResponseHeaderGatewayFilterFactory();
	}

	@Configuration
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	protected static class HystrixConfiguration {
		@Bean
		public HystrixGatewayFilterFactory hystrixGatewayFilterFactory() {
			return new HystrixGatewayFilterFactory();
		}
	}

	@Bean // 3.4
	public PrefixPathGatewayFilterFactory prefixPathGatewayFilterFactory() {
		return new PrefixPathGatewayFilterFactory();
	}

	@Bean // 3.5
	public RedirectToGatewayFilterFactory redirectToGatewayFilterFactory() {
		return new RedirectToGatewayFilterFactory();
	}

	@Bean // 3.6
	public RemoveNonProxyHeadersGatewayFilterFactory removeNonProxyHeadersGatewayFilterFactory() {
		return new RemoveNonProxyHeadersGatewayFilterFactory();
	}

	@Bean // 3.7
	public RemoveRequestHeaderGatewayFilterFactory removeRequestHeaderGatewayFilterFactory() {
		return new RemoveRequestHeaderGatewayFilterFactory();
	}

	@Bean // 3.8
	public RemoveResponseHeaderGatewayFilterFactory removeResponseHeaderGatewayFilterFactory() {
		return new RemoveResponseHeaderGatewayFilterFactory();
	}

	@Bean(name = PrincipalNameKeyResolver.BEAN_NAME)
	@ConditionalOnBean(RateLimiter.class)
	public PrincipalNameKeyResolver principalNameKeyResolver() {
		return new PrincipalNameKeyResolver();
	}

	@Bean
	@ConditionalOnBean({RateLimiter.class, KeyResolver.class})
	public RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory(RateLimiter rateLimiter, PrincipalNameKeyResolver resolver) {
		return new RequestRateLimiterGatewayFilterFactory(rateLimiter, resolver);
	}

	@Bean // 3.9
	public RewritePathGatewayFilterFactory rewritePathGatewayFilterFactory() {
		return new RewritePathGatewayFilterFactory();
	}

	@Bean // 3.10
	public SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
		return new SetPathGatewayFilterFactory();
	}

	@Bean // 3.12
	public SecureHeadersGatewayFilterFactory secureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties);
	}

	@Bean // 3.13
	public SetResponseHeaderGatewayFilterFactory setResponseHeaderGatewayFilterFactory() {
		return new SetResponseHeaderGatewayFilterFactory();
	}

	@Bean // 3.14
	public SetStatusGatewayFilterFactory setStatusGatewayFilterFactory() {
		return new SetStatusGatewayFilterFactory();
	}

	@ManagementContextConfiguration
	@ConditionalOnProperty(value = "management.gateway.enabled", matchIfMissing = true)
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayWebfluxEndpoint gatewayWebfluxEndpoint(RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
															 List<GatewayFilterFactory> GatewayFilters, RouteDefinitionWriter routeDefinitionWriter,
															 RouteLocator routeLocator) {
			return new GatewayWebfluxEndpoint(routeDefinitionLocator, globalFilters, GatewayFilters, routeDefinitionWriter, routeLocator);
		}
	}

}

