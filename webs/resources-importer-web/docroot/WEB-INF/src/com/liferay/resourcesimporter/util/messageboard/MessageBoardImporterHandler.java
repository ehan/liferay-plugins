
package com.liferay.resourcesimporter.util.messageboard;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.bean.BeanPropertiesUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.ObjectValuePair;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.model.User;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portlet.messageboards.model.MBCategory;
import com.liferay.portlet.messageboards.model.MBCategoryConstants;
import com.liferay.portlet.messageboards.model.MBMessage;
import com.liferay.portlet.messageboards.model.MBMessageConstants;
import com.liferay.portlet.messageboards.model.MBThread;
import com.liferay.portlet.messageboards.service.MBCategoryLocalServiceUtil;
import com.liferay.portlet.messageboards.service.MBMessageLocalServiceUtil;
import com.liferay.resourcesimporter.util.messageboard.MessageBoardXMLStreamReader.MessageBoardObjectHandler;

import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
public class MessageBoardImporterHandler extends MessageBoardObjectHandler {

	public MessageBoardImporterHandler(ServiceContext serviceContext) {

		this.serviceContext = serviceContext;
	}

	@Override
	protected void endObject(
		String modelClassName, List<Map<String, String>> pathElements,
		Map<String, String> currentModelObject)
		throws Exception {

		if (MBMessage.class.getName().equals(modelClassName)) {
			String body = currentModelObject.get("body").trim();
			MBMessage lastProcessedMessage = getLastProcessedMessage();
			try {
				lastProcessedMessage.setBody(body);
				MBMessageLocalServiceUtil.updateMessage(
					lastProcessedMessage.getMessageId(), body);
			}
			catch (Exception e) {
				System.err.println("Could not update body for :" +
					lastProcessedMessage.getCategory().getName() + ":" +
					lastProcessedMessage.getSubject());
				e.printStackTrace();
			}

			currentMBMessages.remove(lastProcessedMessage);
		}
	}

	@Override
	protected void startObject(
		String modelClassName, List<Map<String, String>> currentPathElements,
		Map<String, String> attributesMap)
		throws Exception {

		Map<String, String> parent = null;

		if (!currentPathElements.isEmpty()) {
			int parentIndex = currentPathElements.size() - 1;
			parent = currentPathElements.get(parentIndex);
		}

		if (modelClassName.equals(MBCategory.class.getName())) {
			currentCategory = createMBCategory(parent, attributesMap);
		}
		else if (modelClassName.equals(MBMessage.class.getName())) {
			MBMessage parentMessage = null;

			if (MBMessage.class.getName().equals(parent.get("modelClassName"))) {
				parentMessage = getLastProcessedMessage();
			}

			MBMessage currentMessage =
				createMBMessage(
					currentCategory, currentThread, parentMessage,
					attributesMap);
			currentMBMessages.add(currentMessage);
		}
	}

	private MBCategory createMBCategory(
		Map<String, String> parent, Map<String, String> attributesMap)
		throws Exception {

		long parentCategoryId = MBCategoryConstants.DEFAULT_PARENT_CATEGORY_ID;

		if (parent != null) {
			String parentModelClassName = parent.get("modelClassName");

			if (parentModelClassName != null &&
				parentModelClassName.equals(MBCategory.class.getName())) {
				String parentCategoryIdString = parent.get("categoryId");
				parentCategoryId = Long.valueOf(parentCategoryIdString);
			}
		}

		long categoryId = CounterLocalServiceUtil.increment(
			MBCategory.class.getName());
		MBCategory category = MBCategoryLocalServiceUtil.createMBCategory(
			categoryId);

		setImportProperties(
			category, categoryIncludeProperties, categoryExcludeProperties,
			attributesMap);

		category.setGroupId(serviceContext.getScopeGroupId());
		category.setParentCategoryId(parentCategoryId);

		MBCategoryLocalServiceUtil.addMBCategory(category);
		attributesMap.put("categoryId", String.valueOf(categoryId));
		return category;
	}

	private MBMessage createMBMessage(
		MBCategory currentCategory, MBThread currentThread,
		MBMessage parentMessage, Map<String, String> attributesMap)
		throws Exception {

		long categoryId = currentCategory.getCategoryId();
		long threadId = 0;

		if (currentThread != null) {
			threadId = currentThread.getThreadId();
		}

		long parentMessageId = MBMessageConstants.DEFAULT_PARENT_MESSAGE_ID;

		if (parentMessage != null) {
			threadId = parentMessage.getThreadId();
			parentMessageId = parentMessage.getMessageId();
		}

		String originalUserUuid = attributesMap.get("userUuid");
		String originalUserId = attributesMap.get("userId");

		User user = null;
		Long userId = null;
		String userName = null;

		if (originalUserUuid != null) {
			try {
				user =
					UserLocalServiceUtil.getUserByUuidAndCompanyId(
						originalUserUuid, serviceContext.getCompanyId());
			}
			catch (Exception e) {
			}
		}
		else if (originalUserId != null) {
			user =
				UserLocalServiceUtil.fetchUserById(Long.parseLong(originalUserId));
		}

		if (user != null) {
			userId = user.getUserId();
		}
		else {
			userId = serviceContext.getUserId();
			user = UserLocalServiceUtil.getUser(userId);
		}

		userName = user.getScreenName();

		String subject = attributesMap.get("subject");
		String format = attributesMap.get("format");
		String body = StringPool.BLANK;

		MBMessage mbMessage =
			MBMessageLocalServiceUtil.addMessage(
				userId, userName, serviceContext.getScopeGroupId(), categoryId,
				threadId, parentMessageId, subject, body, format,
				new ArrayList<ObjectValuePair<String, InputStream>>(), false,
				1.0, false, serviceContext);

		String createDate = attributesMap.get("createDate");
		String modifiedDate = attributesMap.get("modifiedDate");
		setProperty(mbMessage, "createDate", createDate);
		setProperty(mbMessage, "modifiedDate", modifiedDate);

		mbMessage = MBMessageLocalServiceUtil.updateMBMessage(mbMessage);

		return mbMessage;
	}

	private MBMessage getLastProcessedMessage() {

		return currentMBMessages.get(currentMBMessages.size() - 1);
	}

	private void setImportProperties(
		Object bean, String[] includeProperties, String[] excludeProperties,
		Map<String, String> attributesMap)
		throws Exception {

		if (includeProperties != null) {
			for (int p = 0; p < includeProperties.length; p++) {
				String propertyName = includeProperties[p];
				String propertyValueAsString = attributesMap.get(propertyName);
				setProperty(bean, propertyName, propertyValueAsString);
			}
		}
		else if (excludeProperties != null) {
			Set<String> keys = attributesMap.keySet();

			for (String propertyName : keys) {
				if (!ArrayUtil.contains(excludeProperties, propertyName)) {
					String propertyValueAsString = attributesMap.get(
						propertyName);
					setProperty(bean, propertyName, propertyValueAsString);
				}
			}
		}
	}

	private void setProperty(
		Object bean, String propertyName, String propertyValueAsString)
		throws Exception {

		Object value = transformValue(
			bean, propertyName, propertyValueAsString);
		BeanPropertiesUtil.setProperty(bean, propertyName, value);
	}

	private Object transformValue(
		Object bean, String propertyName, String propertyValue)
		throws Exception {

		Object value = propertyValue;

		if (propertyValue != null && propertyName.endsWith("Date")) {
			value = DateUtil.getISOFormat().parse(propertyValue);
		}

		return value;
	}

	private String[] categoryExcludeProperties = null;
	private String[] categoryIncludeProperties = new String[] {
		"name", "createDate", "description", "displayStyle", "lastPostDate",
		"modifiedDate", "userName"
	};
	private MBCategory currentCategory = null;
	private List<MBMessage> currentMBMessages = new ArrayList<MBMessage>();
	private MBThread currentThread = null;
	private ServiceContext serviceContext = null;

}