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

import com.liferay.resourcesimporter.util.Resource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Edward C. Han
 */
public class FileSystemResourceImpl extends BaseResourceImpl {

	public FileSystemResourceImpl(File file) {
		_file = file;
	}

	@Override
	public boolean canRead() {
		return _file.canRead();
	}

	@Override
	public String getAbsolutePath() {
		return _file.getAbsolutePath();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		if (!_file.exists() || _file.isDirectory() || !_file.canRead()) {
			return null;
		}

		return new BufferedInputStream(new FileInputStream(_file));
	}

	@Override
	public String getName() {
		return _file.getName();
	}

	@Override
	public String getPath() {
		return _file.getPath();
	}

	@Override
	public boolean isFile() {
		return _file.isFile();
	}

	@Override
	public long length() {
		return _file.length();
	}

	protected Resource[] listResourcesByType(ResourceType type) {
		if (isFile()) {
			return new Resource[0];
		}

		File[] files = _file.listFiles();

		List<Resource> resources = new ArrayList<Resource>(files.length);

		for (File file : files) {
			if (type == BaseResourceImpl.ResourceType.TYPE_ANY ||
				(type == ResourceType.TYPE_FILE && file.isFile()) ||
				(type == ResourceType.TYPE_FOLDER && file.isDirectory())) {

				resources.add(new FileSystemResourceImpl(file));
			}
		}

		return resources.toArray(new Resource[resources.size()]);
	}

	private File _file;
}