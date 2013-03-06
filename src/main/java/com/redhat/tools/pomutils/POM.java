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

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;


/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class POM {

    public final Document doc;
    private final File file;
    private final List<POM> children=new ArrayList<POM>();
    private boolean modified=false;

    private String cachedArtifactId;
    private String cachedGroupId;
    private String cachedVersion;
    private String cachedParentArtifactId;
    private String cachedParentGroupId;
    private String cachedParentVersion;

    public static final Map<String,POM> allPOMs=new HashMap<String,POM>();
    
    public POM(File file) 
        throws SAXException, IOException {
        this.file=file;
        doc=XML.docBuilder.parse(file);

        String[] modules=XML.getElementTexts(doc.getDocumentElement(),
                                             XML.xp_module);
        for(String module:modules) {
            File childFile=new File(new File(file.getParentFile(),module),"pom.xml");
            POM childPom=new POM(childFile);
            children.add(childPom);
        }
        String id=getId();
        allPOMs.put(id,this);
    }

    public File getFile() {
        return file;
    }

    public void write() throws Exception {
        XML.write(doc,file);
        modified=false;
    }

    public String getId() {
        return getGroupId()+":"+getArtifactId();
    }

    public void setModified() {
        modified=true;
    }

    public boolean isModified() {
        return modified;
    }

    public POM getRootPom() {
        POM root=this;
        POM parent=null;
        do {
            parent=root.getParentPom();
            if(parent==null||root==parent)
                break;
            root=parent;
        } while(true);
        return root;
    }

    public POM getParentPom() {
        String parentGroupId=getParentGroupId();
        String parentArtifactId=getParentArtifactId();
        if(parentGroupId==null&&parentArtifactId==null)
            return this;
        else
            return allPOMs.get(parentGroupId+":"+parentArtifactId);
    }

    public String getGroupId() {
        if(cachedGroupId==null) {
            String s=XML.getElementText(doc.getDocumentElement(),
                                        XML.xp_groupId);
            if(s==null)
                s=getParentGroupId();
            cachedGroupId=resolve(s);
        }
        return cachedGroupId;
    }

    public String getArtifactId() {
        if(cachedArtifactId==null)
            cachedArtifactId=resolve(XML.getElementText(doc.getDocumentElement(),
                                                        XML.xp_artifactId));
        return cachedArtifactId;
    }

    public String getVersion() {
        if(cachedVersion==null) {
            String s=XML.getElementText(doc.getDocumentElement(),
                                        XML.xp_version);
            if(s==null)
                s=getParentVersion();
            cachedVersion=resolve(s);
        }
        return cachedVersion;
    }

    public boolean setVersion(String v) {
        cachedVersion=null;
        Element el=XML.getElement(doc.getDocumentElement(),
                                  XML.xp_version);
        if(el==null) {
            Element artId=XML.getElement(doc.getDocumentElement(),
                                         XML.xp_artifactId);
            if(artId==null)
                throw new RuntimeException("No version in "+
                                           getGroupId()+":"+getArtifactId());
            el=doc.createElement("version");
            artId.getParentNode().insertBefore(el,artId);
        }
        if(!el.getTextContent().equals(v)) {
            el.setTextContent(v);
            modified=true;
            return true;
        }
        return false;
    }

    public String getParentGroupId() {
        if(cachedParentGroupId==null)
            cachedParentGroupId=XML.getElementText(doc.getDocumentElement(),
                                                   XML.xp_parentGroupId);
        return cachedParentGroupId;
    }

    public String getParentArtifactId() {
        if(cachedParentArtifactId==null)
            cachedParentArtifactId=XML.getElementText(doc.getDocumentElement(),
                                                      XML.xp_parentArtifactId);
        return cachedParentArtifactId;
    }

    public String getParentVersion() {
        if(cachedParentVersion==null)
            cachedParentVersion=XML.getElementText(doc.getDocumentElement(),
                                                   XML.xp_parentVersion);
        return cachedParentVersion;
    }

    public boolean setParentVersion(String v) {
        cachedParentVersion=null;
        Element el=XML.getElement(doc.getDocumentElement(),
                                  XML.xp_parentVersion);
        if(el==null)
            throw new RuntimeException("No parent version in "+getGroupId()+":"+getArtifactId());
        if(!el.getTextContent().equals(v)) {
            el.setTextContent(v);
            modified=true;
            return true;
        }
        return false;
    }

    public NodeList getDependencies() {
        return XML.getElements(doc.getDocumentElement(),XML.xp_dependency);
    }

    public NodeList getDependencyManagement() {
        return XML.getElements(doc.getDocumentElement(),XML.xp_depmgmt);
    }
    
    public Iterator depthFirstIterator() {
        List<POM> l=new ArrayList<POM>();
        fillDF(l,this);
        return l.iterator();
    }

    private void fillDF(List<POM> list,POM root) {
        list.add(root);
        for(POM p:root.children)
            fillDF(list,p);
    }

    public String resolve(String s) {
        if(s==null)
            return null;

        StringBuffer out=new StringBuffer();
        StringBuffer sym=new StringBuffer();
        int n=s.length();
        int state=0;
        for(int i=0;i<n;i++) {
            char c=s.charAt(i);
            switch(state) {
            case 0: 
                if(c=='$')
                    state=1;
                else
                    out.append(c);
                break;

            case 1:
                if(c=='{')
                    state=2;
                else {
                    out.append('$').append(c);
                    state=0;
                }
                break;

            case 2:
                if(c=='}') {
                    String result=lookupProperty(this,sym.toString());
                    if(result==null)
                        out.append('$').append('{').append(sym).append('}');
                    else
                        out.append(result);
                    sym=new StringBuffer();
                    state=0;
                } else
                    sym.append(c);
                break;
            }
        }
        if(state==1)
            out.append('$');
        else if(state==2)
            out.append('$').append('{').append(sym);
        return out.toString();
    }

    private String lookupProperty(POM pom,String property) {
        POM current=pom;
        if(property.equals("version"))
            return pom.getVersion();
        while(current!=null) {
            String s=current.lookupProperty(property);
            if(s!=null)
                return s;
            String parentArtifactId=XML.getElementText(current.doc.getDocumentElement(),XML.xp_parentArtifactId);
            String parentGroupId=XML.getElementText(current.doc.getDocumentElement(),XML.xp_parentGroupId);
            if(parentArtifactId!=null&&
               parentGroupId!=null) {
                String id=parentGroupId+":"+parentArtifactId;
                POM parent=allPOMs.get(id);
                if(parent==null)
                    throw new RuntimeException("Cannot find parent "+id);
                current=parent;
            } else
                current=null;
        }
        return null;
    }

    private String lookupProperty(String property) {
        NodeList nl=XML.getElements(doc.getDocumentElement(),XML.xp_property);
        int n=nl.getLength();
        for(int i=0;i<n;i++) {
            Element el=(Element)nl.item(i);
            String name=el.getTagName();
            if(name.equals(property))
                return resolve(el.getTextContent());
        }
        return null;
    }
}
