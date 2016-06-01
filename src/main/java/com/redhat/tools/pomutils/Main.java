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

import java.io.*;

import java.util.*;

import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathExpression;

/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class Main {
    
    private static void printVersions(POM root) throws Exception {
        for(Iterator<POM> itr=root.depthFirstIterator();itr.hasNext();) {
            POM p=itr.next();
            System.out.println(p.getGroupId()+":"+
                               p.getArtifactId()+":"+
                               p.getVersion());
        }
    }

    private static void checkDependencyVersionSanity(POM p,NodeList dependencies) throws Exception {
        int n=dependencies.getLength();
        for(int i=0;i<n;i++) {
            Element el=(Element)dependencies.item(i);
            String artifactId=XML.getElementText(el,XML.xp_rel_artifactId);
            String groupId=XML.getElementText(el,XML.xp_rel_groupId);
            String version=p.resolve(XML.getElementText(el,XML.xp_rel_version));
            if(version!=null) {
                POM dep=POM.allPOMs.get(groupId+":"+artifactId);
                if(dep!=null) {
                    if(!dep.getVersion().equals(version))
                        System.out.println(p.getGroupId()+":"+p.getArtifactId()+
                                           " depends on "+
                                           dep.getGroupId()+":"+dep.getArtifactId()+
                                           " version "+version+" but the correct version should be "+dep.getVersion());
                }
            }
        }
    }

    private static void checkVersionSanity(POM p) throws Exception {
        // Make sure all dependencies of this pom that point to other poms in the tree has the correct version
        NodeList dependencies=p.getDependencies();
        checkDependencyVersionSanity(p,dependencies);
        // Make sure dependency management is sane
        dependencies=p.getDependencyManagement();
        checkDependencyVersionSanity(p,dependencies);

        // Make sure parent pom version is correct
        String parentGroupId=p.getParentGroupId();
        String parentArtifactId=p.getParentArtifactId();
        if(parentGroupId!=null&&parentArtifactId!=null) {
            POM par=POM.allPOMs.get(parentGroupId+":"+parentArtifactId);
            if(par!=null) {
                if(par.getVersion()==null)
                    System.out.println(par.getGroupId()+":"+
                                       par.getArtifactId()+
                                       " has no version");
                else if(!par.getVersion().equals(p.getParentVersion())) {
                    System.out.println(p.getGroupId()+":"+p.getArtifactId()+
                                       " has parent "+
                                       par.getGroupId()+":"+par.getArtifactId()+
                                       " version "+p.getParentVersion()+
                                       " but the correct version should be "+par.getVersion());
                }
            } else
                System.out.println(p.getGroupId()+":"+p.getArtifactId()+
                                   " has parent "+
                                   parentGroupId+":"+parentArtifactId+
                                   " but the parent is not in the tree");
        }
        
    }

    private static void checkVersionSanity() throws Exception {
        for(POM p:POM.allPOMs.values())
            checkVersionSanity(p);
    }

    private static String readResponse() throws Exception {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        return stdin.readLine();
    }

    private static boolean fixDependencies(POM pom,Artifact a,NodeList dependencies) throws Exception {
        int n=dependencies.getLength();
        boolean changed=false;
        for(int i=0;i<n;i++) {
            Element el=(Element)dependencies.item(i);
            Element depVersionEl=XML.getElement(el,XML.xp_rel_version);
            if(depVersionEl!=null) {
                String depVersion=depVersionEl.getTextContent();
                if(!depVersion.equals(a.version)) {
                    depVersionEl.setTextContent(a.version);
                    pom.setModified();
                    changed=true;
                }
            } else
                System.out.println("Cannot set version in "+pom.getGroupId()+":"+pom.getArtifactId());
        }
        return changed;
    }

    private static boolean fixDependencies(Artifact a) throws Exception {
        boolean changed=false;
        XPathExpression x=XML.xpf.newXPath().compile("/project/dependencies/dependency [artifactId='"+a.artifactId+"' and groupId='"+a.groupId+"']");
        XPathExpression d=XML.xpf.newXPath().compile("/project/dependencyManagement/dependencies/dependency [artifactId='"+a.artifactId+"' and groupId='"+a.groupId+"']");

        for(POM pom:POM.allPOMs.values()) {
            // Go thru all dependencies and fix them
            if(fixDependencies(pom,a,XML.getElements(pom.doc,x)))
                changed=true;
            if(fixDependencies(pom,a,XML.getElements(pom.doc,d)))
                changed=true;
            
            // Update parent if necessary
            String parentGroupId=pom.getParentGroupId();
            String parentArtifactId=pom.getParentArtifactId();
            if(a.groupId.equals(parentGroupId)&&a.artifactId.equals(parentArtifactId)) {
                changed=pom.setParentVersion(a.version);
            }
        }
        return changed;
    }

    private static Element findDependency(POM pom,Artifact a) throws Exception {
        NodeList dependencies=pom.getDependencies();
        int n=dependencies.getLength();
        for(int i=0;i<n;i++) {
            Element el=(Element)dependencies.item(i);
            String depArtifactId=XML.getElementText(el,XML.xp_rel_artifactId);
            String depGroupId=XML.getElementText(el,XML.xp_rel_groupId);
            if(a.groupId.equals(depGroupId)&&a.artifactId.equals(depArtifactId)) {
                if(a.version.equals("*"))
                    return el;
                else {
                    Element depVersionEl=XML.getElement(el,XML.xp_rel_version);
                    if(depVersionEl!=null) {
                        String depVersion=depVersionEl.getTextContent();
                        if(a.version.equals(depVersion))
                            return el;
                    }
                }
            }
        }
        return null;
    }
            

    private static void write(boolean writeAll) throws Exception {
        for(POM p:POM.allPOMs.values()) {
            if(writeAll||p.isModified()) {
                System.out.println(p.getGroupId()+":"+p.getArtifactId()+ " is modified");
                p.write();
            }
        }
    }
    
   public static void main(String[] args) throws Exception {
        String pomfile=null;
        String cmd=null;
        String varg=null;
        boolean writeAll=false;
        boolean pomNeeded=false;
        String buildManifest=null;
        String allManifest="all.mf.xml";
        String outputFile=null;
        String skeleton=null;
        
        for(int i=0;i<args.length;i++) {
            if(args[i].startsWith("-")) {
                if(args[i].startsWith("-df")) {
                    cmd="-df";
                    varg=args[i].substring(3);
                } else if(args[i].startsWith("-dr")) {
                    cmd="-dr";
                    varg=args[i].substring(4);
                } else if(args[i].startsWith("-r")) {
                    cmd="-r";
                    buildManifest=args[i].substring(2);
                    if(buildManifest.trim().length()==0)
                        buildManifest=null;
                } else if(args[i].startsWith("-l")) {
                    allManifest=args[i].substring(2);
                } else if(args[i].startsWith("-v")) {
                    cmd="-v";
                    varg=args[i].substring(2);
                    pomNeeded=true;
                } else if(args[i].startsWith("-f")) {
                    cmd="-f";
                    varg=args[i].substring(2);
                    pomNeeded=true;
                } else if(args[i].equals("-a")) 
                    writeAll=true;
                else if(args[i].equals("-x")) {
                    cmd="-x";
                    pomNeeded=true;
                } else if(args[i].startsWith("-xp")) {
                    cmd="-xp";
                    varg=args[i].substring(3);
                    pomNeeded=true;
                } else if(args[i].equals("-p")) {
                    cmd=args[i];
                    pomNeeded=true;
                } else if(args[i].startsWith("-o")) {
                    outputFile=args[i].substring(2);
                } else if(args[i].startsWith("-s")) {
                    skeleton=args[i].substring(2);
                }
            } else
                pomfile=args[i];
        }
        if(cmd==null||(pomNeeded&&pomfile==null))
            printHelp();
        
        if(cmd.equals("-r")) {
            Manifest mf=new Manifest();
            mf.parse(new File(allManifest));
            if(buildManifest!=null&&!buildManifest.equals(allManifest))
                mf.parse(new File(buildManifest));
            GenerateRootPom grp=new GenerateRootPom(mf,allManifest,skeleton);
            Document doc=grp.generatePOM(grp.getPOMsToBuild());
            File f=new File(allManifest);
            String dir=f.getParent();

            if(outputFile!=null)
                f=new File(outputFile);
            else {
                f=new File(buildManifest==null?allManifest:buildManifest);
                String fname=f.getName();
                if(fname.toLowerCase().endsWith(".xml"))
                    fname=fname.substring(0,fname.length()-4);
                if(fname.toLowerCase().endsWith(".mf")) 
                    fname=fname.substring(0,fname.length()-3);
                fname=fname+".pom.xml";
                f=new File(dir,fname);
            }
            XML.write(doc,f);
        } else {
            POM root=new POM(new File(pomfile));
            if(cmd.equals("-p"))
                printVersions(root);
            else if(cmd.equals("-x"))
                checkVersionSanity();
            else if(cmd.equals("-xp")) {
                XPathExpression x=XML.xpf.newXPath().compile(varg);
                NodeList nl=XML.getElements(root.doc,x);
                int n=nl.getLength();
                for(int i=0;i<n;i++)
                    System.out.println(nl.item(i).getTextContent());
            } else if(cmd.equals("-v")) {
                Artifact a=Artifact.parse(varg);
                POM vc=POM.allPOMs.get(a.groupId+":"+a.artifactId);
                boolean changed=false;
                System.out.print("Setting the version of "+a.groupId+":"+a.artifactId+
                                 " to "+a.version);
                if(vc!=null) {
                    if(vc.setVersion(a.version))
                        changed=true;
                }
                if(fixDependencies(a))
                    changed=true;
                if(changed)
                    write(writeAll);
            } else if(cmd.equals("-f")) {
                BufferedReader reader=new BufferedReader(new FileReader(varg));
                String line;
                boolean changed=false;
                while((line=reader.readLine())!=null) {
                    line=line.trim();
                    if(line.length()>0) {
                        Artifact a=Artifact.parse(line);
                        POM vc=POM.allPOMs.get(a.groupId+":"+a.artifactId);
                        if(vc!=null) {
                            if(vc.setVersion(a.version))
                                changed=true;
                        }
                        if(fixDependencies(a))
                            changed=true;
                    }
                }
                if(changed)
                    write(writeAll);
            } else if(cmd.equals("-df")) {
                Artifact a=Artifact.parse(varg);
                for(POM pom:POM.allPOMs.values()) {
                    if(findDependency(pom,a)!=null)
                        System.out.println(pom.getFile().getPath());
                }
            } else if(cmd.equals("-dr")) {
                boolean changed=false;
                Artifact a=Artifact.parse(varg);
                for(POM pom:POM.allPOMs.values()) {
                    Element el=findDependency(pom,a);
                    if(el!=null) {
                        el.getParentNode().removeChild(el);
                        pom.setModified();
                        changed=true;
                    }
                }
                if(changed)
                    write(writeAll);
            } else 
                printHelp();
        }
   }
    
    private static void printHelp() {
        System.out.println("This is how it works:\n"+
                           "\n"+
                           "Run an XPath on a POM:\n"+
                           "\n"+
                           "  pomutil <pomfile> -xp<XPath>\n"+
                           "\n"+
                           "Check version number sanity:\n"+
                           "\n"+
                           "  pomutil <pomfile> -x\n"+
                           "\n"+
                           "This will cross check all version numbers in the source tree, and\n"+
                           "print out inconsistencies.\n"+
                           "\n"+
                           "\n"+
                           "\n"+
                           "Print all versions:\n"+
                           "\n"+
                           "  pomutil <pomfile> -p\n"+
                           "\n"+
                           "\n"+
                           "\n"+
                           "Change the version of an artifact, and fix its direct dependencies:\n"+
                           "\n"+
                           "  pomutil <pomfile> -vgroupId:artifact:version\n"+
                           "\n"+
                           " Use -a flag to write all poms even if they're not changed\n"+
                           "Sets the version number of groupId:artifact to version in all the poms\n"+
                           "it is referred.\n"+
                           "\n"+
                           "\n"+
                           "Change the versions of artifacts:\n"+
                           "\n"+
                           "  pomutil <pomfile> -ffile\n"+
                           "\n"+
                           "Sets the version numbers of all artifacts in <file>\n"+
                           "\n"+
                           "\n"+
                           "So, print all versions, redirect the output to a text file. Then,\n"+
                           "edit the file to set the new version numbers, and feed it back using\n"+
                           "the -f switch.\n"+
                           "\n"+
                           "\n"+
                           "Find a direct dependency in all the POM files in a tree (version can be '*'):\n"+
                           "\n"+
                           "  pomutil <pomfile> -dfgroupId:artifact:version\n"+
                           "\n"+
                           "\n"+
                           "Remove a direct dependency from all the POM files in a tree (version can be '*'):\n"+
                           "\n"+
                           "  pomutil <pomfile> -drgroupId:artifact:version\n"+
                           "\n"+
                           "\n"+
                           "  pomutil -r[build manifest] [-lall.mf.xml] [-ooutputFile] [-sskeleton]\n"+
                           "\n"+
                           "Builds a root pom based on the given build manifest, or if omitted, all.mf.xml\n"+
                           "If a skeleton pom file is given, the <modules> section is replaced with the modules to be built.\n");
        System.exit(0);

    }
}
