package org.springframework.cloud.gateway.filter.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class PrincipalNameKeyResolver implements KeyResolver {

	public static final String BEAN_NAME = "principalNameKeyResolver";

	@Override
	public Mono<String> resolve(ServerWebExchange exchange) {
//		return exchange.getPrincipal().map(Principal::getName).switchIfEmpty(Mono.empty()); // TODO 临时注释
//		return  Mono.just(exchange.getRequest().getQueryParams().getFirst("user")).defaultIfEmpty("123");
        return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
	}
}
