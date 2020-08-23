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

package org.springframework.cloud.gateway.handler.predicate;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
/**
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *       - id: after_route
 *         uri: http://example.org
 *         predicates:
 *         - After=2017-01-20T17:42:47.789-07:00[America/Denver] # ①
 *
 * PredicateDefinition {
 *     name='After',
 *     args={_genkey_0=2017-01-20T17:42:47.789-07:00[America/Denver]}
 * }
 */
public class AfterRoutePredicateFactory implements RoutePredicateFactory {

	public static final String DATETIME_KEY = "datetime";

	@Override
	public List<String> argNames() {
		return Collections.singletonList(DATETIME_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		Object value = args.getValue(DATETIME_KEY);
		final ZonedDateTime dateTime = BetweenRoutePredicateFactory.getZonedDateTime(value);

		return exchange -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isAfter(dateTime);
		};
	}

}
