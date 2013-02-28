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

/**
 * @author Burak Serdar (bserdar@redhat.com)
 */
public class Artifact {
    final String groupId;
    final String artifactId;
    final String version;
    
    public Artifact(String g,String a, String v) {
        groupId=g;
        artifactId=a;
        version=v;
    }
    
    
    static public Artifact parse(String s) {
        int index=s.indexOf(':');
        String g=s.substring(0,index);
        s=s.substring(index+1);
        index=s.indexOf(':');
        String a=s.substring(0,index);
        String v=s.substring(index+1);
        return new Artifact(g,a,v);
    }
    
    public String toString() {
        return groupId+":"+artifactId+":"+version;
    }
}
