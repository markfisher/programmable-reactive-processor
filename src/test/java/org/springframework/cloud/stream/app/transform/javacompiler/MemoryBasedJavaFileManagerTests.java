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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * 
 * @author Andy Clement
 */
public class MemoryBasedJavaFileManagerTests {
	
	static String ThisClassFilename;

	static {
		ThisClassFilename = MemoryBasedJavaFileManagerTests.class.getName().replace('.', '/')+".class";
	}
	
	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	MemoryBasedJavaFileManager jfm;
	
	@Before
	public void setup() {
		jfm = new MemoryBasedJavaFileManager();
	}
	
	@After
	public void teardown() throws Exception {
		jfm.close();
	}
	
	@Test
	public void memoryBasedJavaFileManager() throws Exception {
		assertEquals(-1,jfm.isSupportedOption("foo"));
		for (StandardLocation location: StandardLocation.values()) {
			assertNull(jfm.getClassLoader(location));
		}
		assertEquals(0,jfm.getCompiledClasses().size());
	}
	
	@Test
	public void basicListing() throws Exception {
		assertTrue(jfm.hasLocation(StandardLocation.CLASS_PATH));
		assertTrue(jfm.hasLocation(StandardLocation.PLATFORM_CLASS_PATH));
		assertTrue(jfm.hasLocation(StandardLocation.SOURCE_PATH));
		assertFalse(jfm.hasLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH));
		
		Iterable<JavaFileObject> iterable = jfm.list(StandardLocation.CLASS_PATH, null, null, true);
		JavaFileObject jfo = find(iterable.iterator(),ThisClassFilename);
		assertNotNull(jfo);
		
		iterable = jfm.list(StandardLocation.PLATFORM_CLASS_PATH, null, null, true);
		jfo = find(iterable.iterator(),ThisClassFilename);
		assertNull(jfo);
		
		iterable = jfm.list(StandardLocation.SOURCE_PATH, null, null, true);
		Iterator<JavaFileObject> jfoIterator = iterable.iterator();
		assertFalse(jfoIterator.hasNext());
		
		iterable = jfm.list(StandardLocation.ANNOTATION_PROCESSOR_PATH, null, null, true);
		jfoIterator = iterable.iterator();
		assertFalse(jfoIterator.hasNext());
	}
	
	@Test
	public void filteredListing() throws Exception {
		Iterable<JavaFileObject> iterable = jfm.list(StandardLocation.CLASS_PATH, null, null, true);
		assertNotNull(find(iterable.iterator(),ThisClassFilename));
		iterable = jfm.list(StandardLocation.CLASS_PATH, "org", null, true);
		assertNotNull(find(iterable.iterator(),ThisClassFilename));
		iterable = jfm.list(StandardLocation.CLASS_PATH, "org", null, false);
		assertNull(find(iterable.iterator(),ThisClassFilename));
		iterable = jfm.list(StandardLocation.CLASS_PATH, "org", Collections.singleton(Kind.SOURCE), true);
		assertNull(find(iterable.iterator(),ThisClassFilename));
		iterable = jfm.list(StandardLocation.CLASS_PATH, "org", Collections.singleton(Kind.CLASS), true);
		assertNotNull(find(iterable.iterator(),ThisClassFilename));		
		iterable = jfm.list(StandardLocation.CLASS_PATH, "org",new HashSet<Kind>(Arrays.asList(Kind.SOURCE,Kind.CLASS)), true);
		assertNotNull(find(iterable.iterator(),ThisClassFilename));		
	}
	
	@Test
	public void inferBinaryName() throws Exception {
		Iterable<JavaFileObject> iterable = jfm.list(StandardLocation.CLASS_PATH, null, null, true);
		JavaFileObject jfo = find(iterable.iterator(),ThisClassFilename);
		assertNotNull(jfo);
		assertEquals(MemoryBasedJavaFileManagerTests.class.getName(),jfm.inferBinaryName(StandardLocation.CLASS_PATH, jfo));
		assertNull(jfm.inferBinaryName(StandardLocation.SOURCE_PATH, jfo));
	}
	
	@Test
	public void handleOption() throws Exception {
		assertFalse(jfm.handleOption("foo", Arrays.asList("a","b","c").iterator()));
		assertFalse(jfm.handleOption(null, null));
	}
	
	@Test
	public void getFileForInputOperations() throws Exception {
		try {
			jfm.getFileForInput(StandardLocation.SOURCE_PATH, "com.foo.bar", "META-INF/foo.properties");
			fail();
		} catch (IllegalStateException ise) {
			// success - not required in current use case
		}
		try {
			jfm.getJavaFileForInput(StandardLocation.SOURCE_PATH, "Foo", Kind.CLASS);
			fail();
		} catch (IllegalStateException ise) {
			// success - not required in current use case
		}
	}
	
	@Test
	public void compilationOutputCollector() throws Exception {
		CompilationOutputCollector collector = new CompilationOutputCollector();
		assertEquals(0,collector.getCompiledClasses().size());

		// Simulate a compile.
		// The compiler will pass a FooSource to the compiler and the compiler will ask
		// the CompilationOutputCollector (through the MemoryBasedJavaFileManager) for a place to put the
		// output.
		JavaFileObject FooSource = InMemoryJavaFileObject.getSourceJavaFileObject("Foo.java", "public class Foo {}");
		InMemoryJavaFileObject fo1 = collector.getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, "Foo", Kind.CLASS, FooSource);
		try (Writer w = fo1.openWriter()) {
			w.write("hello world");
		}
		JavaFileObject BarSource = InMemoryJavaFileObject.getSourceJavaFileObject("Bar.java", "public class Bar {}");
		InMemoryJavaFileObject fo2 = collector.getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, "Bar", Kind.CLASS, BarSource);
		try (Writer w = fo2.openWriter()) {
			w.write("goodbye world");
		}
		
		List<CompiledClassDefinition> compiledClasses = collector.getCompiledClasses();
		assertEquals(2,compiledClasses.size());
		CompiledClassDefinition ccd = compiledClasses.get(0);
		assertEquals("Foo",ccd.getClassName());
		assertEquals("hello world",new String(ccd.getBytes()));
		ccd = compiledClasses.get(1);
		assertEquals("Bar",ccd.getClassName());
		assertEquals("goodbye world",new String(ccd.getBytes()));

		// This can be called when the annotation config processor runs, producing this JSON file for spring projects:
		InMemoryJavaFileObject fo3 = collector.getFileForOutput(StandardLocation.CLASS_OUTPUT, "", "META-INF/spring-configuration-metadata.json", null);
		try (Writer w = fo3.openWriter()) {
			w.write("testing1");
		}
		InMemoryJavaFileObject fo4 = collector.getFileForOutput(StandardLocation.CLASS_OUTPUT, "abc.def", "META-INF/spring-configuration-metadata.json", null);
		try (Writer w = fo3.openWriter()) {
			w.write("testing2");
		}
		assertEquals("file:/META-INF/spring-configuration-metadata.json",fo3.toUri().toString());
		assertEquals("file:/abc/def/META-INF/spring-configuration-metadata.json",fo4.toUri().toString());
		
		// fo3/fo4 are not class files, they should not change the result here:
		assertEquals(2,collector.getCompiledClasses().size());
	}
	
	@Test
	public void InMemoryJavaFileObject() throws Exception {
		CompilationOutputCollector collector = new CompilationOutputCollector();
		JavaFileObject FooSource = InMemoryJavaFileObject.getSourceJavaFileObject("Foo.java", "public class Foo {}");
		InMemoryJavaFileObject jfo = collector.getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, "Foo", Kind.CLASS, FooSource);
		
		try (InputStream is = jfo.openInputStream()) {
			assertEquals("",IterableClasspathTests.readContent(is));
			fail("Shouldn't be anything there yet");
		} catch (FileNotFoundException fnfe) {
			// expected
		}
		
		assertEquals(0,jfo.getLastModified());

		try (OutputStream os = jfo.openOutputStream()) {
			os.write("test".getBytes());
		}
		
		try (InputStream is = jfo.openInputStream()) {
			assertEquals("test",IterableClasspathTests.readContent(is));
		}
		
		assertEquals("file:/Foo.class",jfo.toUri().toString());
		assertEquals("/Foo.class",jfo.toUri().getPath());
		
		assertEquals("test",new String(jfo.getBytes()));
		assertNotEquals(0,jfo.getLastModified());

		try (Writer w = jfo.openWriter()) {
			w.write("hello world");
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		
		try (InputStream is = jfo.openInputStream()) {
			assertEquals("hello world",IterableClasspathTests.readContent(is));
		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
			fail();
		}

		try (Reader r = jfo.openReader(true)) {
			assertEquals("hello world",IterableClasspathTests.readContent(r));
		} catch (FileNotFoundException fnfe) {
			fnfe.printStackTrace();
			fail();
		}

		assertNull(jfo.getNestingKind()); // null indicates unknown
		assertNull(jfo.getAccessLevel()); // null indicates unknown
		assertFalse(jfo.delete());
		assertEquals(JavaFileObject.Kind.CLASS,jfo.getKind());
		long lmt = jfo.getLastModified();
		if (lmt<=0) {
			fail("Expected a real last modified time, not: "+lmt);
		}
		try {
			jfo.getCharContent(true);
			fail();
		} catch (UnsupportedOperationException uoe) {
			// success, not allowed on CLASS
		}
		try {
			assertEquals("public class Foo {}",FooSource.getCharContent(true));
		} catch (UnsupportedOperationException uoe) {
			fail();
		}
		assertEquals("OutputJavaFileObject: Location=CLASS_OUTPUT,className=Foo,kind=CLASS,relativeName=null,sibling=OutputJavaFileObject: Location=SOURCE_PATH,className=Foo.java,kind=SOURCE,relativeName=null,sibling=null,packageName=null,packageName=null",jfo.toString());
	}

	@Test
	public void equals() throws Exception {
		Iterable<JavaFileObject> iterable = jfm.list(StandardLocation.CLASS_PATH, null, null, true);
		JavaFileObject jfo = find(iterable.iterator(),ThisClassFilename);
		assertNotNull(jfo);
		JavaFileObject jfo2 = find(iterable.iterator(),ThisClassFilename);
		assertNotNull(jfo2);
		assertTrue(jfm.isSameFile(jfo,jfo2));
	}

	// ---
	
	private JavaFileObject find(Iterator<JavaFileObject> iterator, String lookingFor) {
		while (iterator.hasNext()) {
			JavaFileObject jfo = iterator.next();
			// Really must find ourselves
			if (jfo.getName().equals(lookingFor)) {
				return jfo;
			}
		}
		return null;
	}
}
