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

import java.security.MessageDigest;

import java.io.File;

import java.util.List;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Comment;
import org.w3c.dom.CharacterData;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Attr;

/**
 * Maintains XML fragments externally.
 *
 * In an XML document, if a comment of the following form is
 * encountered, then the contents of the comment is replaced with the
 * XML fragment given in the comment.
 * <pre>
 *    <!-- Fragment: blah.xml -->
 * </pre>
 * In this example, the "blah.xml" is an XML file of the form:
 * <pre>
 *   <fragment>
 *        XML content
 *   </fragment>
 * </pre>
 * Contents of the element "fragment" is inserted into the file.
 *
 * If the XML document contains the following fragment:
 *
 * <pre>
 *    <!-- Fragment Begin: blah.xml 1234567890abcdfed -->
 *    Content
 *    <!-- Fragment End: blah.xml -->
 * </pre>
 *
 * Then, if the semantic hash of the fragment in that XML matches the
 * hash (i.e. if the fragment has not been modified manually), then
 * the contents of the fragment is replaced with "blah.xml").
 *
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class XmlFrag {

    private static class FragmentDirective {
        final String fileName;
        final Comment node;
        public FragmentDirective(String fileName,Comment node) {
            this.fileName=fileName;
            this.node=node;
        }

        public String toString() {
            return fileName;
        }
    }

    private static class FragmentContent extends FragmentDirective {
        final String hash;
        final List<Node> nodes=new ArrayList<Node>();

        public FragmentContent(String fileName,String hash) {
            super(fileName,null);
            this.hash=hash;
        }
        
        public String toString() {
            return super.toString()+" "+hash;
        }
    }

    private final File fragDir;

    public XmlFrag(File fragDir) {
        this.fragDir=fragDir;
    }

    public boolean processXML(File file,Document doc,boolean forceRefresh) throws Exception {
        Element root=doc.getDocumentElement();
        List<FragmentDirective> fragments=new ArrayList<FragmentDirective>();
        scanComments(root,fragments);

        MessageDigest digest=MessageDigest.getInstance("SHA-1");

        boolean modified=false;
        for(FragmentDirective frag:fragments) {
            DocumentFragment fragmentDoc=loadFragment(frag.fileName);
            digest.reset();
            digestFragment(digest,fragmentDoc);
            String hash=tostr(digest.digest());
            if(frag instanceof FragmentContent) {
                FragmentContent fc=(FragmentContent)frag;
                // Check if the fragment needs to be replaced
                // First compute the real hash of the fragment
                digest.reset();
                digest(digest,fc.nodes);
                String fragmentHash=tostr(digest.digest());
                if(fragmentHash.equals(fc.hash)||forceRefresh) {
                    // Fragment was not modified, so we can replace it
                    if(!fragmentHash.equals(hash)||forceRefresh) {
                        // The replacement content is modified. We need to replace
                        // Remove all nodes except the first and last
                        int n=fc.nodes.size();
                        Node lastNode=fc.nodes.get(n-1);
                        int i=0;
                        for(Node node:fc.nodes) {
                            if(i>0&&node!=lastNode) {
                                node.getParentNode().removeChild(node);
                            }
                            i++;
                        }
                        // Insert the fragment after the first comment node
                        importFragment(fc.nodes.get(n-1),fragmentDoc,doc);
                        // Modify the comment node text to reflect the new hash
                        ((Comment)fc.nodes.get(0)).setData("Fragment Begin: "+fc.fileName+" "+hash);
                        modified=true;
                    } 
                } else
                    System.out.println(file.toString()+": Fragment for "+frag.fileName+
                                       " was modified in file, cannot replace (fragment file hash:"+hash+" hash in file:"+fragmentHash+" has in comment:"+fc.hash+")");
            } else {
                // This will be the fragment end comment node
                frag.node.setData("Fragment End: "+frag.fileName);
                Comment c=doc.createComment("Fragment Begin: "+frag.fileName+" "+hash);
                frag.node.getParentNode().insertBefore(c,frag.node);
                // Import the fragment here
                importFragment(frag.node,fragmentDoc,doc);
                modified=true;
            }
        }
        return modified;
    }

    private void importFragment(Node commentNode,DocumentFragment fragmentDoc,Document doc) {
        Node child=fragmentDoc.getFirstChild();
        while(child!=null) {
            Node importedNode=doc.importNode(child,true);
            commentNode.getParentNode().insertBefore(importedNode,commentNode);
            child=child.getNextSibling();
        }
    }

    private String tostr(byte[] arr) {
        StringBuffer buf=new StringBuffer(arr.length*2);
        for(int i=0;i<arr.length;i++) {
            buf.append(Character.forDigit(arr[i]&0x0F,16));
            buf.append(Character.forDigit((arr[i]>>>4)&0x0F,16));
        }
        return buf.toString();
    }

    private DocumentFragment loadFragment(String fileName) throws Exception {
        Document doc=XML.docBuilder.parse(new File(fragDir,fileName));
        Element root=doc.getDocumentElement();
        if(root.getTagName().equals("fragment")) {
            DocumentFragment fragment=doc.createDocumentFragment();
            Node child=root.getFirstChild();
            while(child!=null) {
                Node next=child.getNextSibling();
                fragment.appendChild(child);
                child=next;
            }
            return fragment;
        } else
            throw new RuntimeException("Document element 'fragment' is expected in "+fileName);
    }

    private void scanComments(Node node,List<FragmentDirective> fragments) throws Exception {
        boolean ret=false;
        FragmentContent currentContent=null;

        if(node.getNodeType()==Node.ELEMENT_NODE) {
            Node child=node.getFirstChild();
            while(child!=null) {
                if(currentContent!=null)
                    currentContent.nodes.add(child);
                if(child.getNodeType()==Node.COMMENT_NODE) {
                    String text=((CharacterData)child).getData();
                    if(text!=null) {
                        text=text.trim();
                        if(text.startsWith("Fragment:")) {
                            text=text.substring("Fragment:".length()).trim();
                            fragments.add(new FragmentDirective(text,(Comment)child));
                        } else if(text.startsWith("Fragment Begin:")) {
                            if(currentContent!=null)
                                throw new RuntimeException("Nested fragments are not allowed");
                            text=text.substring("Fragment Begin:".length()).trim();
                            int i=text.lastIndexOf(' ');
                            if(i==-1)
                                throw new RuntimeException("Hash expected:"+text);
                            currentContent=new FragmentContent(text.substring(0,i).trim(),
                                                               text.substring(i+1).trim());
                            currentContent.nodes.add(child);
                        } else if(text.startsWith("Fragment End:")) {
                            if(currentContent==null)
                                throw new RuntimeException("Unexpected fragment end:"+text);
                            text=text.substring("Fragment End:".length()).trim();
                            if(!text.equals(currentContent.fileName))
                                throw new RuntimeException("Mismatched fragment:"+text);
                            currentContent.nodes.add(child);
                            fragments.add(currentContent);
                            currentContent=null;
                        }
                    }
                } else
                    scanComments(child,fragments);
                child=child.getNextSibling();
            }
            if(currentContent!=null)
                throw new RuntimeException("Fragment with no end:"+currentContent);
        }
    }

    /**
     * Update the digest using the subtree rooted at node
     */
    public static MessageDigest digest(MessageDigest digest,Node node) throws Exception {
        switch(node.getNodeType()) {
        case Node.TEXT_NODE:
        case Node.CDATA_SECTION_NODE: 
            update(digest, ((CharacterData)node).getData() );
            break;
        case Node.ELEMENT_NODE:
            update(digest,node.getNodeName());
            NamedNodeMap attrMap=node.getAttributes();
            int n=attrMap.getLength();
            for(int i=0;i<n;i++) {
                Attr attr=(Attr)attrMap.item(i);
                update(digest,(attr.getName()+"="+attr.getValue()));
            }
            Node ch=node.getFirstChild();
            while(ch!=null) {
                digest(digest,ch);
                ch=ch.getNextSibling();
            }
            break;
        }
        return digest;
    }

    private static void update(MessageDigest digest,String s) {
        if(s!=null) {
            s=s.trim();
            if(s.length()>0) {
                digest.update(s.getBytes());
            }
        }
    }

    // Computes digest using only the child nodes of a node (used for fragments)
    public static MessageDigest digestFragment(MessageDigest digest,
                                               Node parentNode) throws Exception {
        Node child=parentNode.getFirstChild();
        while(child!=null) {
            digest(digest,child);
            child=child.getNextSibling();
        }
        return digest;
    }

    public static MessageDigest digest(MessageDigest digest,
                                       List<Node> nodes) throws Exception {
        for(Node node:nodes)
            digest(digest,node);
        return digest;
    }

    public static void main(String[] args) throws Exception {
        File fragdir=new File(".");
        boolean forceRefresh=false;
        List<String> xmlFiles=new ArrayList<String>();
        for(int i=0;i<args.length;i++) {
            if(args[i].startsWith("-f"))
                fragdir=new File(args[i].substring(2));
            else if(args[i].equals("-x"))
                forceRefresh=true;
            else
                xmlFiles.add(args[i]);
        }
        XmlFrag xmlFrag=new XmlFrag(fragdir);
        if(!xmlFiles.isEmpty()) {
            for(String file:xmlFiles) {
                File f=new File(file);
                Document doc=XML.docBuilder.parse(f);
                if(xmlFrag.processXML(f,doc,forceRefresh)) {
                    XML.write(doc,f);
                    System.out.println(file+": updated");
                } else {
                    System.out.println(file+": no changes");
                }
            }
        } else {
            System.out.println("XmlFrag [-ffragmentDir] [-x] xmlfiles...\n"+
                               "\n"+
                               " Maintains/replaces XML fragments in XML files.\n\n"+
                               " -x: forces replacement of fragments\n"+
                               "\n"+
                               "A fragment is an xml file of the form:\n"+
                               "<fragment>\n"+
                               "  some xml content\n"+
                               "</fragment>\n"+
                               "\n"+
                               "If fragmentdir is not given, fragments are read from the current dir\n"+
                               "\n"+
                               "The XML files should contain the following directive:\n"+
                               "<!-- Fragment: filename.xml -->\n"+
                               "The, the contents of 'filename.xml' will be inserted here along with comments containing a has of the fragment.\n"+
                               "If the fragment file is modified, running XmlFrag will update the\n"+
                               "inserted fragment. If the inserted fragment was manually edited, a warning will be given\n"+
                               "and won't be updated.\n");
        }
    }
}
