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

import java.util.List;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.app.transform.javacompiler.CompilationMessage;
import org.springframework.cloud.stream.app.transform.javacompiler.CompilationResult;
import org.springframework.cloud.stream.app.transform.javacompiler.RuntimeJavaCompiler;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.context.annotation.Bean;

/**
 * A class that can return a ReactiveProcessor bean but compiles code supplied in a property in order
 * to do so.
 * 
 * @author Andy Clement
 * @author Mark Fisher
 */
@EnableBinding(Processor.class)
@EnableConfigurationProperties(ProgrammableReactiveProcessorProperties.class)
public class ReactiveTransformer {

	private static Logger logger = LoggerFactory.getLogger(ReactiveTransformer.class);

	// Newlines in the property are escaped
	private static final String NEWLINE_ESCAPE = Matcher.quoteReplacement("\\n");
	
	// Individual double-quote characters are represented by two double quotes in the DSL
	private static final String DOUBLE_DOUBLE_QUOTE = Matcher.quoteReplacement("\"\"");

	private final static String MAIN_COMPILED_CLASS_NAME = "org.springframework.cloud.stream.app.transform.ReactiveClass";
	
	/**
	 * The user supplied code snippet is inserted into the template and then the result is compiled
	 */
	private static String SOURCE_CODE_TEMPLATE = 
			"package org.springframework.cloud.stream.app.transform;\n"+
			"import java.util.*;\n"+ // Helpful to include this
			"import reactor.core.publisher.Flux;\n" +
			"public class ReactiveClass implements ProcessorFactory {\n"+
			" public ReactiveProcessor<Object,Object> getProcessor() {\n"+
			"  %s\n"+
			" }\n"+
			"}\n";

	@Autowired
	private RuntimeJavaCompiler compiler;
	
	@Autowired
	private ProgrammableReactiveProcessorProperties properties;

	/**
	 * Produce an ReactiveProcessor instance by:<ul>
	 * <li>Decoding the code property to process any newlines/double-double-quotes
	 * <li>Insert the code into the source code template for a class
	 * <li>Compiling the class using the JDK provided Java Compiler
	 * <li>Loading the compiled class
	 * <li>Invoking a well known method on the class to produce a ReactiveProcessor instance
	 * <li>Returning that instance.
	 * </ul>
	 * 
	 * @return a ReactiveProcessor instance
	 */
	@Bean
	public ReactiveProcessor<Object,Object> processor() {
		logger.info("Initial code property value :'{}'",properties.getCode());
 		String code = decode(properties.getCode());
 		if (code.startsWith("\"") && code.endsWith("\"")) {
 			code = code.substring(1,code.length()-1);
 		}
		logger.info("Processed code property value :\n{}\n",code);
		CompilationResult compilationResult = buildAndCompileSourceCode(code);
		if (compilationResult.wasSuccessful()) {
			List<Class<?>> clazzes = compilationResult.getCompiledClasses();
			logger.info("Compilation resulted in this many classes: #{}",clazzes.size());
			for (Class<?> clazz: clazzes) { 
				if (clazz.getName().equals(MAIN_COMPILED_CLASS_NAME)) {
					try {
						ProcessorFactory processorFactory = (ProcessorFactory)clazz.newInstance();
						return processorFactory.getProcessor();
					} catch (Exception e) {
						logger.error("Unexpected problem during retrieval of processor from compiled class",e);
					}
				}
			}
			logger.error("Failed to find the expected compiled class");
		} else {
			List<CompilationMessage> compilationMessages = compilationResult.getCompilationMessages();
			logger.error("Compilation failed");
			for (CompilationMessage compilationMessage: compilationMessages) {
				logger.error("{}",compilationMessage);
			}
		}
		return null;
	} 

	/**
	 * Create the source for and then compile and load a class that embodies
	 * the supplied methodBody. The methodBody is inserted into a class template that
	 * returns a <tt>ReactiveProcessor&lt;Object,Object&gt;</tt>. 
	 * This method can return more than one class if the method body includes local class
	 * declarations. An example methodBody would be <tt>return input -> input.buffer(5).map(list->list.get(0));</tt>.
	 * 
	 * @param methodBody the source code for a method that should return a <tt>ReactiveProcessor&lt;Object,Object&gt;</tt>
	 * @return the list of Classes produced by compiling and then loading the snippet of code
	 */
	private CompilationResult buildAndCompileSourceCode(String methodBody) {
		String sourceCode = makeSourceClassDefinition(methodBody);
		return compiler.compile(MAIN_COMPILED_CLASS_NAME,sourceCode);
	}

	private static String decode(String input) {
		return input.replaceAll(NEWLINE_ESCAPE, "\n").replaceAll(DOUBLE_DOUBLE_QUOTE, "\"");
	}
	
	/**
	 * Make a full source code definition for a class by applying the specified method body
	 * to the Reactive template.
	 * 
	 * @param methodBody the code to insert into the Reactive source class template
	 * @return a complete Java Class definition
	 */
	public static String makeSourceClassDefinition(String methodBody) {
		return String.format(SOURCE_CODE_TEMPLATE, methodBody);
	}
	
}
