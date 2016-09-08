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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.cloud.stream.test.matcher.MessageQueueMatcher.receivesPayloadThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.annotation.Bindings;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration Tests for the Programmable Reactive Processor.
 *
 * @author Andy Clement
 * @author Mark Fisher
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DirtiesContext
public abstract class ProgrammableReactiveProcessorIntegrationTests {

	@Autowired
	@Bindings(ReactiveTransformer.class)
	protected Processor channels;

	@Autowired
	protected MessageCollector collector;
	
	@SpringBootTest(properties="code=return input -> input.buffer(5).map(list->list.get(4));")
	public static class BasicIntegrationTests extends ProgrammableReactiveProcessorIntegrationTests {
		@Test
		public void testBasic() {
			channels.input().send(new GenericMessage<Object>(100));
			channels.input().send(new GenericMessage<Object>(200));
			channels.input().send(new GenericMessage<Object>(300));
			channels.input().send(new GenericMessage<Object>(400));
			channels.input().send(new GenericMessage<Object>(500));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(500)));
		}
	}
	
	@SpringBootTest(properties="code=return input -> input.map(s->Integer.valueOf(s.toString())).buffer(3).map(is->{int sum=0;for (int i: is) sum+=i; return sum;});")
	public static class SumIntegrationTests extends ProgrammableReactiveProcessorIntegrationTests {
		@Test
		public void testBasic() {
			channels.input().send(new GenericMessage<String>("100"));
			channels.input().send(new GenericMessage<String>("200"));
			channels.input().send(new GenericMessage<String>("300"));
			channels.input().send(new GenericMessage<Integer>(400));
			channels.input().send(new GenericMessage<Integer>(500));
			channels.input().send(new GenericMessage<Integer>(600));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(600)));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(1500)));
		}
	}

	@SpringBootTest(properties="code=class Foo { public int x() { return 4; }} return input -> input.map(s->((Integer)s)*new Foo().x());")
	public static class LocalClassIntegrationTests extends ProgrammableReactiveProcessorIntegrationTests {
		@Test
		public void testBasic() {
			channels.input().send(new GenericMessage<Object>(100));
			channels.input().send(new GenericMessage<Object>(200));
			channels.input().send(new GenericMessage<Object>(300));
			channels.input().send(new GenericMessage<Object>(400));
			channels.input().send(new GenericMessage<Object>(500));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(400)));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(800)));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(1200)));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(1600)));
			assertThat(collector.forChannel(channels.output()), receivesPayloadThat(is(2000)));
		}
	}
	
	// TODO local class

}
