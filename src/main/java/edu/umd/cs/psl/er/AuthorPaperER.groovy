package edu.umd.cs.psl.er

import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver
import edu.umd.cs.psl.groovy.*
import edu.umd.cs.psl.groovy.experiments.ontology.*
import edu.umd.cs.psl.er.modelevaluation.ModelEvaluation
import edu.umd.cs.psl.er.weightlearning.WeightLearning
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.ui.functions.textsimilarity.*


/*
 * First, we'll parse the command line arguments.
 */
if (args.size() < 1) {
	println "\nusage: AuthorPaperER <data_dir> [ -l ]\n";
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
 * Let's start a timer.
 */
long startTime = System.nanoTime();


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
 * "Open" predicates are ones that must be inferred.
 */
m.add predicate: "authorName" , author  : Entity,  name    : Text;
m.add predicate: "paperTitle" , paper   : Entity,  title   : Text;
m.add predicate: "authorOf"   , author  : Entity,  paper   : Entity;
m.add predicate: "simName"    , name1   : Text,    name2   : Text;
m.add predicate: "simTitle"   , title1  : Text,    title2  : Text;
m.add predicate: "sameAuthor" , author1 : Entity,  author2 : Entity, open: true;
m.add predicate: "samePaper"  , paper1  : Entity,  paper2  : Entity, open: true;
m.add predicate: "sameInitials" , name1 : Text,    name2   : Text;
m.add predicate: "sameNumTokens", title1: Text,    title2  : Text;

/*
 * Set comparison functions operate on sets and return a scalar.
 */
m.add setcomparison: "sameAuthorSet" , using: SetComparison.Equality, on : sameAuthor;

/*
 * Now we can put everything together by defining some rules for our model.
 */

/*
 * Here are some basic rules.
 */
// similar names => same author
m.add rule : (authorName(A1,N1) & authorName(A2,N2) & simName(N1,N2)) >> sameAuthor(A1,A2), weight : 0.9009869263961426;
// similar titles => same paper
m.add rule : (paperTitle(P1,T1) & paperTitle(P2,T2) & simTitle(T1,T2) ) >> samePaper(P1,P2),  weight : 0.9246392859883283;

/*
 * Here are some relational rules.
 * To see the benefit of the relational rules, comment this section out and re-run the script.
 */
// if two references share a common publication, and have the same initials, then => same author
m.add rule : (authorOf(A1,P1)   & authorOf(A2,P2)   & samePaper(P1,P2) &
              authorName(A1,N1) & authorName(A2,N2) & sameInitials(N1,N2)) >> sameAuthor(A1,A2), weight : 1.2102269147345088;
// if two papers have a common set of authors, and the same number of tokens in the title, then => same paper
m.add rule : (sameAuthorSet({P1.authorOf(inv)},{P2.authorOf(inv)}) & paperTitle(P1,T1) & paperTitle(P2,T2) & 
              sameNumTokens(T1,T2)) >> samePaper(P1,P2),  weight : 1.0330894968159279;

/* 
 * Now we'll add a prior to the open predicates.
 */
m.add Prior.Simple, on : sameAuthor, weight: 1E-10;
m.add Prior.Simple, on : samePaper,  weight: 1E-10;

/*
 * We'll also set the activation threshold
 * (Note: the default activation threshold is 0, but we'll override that for this project.)
 */
m.setDefaultActivationParameter(1E-10);

println "done!"

/*** LOAD DATA ***/
println "Creating a new DB and loading data:"

/*
 * This creates a new relational DB.
 * The setup command instructs it to use the H2 driver, with memory as a backing store.
 */
DataStore data = new RelationalDataStore(m);
//data.setup db: DatabaseDriver.H2, type: "memory";
data.setup db: DatabaseDriver.H2, folder: "/scratch/";

/*
 * These are just some constants that we'll use to reference data files and DB partitions.
 * To change the dataset (e.g. big, medium, small, tiny), change the dir variable.
 */
int trainingFold = 0;
int testingFold = 1;
int evidenceTrainingPartition = 1;
int evidenceTestingPartition = 2;
int targetTrainingPartition = 3;
int targetTestingPartition = 4;

/* 
 * Now we'll load some data from tab-delimited files into the DB.
 * Note that the second parameter to each call to loadFromFile() determines the DB partition.
 */
def sep = java.io.File.separator;
def insert;

/* 
 * We start by reading in the non-target (i.e. evidence) predicate data.
 */
for (Predicate p1 : [authorName, paperTitle, authorOf, sameInitials, sameNumTokens])
{
	String trainFile = datadir + p1.getName() + "." + trainingFold + ".txt";
	print "\tReading " + trainFile + " ... ";
	insert = data.getInserter(p1,evidenceTrainingPartition);
	insert.loadFromFile(trainFile);
	println "done!"

	String testFile = datadir + p1.getName() + "." + testingFold + ".txt";
	print "\tReading " + testFile + " ... ";
	insert = data.getInserter(p1,evidenceTestingPartition);
	insert.loadFromFile(testFile);
	println "done!"
}
/*
 * This is the precomputed similarity predicate data.
 */
for (Predicate p2 : [simName, simTitle])
{
	String trainFile = datadir + p2.getName() + "." + trainingFold + ".txt";
	print "\tReading " + trainFile + " ... ";
	insert = data.getInserter(p2,evidenceTrainingPartition);
	insert.loadFromFileWithTruth(trainFile);
	println "done!"

	String testFile = datadir + p2.getName() + "." + testingFold + ".txt";
	print "\tReading " + testFile + " ... ";
	insert = data.getInserter(p2,evidenceTestingPartition);
	insert.loadFromFileWithTruth(testFile);
	println "done!"
}
/* 
 * Now we read the target predicate data.
 */
for (Predicate p3 : [sameAuthor,samePaper])
{
	//training data
	String trainFile = datadir + p3.getName() + "." + trainingFold + ".txt";
	print "\tReading " + trainFile + " ... ";
	insert = data.getInserter(p3,targetTrainingPartition)
	insert.loadFromFileWithTruth(trainFile);
	println "done!"
	
	//testing data
	String testFile = datadir + p3.getName() + "." + testingFold + ".txt";
	print "\tReading " + testFile + " ... ";
	insert = data.getInserter(p3,targetTestingPartition)
	insert.loadFromFileWithTruth(testFile);
	println "done!"
}

/*** WEIGHT LEARNING ***/

/*
 * This is how we perform weight learning.
 * Note that one must specify the open predicates and the evidence and target partitions.
 */
if (learnWeights)
{
	print "Starting weight learning ... ";
	WeightLearning w = new WeightLearning(m,data);
	w.setConfigBundle(cb);
	m = w.learnModel([sameAuthor, samePaper], evidenceTrainingPartition, targetTrainingPartition);
	println "done!"
	println "\tWeight learning took " + w.printRunningTime() + " secs";
	
	/*
	 * Now let's print the model to see the learned weights.
	 */
	println "Learned model:\n";
	println m;
}

/*** INFERENCE ***/

/*
 * You can uncomment this section if you'd like to evaluate inference on the training set.
 */
println "Starting inference on the training fold ... ";
ModelEvaluation eval0 = new ModelEvaluation(m,data);
eval0.setConfigBundle(cb);
eval0.evaluateModel([sameAuthor, samePaper], evidenceTrainingPartition, targetTrainingPartition, [1574*1573,1116*1115]);

/*
 * Note: to run evaluation of ER inference, we need to specify the total number of
 * pairwise combinations of authors and papers, which we pass to evaluateModel() in an array.
 * This is for memory efficiency, since we don't want to actually load truth data for all
 * possible pairs (though one could).
 */
println "Starting inference on the testing fold ... ";
ModelEvaluation eval1 = new ModelEvaluation(m,data);
eval1.setConfigBundle(cb);
eval1.evaluateModel([sameAuthor, samePaper], evidenceTestingPartition, targetTestingPartition, [1316*1315,809*808]);


