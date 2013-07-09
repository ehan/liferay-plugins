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

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.template.TemplateConstants;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.xml.Attribute;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutConstants;
import com.liferay.portal.model.LayoutSetPrototype;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.LayoutTypePortletConstants;
import com.liferay.portal.model.PortletConstants;
import com.liferay.portal.model.Theme;
import com.liferay.portal.model.User;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.LayoutSetLocalServiceUtil;
import com.liferay.portal.service.LayoutSetPrototypeLocalServiceUtil;
import com.liferay.portal.service.RepositoryLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ThemeLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portlet.asset.model.AssetTag;
import com.liferay.portlet.asset.service.AssetTagLocalServiceUtil;
import com.liferay.portlet.blogs.service.BlogsEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.model.DLFolder;
import com.liferay.portlet.documentlibrary.model.DLFolderConstants;
import com.liferay.portlet.documentlibrary.service.DLAppLocalServiceUtil;
import com.liferay.portlet.documentlibrary.service.DLFolderLocalServiceUtil;
import com.liferay.portlet.documentlibrary.util.DLUtil;
import com.liferay.portlet.dynamicdatamapping.model.DDMStructure;
import com.liferay.portlet.dynamicdatamapping.model.DDMStructureConstants;
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplate;
import com.liferay.portlet.dynamicdatamapping.model.DDMTemplateConstants;
import com.liferay.portlet.dynamicdatamapping.service.DDMStructureLocalServiceUtil;
import com.liferay.portlet.dynamicdatamapping.service.DDMTemplateLocalServiceUtil;
import com.liferay.portlet.journal.model.JournalArticle;
import com.liferay.portlet.journal.model.JournalArticleConstants;
import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil;
import com.liferay.portlet.journal.util.JournalConverterUtil;
import com.liferay.portlet.messageboards.service.MBCategoryLocalServiceUtil;
import com.liferay.portlet.wiki.model.WikiNode;
import com.liferay.portlet.wiki.service.WikiNodeLocalServiceUtil;
import com.liferay.portlet.wiki.service.WikiPageLocalServiceUtil;
import com.liferay.resourcesimporter.util.messageboard.MessageBoardImporterHandler;
import com.liferay.resourcesimporter.util.messageboard.MessageBoardXMLStreamReader;

import java.io.InputStream;
import java.io.Serializable;

import java.text.DateFormat;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.portlet.PortletPreferences;

/**
 * @author Ryan Park
 * @author Raymond Augé
 */
public abstract class ResourceImporter extends BaseImporter {

	@Override
	public void importResources() throws Exception {
		_resourcesDir = getResource(resourcesDir);

		if (_resourcesDir.isFile() || !_resourcesDir.canRead()) {
			throw new IllegalArgumentException(
				"Unaccessible resource directory " + resourcesDir);
		}

		doImportResources();
	}

	protected void addBlogEntry(
			String blogsRelativeFileName, InputStream contentInputStream,
			InputStream blogMetaDataInputStream, String smallImageFileName,
			InputStream smallImageInputStream)
		throws Exception {

		String decodedFileName = FileUtil.decodeSafeFileName(
			blogsRelativeFileName);
		String blogsEntryShortFileName = FileUtil.getShortFileName(
			decodedFileName);
		String[] pathSegments = decodedFileName.split(StringPool.SLASH);

		String title =
			blogsEntryShortFileName.substring(
				0, blogsEntryShortFileName.lastIndexOf("."));

		long userId = super.userId;
		Calendar calendar = GregorianCalendar.getInstance();

		String description = "";
		boolean allowPingbacks = false;
		String smallImageURL = "";
		boolean smallImage = false;
		boolean allowTrackbacks = false;
		String[] trackbacks = null;

		User user = null;
		String userName = null;

		if (blogMetaDataInputStream != null) {
			String blogMetaDataJSON = StringUtil.read(blogMetaDataInputStream);
			JSONObject blogMetaData = JSONFactoryUtil.createJSONObject(
				blogMetaDataJSON);

			if (blogMetaData != null) {
				userName = blogMetaData.getString("userScreenName");
				description = blogMetaData.getString("description");
				String displayDate = blogMetaData.getString("displayDate");
				DateFormat format = DateUtil.getISOFormat(displayDate);
				Date date = format.parse(displayDate);
				calendar.setTime(date);

				allowPingbacks = blogMetaData.getBoolean("allowPingBacks");
				trackbacks = new String[] {
					blogMetaData.getString("trackbacks")
				};
				smallImageURL = blogMetaData.getString("smallImageURL");
				smallImageFileName = blogMetaData.getString(
					"smallImageFileName");
				smallImage = blogMetaData.getBoolean("smallImage");
				smallImageInputStream = null;
				allowTrackbacks = blogMetaData.getBoolean("allowTrackbacks");

				if (Validator.isNotNull(userName)) {
					user =
						UserLocalServiceUtil.fetchUserByScreenName(
							companyId, userName);
				}
			}
		}

		if (Validator.isNull(userName) || user == null) {
			userName = pathSegments[0];
			user = UserLocalServiceUtil.fetchUserByScreenName(
				companyId, userName);
		}

		if (user != null) {
			userId = user.getUserId();
		}

		int displayDateMonth = calendar.get(GregorianCalendar.MONTH);
		int displayDateDay = calendar.get(GregorianCalendar.DAY_OF_MONTH);
		int displayDateYear = calendar.get(GregorianCalendar.YEAR);
		int displayDateHour = calendar.get(GregorianCalendar.HOUR);
		int displayDateMinute = calendar.get(GregorianCalendar.MINUTE);

		String content = StringUtil.read(contentInputStream);
		content = replaceFileEntryURL(content);

		BlogsEntryLocalServiceUtil.addEntry(
			userId, title, description, content, displayDateMonth, displayDateDay,
			displayDateYear, displayDateHour, displayDateMinute, allowPingbacks,
			allowTrackbacks, trackbacks, smallImage, smallImageURL,
			smallImageFileName, smallImageInputStream, _serviceContext);
	}

	protected void addBlogs(String blogsDirectoryPath) throws Exception {
		Resource blogsBaseDirectory = getResource(blogsDirectoryPath);
		Resource[] blogDirectories = blogsBaseDirectory.listResources();

		if (blogDirectories != null) {
			for (Resource userBlogDirectory : blogDirectories) {
				Resource[] userBlogEntries = userBlogDirectory.listResources();

				if (userBlogEntries != null) {
					for (Resource userBlogEntryFile : userBlogEntries) {
						String blogEntryFileName =
							userBlogEntryFile.getAbsolutePath();

						InputStream contentInputStream = null;
						InputStream blogMetaDataInputStream = null;

						try {
							if (blogEntryFileName.endsWith(".html")) {
									contentInputStream =
										userBlogEntryFile.getInputStream();

								if (contentInputStream != null) {
									String userBlogBaseFileName =
										blogEntryFileName.substring(
											resourcesDir.length(),
											blogEntryFileName.length() - 5);

									int blogPathLength =
										blogsBaseDirectory.getPath().length() +
											1;
									String relativeBlogEntryFileName =
										blogEntryFileName.substring(
											blogPathLength);

									String blogMetaDataFileName =
										userBlogBaseFileName + ".json";
									Resource blogMetaDataFile = getResource(
										blogMetaDataFileName);

									if (blogMetaDataFile != null) {
										blogMetaDataInputStream =
											blogMetaDataFile.getInputStream();
									}

									addBlogEntry(
										relativeBlogEntryFileName,
										contentInputStream,
										blogMetaDataInputStream,
										StringPool.BLANK, null);
								}
							}
						}
						finally {
							if (contentInputStream != null) {
								contentInputStream.close();
							}

							if (blogMetaDataInputStream != null) {
								blogMetaDataInputStream.close();
							}
						}
					}
				}
			}
		}
	}

	protected void addDDMStructures(
			String parentDDMStructureKey, String structuresDirName)
		throws Exception {

		Resource journalStructuresResource = getResource(structuresDirName);

		if (journalStructuresResource.isFile() ||
			!journalStructuresResource.canRead()) {

			return;
		}

		Resource[] resources = journalStructuresResource.listFileResources();

		if (resources != null) {
			for (Resource resource : resources) {
				InputStream inputStream = null;

				try {
					inputStream = resource.getInputStream();

					addDDMStructures(
						parentDDMStructureKey, resource.getName(), inputStream);
				}
				finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}
			}
		}
	}

	protected void addDDMStructures(
			String parentDDMStructureKey, String fileName,
			InputStream inputStream)
		throws Exception {

		String ddmStructureKey = getJournalId(fileName);

		String name = FileUtil.stripExtension(fileName);

		Map<Locale, String> nameMap = getMap(name);

		String xsd = StringUtil.read(inputStream);

		if (isJournalStructureXSD(xsd)) {
			xsd = JournalConverterUtil.getDDMXSD(xsd);
		}

		setServiceContext(fileName);

		DDMStructure ddmStructure = DDMStructureLocalServiceUtil.addStructure(
			userId, groupId, parentDDMStructureKey,
			PortalUtil.getClassNameId(JournalArticle.class), ddmStructureKey,
			nameMap, null, xsd,
			PropsUtil.get(PropsKeys.JOURNAL_ARTICLE_STORAGE_TYPE),
			DDMStructureConstants.TYPE_DEFAULT, _serviceContext);

		addDDMTemplates(
			ddmStructure.getStructureKey(), _JOURNAL_DDM_TEMPLATES_DIR_NAME +
				name + "/");

		if (Validator.isNull(parentDDMStructureKey)) {
			addDDMStructures(
				ddmStructure.getStructureKey(),
				_JOURNAL_DDM_STRUCTURES_DIR_NAME + name + "/");
		}
	}

	protected void addDDMTemplates(
			String ddmStructureKey, String templatesDirName)
		throws Exception {

		Resource journalTemplatesDir = getResource(templatesDirName);

		if (journalTemplatesDir.isFile() || !journalTemplatesDir.canRead()) {
			return;
		}

		Resource[] files = journalTemplatesDir.listFileResources();

		if (files != null) {
			for (Resource file : files) {
				InputStream inputStream = null;

				try {
					inputStream = file.getInputStream();

					addDDMTemplates(
						ddmStructureKey, file.getName(), inputStream);
				}
				finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}
			}
		}
	}

	protected void addDDMTemplates(
			String ddmStructureKey, String fileName, InputStream inputStream)
		throws Exception {

		String ddmTemplateKey = getJournalId(fileName);

		String name = FileUtil.stripExtension(fileName);

		Map<Locale, String> nameMap = getMap(name);

		String language = getDDMTemplateLanguage(fileName);

		String xsl = StringUtil.read(inputStream);

		xsl = replaceFileEntryURL(xsl);

		setServiceContext(fileName);

		DDMStructure ddmStructure = DDMStructureLocalServiceUtil.getStructure(
			groupId, PortalUtil.getClassNameId(JournalArticle.class),
			ddmStructureKey);

		DDMTemplate ddmTemplate = DDMTemplateLocalServiceUtil.addTemplate(
			userId, groupId, PortalUtil.getClassNameId(DDMStructure.class),
			ddmStructure.getStructureId(), ddmTemplateKey, nameMap, null,
			DDMTemplateConstants.TEMPLATE_TYPE_DISPLAY, null, language, xsl,
			false, false, null, null, _serviceContext);

		addJournalArticles(
			ddmStructureKey, ddmTemplate.getTemplateKey(),
			_JOURNAL_ARTICLES_DIR_NAME + name + "/");
	}

	protected void addDLFileEntries(String fileEntriesDirName)
		throws Exception {

		Resource dlDocumentsDir = getResource(fileEntriesDirName);

		if (dlDocumentsDir.isFile() || !dlDocumentsDir.canRead()) {
			return;
		}

		Resource[] resources = dlDocumentsDir.listResources();

		if (Validator.isNull(resources)) {
			return;
		}

		for (Resource resource : resources) {
			if (resource.isFile()) {
				addDLFileEntry(
					DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, resource);
			}
			else {
				addDLFolder(
					DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, resource);
			}
		}
	}

	protected void addDLFileEntry(long parentFolderId, Resource resource)
		throws Exception {

		InputStream inputStream = null;

		try {
			inputStream = resource.getInputStream();
			long fileSize = resource.length();

			addDLFileEntry(
				parentFolderId, resource.getName(), inputStream, fileSize);
		}
		finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	protected void addDLFileEntry(
		long parentFolderId, String fileName, InputStream inputStream,
		long length)
		throws Exception {

		setServiceContext(fileName);

		FileEntry fileEntry = DLAppLocalServiceUtil.addFileEntry(
			userId, groupId, parentFolderId, fileName,
			MimeTypesUtil.getContentType(fileName),
			FileUtil.stripExtension(fileName), StringPool.BLANK,
			StringPool.BLANK, inputStream, length, _serviceContext);

		_fileEntries.put(fileName, fileEntry);
	}

	protected long addDLFolder(long parentFolderId, Resource folder)
		throws Exception {

		long folderId = addDLFolder(parentFolderId, folder.getName());

		Resource[] resources = folder.listResources();

		if (Validator.isNull(resources)) {
			return folderId;
		}

		for (Resource resource : resources) {
			if (resource.isFile()) {
				addDLFileEntry(folderId, resource);
			}
			else {
				addDLFolder(folderId, resource);
			}
		}

		return folderId;
	}

	protected long addDLFolder(long parentFolderId, String folderName)
		throws Exception {

		DLFolder dlFolder = DLFolderLocalServiceUtil.fetchFolder(
				groupId, parentFolderId, folderName);

		if (dlFolder == null) {
			dlFolder = DLFolderLocalServiceUtil.addFolder(
				userId, groupId, groupId, false, parentFolderId, folderName,
				null, false, _serviceContext);
		}

		return dlFolder.getFolderId();
	}

	protected void addJournalArticles(
			String ddmStructureKey, String ddmTemplateKey,
			String articlesDirName)
		throws Exception {

		Resource journalArticlesDir = getResource(articlesDirName);

		if (journalArticlesDir.isFile() || !journalArticlesDir.canRead()) {
			return;
		}

		Resource[] resources = journalArticlesDir.listFileResources();

		if (resources != null) {
			for (Resource resource : resources) {
				InputStream inputStream = null;

				try {
					inputStream = resource.getInputStream();

					addJournalArticles(
						ddmStructureKey, ddmTemplateKey, resource.getName(),
						inputStream);
				}
				finally {
					if (inputStream != null) {
						inputStream.close();
					}
				}
			}
		}
	}

	protected void addJournalArticles(
			String ddmStructureKey, String ddmTemplateKey, String fileName,
			InputStream inputStream)
		throws Exception {

		String journalArticleId = getJournalId(fileName);

		String title = FileUtil.stripExtension(fileName);

		JSONObject assetJSONObject = _assetJSONObjectMap.get(fileName);

		Map<Locale, String> descriptionMap = null;

		if (assetJSONObject != null) {
			String abstractSummary = assetJSONObject.getString(
				"abstractSummary");

			descriptionMap = getMap(abstractSummary);
		}

		String content = StringUtil.read(inputStream);

		content = processJournalArticleContent(content);

		Locale articleDefaultLocale = LocaleUtil.fromLanguageId(
			LocalizationUtil.getDefaultLocale(content));

		Map<Locale, String> titleMap = getMap(articleDefaultLocale, title);

		boolean smallImage = false;
		String smallImageURL = StringPool.BLANK;

		if (assetJSONObject != null) {
			String smallImageFileName = assetJSONObject.getString("smallImage");

			if (Validator.isNotNull(smallImageFileName)) {
				smallImage = true;

				FileEntry fileEntry = _fileEntries.get(smallImageFileName);

				if (fileEntry != null) {
					smallImageURL = DLUtil.getPreviewURL(
						fileEntry, fileEntry.getFileVersion(), null,
						StringPool.BLANK);
				}
			}
		}

		setServiceContext(fileName);

		JournalArticle journalArticle =
			JournalArticleLocalServiceUtil.addArticle(
				userId, groupId, 0, 0, 0, journalArticleId, false,
				JournalArticleConstants.VERSION_DEFAULT, titleMap,
				descriptionMap, content, "general", ddmStructureKey,
				ddmTemplateKey, StringPool.BLANK, 1, 1, 2010, 0, 0, 0, 0, 0, 0,
				0, true, 0, 0, 0, 0, 0, true, true, smallImage, smallImageURL,
				null, new HashMap<String, byte[]>(), StringPool.BLANK,
				_serviceContext);

		JournalArticleLocalServiceUtil.updateStatus(
			userId, groupId, journalArticle.getArticleId(),
			journalArticle.getVersion(), WorkflowConstants.STATUS_APPROVED,
			StringPool.BLANK, new HashMap<String, Serializable>(),
			_serviceContext);
	}

	protected void addLayout(
			boolean privateLayout, long parentLayoutId,
			JSONObject layoutJSONObject)
		throws Exception {

		if (targetClassName.equals(LayoutSetPrototype.class.getName())) {
			privateLayout = true;
		}

		Map<Locale, String> nameMap = new HashMap<Locale, String>();

		JSONObject nameMapJSONObject = layoutJSONObject.getJSONObject(
			"nameMap");

		if (nameMapJSONObject != null) {
			nameMap = (Map<Locale, String>)LocalizationUtil.deserialize(
				nameMapJSONObject);
		}
		else {
			String name = layoutJSONObject.getString("name");

			nameMap.put(LocaleUtil.getDefault(), name);
		}

		Map<Locale, String> titleMap = new HashMap<Locale, String>();

		JSONObject titleMapJSONObject = layoutJSONObject.getJSONObject(
			"nameMap");

		if (titleMapJSONObject != null) {
			titleMap = (Map<Locale, String>)LocalizationUtil.deserialize(
				titleMapJSONObject);
		}
		else {
			String title = layoutJSONObject.getString("title");

			titleMap.put(LocaleUtil.getDefault(), title);
		}

		boolean hidden = layoutJSONObject.getBoolean("hidden");

		Map<Locale, String> friendlyURLMap = new HashMap<Locale, String>();

		String friendlyURL = layoutJSONObject.getString("friendlyURL");

		if (Validator.isNotNull(friendlyURL) &&
			!friendlyURL.startsWith(StringPool.SLASH)) {

			friendlyURL = StringPool.SLASH + friendlyURL;
		}

		friendlyURLMap.put(LocaleUtil.getDefault(), friendlyURL);

		String typeSettings = layoutJSONObject.getString("typeSettings");

		Layout layout = LayoutLocalServiceUtil.addLayout(
			userId, groupId, privateLayout, parentLayoutId, nameMap, titleMap,
			null, null, null, LayoutConstants.TYPE_PORTLET, typeSettings,
			hidden, friendlyURLMap, _serviceContext);

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)layout.getLayoutType();

		String layoutTemplateId = layoutJSONObject.getString(
			"layoutTemplateId", _defaultLayoutTemplateId);

		if (Validator.isNotNull(layoutTemplateId)) {
			layoutTypePortlet.setLayoutTemplateId(
				userId, layoutTemplateId, false);
		}

		JSONArray columnsJSONArray = layoutJSONObject.getJSONArray("columns");

		addLayoutColumns(
			layout, LayoutTypePortletConstants.COLUMN_PREFIX, columnsJSONArray);

		LayoutLocalServiceUtil.updateLayout(
			groupId, layout.isPrivateLayout(), layout.getLayoutId(),
			layout.getTypeSettings());

		JSONArray layoutsJSONArray = layoutJSONObject.getJSONArray("layouts");

		addLayouts(privateLayout, layout.getLayoutId(), layoutsJSONArray);
	}

	protected void addLayoutColumn(
			Layout layout, String columnId, JSONArray columnJSONArray)
		throws Exception {

		if (columnJSONArray == null) {
			return;
		}

		for (int i = 0; i < columnJSONArray.length(); i++) {
			JSONObject portletJSONObject = columnJSONArray.getJSONObject(i);

			if (portletJSONObject == null) {
				String journalArticleId = getJournalId(
					columnJSONArray.getString(i));

				portletJSONObject = getDefaultPortletJSONObject(
					journalArticleId);
			}

			addLayoutColumnPortlet(layout, columnId, portletJSONObject);
		}
	}

	protected void addLayoutColumnPortlet(
			Layout layout, String columnId, JSONObject portletJSONObject)
		throws Exception {

		LayoutTypePortlet layoutTypePortlet =
			(LayoutTypePortlet)layout.getLayoutType();

		String rootPortletId = portletJSONObject.getString("portletId");

		if (Validator.isNull(rootPortletId)) {
			throw new ImporterException("portletId is not specified");
		}

		String portletId = layoutTypePortlet.addPortletId(
			userId, rootPortletId, columnId, -1, false);

		JSONObject portletPreferencesJSONObject =
			portletJSONObject.getJSONObject("portletPreferences");

		if ((portletPreferencesJSONObject == null) ||
			(portletPreferencesJSONObject.length() == 0)) {

			return;
		}

		PortletPreferences portletSetup =
			PortletPreferencesFactoryUtil.getLayoutPortletSetup(
				layout, portletId);

		Iterator<String> iterator = portletPreferencesJSONObject.keys();

		while (iterator.hasNext()) {
			String key = iterator.next();

			String value = portletPreferencesJSONObject.getString(key);

			if (rootPortletId.equals(PortletKeys.JOURNAL_CONTENT) &&
				key.equals("articleId")) {

				value = getJournalId(value);
			}

			portletSetup.setValue(key, value);
		}

		portletSetup.store();

		if (rootPortletId.equals(PortletKeys.NESTED_PORTLETS)) {
			JSONArray columnsJSONArray =
				portletPreferencesJSONObject.getJSONArray("columns");

			StringBundler sb = new StringBundler(4);

			sb.append(StringPool.UNDERLINE);
			sb.append(portletId);
			sb.append(StringPool.DOUBLE_UNDERLINE);
			sb.append(LayoutTypePortletConstants.COLUMN_PREFIX);

			addLayoutColumns(layout, sb.toString(), columnsJSONArray);
		}
	}

	protected void addLayoutColumns(
			Layout layout, String columnPrefix, JSONArray columnsJSONArray)
		throws Exception {

		if (columnsJSONArray == null) {
			return;
		}

		for (int i = 0; i < columnsJSONArray.length(); i++) {
			JSONArray columnJSONArray = columnsJSONArray.getJSONArray(i);

			addLayoutColumn(layout, columnPrefix + (i + 1), columnJSONArray);
		}
	}

	protected void addLayouts(
			boolean privateLayout, long parentLayoutId,
			JSONArray layoutsJSONArray)
		throws Exception {

		if (layoutsJSONArray == null) {
			return;
		}

		for (int i = 0; i < layoutsJSONArray.length(); i++) {
			JSONObject layoutJSONObject = layoutsJSONArray.getJSONObject(i);

			addLayout(privateLayout, parentLayoutId, layoutJSONObject);
		}
	}

	protected void addMessageBoards(String messageBoardsDirName)
		throws Exception {

		InputStream messageBoardFileIS = null;
		MessageBoardXMLStreamReader reader = null;
		try {
			Resource messageBoardDirectory = getResource(messageBoardsDirName);
			if (messageBoardDirectory != null) {
				Resource[] messageBoardFiles =
					messageBoardDirectory.listFileResources();

				if (messageBoardFiles != null) {
					for (Resource messageBoardFile : messageBoardFiles) {
						messageBoardFileIS = messageBoardFile.getInputStream();
						reader =
							new MessageBoardXMLStreamReader(messageBoardFileIS);
						MessageBoardImporterHandler xmlHandler =
							new MessageBoardImporterHandler(_serviceContext);
						reader.setMessageBoardHandler(xmlHandler);
						reader.read();
					}
				}
			}
		}
		finally {
			if (messageBoardFileIS != null) {
				messageBoardFileIS.close();
			}

			if (reader != null) {
				reader.close();
			}
		}
	}

	protected void addWiki(String wikiDirectoryName) throws Exception {
		Resource wikiDirectory = getResource(_WIKI_DIR_NAME);

		Resource[] wikiNodeDirectories = wikiDirectory.listResources();

		if (wikiNodeDirectories == null) {
			return;
		}

		for (Resource wikiNodeDirectory : wikiNodeDirectories) {
			String wikiNodeName = wikiNodeDirectory.getName();
			Resource[] wikiContentResources = wikiNodeDirectory.listResources();

			for (Resource resource : wikiContentResources) {
				String fileName = resource.getName();
				WikiNode wikiNode = WikiNodeLocalServiceUtil.fetchNode(
					groupId, wikiNodeName);

				if (wikiNode == null) {
					wikiNode =
						WikiNodeLocalServiceUtil.addNode(
							userId, wikiNodeName, "", _serviceContext);
				}

				long nodeId = wikiNode.getNodeId();
				String titleAndFormat = fileName.substring(
					0, fileName.lastIndexOf('.'));
				int formatIndex = titleAndFormat.lastIndexOf('.');
				String title = titleAndFormat.substring(0, formatIndex);
				String wikiFormat = titleAndFormat.substring(formatIndex + 1);
				String summary = null;
				boolean minorEdit = false;
				InputStream contentInputStream = null;

				try {
					contentInputStream = resource.getInputStream();
					String content = StringUtil.read(contentInputStream);
					WikiPageLocalServiceUtil.addPage(
						userId, nodeId, title, 1.0, content, summary, minorEdit,
						wikiFormat, true, null, null, _serviceContext);
				}
				finally {
					if (contentInputStream != null) {
						contentInputStream.close();
					}
				}
			}
		}
	}

	protected void doImportResources() throws Exception {
		_serviceContext = new ServiceContext();

		_serviceContext.setAddGroupPermissions(true);
		_serviceContext.setAddGuestPermissions(true);
		_serviceContext.setUserId(userId);
		_serviceContext.setCompanyId(companyId);
		_serviceContext.setScopeGroupId(groupId);

		setupAssets("assets.json");
		setupSettings("settings.json");
		setupSitemap("sitemap.json");
	}

	protected String getDDMTemplateLanguage(String fileName) {
		String extension = FileUtil.getExtension(fileName);

		if (extension.equals(TemplateConstants.LANG_TYPE_CSS) ||
			extension.equals(TemplateConstants.LANG_TYPE_FTL) ||
			extension.equals(TemplateConstants.LANG_TYPE_VM) ||
			extension.equals(TemplateConstants.LANG_TYPE_XSL)) {

			return extension;
		}

		return TemplateConstants.LANG_TYPE_VM;
	}

	protected JSONObject getDefaultPortletJSONObject(String journalArticleId) {
		JSONObject portletJSONObject = JSONFactoryUtil.createJSONObject();

		portletJSONObject.put("portletId", PortletKeys.JOURNAL_CONTENT);

		JSONObject portletPreferencesJSONObject =
			JSONFactoryUtil.createJSONObject();

		portletPreferencesJSONObject.put("articleId", journalArticleId);
		portletPreferencesJSONObject.put("groupId", groupId);
		portletPreferencesJSONObject.put("portletSetupShowBorders", false);

		portletJSONObject.put(
			"portletPreferences", portletPreferencesJSONObject);

		return portletJSONObject;
	}

	protected String getJournalId(String fileName) {
		String id = FileUtil.stripExtension(fileName);

		id = id.toUpperCase();

		return StringUtil.replace(id, StringPool.SPACE, StringPool.DASH);
	}

	protected String[] getJSONArrayAsStringArray(
		JSONObject jsonObject, String key) {

		JSONArray jsonArray = jsonObject.getJSONArray(key);

		if (jsonArray != null) {
			return ArrayUtil.toStringArray(jsonArray);
		}

		return new String[0];
	}

	protected JSONObject getJSONObject(String fileName) throws Exception {
		String json = null;

		Resource resource = getResource(fileName);
		InputStream inputStream = resource.getInputStream();

		if (inputStream == null) {
			return null;
		}

		try {
			json = StringUtil.read(inputStream);
		}
		finally {
			inputStream.close();
		}

		json = StringUtil.replace(
			json, new String[] {"${companyId}", "${groupId}", "${userId}"},
			new String[] {
				String.valueOf(companyId), String.valueOf(groupId),
				String.valueOf(userId)
			});

		return JSONFactoryUtil.createJSONObject(json);
	}

	protected Map<Locale, String> getMap(String value) {
		Locale locale = LocaleUtil.getDefault();

		return getMap(locale, value);
	}

	protected Map<Locale, String> getMap(Locale locale, String value) {
		Map<Locale, String> nameMap = new HashMap<Locale, String>();

		nameMap.put(locale, value);

		return nameMap;
	}
	
	protected abstract Resource getResource(String filePath) throws Exception;

	protected boolean isJournalStructureXSD(String xsd) throws Exception {
		Document document = SAXReaderUtil.read(xsd);

		Element rootElement = document.getRootElement();

		Attribute availableLocalesAttribute = rootElement.attribute(
			"available-locales");

		if (availableLocalesAttribute == null) {
			return true;
		}

		return false;
	}

	protected String processJournalArticleContent(String content)
		throws Exception {

		content = replaceFileEntryURL(content);

		if (content.contains("<?xml version=\"1.0\"")) {
			return content;
		}

		StringBundler sb = new StringBundler(13);

		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		sb.append("<root available-locales=\"");
		sb.append(LocaleUtil.getDefault());
		sb.append("\" default-locale=\"");
		sb.append(LocaleUtil.getDefault());
		sb.append("\">");
		sb.append("<static-content language-id=\"");
		sb.append(LocaleUtil.getDefault());
		sb.append("\">");
		sb.append("<![CDATA[");
		sb.append(content);
		sb.append("]]>");
		sb.append("</static-content></root>");

		return sb.toString();
	}

	protected String replaceFileEntryURL(String content) throws Exception {
		Matcher matcher = _fileEntryPattern.matcher(content);

		while (matcher.find()) {
			String fileName = matcher.group(1);

			FileEntry fileEntry = _fileEntries.get(fileName);

			String fileEntryURL = StringPool.BLANK;

			if (fileEntry != null) {
				fileEntryURL = DLUtil.getPreviewURL(
					fileEntry, fileEntry.getFileVersion(), null,
					StringPool.BLANK);
			}

			content = matcher.replaceFirst(fileEntryURL);

			matcher.reset(content);
		}

		return content;
	}

	protected void setServiceContext(String name) {
		JSONObject assetJSONObject = _assetJSONObjectMap.get(name);

		String[] assetTagNames = null;

		if (assetJSONObject != null) {
			assetTagNames = getJSONArrayAsStringArray(assetJSONObject, "tags");
		}

		_serviceContext.setAssetTagNames(assetTagNames);
	}

	protected void setupAssets(JSONArray assetsJSONArray) {
		if (assetsJSONArray == null) {
			return;
		}

		for (int i = 0; i < assetsJSONArray.length(); i++) {
			JSONObject assetJSONObject = assetsJSONArray.getJSONObject(i);

			String name = assetJSONObject.getString("name");

			_assetJSONObjectMap.put(name, assetJSONObject);
		}
	}

	protected void setupAssets(String fileName) throws Exception {
		List<AssetTag> assetTags = AssetTagLocalServiceUtil.getGroupTags(
			groupId);

		for (AssetTag assetTag : assetTags) {
			AssetTagLocalServiceUtil.deleteAssetTag(assetTag);
		}

		RepositoryLocalServiceUtil.deleteRepositories(groupId);

		JournalArticleLocalServiceUtil.deleteArticles(groupId);

		DDMTemplateLocalServiceUtil.deleteTemplates(groupId);

		DDMStructureLocalServiceUtil.deleteStructures(groupId);

		BlogsEntryLocalServiceUtil.deleteEntries(groupId);

		MBCategoryLocalServiceUtil.deleteCategories(groupId);

		WikiNodeLocalServiceUtil.deleteNodes(groupId);

		JSONObject jsonObject = getJSONObject(fileName);

		if (jsonObject != null) {
			JSONArray assetsJSONArray = jsonObject.getJSONArray("assets");

			setupAssets(assetsJSONArray);
		}

		addDLFileEntries(_DL_DOCUMENTS_DIR_NAME);

		addJournalArticles(
			StringPool.BLANK, StringPool.BLANK, _JOURNAL_ARTICLES_DIR_NAME);

		addDDMStructures(StringPool.BLANK, _JOURNAL_DDM_STRUCTURES_DIR_NAME);

		addDDMTemplates(StringPool.BLANK, _JOURNAL_DDM_TEMPLATES_DIR_NAME);

		addBlogs(_BLOGS_DIR_NAME);

		addMessageBoards(_MESSAGE_BOARDS_DIR_NAME);

		addWiki(_WIKI_DIR_NAME);
	}

	protected void setupSettings(String fileName) throws Exception {
		if (targetClassName.equals(Group.class.getName())) {
			return;
		}

		JSONObject jsonObject = getJSONObject(fileName);

		if (jsonObject == null) {
			return;
		}

		LayoutSetPrototype layoutSetPrototype =
			LayoutSetPrototypeLocalServiceUtil.getLayoutSetPrototype(
				getTargetClassPK());

		String layoutSetPrototypeSettings = jsonObject.getString(
			"layoutSetPrototypeSettings", StringPool.BLANK);

		layoutSetPrototype.setSettings(layoutSetPrototypeSettings);

		LayoutSetPrototypeLocalServiceUtil.updateLayoutSetPrototype(
			layoutSetPrototype);
	}

	protected void setupSitemap(String fileName) throws Exception {
		LayoutLocalServiceUtil.deleteLayouts(
			groupId, true, new ServiceContext());

		LayoutLocalServiceUtil.deleteLayouts(
			groupId, false, new ServiceContext());

		JSONObject jsonObject = getJSONObject(fileName);

		if (jsonObject == null) {
			return;
		}

		_defaultLayoutTemplateId = jsonObject.getString(
			"layoutTemplateId", StringPool.BLANK);

		updateLayoutSetThemeId(jsonObject);

		JSONArray layoutsJSONArray = jsonObject.getJSONArray("layouts");

		if (layoutsJSONArray != null) {
			addLayouts(
				false, LayoutConstants.DEFAULT_PARENT_LAYOUT_ID,
				layoutsJSONArray);
		}
		else {
			JSONArray publicPagesJSONArray = jsonObject.getJSONArray(
				"publicPages");

			if (publicPagesJSONArray != null) {
				addLayouts(
					false, LayoutConstants.DEFAULT_PARENT_LAYOUT_ID,
					publicPagesJSONArray);
			}

			JSONArray privatePagesJSONArray = jsonObject.getJSONArray(
				"privatePages");

			if (privatePagesJSONArray != null) {
				addLayouts(
					true, LayoutConstants.DEFAULT_PARENT_LAYOUT_ID,
					privatePagesJSONArray);
			}
		}
	}

	protected void updateLayoutSetThemeId(JSONObject sitemapJSONObject)
		throws Exception {

		String themeId = sitemapJSONObject.getString("themeId");

		if (Validator.isNotNull(themeId)) {
			Theme theme = ThemeLocalServiceUtil.fetchTheme(companyId, themeId);

			if (theme == null) {
				themeId = null;
			}
		}

		if (Validator.isNull(themeId)) {
			int pos = servletContextName.indexOf("-theme");

			if (pos != -1) {
				themeId =
					servletContextName.substring(0, pos) +
						PortletConstants.WAR_SEPARATOR + servletContextName;

				themeId = PortalUtil.getJsSafePortletId(themeId);

				Theme theme = ThemeLocalServiceUtil.fetchTheme(
					companyId, themeId);

				if (theme == null) {
					themeId = null;
				}
			}
		}

		if (Validator.isNotNull(themeId)) {
			LayoutSetLocalServiceUtil.updateLookAndFeel(
				groupId, themeId, null, null, false);
		}
	}

	private static final String _BLOGS_DIR_NAME = "/blogs/";

	private static final String _DL_DOCUMENTS_DIR_NAME =
		"/document_library/documents/";

	private static final String _JOURNAL_ARTICLES_DIR_NAME =
		"/journal/articles/";

	private static final String _JOURNAL_DDM_STRUCTURES_DIR_NAME =
		"/journal/structures/";

	private static final String _JOURNAL_DDM_TEMPLATES_DIR_NAME =
		"/journal/templates/";

	private static final String _MESSAGE_BOARDS_DIR_NAME = "/message_boards/";

	private static final String _WIKI_DIR_NAME = "/wiki/";

	private Map<String, JSONObject> _assetJSONObjectMap =
		new HashMap<String, JSONObject>();
	private String _defaultLayoutTemplateId;
	private Map<String, FileEntry> _fileEntries =
		new HashMap<String, FileEntry>();
	private Pattern _fileEntryPattern = Pattern.compile(
		"\\[\\$FILE=([^\\$]+)\\$\\]");
	private Resource _resourcesDir;
	private ServiceContext _serviceContext;

}