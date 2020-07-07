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

package org.springframework.cloud.gateway.filter.factory;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import rx.Observable;
import rx.RxReactiveStreams;
import rx.Subscription;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static com.netflix.hystrix.exception.HystrixRuntimeException.FailureType.TIMEOUT;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * @author Spencer Gibb
 */
public class HystrixGatewayFilterFactory implements GatewayFilterFactory {

	@Override
	public List<String> argNames() {
		return Arrays.asList(NAME_KEY);
	}

	@Override
	public GatewayFilter apply(Tuple args) {
		//TODO: if no name is supplied, generate one from command id (useful for default filter)
		final String commandName = args.getString(NAME_KEY);
		final HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey(getClass().getSimpleName());
		final HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey(commandName);

		final HystrixObservableCommand.Setter setter = HystrixObservableCommand.Setter
				.withGroupKey(groupKey)
				.andCommandKey(commandKey);

		return (exchange, chain) -> {
			RouteHystrixCommand command = new RouteHystrixCommand(setter, exchange, chain);

			return Mono.create(s -> {
			    // 使用 Hystrix Command Observable 订阅
				Subscription sub = command.toObservable().subscribe(s::success, s::error, s::success);
				// Mono 取消时，取消 Hystrix Command Observable 的订阅，结束 Hystrix Command 的执行
				s.onCancel(sub::unsubscribe);
			}).onErrorResume((Function<Throwable, Mono<Void>>) throwable -> {
				if (throwable instanceof HystrixRuntimeException) {
					HystrixRuntimeException e = (HystrixRuntimeException) throwable;
					if (e.getFailureType() == TIMEOUT) { //TODO: optionally set status
						setResponseStatus(exchange, HttpStatus.GATEWAY_TIMEOUT);
						return exchange.getResponse().setComplete();
					}
				}
				return Mono.empty();
			}).then();
		};
	}

	//TODO: replace with HystrixMonoCommand that we write
	private class RouteHystrixCommand extends HystrixObservableCommand<Void> {
		private final ServerWebExchange exchange;
		private final GatewayFilterChain chain;

		RouteHystrixCommand(Setter setter, ServerWebExchange exchange, GatewayFilterChain chain) {
			super(setter);
			this.exchange = exchange;
			this.chain = chain;
		}

		@Override
		protected Observable<Void> construct() {
			return RxReactiveStreams.toObservable(this.chain.filter(this.exchange));
		}
	}
}
