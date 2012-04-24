*********************************************************
* SETUP INSTRUCTIONS FOR PSL ER EXAMPLE *
*********************************************************

The following instructions detail the installation procedure for PSL.

1) Install Git: Git is a popular version control system that we are using to manage our repositories. It can be found here:

	http://git-scm.com/


2) Install Maven: Apache Maven is a tool for managing dependencies. It can be found here:

	http://maven.apache.org/


3) Obtain Mosek: Mosek is the optimization library used in PSL. Unfortunately, this is neither free nor open-source software; you must request an academic license on the company's site. (They have been pretty quick about responding to license requests.) You will obtain a license file that will work for 90 days; after this period, you can request another license. Mosek can be found here:

	http://mosek.com/

Be sure to set the appropriate environment variables for Mosek. For 64-bit Linux, you should have the following in your bash profile:

	LD_LIBRARY_PATH=<path_to_mosek>/mosek//6/tools/platform/linux64x86/bin
	MOSEKLM_LICENSE_FILE=<path_to_mosek>/mosek/6/licenses/mosek.lic

(Note: <path_to_mosek> should be replaced with the path to wherever you installed Mosek.)

Run the following command to install MOSEK into your maven repository

	>> mvn install:install-file -Dfile=<path-to-mosek.jar> -DgroupId=com.mosek -DartifactId=mosek -Dversion=6.0 -Dpackaging=jar

For instructions for other platforms, go to:

	http://docs.mosek.com/6.0/toolsinstall/node003.html
	

4) Download the ER example: The example is hosted on Github. To download the files, simply execute the following command:

	>> git clone git@github.com:linqs/er-example.git

Once you've downloaded everything, uncompress the data files (data.tar.gz) to the project directory.


5) Run the ER example: There are several steps necessary to run the program:

5a) Compile the program using the command:

	>> mvn compile 
	
5b) Generate the classpath using the command:

	>> mvn dependency:build-classpath -Dmdep.outputFile=classpath.out  

	Note: you only have to do this once, and after that only if the dependencies change. i.e. If you just make a small tweak to the source code, you don't need to re-generate the classpath.

5c) Run the program with the following command (from within the project's directory):

	>> java -Xmx2048m -cp ./target/classes:`cat classpath.out` edu.umd.cs.psl.er.AuthorPaperER <data_dir> [ -l ]

where <data_dir> is the relative path to the data (e.g. data/CiteSeer/big) and the optional parameter "-l" invokes weight learning (as opposed to manual weights, which are by default all 1).

Note: the "-Xmx2048m" option increases the maximum heap size to 2GB. Make sure your system can handle this. If you opt to load all of the data into main memory, you may want to up this number to 4-8GB.


6) (Optional) Create an Eclipse project: To convert a Maven project to an Eclipse project, simply type the following command (within the project's directory):

	>> mvn eclipse:eclipse

Then simply import the project into your Eclipse workspace.


For more information, please refer to our PSL wiki at:

	https://github.com/linqs/psl/wiki


***********************************
* A NOTE ON THE ER MODEL *
***********************************

This dataset has been designed such that simple string matching alone will be ineffective. Relational rules are necessary to achieve reasonable accuracy. To see the effect of the relational rules, simply comment them out and re-run the model.


