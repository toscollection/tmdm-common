/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 *
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.util.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.mdm.commmon.util.exception.SAXErrorHandler;
import org.talend.mdm.commmon.util.exception.XmlBeanDefinitionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class MDMXMLUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MDMXMLUtils.class);

    public static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    public static final String FEATURE_LOAD_EXTERNAL = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    public static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";

    public static final String FEATURE_EXTERNAL_PARAM_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    public static final String FEATURE_DEFER_NODE_EXPANSION = "http://apache.org/xml/features/dom/defer-node-expansion";

    public static final String PROPERTY_IS_SUPPORT_EXTERNAL_ENTITIES = "javax.xml.stream.isSupportingExternalEntities";

    public static final String PROPERTY_SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

    public static final String PROPERTY_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema";

    public static final String PROPERTY_SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    private static final DocumentBuilderFactory DOC_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private static final DocumentBuilderFactory DOC_BUILDER_FACTORY_WITH_NAMESPACE = DocumentBuilderFactory.newInstance();

    private static final DocumentBuilderFactory NON_VALIDATING_DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private static final DocumentBuilderFactory SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private static final XMLReader XML_READER;

    private static final SAXParserFactory SAX_PARSER_FACTORY;

    private static final XMLInputFactory XML_INPUT_FACTORY;

    private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();

    static {
        try {
            DOC_BUILDER_FACTORY.setIgnoringComments(true);
            secureFeatures(DOC_BUILDER_FACTORY);

            DOC_BUILDER_FACTORY_WITH_NAMESPACE.setIgnoringComments(true);
            DOC_BUILDER_FACTORY_WITH_NAMESPACE.setExpandEntityReferences(false);
            DOC_BUILDER_FACTORY_WITH_NAMESPACE.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
            secureFeatures(DOC_BUILDER_FACTORY_WITH_NAMESPACE);

            NON_VALIDATING_DOCUMENT_BUILDER_FACTORY.setValidating(false);
            secureFeatures(NON_VALIDATING_DOCUMENT_BUILDER_FACTORY);

            // initialize the sax parser which uses Xerces
            secureFeatures(SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY);
            // Schema validation based on schemaURL
            SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.setAttribute(PROPERTY_SCHEMA_LANGUAGE, PROPERTY_XML_SCHEMA);
        } catch (Exception e) {
            LOGGER.error("Error during creating secured DocumentBuilderFactory instance: " + e);
            throw new XmlBeanDefinitionException("Error occurred while initializing DocumentBuilderFactory", e);
        }

        try {
            XML_READER = XMLReaderFactory.createXMLReader();
            XML_READER.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
            XML_READER.setFeature(FEATURE_LOAD_EXTERNAL, false);
            XML_READER.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            XML_READER.setFeature(FEATURE_EXTERNAL_PARAM_ENTITIES, false);
            XML_READER.setContentHandler(new DefaultHandler());
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while initializing XMLReader", e);
        }

        try {
            SAX_PARSER_FACTORY = SAXParserFactory.newInstance();
            SAX_PARSER_FACTORY.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
            SAX_PARSER_FACTORY.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            SAX_PARSER_FACTORY.setFeature(FEATURE_EXTERNAL_PARAM_ENTITIES, false);
            SAX_PARSER_FACTORY.setFeature(FEATURE_LOAD_EXTERNAL, false);
            SAX_PARSER_FACTORY.setValidating(false);
            SAX_PARSER_FACTORY.setNamespaceAware(true);
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while initializing SAXParserFactory", e);
        }

        try {
            TRANSFORMER_FACTORY.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            TRANSFORMER_FACTORY.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            TRANSFORMER_FACTORY.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception e) {
            // Just catch this, as Xalan doesn't support the above
        }

        XML_INPUT_FACTORY = XMLInputFactory.newInstance();
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        XML_INPUT_FACTORY.setProperty(PROPERTY_IS_SUPPORT_EXTERNAL_ENTITIES, false);
    }

    public static void secureFeatures(DocumentBuilderFactory factory) {
        try {
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE, true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
            factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(FEATURE_EXTERNAL_PARAM_ENTITIES, false);
        } catch (ParserConfigurationException e) {
            throw new XmlBeanDefinitionException("Error occurred while initializing DocumentBuilderFactory", e);
        }
    }

    public static XMLStreamReader createXMLStreamReader(InputStream inputStream) {
        try {
            return XML_INPUT_FACTORY.createXMLStreamReader(inputStream);
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while creating a new XMLStreamReader from InputStream.", e);
        }
    }

    public static XMLEventReader createXMLEventReader(InputStream inputStream) {
        try {
            return XML_INPUT_FACTORY.createXMLEventReader(inputStream);
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while creating a new XMLEventReader from InputStream.", e);
        }
    }

    public static XMLEventReader createXMLEventReader(StringReader source) {
        try {
            return XML_INPUT_FACTORY.createXMLEventReader(source);
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while creating a new XMLEventReader from StringReader.", e);
        }
    }

    public static SAXParser getSAXParser() {
        try {
            return SAX_PARSER_FACTORY.newSAXParser();
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while creating a SAXParserFactory from SAXParser.", e);
        }
    }

    public static Transformer getTransformer() {
        try {
            Transformer transformer = TRANSFORMER_FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.toString());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            return transformer;
        } catch (Exception e) {
            throw new XmlBeanDefinitionException("Error occurred while creating a Transformer from TransformerFactory. \n caused by ", e);
        }
    }

    public static XMLReader getXMLReader() {
        return getXMLReader(new DefaultHandler());
    }

    public static XMLReader getXMLReader(DefaultHandler handler) {
        XML_READER.setContentHandler(handler);
        return XML_READER;
    }

    private static DocumentBuilderFactory getDocumentBuilderFactory() {
        return DOC_BUILDER_FACTORY;
    }

    private static DocumentBuilderFactory getDocumentBuilderFactoryWithNamespace() {
        return DOC_BUILDER_FACTORY_WITH_NAMESPACE;
    }

    private static synchronized DocumentBuilderFactory getNoValidationDocumentBuilderFactory() {
        return NON_VALIDATING_DOCUMENT_BUILDER_FACTORY;
    }

    public static Optional<DocumentBuilder> getDocumentBuilder() {
        DocumentBuilder builder = null;
        try {
            builder = getDocumentBuilderFactory().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new XmlBeanDefinitionException("Error occurred while using DocumentBuilderFactory to create a DocumentBuilder.", e);
        }
        return Optional.ofNullable(builder);
    }

    public static Optional<DocumentBuilder> getDocumentBuilderWithNamespace() {
        DocumentBuilder builder = null;
        try {
            builder = getDocumentBuilderFactoryWithNamespace().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new XmlBeanDefinitionException("Error occurred while using DocumentBuilderFactory to create a DocumentBuilder.", e);
        }
        return Optional.ofNullable(builder);
    }

    public static boolean isExistExtEntity(InputStream stream) {
        boolean results = false;
        try {
            String result = new BufferedReader(new InputStreamReader(stream))
                    .lines().collect(Collectors.joining(System.lineSeparator()));
            results = isExistExtEntity(result);
        } catch (Exception e) {
            LOGGER.error("An unexpected exception occurred." + e);
        }
        return results;
    }

    /**
     * Take a security measurements against XML external entity attacks.
     *  {"<!ENTITY desc SYSTEM \"ect/passwd\">,
     * "<!ENTITY desc SYSTEM>", "<!ENTITY desc public>", "<!ENTITY desc system \"file:abc.txt\">", "<!ENTITY desc public
     * \"http://www.baidu.com\">", "<!ENTITY abc public>", "<!entity public \"http://www.baidu.com\">", }
     *
     * @param rawXml
     * @return
     */
    public static boolean isExistExtEntity(String rawXml) {
        if (rawXml == null) {
            return false;
        }
        Pattern pattern = Pattern.compile("<!ENTITY\\s+\\S*\\s+[SYSTEM|PUBLIC]{1}.+?>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawXml);
        return matcher.find();
    }

    /**
     * Utility class, extract some part of an XML. If you have an xml and want to extract some part of that. you may use
     * below {{@link #unwrap()} to get it. like:
     * 
     * <pre>
     * &lt;root&gt;
     *  &lt;Product&gt;
     *      &lt;ID&gt;11&lt;/ID&gt;
     *      &lt;Name&gt;alan&lt;/Name&gt;
     *  &lt;/Product&gt;
     *  &lt;Product&gt;
     *      &lt;ID&gt;22&lt;/ID&gt;
     *      &lt;Name&gt;emily&lt;/Name&gt;
     *  &lt;/Product&gt;
     * &lt;/root&gt;
     * </pre>
     * 
     * using <b>[tagname:"Product",index:1]</b>, will get part of xml as follows:
     * 
     * <pre>
     *  &lt;Product&gt;
     *      &lt;ID&gt;22&lt;/ID&gt;
     *      &lt;Name&gt;emily&lt;/Name&gt;
     *  &lt;/Product&gt;
     * </pre>
     * 
     * @param doc : XML
     * @param tagName : root node name of part of XML.
     * @param index : child
     * @return : the part of xml which comes inside <b>tagName</b>
     * @throws ParserConfigurationException
     */
    public static Document unwrap(Document doc, String tagName, int index) throws ParserConfigurationException {
        assert doc != null : "Document is null";
        assert tagName != null : "tagName is null";
        assert index >= 0 : "Index (zero based) must be greater than or equal to zero";
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() <= index) {
            // only return the whole xml
            LOGGER.warn("Index [" + index + "] is invalid because the value is out of bounds " + nodeList.getLength()
                    + ", so only return the raw xml");
            return doc;
        }
        Node node = nodeList.item(index);
        Optional<DocumentBuilder> dBuilder = getDocumentBuilder();
        if (dBuilder.isPresent()) {
            Document result = dBuilder.get().newDocument();
            Node importNode = result.importNode(node, true);
            result.appendChild(importNode);
            return result;
        } else {
            return doc;
        }
    }

    /**
     * Utility class to convert outputstream to a string using String initializer in Java.
     * <p>
     * If you want to unwrap a node, you can use another method {{@link #unwrap()} to get the part of xml,then you can
     * perform the following method {#{@link transformXMLToString} to simply convert the OutputStream to finalString
     * using String's toString which created a string writer.
     * 
     * @param doc : the xml want to convert to string
     * @return a String that want to pull out
     * @throws Exception
     */
    public static String transformXMLToString(Document doc) throws Exception {
        assert doc != null : "Document is null";
        Transformer transformer = getTransformer();
        DOMSource source = new DOMSource(doc);
        String content = StringUtils.EMPTY;
        try (StringWriter stringWriter = new StringWriter();) {
            StreamResult result = new StreamResult(stringWriter);
            transformer.transform(source, result);
            content = stringWriter.toString();
        }
        return content;
    }

    /**
     * Method {@link MDMXMLUtils#transformXmlToString()} come from deprecated class XmlUtil in project
     * org.talend.mdm.webapp.base
     * 
     * @param XmlString
     * @return
     */
    public static String transformXmlToString(String XmlString) {
        return XmlString.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
                .replaceAll("'", "&apos;"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Method {@link MDMXMLUtils#transformStringToXml()} come from deprecated class XmlUtil in project
     * org.talend.mdm.webapp.base
     * 
     * @param XmlString
     * @return
     */
    public static String transformStringToXml(String value) {
        return value.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&").replaceAll("&quot;", "\"") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
                .replaceAll("&apos;", "'"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Method {@link MDMXMLUtils#escapeXml()} come from deprecated class XmlUtil in package com.amalto.webapp.core.util.
     * 
     * @param XmlString
     * @return
     */
    public static String escapeXml(String value) {
        if (value == null)
            return null;
        boolean isEscaped = false;
        if (value.contains("&quot;") || //$NON-NLS-1$
                value.contains("&amp;") || //$NON-NLS-1$
                value.contains("&lt;") || //$NON-NLS-1$
                value.contains("&gt;")) { //$NON-NLS-1$
            isEscaped = true;
        }
        if (!isEscaped) {
            value = StringEscapeUtils.escapeXml(value);
        }
        return value;
    }

    public static String unescapeXml(String value) {
        if (value == null) {
            return null;
        }
        return StringEscapeUtils.unescapeXml(value);
    }

    public static Document parse(String xmlString) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = getNoValidationDocumentBuilderFactory();
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        SAXErrorHandler seh = new SAXErrorHandler();
        builder.setErrorHandler(seh);
        Document d = builder.parse(new InputSource(new StringReader(xmlString)));
        // check if document parsed correctly against the schema
        String errors = seh.getErrors();
        if (errors.length() != 0) {
            String err = "Document did not parse against schema: \n" + errors + "\n"
                    + xmlString.substring(0, Math.min(100, xmlString.length()));
            throw new SAXException(err);
        }
        return d;
    }

    public static String getFirstTextNode(Node contextNode, String xPath, Node namespaceNode) throws TransformerException {
        String[] res = getTextNodes(contextNode, xPath, namespaceNode);
        if (res.length == 0) {
            return null;
        }
        return res[0];
    }

    public static String getFirstTextNode(Node contextNode, String xPath) throws TransformerException {
        return getFirstTextNode(contextNode, xPath, contextNode);
    }

    /**
     * The value of the first text node at the xPath which is not null<br>
     * @see #getTextNodes(Node, String)
     * @param contextNode
     * @param xPath
     * @return teh String value
     * @throws TransformerException
     */
    public static String getFirstTextNodeNotNull(Node contextNode, String xPath) throws TransformerException {
        String val = getFirstTextNode(contextNode, xPath, contextNode);
        return val == null ? "" : val;
    }

    /**
     * Return the String values of the Text Nodes below an xPath
     * @param contextNode
     * @param xPath
     * @return a String Array of the text node values
     * @throws TransformerException
     */
    public static String[] getTextNodes(Node contextNode, String xPath) throws TransformerException {
        return getTextNodes(contextNode, xPath, contextNode);
    }

    public static String[] getTextNodes(Node contextNode, String xPath, final Node namespaceNode) throws TransformerException {
        String[] results;
        // test for hard-coded values
        if (xPath.startsWith("\"") && xPath.endsWith("\"")) {
            return new String[] { xPath.substring(1, xPath.length() - 1) };
        }
        // test for incomplete path (elements missing /text())
        if (!xPath.matches(".*@[^/\\]]+")) { // attribute
            if (!xPath.endsWith(")")) { // function
                xPath += "/text()";
            }
        }
        try {
            XPath path = XPathFactory.newInstance().newXPath();
            path.setNamespaceContext(new NamespaceContext() {

                @Override
                public String getNamespaceURI(String s) {
                    return namespaceNode.getNamespaceURI();
                }

                @Override
                public String getPrefix(String s) {
                    return namespaceNode.getPrefix();
                }

                @Override
                public Iterator getPrefixes(String s) {
                    return Collections.singleton(namespaceNode.getPrefix()).iterator();
                }
            });
            NodeList xo = (NodeList) path.evaluate(xPath, contextNode, XPathConstants.NODESET);
            results = new String[xo.getLength()];
            for (int i = 0; i < xo.getLength(); i++) {
                results[i] = xo.item(i).getTextContent();
            }
        } catch (Exception e) {
            String err = "Unable to get the text node(s) of " + xPath + ": " + e.getClass().getName() + ": "
                    + e.getLocalizedMessage();
            throw new TransformerException(err);
        }
        return results;
    }

    /**
     * Parsed an XML String into a {@link Document} without schema validation
     * @param xmlString
     * @return the Document
     * @throws ParserConfigurationException,IOException, SAXException
     */
    public static Document parseXml(String xmlString) throws ParserConfigurationException, IOException, SAXException {
        return parse(xmlString, null);
    }

    /**
     * Parses an XML String into a Document<br>
     * The thrown Exception will contain validation errors when a schema is provided.
     * @param xmlString
     * @param schema - the schema XSD
     * @return The org.w3c.dom.Document
     * @throws ParserConfigurationException,IOException, SAXException
     */
    public static Document parse(String xmlString, String schema) throws ParserConfigurationException, IOException, SAXException {
        // parse
        Document document = null;
        SAXErrorHandler seh = new SAXErrorHandler();

        SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.setValidating((schema != null));
        if (schema != null) {
            SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource",
                    new InputSource(new StringReader(schema)));
        }
        DocumentBuilder builder = SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        builder.setErrorHandler(seh);
        document = builder.parse(new InputSource(new StringReader(xmlString)));

        // check if document parsed correctly against the schema
        if (schema != null) {
            String errors = seh.getErrors();
            if (!errors.equals("")) {
                String err = "Document  did not parse against schema: \n" + errors + "\n"
                        + xmlString.substring(0, Math.min(100, xmlString.length()));
                LOGGER.error(err);
                throw new SAXException(err);
            }
        }
        return document;
    }

    public static Transformer generateTransformer() throws TransformerConfigurationException {
        return TRANSFORMER_FACTORY.newTransformer();
    }

    public static Transformer generateTransformer(boolean isOmitXmlDeclaration) throws TransformerConfigurationException {
        Transformer transformer = generateTransformer();
        if (isOmitXmlDeclaration) {
            transformer.setOutputProperty("omit-xml-declaration", "yes");
        } else {
            transformer.setOutputProperty("omit-xml-declaration", "no");
        }
        return transformer;
    }

    public static Transformer generateTransformer(boolean isOmitXmlDeclaration, boolean isIndent)
            throws TransformerConfigurationException {
        Transformer transformer = generateTransformer(isOmitXmlDeclaration);
        if (isIndent) {
            transformer.setOutputProperty("indent", "yes");
        } else {
            transformer.setOutputProperty("indent", "no");
        }
        return transformer;
    }

    public static String nodeToString(Node n) throws TransformerException {
        return nodeToString(n, true, true);
    }

    public static String nodeToString(Node n, boolean isOmitXmlDeclaration, boolean isIndent) throws TransformerException {
        StringWriter sw = new StringWriter();
        Transformer transformer = generateTransformer(isOmitXmlDeclaration, isIndent);
        transformer.transform(new DOMSource(n), new StreamResult(sw));
        return sw.toString();
    }

    /**
     * Validates the element against the provided XSD schema
     * @param element
     * @param schema
     * @return
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws TransformerException
     */
    public static Document validate(Element element, String schema) throws SAXException, ParserConfigurationException,
            IOException, TransformerException {
        // parse
        Document document = null;
        SAXErrorHandler seh = new SAXErrorHandler();
        SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.setValidating((schema != null));
        if (schema != null) {
            SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource",
                    new InputSource(new StringReader(schema)));
        }
        DocumentBuilder builder = SCHEMA_VALIDATING_DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        builder.setErrorHandler(seh);
        document = builder.parse(new InputSource(new StringReader(nodeToString(element))));

        // check if dcument parsed correctly against the schema
        if (schema != null) {
            String errors = seh.getErrors();
            if (!errors.equals("")) {
                String xmlString = nodeToString(element);
                String err = "The item " + element.getLocalName() + " did not validate against the model: \n" + errors + "\n"
                        + xmlString; // .substring(0, Math.min(100, xmlString.length()));
                throw new SAXException(err);
            }
        }
        return document;
    }
}
