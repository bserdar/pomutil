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

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import java.io.File;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class Manifest {

    private final Map<String,String> projectPomMap=new HashMap<String,String>();
    private final Set<String> buildProjects=new HashSet<String>();

    public String[] getAllProjects() {
        String[] ret=new String[projectPomMap.size()];
        projectPomMap.keySet().toArray(ret);
        return ret;
    }

    public String getPOMForProject(String project) {
        return projectPomMap.get(project);
    }

    public String[] getBuildProjects() {
        String[] ret=new String[buildProjects.size()];
        buildProjects.toArray(ret);
        return ret;
    }

    public void parse(File f) {
        try {
            String manifestDir=f.getParent();
            Document doc=XML.docBuilder.parse(f);
            Element root=doc.getDocumentElement();
            if(root.getTagName().equals("manifest")) {
                NodeList moduleMap=XML.getElements(root,XML.xp_mf_modulemap);
                int n=moduleMap.getLength();
                for(int i=0;i<n;i++) {
                    Node mapItem=moduleMap.item(i);
                    String name=XML.getElementText(mapItem,XML.xp_mf_modulename);
                    String pom=XML.getElementText(mapItem,XML.xp_mf_modulepom);
                    projectPomMap.put(name,pom);
                }
                NodeList buildset=XML.getElements(root,XML.xp_mf_buildset);
                n=buildset.getLength();
                for(int i=0;i<n;i++) {
                    Node item=buildset.item(i);
                    if(item.getNodeType()==Node.ELEMENT_NODE&&
                       item.getNodeName().equals("module"))
                        buildProjects.add(item.getTextContent());
                }
            } else
                throw new RuntimeException("Root element 'manifest' is expected:"+f);
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        return "POM Map="+projectPomMap+"\n"+"buildSet="+buildProjects;
    }

    public Manifest() {}
}
