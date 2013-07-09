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

/**
 * @author Edward C. Han
 */
public abstract class BaseResourceImpl implements Resource {

	@Override
	public Resource[] listFileResources() {
		return listResourcesByType(ResourceType.TYPE_FILE);
	}

	@Override
	public Resource[] listFolderResources() {
		return listResourcesByType(ResourceType.TYPE_FOLDER);
	}

	@Override
	public Resource[] listResources() {
		return listResourcesByType(ResourceType.TYPE_ANY);
	}

	protected abstract Resource[] listResourcesByType(ResourceType type);

	protected enum ResourceType {
		TYPE_ANY, TYPE_FILE, TYPE_FOLDER
	}
}