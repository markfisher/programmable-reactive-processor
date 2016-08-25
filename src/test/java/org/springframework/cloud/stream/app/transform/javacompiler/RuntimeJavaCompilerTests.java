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
package org.springframework.cloud.stream.app.transform.javacompiler;

import static org.junit.Assert.assertEquals;

import java.io.PrintStream;
import java.lang.reflect.Method;

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.cloud.stream.app.transform.ProcessorFactory;
import org.springframework.cloud.stream.app.transform.ReactiveProcessor;
import org.springframework.cloud.stream.app.transform.ReactiveTransformer;

import reactor.core.publisher.Flux;

/**
 * @author Andy Clement
 * @author Mark Fisher
 */
public class RuntimeJavaCompilerTests {
		
	@Test
	public void basicCompile() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("a.b.c.Foo",
				"package a.b.c;\n"+
				"public class Foo {\n"+
				"  public static void main(String[] argv) {\n"+
				"    System.out.println(\"hello world\");\n"+
				"  }\n"+
				"}");
		System.out.println(cr);
		Assert.assertTrue(cr.wasSuccessful());
		String output = captureOutputDuringRunOfMainMethod(cr.getCompiledClasses().get(0));
		Assert.assertEquals("hello world\n",output);
	}
	
	@Test
	public void compileError() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		String source = 
				"package a.b.c;\n"+
				"public class Foo {\n"+
				"  public static void main(Strin[] argv) {\n"+
				"    System.out.println(\"hello world\");\n"+
				"  }\n"+
				"}";
		CompilationResult cr = rjc.compile("a.b.c.Foo",source);
		Assert.assertFalse(cr.wasSuccessful());
		CompilationMessage compilationMessage = cr.getCompilationMessages().get(0);
		Assert.assertEquals(
				"==========\n"+
				"  public static void main(Strin[] argv) {\n"+
                "                          ^^^^^\n"+
                "ERROR:cannot find symbol\n"+
                "  symbol:   class Strin\n"+
                "  location: class a.b.c.Foo\n"+
                "==========\n", compilationMessage.toString());
		assertEquals(60,compilationMessage.getStartPosition());
		assertEquals(65,compilationMessage.getEndPosition());
		assertEquals(source,compilationMessage.getSourceCode());
		assertEquals("cannot find symbol\n"+
					 "  symbol:   class Strin\n"+
					 "  location: class a.b.c.Foo",compilationMessage.getMessage());
		assertEquals(CompilationMessage.Kind.ERROR,compilationMessage.getKind());
	}
	
	@Test
	public void compileErrorWithTabs() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		CompilationResult cr = rjc.compile("a.b.c.Foo",
				"package a.b.c;\n"+
				"public class Foo {\n"+
				"  	public 		static void 	main(Strin[] argv) {\n"+
				"    System.out.println(\"hello world\");\n"+
				"  }\n"+
				"}");
		Assert.assertFalse(cr.wasSuccessful());
		Assert.assertEquals(
				"==========\n"+
				"  	public 		static void 	main(Strin[] argv) {\n"+
                "  	       		            	     ^^^^^\n"+
                "ERROR:cannot find symbol\n"+
                "  symbol:   class Strin\n"+
                "  location: class a.b.c.Foo\n"+
                "==========\n", cr.getCompilationMessages().get(0).toString());
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void realTemplate() throws Exception {
		RuntimeJavaCompiler rjc = new RuntimeJavaCompiler();
		// Buffer values in groups of 6 and add them up
		String insert = "return input -> input.map(s->Integer.valueOf((String)s)).buffer(6).map(is->{int sum=0;for (int i: is) sum+=i; return sum;});";
		String source = ReactiveTransformer.makeSourceClassDefinition(insert);
		CompilationResult cr = rjc.compile("org.springframework.cloud.stream.module.transform.ReactiveClass", source );
		if (!cr.wasSuccessful()) {
			Assert.fail("Compilation does not appear to have worked:\n"+cr.toString());
		}
		ReactiveProcessor rp = invokeGetProcessor(cr.getCompiledClasses().get(0));
		Flux<String> strings = Flux.just(new String[]{"2","4","6","8","10","12"});
		Flux output = rp.process(strings);
		Object resultElement = output.blockFirst();
		Assert.assertEquals(42, resultElement); // Should be the total of the inputs
	}
	
	// ---
	
	private ReactiveProcessor<?,?> invokeGetProcessor(Class<?> clazz) throws Exception {
		Object o = clazz.newInstance();
		if (!(o instanceof ProcessorFactory)) {
			Assert.fail("Expected instance of class "+clazz.getName()+" to be instance of ProcessorFactory");
		}
		ProcessorFactory processorFactory = (ProcessorFactory)o;
		return processorFactory.getProcessor();
	}

	/**
	 * Capture standard out whilst running the main method of the specified class
	 * and return that captured output.
	 * 
	 * @param clazz the class on which to invoke <tt>main(String[])</tt>
	 * @return stdout whilst the method was running
	 */
	private String captureOutputDuringRunOfMainMethod(Class<?> clazz) {
		PrintStream oldOut = System.out;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			System.setOut(new PrintStream(baos));
			Method m = clazz.getDeclaredMethod("main",String[].class);
			m.invoke(null,(Object)null);
			System.out.flush();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			Assert.fail("Failed to run main method: "+e.getMessage());
		} finally {
			System.setOut(oldOut);;
		}
		return new String(baos.toByteArray());
	}
	
}
