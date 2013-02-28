/*
    Copyright 2013 Red Hat, Inc. and/or its affiliates.

    This file is part of pomutils.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.redhat.tools.pomutils;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.CharacterData;

import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.OutputKeys;

import javax.xml.transform.dom.DOMSource;

import javax.xml.transform.stream.StreamResult;

/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class XML {

    public static final DocumentBuilder docBuilder;
    public static final XPathFactory xpf;
    public static final XPathExpression xp_modules;
    public static final XPathExpression xp_module;
    public static final XPathExpression xp_groupId;
    public static final XPathExpression xp_artifactId;
    public static final XPathExpression xp_version;
    public static final XPathExpression xp_property;
    public static final XPathExpression xp_parentGroupId;
    public static final XPathExpression xp_parentArtifactId;
    public static final XPathExpression xp_parentVersion;
    public static final XPathExpression xp_dependency;
    public static final XPathExpression xp_depmgmt;
    public static final XPathExpression xp_rel_artifactId;
    public static final XPathExpression xp_rel_groupId;
    public static final XPathExpression xp_rel_version;

    public static final XPathExpression xp_mf_modulemap;
    public static final XPathExpression xp_mf_modulename;
    public static final XPathExpression xp_mf_modulepom;
    public static final XPathExpression xp_mf_buildset;

    static {
        try {
            docBuilder=DocumentBuilderFactory.
                newInstance().
                newDocumentBuilder();
            xpf=XPathFactory.newInstance();

            xp_modules=xpf.newXPath().compile("/project/modules");
            xp_module=xpf.newXPath().compile("/project/modules/module");
            xp_groupId=xpf.newXPath().compile("/project/groupId");
            xp_artifactId=xpf.newXPath().compile("/project/artifactId");
            xp_parentGroupId=xpf.newXPath().compile("/project/parent/groupId");
            xp_parentArtifactId=xpf.newXPath().compile("/project/parent/artifactId");
            xp_parentVersion=xpf.newXPath().compile("/project/parent/version");
            xp_version=xpf.newXPath().compile("/project/version");
            xp_property=xpf.newXPath().compile("/project/properties/*");
            xp_dependency=xpf.newXPath().compile("/project/dependencies/*");
            xp_depmgmt=xpf.newXPath().compile("/project/dependencyManagement/*");
            xp_rel_artifactId=xpf.newXPath().compile("./artifactId");
            xp_rel_groupId=xpf.newXPath().compile("./groupId");
            xp_rel_version=xpf.newXPath().compile("./version");

            xp_mf_modulemap=xpf.newXPath().compile("/manifest/modulemap/*");
            xp_mf_modulename=xpf.newXPath().compile("./name");
            xp_mf_modulepom=xpf.newXPath().compile("./pom");
            xp_mf_buildset=xpf.newXPath().compile("/manifest/buildset/*");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Element getElement(Object context,XPathExpression xp) {
        try {
            return (Element)xp.evaluate(context,XPathConstants.NODE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getElementText(Object context,XPathExpression xp) {
        try {
            Element e=(Element)xp.evaluate(context,XPathConstants.NODE);
            if(e==null)
                return null;
            else
                return e.getTextContent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static NodeList getElements(Object context,XPathExpression xp) {
        try {
            return (NodeList)xp.evaluate(context,XPathConstants.NODESET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String[] getElementTexts(Object context,XPathExpression xp) {
        try {
            NodeList nl=getElements(context,xp);
            int n=nl.getLength();
            String[] ret=new String[n];
            for(int i=0;i<n;i++) {
                ret[i]=((Element)nl.item(i)).getTextContent();
            }
            return ret;
        } catch (Exception e) {
            throw new  RuntimeException(e);
        }
    }

    public static void write(Document doc,File file) throws Exception {
        FileOutputStream ostream=new FileOutputStream(file);
        TransformerFactory tf=TransformerFactory.newInstance();
        Transformer transformer=tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD,"xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"no");
        transformer.transform(new DOMSource(doc),
                              new StreamResult(ostream));
        ostream.close();
    }

}
