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

package com.liferay.resourcesimporter.util.impl;

import com.liferay.portal.kernel.util.CharPool;
import com.liferay.resourcesimporter.util.Resource;

import java.io.InputStream;

import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;

/**
 * @author Edward C. Han
 */
public class URLResourceImpl extends BaseResourceImpl {

	public URLResourceImpl(
			String absoluteResourcePath, ServletContext servletContext) {

		_resourcePath = absoluteResourcePath;
		_servletContext = servletContext;

		int indexOfLastSlash = absoluteResourcePath.lastIndexOf(CharPool.SLASH);

		_isFile = indexOfLastSlash != (absoluteResourcePath.length() - 1);

		if (_isFile) {
			_name = absoluteResourcePath.substring(indexOfLastSlash + 1);

			try {
				URL url = _servletContext.getResource(absoluteResourcePath);

				if (url != null) {
					URLConnection urlConnection = url.openConnection();
					String contentLengthHeader = urlConnection.getHeaderField(
						"content-length");
					_length = Long.parseLong(contentLengthHeader);
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		else {
			String noLastSlash = absoluteResourcePath.substring(
				0, indexOfLastSlash);

			int indexOfSlash = noLastSlash.lastIndexOf(CharPool.SLASH);

			_name = noLastSlash.substring(indexOfSlash + 1);
		}
	}

	@Override
	public boolean canRead() {
		return true;
	}

	@Override
	public String getAbsolutePath() {
		return _resourcePath;
	}

	@Override
	public InputStream getInputStream() {
		InputStream inputStream = _servletContext.getResourceAsStream(
			_resourcePath);

		return inputStream;
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public String getPath() {
		return _resourcePath;
	}

	@Override
	public boolean isFile() {
		return _isFile;
	}

	@Override
	public long length() {
		return _length;
	}

	@Override
	protected Resource[] listResourcesByType(ResourceType type) {
		if (isFile()) {
			return new Resource[0];
		}

		Set<String> resourcePaths = _servletContext.getResourcePaths(
				_resourcePath);

		if (resourcePaths == null) {
			return null;
		}

		List<Resource> resources = new ArrayList<Resource>(
			resourcePaths.size());

		for (String resourcePath : resourcePaths) {
			Resource resource = new URLResourceImpl(
				resourcePath, _servletContext);

			if (type == ResourceType.TYPE_ANY ||
				(type == ResourceType.TYPE_FILE && resource.isFile()) ||
				(type == ResourceType.TYPE_FOLDER && !resource.isFile())) {

				resources.add(resource);
			}
		}

		return resources.toArray(new Resource[resources.size()]);
	}

	private boolean _isFile;
	private long _length;
	private String _name;
	private String _resourcePath;
	private ServletContext _servletContext;
}