package org.springframework.cloud.gateway.filter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

public class ForwardRoutingFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ForwardRoutingFilter.class);

	private final DispatcherHandler dispatcherHandler;

	public ForwardRoutingFilter(DispatcherHandler dispatcherHandler) {
		this.dispatcherHandler = dispatcherHandler;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
	    // 获得 requestUrl
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		// 判断是否能够处理
		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || !scheme.equals("forward")) {
			return chain.filter(exchange);
		}

		// 设置已经路由
		setAlreadyRouted(exchange);

		//TODO: translate url?

		if (log.isTraceEnabled()) {
			log.trace("Forwarding to URI: "+requestUrl);
		}

		// DispatcherHandler 匹配并转发到当前网关实例本地接口
		return this.dispatcherHandler.handle(exchange);
	}
}
