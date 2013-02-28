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

import java.util.*;
import java.io.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class GenerateRootPom {

    private final Manifest mf;
    private final HashMap<String,POM> pomMap=new HashMap<String,POM>();
    private final File allManifest;
    private final String skeleton;

    public GenerateRootPom(Manifest mf,String allManifest,String skeleton) {
        this.mf=mf;
        this.allManifest=new File(allManifest);
        this.skeleton=skeleton;
    }


    private static void addAllArtifacts(Set<String> set,POM root) {
        set.add(root.getId());
        for(Iterator itr=root.depthFirstIterator();itr.hasNext();)
            set.add(((POM)itr.next()).getId());
    }

    private static String[] findDependentArtifacts(String artifact) {
        POM pom=POM.allPOMs.get(artifact);
        NodeList nl=pom.getDependencies();
        int n=nl.getLength();
        Set<String> artifacts=new HashSet<String>();
        for(int i=0;i<n;i++) {
            Element el=(Element)nl.item(i);
            String depArtifactId=XML.getElementText(el,XML.xp_rel_artifactId);
            String depGroupId=XML.getElementText(el,XML.xp_rel_groupId);
            POM depPom=POM.allPOMs.get(depGroupId+":"+depArtifactId);
            if(depPom!=null)
                artifacts.add(depPom.getId());
        }
        return artifacts.toArray(new String[artifacts.size()]);
    }

    private static String[] findArtifactsDependingOn(String artifact) {
        int index=artifact.indexOf(':');
        String groupId=artifact.substring(0,index);
        String artifactId=artifact.substring(index+1);
        Set<String> artifacts=new HashSet<String>();
        for(POM pom:POM.allPOMs.values()) {
            NodeList dependencies=pom.getDependencies();
            int n=dependencies.getLength();
            for(int i=0;i<n;i++) {
                Element el=(Element)dependencies.item(i);
                String depArtifactId=XML.getElementText(el,XML.xp_rel_artifactId);
                String depGroupId=XML.getElementText(el,XML.xp_rel_groupId);
                if(depArtifactId.equals(artifactId)&&
                   depGroupId.equals(groupId)) {
                    artifacts.add(pom.getId());
                    break;
                }
            }
        }
        return artifacts.toArray(new String[artifacts.size()]);
    }

    private static POM[] getRootPoms(Set<String> artifacts) {
        HashSet<POM> set=new HashSet<POM>();
        for(String artifact:artifacts) {
            POM pom=POM.allPOMs.get(artifact);
            POM rootPom=pom.getRootPom();
            set.add(rootPom);
        }
        return set.toArray(new POM[set.size()]);
    }

    private String getPomPath(POM p) {
        for(Map.Entry<String,POM> entry:pomMap.entrySet()) {
            if(entry.getValue()==p) {
                String s=mf.getPOMForProject(entry.getKey());
                File f=new File(s);
                return f.getParent();
            }
        }
        throw new RuntimeException("Cannot find path for "+p.getId());
    }

    private void generateSkeleton(Document doc) {
        Element root=doc.createElement("project");
        doc.appendChild(root);
        root.appendChild(doc.createTextNode("\n"));
        Element el=doc.createElement("modelVersion");
        root.appendChild(el);
        root.appendChild(doc.createTextNode("\n"));
        el.appendChild(doc.createTextNode("4.0.0"));
        el=doc.createElement("groupId");
        root.appendChild(el);
        root.appendChild(doc.createTextNode("\n"));
        el.appendChild(doc.createTextNode("autogen"));
        el=doc.createElement("artifactId");
        root.appendChild(el);
        root.appendChild(doc.createTextNode("\n"));
        el.appendChild(doc.createTextNode("autogen"));
        el=doc.createElement("packaging");
        root.appendChild(el);
        root.appendChild(doc.createTextNode("\n"));
        el.appendChild(doc.createTextNode("pom"));
        el=doc.createElement("version");
        root.appendChild(el);
        root.appendChild(doc.createTextNode("\n"));
        el.appendChild(doc.createTextNode("0"));
        el=doc.createElement("modules");
        root.appendChild(el);
        el.appendChild(doc.createTextNode("\n"));
    }

    public Document generatePOM(POM[] rootPoms) throws Exception {
        Document doc;
        if(skeleton!=null)
            doc=XML.docBuilder.parse(new File(skeleton));
        else {
            doc=XML.docBuilder.newDocument();
            generateSkeleton(doc);
        }
        // Find modules
        Element root=doc.getDocumentElement();
        Element modules=XML.getElement(root,XML.xp_modules);
        if(modules==null)
            throw new RuntimeException("Cannot find <modules> in document");
        // Empty modules
        while(modules.getFirstChild()!=null)
            modules.removeChild(modules.getFirstChild());
        modules.appendChild(doc.createTextNode("\n"));
        for(POM p:rootPoms) {
            Element module=doc.createElement("module");
            modules.appendChild(module);
            modules.appendChild(doc.createTextNode("\n"));
            module.appendChild(doc.createTextNode(getPomPath(p)));
        }
        return doc;
    }

    public POM[] getPOMsToBuild() throws Exception {
        boolean all=false;
        String[] allProjects=mf.getAllProjects();
        for(String x:allProjects) {
            File pomPath=new File(allManifest.getParentFile(),mf.getPOMForProject(x));
            pomMap.put(x,new POM(pomPath));
        }
        HashSet<String> buildSet=new HashSet<String>();

        // Put all artifacts in the manifest into the buildSet 
        String[] buildProjects=mf.getBuildProjects();
        if(buildProjects==null||buildProjects.length==0) {
            buildProjects=allProjects;
            all=true;
        }
        
        for(String x:buildProjects) {
            POM rootPom=pomMap.get(x);
            if(rootPom==null)
                throw new RuntimeException("POM for "+x+" not found");
            if(all)
                buildSet.add(rootPom.getId());
            else
                addAllArtifacts(buildSet,rootPom);
        }
        if(!all) {
            HashSet<String> add=new HashSet<String>();
//             do {
//                 for(String artifact:buildSet) {
//                     String[] deps=findDependentArtifacts(artifact);
//                     if(deps!=null) 
//                         for(String x:deps)
//                             if(!buildSet.contains(x))
//                                 add.add(x);
//                 }
//                 buildSet.addAll(add);
//                 add.clear();
//             } while(!add.isEmpty());

            // For every artifact in the buildset, find a project depending on it
            // and add it to the build set
            do {
                for(String artifact:buildSet) {
                    String[] deps=findArtifactsDependingOn(artifact);
                    if(deps!=null)
                        for(String x:deps)
                            if(!buildSet.contains(x))
                                add.add(x);
                }
                buildSet.addAll(add);
                add.clear();
            } while(!add.isEmpty());
        }
        System.out.println("Buildset:");
        for(String x:buildSet)
            System.out.println(x);
        // Convert the artifact list into root pom list
        POM[] poms=getRootPoms(buildSet);
        System.out.println("Build projects:");
        for(POM x:poms)
            System.out.println(x.getId());
        return poms;
    }
        
}
