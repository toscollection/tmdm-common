/*
 * Copyright (C) 2006-2021 Talend Inc. - www.talend.com
 *
 * This source code is available under agreement available at
 * %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
 *
 * You should have received a copy of the agreement along with this program; if not, write to Talend SA 9 rue Pages
 * 92150 Suresnes, France
 */

package org.talend.mdm.commmon.metadata.annotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.xsd.XSDAnnotation;
import org.talend.mdm.commmon.metadata.Category;
import org.talend.mdm.commmon.metadata.ComplexTypeMetadata;
import org.talend.mdm.commmon.metadata.MetadataRepository;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings("nls")
public class CategoryAnnotationProcessor implements XmlSchemaAnnotationProcessor {

    @Override
    public void process(MetadataRepository repository, ComplexTypeMetadata type, XSDAnnotation annotation,
            XmlSchemaAnnotationProcessorState state) {
        if (annotation != null) {
            EList<Element> appInfoElements = annotation.getApplicationInformation();
            List<Category> categories = new ArrayList<>();
            for (Element appInfo : appInfoElements) {

                String source = appInfo.getAttribute("source");
                if (source != null && source.equals("X_Category")) {
                    NodeList childNodes = appInfo.getChildNodes();
                    Map<Locale, String> labels = new LinkedHashMap<>();
                    List<String> fields = new ArrayList<>();
                    String categoryName = null;
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        Node node = childNodes.item(i);
                        String localName = node.getLocalName();
                        if (localName != null) {
                            localName = localName.toLowerCase();
                            Node subChild = node.getFirstChild();
                            if (subChild != null) {
                                String nodeValue = subChild.getNodeValue();
                                if (nodeValue != null) {
                                    if (localName.equals("name")) {
                                        categoryName = nodeValue;
                                    } else if (localName.startsWith("label_")) {
                                        String lang = localName.substring(6);
                                        Locale locale = new Locale(lang);
                                        labels.put(locale, nodeValue);
                                    } else if (localName.equals("field") && nodeValue != null) {
                                        fields.add(nodeValue);
                                    }
                                }
                            }
                        }
                    }
                    if (categoryName != null) {
                        Category category = new Category(categoryName, fields, labels);
                        categories.add(category);
                        state.setCategories(categories);
                    }
                }
            }
        }
    }

}
