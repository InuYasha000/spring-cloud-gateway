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

package org.springframework.cloud.gateway.actuate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Spencer Gibb
 */
@RestController
@RequestMapping("${management.context-path:/application}/gateway")
public class GatewayWebfluxEndpoint implements ApplicationEventPublisherAware {

	private static final Log log = LogFactory.getLog(GatewayWebfluxEndpoint.class);

    /**
     * 路由定义定位器
     */
	private RouteDefinitionLocator routeDefinitionLocator;
    /**
     * 全局过滤器
     */
	private List<GlobalFilter> globalFilters;
    /**
     * 网关过滤器工厂
     */
	private List<GatewayFilterFactory> gatewayFilters;
    /**
     * 存储器 RouteDefinitionLocator 对象
     */
	private RouteDefinitionWriter routeDefinitionWriter;
    /**
     * 路由定位器
     */
	private RouteLocator routeLocator;
    /**
     * 应用事件发布器
     */
	private ApplicationEventPublisher publisher;

	public GatewayWebfluxEndpoint(RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
								  List<GatewayFilterFactory> GatewayFilters, RouteDefinitionWriter routeDefinitionWriter,
								  RouteLocator routeLocator) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.globalFilters = globalFilters;
		this.gatewayFilters = GatewayFilters;
		this.routeDefinitionWriter = routeDefinitionWriter;
		this.routeLocator = routeLocator;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	// TODO: Add uncommited or new but not active routes endpoint

	//TODO: this should really be a listener that responds to a RefreshEvent
	@PostMapping("/refresh")
	public Mono<Void> refresh() {
	    this.publisher.publishEvent(new RefreshRoutesEvent(this));
		return Mono.empty();
	}

	@GetMapping("/globalfilters")
	public Mono<HashMap<String, Object>> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	@GetMapping("/routefilters")
	public Mono<HashMap<String, Object>> routefilers() {
		return getNamesToOrders(this.gatewayFilters);
	}

	private <T> Mono<HashMap<String, Object>> getNamesToOrders(List<T> list) {
		return Flux.fromIterable(list).reduce(new HashMap<>(), this::putItem);
	}

	private HashMap<String, Object> putItem(HashMap<String, Object> map, Object o) {
		Integer order = null;
		if (o instanceof Ordered) {
			order = ((Ordered)o).getOrder();
		}
		//filters.put(o.getClass().getName(), order);
		map.put(o.toString(), order);
		return map;
	}

	// TODO: Add support for RouteLocator
	@GetMapping("/routes")
	public Mono<Map<String, List>> routes() {
		Mono<List<RouteDefinition>> routeDefs = this.routeDefinitionLocator.getRouteDefinitions().collectList();
		Mono<List<Route>> routes = this.routeLocator.getRoutes().collectList();
		return Mono.zip(routeDefs, routes).map(tuple -> {
			Map<String, List> allRoutes = new HashMap<>();
			allRoutes.put("routeDefinitions", tuple.getT1());
			allRoutes.put("routes", tuple.getT2());
			return allRoutes;
		});
	}

/*
http POST :8080/admin/gateway/routes/apiaddreqhead uri=http://httpbin.org:80 predicates:='["Host=**.apiaddrequestheader.org", "Path=/headers"]' filters:='["AddRequestHeader=X-Request-ApiFoo, ApiBar"]'
*/
	@PostMapping("/routes/{id}")
	@SuppressWarnings("unchecked")
	public Mono<ResponseEntity<Void>> save(@PathVariable String id, @RequestBody Mono<RouteDefinition> route) {
		return this.routeDefinitionWriter.save(route.map(r ->  { // 设置 ID
			r.setId(id);
			log.debug("Saving route: " + route);
			return r;
		})).then(Mono.defer(() -> // status ：201 ，创建成功。参见 HTTP 规范 ：https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/201
			Mono.just(ResponseEntity.created(URI.create("/routes/"+id)).build())
		));
	}

	@DeleteMapping("/routes/{id}")
	public Mono<ResponseEntity<Object>> delete(@PathVariable String id) {
		return this.routeDefinitionWriter.delete(Mono.just(id))
				.then(Mono.defer(() -> Mono.just(ResponseEntity.ok().build()))) // 删除成功
				.onErrorResume(t -> t instanceof NotFoundException, t -> Mono.just(ResponseEntity.notFound().build())); // 删除失败
	}

	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<RouteDefinition>> route(@PathVariable String id) {
		//TODO: missing RouteLocator
		return this.routeDefinitionLocator.getRouteDefinitions()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty()
				.map(route -> ResponseEntity.ok(route))
				.switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}

	@GetMapping("/routes/{id}/combinedfilters")
	public Mono<HashMap<String, Object>> combinedfilters(@PathVariable String id) {
		//TODO: missing global filters
		return this.routeLocator.getRoutes()
				.filter(route -> route.getId().equals(id))
				.reduce(new HashMap<>(), this::putItem);
	}
}
