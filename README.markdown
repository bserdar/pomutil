Pomutils deals with POM trees. You can

  * Run queries and/or operations on POM trees
  * Generate root POMs to build only the required sub projects of a
    large project based on a build manifest
  * Move common POM fragments into individual XML files, and maintain
    them

Querying and Modifying POM files
--------------------------------

## Querying a POM file

Prints the results of an XPath based on a POM file.

    pomutil <pomfile> -xp<XPath>

Example:

Print out the version of a project:

    pomutil pom.xml -xp/project/version

## Check version number sanity accross POM files

Determines artifact cross references in a POM tree, and prints
inconsistencies where a version of the artifact is built, but a
different version of that artifact is referenced in another part of
the tree.

    pomutil <pomfile> -x

## Print versions of all artifacts in a POM tree

Prints out all artifact names and versions

    pomutil <pomfile> -p

Each artifact is written in a separate line with the following format:

    groupId:artifactId=version

## Change version number of an artifact in a POM tree

Changes the version number of an artifact in the POM file where it is
defined, and in all POM files where it is referenced.

    pomutil <pomfile> [-a] -vgroupId:artifactId:version

Sets the version number of `groupId:artifact` to `version`. The `-a`
switch rewrites all poms. Without `-a`, only modified poms are written.

## Bulk change version

    pomutil <pomfile> -fFile

where `File` is a text file that uses the same format as the `-p` (print
all versions) option.

You can print all versions of a project to a file, then edit that file
to set new version numbers, and use this feature to set version
numbers in bulk:

    pomutil pom.xml -p>versions
    # edit versions file to set the new version numbers
    pomutil pom.xml -fversions

## Find projects in a POM tree depending on an artifact

    pomutil <pomfile> -dfgroupId:artifactId:version

Lists all the pom files in which `groupId:artifactId:version` appears as
a direct dependency. version can be "`*`", meaning any matching
`groupId:artifactId` will be listed.

## Remove a direct dependency from a POM tree

    pomutil <pomfile> -drgroupId:artifactId:version

Remove al occurances of `groupId:artifactId:version` from all projects
in the tree. version can be "`*`".


# Partial Builds

It is rarely necessary to build all modules of a large project
containing many submodules. Pomutil can generate root POM files for
such projects based on a build manifest listing the sub modules that
need to be built. The generated POM file build all the modules given
in the build manifest, and all the modules that depend on the modules
in the build set.

## Project structure

Consider an aggregator style root POM listing basic configuration and
all the sub projects:

`code/pom.xml`:

    <project>
      ...
       <modules>
          <module>module1</module>
          <module>module2</module>
           ...
          <module>moduleN</module>
       </modules>
      ...
    </project>


Every build of this project requires that all the submodules
`module1,...,moduleN` are also built. If our workset is limited to, say,
only `module1`, it is not necessary to rebuild all submodules every
time. What is required is that you build module1 to test/release your
changes, and you build any module that depends on `module1`, so those
can be release as well now that they are using a newer version of
`module1`. To do this, a build manifest is prepared:


`myworkset.xml`:

    <manifest>
       <buildset>
          <module>module1</module>
       </buildset>
    </manifest>


We also need a definition of all the available modules:


`all.mf.xml`:

    <manifest>
       <modulemap>
          <module>
             <name>module1</name>
             <pom>module1/pom.xml</pom>
          </module>
          <module>
             <name>module2</name>
             <pom>module2/pom.xml</pom>
          </module>
          ...
          <module>
             <name>moduleN</name>
             <pom>moduleN/pom.xml</pom>
          </module>
       </modulemap>
    </manifest>


Last, we need to provide a skeleton POM file containing all the
project related configuration we need at the root pom level:

`skel.xml`:

    <project>
       <modelVersion>4.0.0.</modelVersion>
       <groupId>autogen</groupId>
       <artifactId>root</artifactId>
       <version>0</version>
       <packaging>pom</packaging>
       <name>autogen</name>
       <dependencies>
         ...
       </dependencies>
       <build>
         ...
       </build>
       <modules>
       </modules>
    </project>


Note the empty modules element.

Then, running

    pomutil -rmyworkset.xml -lall.mf.xml -opom.xml -sskel.xml

will generate a `pom.xml` file, using `skel.xml` as a baseline, adding
all the modules listed in the build manifest and the modules that are
dependent on that build set.
      
# XMLFrag

Aggregator style Maven projects have some limitations. Most of the
common project information is stored in the root pom, and child
projects inherit from it. It is not possible to modularize such
projects so that sligthly different configurations can be applied for
certain targets. Inheriting from multiple root poms is not allowed, so
sub projects end up overriding things setup in root pom.

Instead of using a root pom, a large project structure can be divided
into individual smaller projects, with fragments of POM files are
stored in a different location. XMLFrag reconstructs POM files using
such fragments.

Move sections of POM files into fragments. For instance:

Original POM file:

    <project>
       ...
       <dependencies>
         <dependency>
          ...
         </dependency>
       </dependencies>
       ...


Move dependencies to a fragment:

`fragments/dependencies.frag.xml`:

    <fragment>
       <dependency> 
         ...
       <dependency>
    </fragment>

POM file:

    <project>
       ...
       <dependencies>
       <!--Fragment: dependencies.frag.xml-->
       </dependencies>
       ...
    </project>

Then, run XMLFrag:

    xmlfrag -ffragments pom.xml

This command inserts the contents of `fragments/dependencies.frag.xml`
into the pom file. It also replaces the fragment comment with hash
information, so that if the fragment is modified, the modifications
are applied to the pom file correctly when `xmlfrag` is run again. If
fragments inserted into the POM file using `xmlfrag` is manually
modified, `xmlfrag` will warn when executed. `-x` switch forces
replacement of the fragments in pom files.
