package edu.umd.cs.psl.er

import java.io.FileReader;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver
import edu.umd.cs.psl.groovy.*
import edu.umd.cs.psl.groovy.experiments.ontology.*
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.evaluation.resultui.UIFullInferenceResult

import edu.umd.cs.psl.er.evaluation.ModelEvaluation
import edu.umd.cs.psl.er.similarity.DiceSimilarity;
import edu.umd.cs.psl.er.similarity.JaroWinklerSimilarity;
import edu.umd.cs.psl.er.similarity.SameInitials
import edu.umd.cs.psl.er.similarity.SameNumTokens
import edu.umd.cs.psl.er.evaluation.FileAtomPrintStream;

/*
 * Start and end times for timing information.
 */
def startTime;
def endTime;

/*
 * First, we'll parse the command line arguments.
 */
if (args.size() < 1) {
	println "\nUsage: AuthorPaperER <data_dir> [ -l ]\n";
	return 1;
}
def datadir = args[0];
if (!datadir[datadir.size()-1].equals("/"))
	datadir += "/";
boolean learnWeights = false;
if (args.size() >= 2) {
	learnWeights = args[1..(args.size()-1)].contains("-l");
}
println "\n*** PSL ER EXAMPLE ***\n"
println "Data directory  : " + datadir;
println "Weight learning : " + (learnWeights ? "ON" : "OFF");


/*
 * We'll use the ConfigManager to access configurable properties.
 * This file is usually in <project>/src/main/resources/psl.properties
 */
ConfigManager cm = ConfigManager.getManager();
ConfigBundle cb = cm.getBundle("er");


/*** MODEL DEFINITION ***/
print "Creating the ER model ... "

PSLModel m = new PSLModel(this);

/*
 * These are the predicates for our model.
 * Predicates are precomputed.
 * Functions are computed online.
 * "Open" predicates are ones that must be inferred.
 */
m.add predicate: "authorName" , author  : Entity,  name    : Text;
m.add predicate: "paperTitle" , paper   : Entity,  title   : Text;
m.add predicate: "paperVenue" , paper   : Entity,  venue   : Entity;
m.add predicate: "venueName"  , venue   : Entity,  title   : Text;
m.add predicate: "authorOf"   , author  : Entity,  paper   : Entity;
m.add function:  "simName"    , name1   : Text,    name2   : Text	, implementation: new LevenshteinStringSimilarity(0.2);
m.add function:  "simTitle"   , title1  : Text,    title2  : Text	, implementation: new LevenshteinStringSimilarity(0.2);
m.add function:  "sameInitials", name1  : Text,    name2   : Text	, implementation: new SameInitials();
m.add predicate: "sameAuthor" , author1 : Entity,  author2 : Entity, open: true;
m.add predicate: "samePaper"  , paper1  : Entity,  paper2  : Entity, open: true;
m.add predicate: "sameVenue"  , venue1  : Entity,  venue2  : Entity, open: true;

/*
 * Now we can put everything together by defining some rules for our model.
 */



/*
 * Here are some basic rules.
 */
// similar names => same author
m.add rule : (authorName(A1,N1) & authorName(A2,N2) & simName(N1,N2)) >> sameAuthor(A1,A2), weight : 1.0;
// similar titles => same paper
m.add rule : (paperTitle(P1,T1) & paperTitle(P2,T2) & simTitle(T1,T2)) >> samePaper(P1,P2),  weight : 1.0;
// similar venues => same venue
m.add rule : (venueName(V1,T1) & venueName(V2,T2) & simTitle(T1,T2)) >> sameVenue(V1,V2), weight : 1.0;

/*
 * Here are some relational rules.
 * To see the benefit of the relational rules, comment this section out and re-run the script.
 */

// If two papers are the same, their authors are the same
m.add rule : (authorOf(A1,P1)   & authorOf(A2,P2)   & samePaper(P1,P2)) >> sameAuthor(A1,A2), weight : 1.0;
// If two papers are the same, their venues are the same
m.add rule : (paperVenue(P1,V1) & paperVenue(P2,V2) & samePaper(P1,P2)) >> sameVenue(V1,V2), weight : 1.0;


/* 
 * Now we'll add a prior to the open predicates.
 */
m.add Prior.Simple, on : sameAuthor, weight: 1E-6;
m.add Prior.Simple, on : samePaper,  weight: 1E-6;
m.add Prior.Simple, on : sameVenue,  weight: 1E-6;

println "done!"

/*** LOAD DATA ***/
println "Creating a new DB and loading data:"
for (testingFold = 0 ; testingFold < 4; testingFold++) {

/*
 * We'll create a new relational DB.
 */
DataStore data = new RelationalDataStore(m);
/*
 * The setup command instructs the DB to use the H2 driver.
 * It can also tell it to use memory as its backing store, or alternately a
 * specific directory in the file system. If neither is specified, the default
 * location is a file in the project root directory.
 * NOTE: In our experiments, we have found that using the hard drive performed
 * better than using main memory, though this may vary from system to system.
 */
//data.setup db: DatabaseDriver.H2;
//data.setup db: DatabaseDriver.H2, type: "memory";
data.setup db: DatabaseDriver.H2, folder: "/tmp/tmp2";

/*
 * These are just some constants that we'll use to reference data files and DB partitions.
 * To change the dataset (e.g. big, medium, small, tiny), change the dir variable.
 */

int evidenceTestingPartition = 1;
int targetTestingPartition = 2;

/* 
 * Now we'll load some data from tab-delimited files into the DB.
 * Note that the second parameter to each call to loadFromFile() determines the DB partition.
 */
def sep = java.io.File.separator;
def insert;

/* 
 * We start by reading in the non-target (i.e. evidence) predicate data.
 */
for (Predicate p1 : [authorName,paperTitle,authorOf,paperVenue,venueName])
{
	String testFile = datadir + p1.getName() + "." + testingFold + ".txt";
	print "  Reading " + testFile + " ... ";
	insert = data.getInserter(p1,evidenceTestingPartition);
	insert.loadFromFile(testFile);
	println "done!"
}
/* 
 * Now we read the target predicate data.
 */
for (Predicate p3 : [sameAuthor,samePaper,sameVenue])
{
	//testing data
	String testFile = datadir + p3.getName() + "." + testingFold + ".txt";
	print "  Reading " + testFile + " ... ";
	insert = data.getInserter(p3,targetTestingPartition)
	insert.loadFromFileWithTruth(testFile);
	println "done!"
}

/*** INFERENCE ***/

/*
 * Evalute inference on the testing set.
 */
print "\nStarting inference on the testing fold ... ";
startTime = System.nanoTime();
def testingInference = m.mapInference(data.getDatabase(read: evidenceTestingPartition), cb);
endTime = System.nanoTime();
println "done!";
println "  Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + " secs";

def writer = new FileAtomPrintStream("coraAuthor"+testingFold+".txt", " ")
//def writer = new FileAtomPrintStream("attribute.txt", " ")
testingInference.printAtoms(sameAuthor, writer, false)

writer = new FileAtomPrintStream("coraPaper"+testingFold+".txt", " ")
//def writer = new FileAtomPrintStream("attribute.txt", " ")
testingInference.printAtoms(samePaper, writer, false)

writer = new FileAtomPrintStream("coraVenue"+testingFold+".txt", " ")
//def writer = new FileAtomPrintStream("attribute.txt", " ")
testingInference.printAtoms(sameVenue, writer, false)

}