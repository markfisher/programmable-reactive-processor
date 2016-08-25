/*
 * Copyright 2016 the original author or authors.
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
 */
package org.springframework.cloud.stream.app.transform;

import java.util.function.Function;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;

import reactor.core.publisher.Flux;

/**
 * @author Mark Fisher
 */
public interface ReactiveProcessor<I, O> extends Function<Flux<Object>, Flux<Object>> {

	@StreamListener
	@Output(Processor.OUTPUT)
	default Flux<Object> process(@Input(Processor.INPUT) Flux<Object> input) {
		return this.apply(input);
	}
}
