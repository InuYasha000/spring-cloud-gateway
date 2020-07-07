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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

/**
 * @author Spencer Gibb
 */
public class NettyRoutingFilter implements GlobalFilter, Ordered {

	private final HttpClient httpClient;

	public NettyRoutingFilter(HttpClient httpClient) {
		this.httpClient = httpClient;
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
		if (isAlreadyRouted(exchange) || (!scheme.equals("http") && !scheme.equals("https"))) {
			return chain.filter(exchange);
		}

        // 设置已经路由
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

        // Request Method
		final HttpMethod method = HttpMethod.valueOf(request.getMethod().toString());

		// 获得 url
		final String url = requestUrl.toString();

		// Request Header
		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		request.getHeaders().forEach(httpHeaders::set);

		// 请求
		return this.httpClient.request(method, url, req -> {
			final HttpClientRequest proxyRequest = req.options(NettyPipeline.SendOptions::flushOnEach) // 【】
					.failOnClientError(false) // 是否请求失败，抛出异常
					.headers(httpHeaders);

			// Request Form
			if (MediaType.APPLICATION_FORM_URLENCODED.includes(request.getHeaders().getContentType())) {
				return exchange.getFormData()
						.flatMap(map -> proxyRequest.sendForm(form -> {
							for (Map.Entry<String, List<String>> entry: map.entrySet()) {
								for (String value : entry.getValue()) {
									form.attr(entry.getKey(), value);
								}
							}
						}).then())
						.then(chain.filter(exchange));
			}

			// Request Body
			return proxyRequest.sendHeaders() //I shouldn't need this
					.send(request.getBody()
							.map(DataBuffer::asByteBuffer) // Flux<DataBuffer> => ByteBuffer
							.map(Unpooled::wrappedBuffer)); // ByteBuffer => Flux<DataBuffer>
		}).doOnNext(res -> {
			ServerHttpResponse response = exchange.getResponse();
			// Response Header
			// put headers and status so filters can modify the response
			HttpHeaders headers = new HttpHeaders();
			res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
			response.getHeaders().putAll(headers);

			// Response Status
			response.setStatusCode(HttpStatus.valueOf(res.status().code()));

			// 设置 Response 到 CLIENT_RESPONSE_ATTR
			// Defer committing the response until all route filters have run
			// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
			exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
		}).then(chain.filter(exchange));
	}
}
