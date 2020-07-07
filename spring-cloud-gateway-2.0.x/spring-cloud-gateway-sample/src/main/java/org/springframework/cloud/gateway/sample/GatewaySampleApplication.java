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

package org.springframework.cloud.gateway.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.discovery.DiscoveryClientRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.Routes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Collections;
import java.util.Map;

import static org.springframework.cloud.gateway.filter.factory.GatewayFilters.addResponseHeader;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.host;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicates.path;
import static org.springframework.tuple.TupleBuilder.tuple;

/**
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({Config.class, AdditionalRoutes.class})
//@EnableDiscoveryClient // {@link DiscoveryClientRouteDefinitionLocator}
public class GatewaySampleApplication {

	@Bean
	public RouteLocator customRouteLocator(ThrottleGatewayFilterFactory throttle) {
		//@formatter:off
		return Routes.locator()
                // Route
				.route("test")
					.predicate(host("**.abc.org").and(path("/image/png")))
					.addResponseHeader("X-TestHeader", "foobar")
					.uri("http://httpbin.org:80")
                // Route
				.route("test2")
					.predicate(path("/image/webp"))
					.add(addResponseHeader("X-AnotherHeader", "baz"))
					.uri("http://httpbin.org:80")
                // Route
				.route("test3")
					.order(-1)
					.predicate(host("**.throttle.org").and(path("/get")))
					.add(throttle.apply(tuple().of("capacity", 1,
							"refillTokens", 1,
							"refillPeriod", 10,
							"refillUnit", "SECONDS")))
					.uri("http://httpbin.org:80")
				.build();
		////@formatter:on
	}

    @RestController
    public static class TestConfig {

        @RequestMapping("/localcontroller")
        public Map<String, String> localController() {
            return Collections.singletonMap("from", "localcontroller");
        }
    }

//	@Bean
//    @Lazy(value = false)
//	public EurekaDiscoveryClient discoveryClient() {
////        EurekaDiscoveryClientConfiguration
//        System.out.println("!");
////        return null;
//	    return new EurekaDiscoveryClient(null, null);
//    }

//	@Bean
	public RouteDefinitionLocator discoveryClientRouteDefinitionLocator(DiscoveryClient discoveryClient) {
	    return new DiscoveryClientRouteDefinitionLocator(discoveryClient);
    }

	@Bean
	public ThrottleGatewayFilterFactory throttleWebFilterFactory() {
		return new ThrottleGatewayFilterFactory();
	}

	@Bean
	public RouterFunction<ServerResponse> testFunRouterFunction() {
		RouterFunction<ServerResponse> route = RouterFunctions.route(
				RequestPredicates.path("/testfun"),
				request -> ServerResponse.ok().body(BodyInserters.fromObject("hello")));
		return route;
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}
}
