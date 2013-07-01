/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.resourcesimporter.util;

import java.io.File;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;
public class ServletContextResourceFile extends File {

	public ServletContextResourceFile(
		ServletContext servletContext, String pathname) {

		super(pathname);
		this.path = pathname;
		this.servletContext = servletContext;

		if (pathname.endsWith("/")) {
			directory = true;
		}
	}

	@Override
	public String getAbsolutePath() {
		return this.path;
	}
	
	@Override
	public boolean canRead() {

		return true;
	}

	public boolean exists() {

		boolean exists = false;
		try {
			URL url = servletContext.getResource(path);
			exists = url != null;
		}
		catch (MalformedURLException e) {
			exists = false;
		}

		return exists;
	}

	@Override
	public boolean isDirectory() {

		return directory;
	}

	@Override
	public boolean isFile() {

		return !directory;
	}

	@Override
	public File[] listFiles() {

		List<File> files = new ArrayList<File>();
		Set<String> resourcePaths = servletContext.getResourcePaths(this.path);

		if (resourcePaths == null) {
			return null;
		}

		for (String resourcePath : resourcePaths) {
			ServletContextResourceFile file = new ServletContextResourceFile(
				servletContext, resourcePath);
			files.add(file);
		}

		return files.toArray(new File[0]);
	}

	@Override
	public String toString() {

		return path;
	}

	private boolean directory = false;
	private String path = null;
	private ServletContext servletContext = null;
}