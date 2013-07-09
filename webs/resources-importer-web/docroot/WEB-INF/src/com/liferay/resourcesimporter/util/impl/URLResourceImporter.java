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
import com.liferay.resourcesimporter.util.ResourceImporter;

/**
 * @author Raymond Augé
 * @author Ryan Park
 */
public class URLResourceImporter extends ResourceImporter {

	@Override
	public void importResources() throws Exception {
		super.doImportResources();
	}

	@Override
	protected Resource getResource(String filePath) throws Exception {
		return new URLResourceImpl(resourcesDir + filePath, servletContext);
	}
}