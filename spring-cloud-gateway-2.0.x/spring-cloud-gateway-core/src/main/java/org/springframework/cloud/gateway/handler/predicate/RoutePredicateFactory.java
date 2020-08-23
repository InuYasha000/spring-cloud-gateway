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

import org.springframework.cloud.gateway.support.ArgumentHints;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

import java.util.function.Predicate;

/**
 * @author Spencer Gibb
 */
//RoutePredicateFactory 是所有 predicate factory 的顶级接口，职责就是生产 Predicate
@FunctionalInterface//函数接口
public interface RoutePredicateFactory extends ArgumentHints {

    String PATTERN_KEY = "pattern";

    //创建一个用于配置用途的对象（config），以其作为参数应用到 apply方法上来生产一个 Predicate 对象，再将 Predicate 对象包装成 AsyncPredicate。
	Predicate<ServerWebExchange> apply(Tuple args);

	default String name() {
		return NameUtils.normalizePredicateName(getClass());
	}

}
