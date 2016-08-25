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

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Very simple classloader that can be used to load the compiled types.
 * 
 * @author Andy Clement
 */
public class SimpleClassLoader extends URLClassLoader {

	private static final URL[] NO_URLS = new URL[0];

	public SimpleClassLoader(ClassLoader classLoader) {
		super(NO_URLS, classLoader);
	}

	public Class<?> defineClass(String name, byte[] bytes) {
		return super.defineClass(name, bytes, 0, bytes.length);
	}
}