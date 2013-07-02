
package com.liferay.resourcesimporter.util.messageboard;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
public class MessageBoardXMLStreamReader {

	public MessageBoardXMLStreamReader(InputStream in) throws Exception {
		this.inputStream = in;
	}

	public final void close() throws IOException {
		if (!closed) {
			inputStream.close();
			closed = true;
		}
	} private void readUsingSAX() throws Exception {
		InputSource inputSource = new InputSource(inputStream);
		SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
		parser.parse(inputSource, messageBoardHandler);
	}

	public final void read() throws Exception {
		try {
			readUsingSAX();
		}
		finally {
			close();
		}
	}

	public void setMessageBoardHandler(
		MessageBoardObjectHandler messageBoardHandler) {

		this.messageBoardHandler = messageBoardHandler;
	}

	public static abstract class MessageBoardObjectHandler
		extends DefaultHandler {

		private List<Map<String, String>> currentPathElements =
			new ArrayList<Map<String, String>>();
		private List<String> currentPath = new ArrayList<String>();
		private StringBuffer text = new StringBuffer();
		private List<Map<String, String>> currentModelObjects =
			new ArrayList<Map<String, String>>();

		@Override
		public final void startElement(
			String uri, String localName, String qName, Attributes attributes)
			throws SAXException {

			try {
				Map<String, String> attributesMap = getAttributesMap(
					attributes);

				currentPath.add(qName);

				String modelClassName = attributesMap.get("modelClassName");

				if (modelClassName != null) {
					currentModelObjects.add(attributesMap);
					startObject(
						modelClassName, currentPathElements, attributesMap);
				}

				currentPathElements.add(attributesMap);
			}
			catch (Exception e) {
				throw new SAXException(e);
			}
		}

		@Override
		public final void characters(char[] ch, int start, int length)
			throws SAXException {

			text.append(new String(ch, start, length));
		}

		@Override
		public final void endElement(String uri, String localName, String qName)
			throws SAXException {

			try {
				int lastElementIndex = currentPath.size() - 1;
				Map<String, String> currentElement = currentPathElements.get(
					lastElementIndex);
				int lastObjectIndex = currentModelObjects.size() - 1;
				Map<String, String> currentModelObject = null;

				if (lastObjectIndex > -1) {
					currentModelObject = currentModelObjects.get(
						lastObjectIndex);
				}

				String modelClassName = currentElement.get("modelClassName");

				if (modelClassName != null) {
					endObject(
						modelClassName, currentPathElements, currentModelObject);
					currentModelObjects.remove(lastObjectIndex);
				}
				else if (text.length() > 0) {
					String text = this.text.toString().trim();

					if (currentModelObject != null) {
						currentModelObject.put(qName, text);
					}

					this.text = new StringBuffer();
				}

				currentPath.remove(lastElementIndex);
				currentPathElements.remove(lastElementIndex);
			}
			catch (Exception e) {
				throw new SAXException(e);
			}
		}

		protected abstract void startObject(
			String modelClassName,
			List<Map<String, String>> currentPathElements,
			Map<String, String> attributesMap)
			throws Exception;

		protected abstract void endObject(
			String modelClassName,
			List<Map<String, String>> currentPathElements,
			Map<String, String> currentModelObject)
			throws Exception;

		private Map<String, String> getAttributesMap(Attributes attributes) {

			Map<String, String> attributesMap = new TreeMap<String, String>();
			int attributeCount = attributes.getLength();

			for (int a = 0; a < attributeCount; a++) {
				String attributeName = attributes.getLocalName(a);
				String attributeValue = attributes.getValue(a);
				attributesMap.put(attributeName, attributeValue);
			}

			return attributesMap;
		}
	};

	private boolean closed = false;
	private InputStream inputStream = null;
	private MessageBoardObjectHandler messageBoardHandler = null;

}