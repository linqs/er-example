package edu.umd.cs.psl.er

import java.io.FileReader
import java.util.concurrent.TimeUnit

import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver
import edu.umd.cs.psl.er.evaluation.ModelEvaluation
import edu.umd.cs.psl.er.similarity.DiceSimilarity;
import edu.umd.cs.psl.er.similarity.SameInitials
import edu.umd.cs.psl.er.similarity.SameNumTokens
import edu.umd.cs.psl.groovy.*
import edu.umd.cs.psl.groovy.experiments.ontology.*
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.ui.functions.textsimilarity.*
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
m.add predicate: "authorOf"   , author  : Entity,  paper   : Entity;
m.add function:  "simName"    , name1   : Text,    name2   : Text	, implementation: new LevenshteinStringSimilarity(0.5);
m.add function:  "simTitle"   , title1  : Text,    title2  : Text	, implementation: new DiceSimilarity(0.5);
m.add function:  "sameInitials", name1  : Text,    name2   : Text	, implementation: new SameInitials();
m.add function:  "sameNumTokens", title1: Text,    title2  : Text	, implementation: new SameNumTokens();
m.add predicate: "sameAuthor" , author1 : Entity,  author2 : Entity, open: true;
m.add predicate: "samePaper"  , paper1  : Entity,  paper2  : Entity, open: true;
/*
 * Set comparison functions operate on sets and return a scalar.
 */
m.add setcomparison: "sameAuthorSet" , using: SetComparison.CrossEquality, on : sameAuthor;

/*
 * Now we can put everything together by defining some rules for our model.
 */

/*
 * Here are some basic rules.
 */
// similar names => same author
m.add rule : (authorName(A1,N1) & authorName(A2,N2) & simName(N1,N2)) >> sameAuthor(A1,A2), weight : 1.0;
// similar titles => same paper
m.add rule : (paperTitle(P1,T1) & paperTitle(P2,T2) & simTitle(T1,T2) ) >> samePaper(P1,P2),  weight : 1.0;

/*
 * Here are some relational rules.
 * To see the benefit of the relational rules, comment this section out and re-run the script.
 */
// if two references share a common publication, and have the same initials, then => same author

m.add rule : (authorOf(A1,P1)   & authorOf(A2,P2)   & samePaper(P1,P2) &
              authorName(A1,N1) & authorName(A2,N2) & sameInitials(N1,N2)) >> sameAuthor(A1,A2), weight : 1.0;
// if two papers have a common set of authors, and the same number of tokens in the title, then => same paper
m.add rule : (sameAuthorSet({P1.authorOf(inv)},{P2.authorOf(inv)}) & paperTitle(P1,T1) & paperTitle(P2,T2) & 
             sameNumTokens(T1,T2)) >> samePaper(P1,P2),  weight : 1.0;

/* 
 * Now we'll add a prior to the open predicates.
 */
m.add Prior.Simple, on : sameAuthor, weight: 1E-6;
m.add Prior.Simple, on : samePaper,  weight: 1E-6;

/*
 * We'll also set the activation threshold
 * (Note: the default activation threshold is 0, but we'll override that for this project.)
 */
m.setDefaultActivationParameter(1E-10);

println "done!"

/*** LOAD DATA ***/
println "Creating a new DB and loading data:"

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
data.setup db: DatabaseDriver.H2, folder: "/tmp/";

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
for (Predicate p1 : [authorName,paperTitle,authorOf])
{
	String trainFile = datadir + p1.getName() + "." + trainingFold + ".txt";
	print "  Reading " + trainFile + " ... ";
	insert = data.getInserter(p1,evidenceTrainingPartition);
	insert.loadFromFile(trainFile);
	println "done!"

	String testFile = datadir + p1.getName() + "." + testingFold + ".txt";
	print "  Reading " + testFile + " ... ";
	insert = data.getInserter(p1,evidenceTestingPartition);
	insert.loadFromFile(testFile);
	println "done!"
}
/* 
 * Now we read the target predicate data.
 */
for (Predicate p3 : [sameAuthor,samePaper])
{
	//training data
	String trainFile = datadir + p3.getName() + "." + trainingFold + ".txt";
	print "  Reading " + trainFile + " ... ";
	insert = data.getInserter(p3,targetTrainingPartition)
	insert.loadFromFileWithTruth(trainFile);
	println "done!"
	
	//testing data
	String testFile = datadir + p3.getName() + "." + testingFold + ".txt";
	print "  Reading " + testFile + " ... ";
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
	/*
	 * We need to setup some weight learning parameters.
	 */
	def learningConfig = new WeightLearningConfiguration();
	learningConfig.setLearningType(WeightLearningConfiguration.Type.LBFGSB);	// limited-memory BFGS optimization
	learningConfig.setPointMoveConvergenceThres(1E-5);							// convergence threshold
	learningConfig.setMaxOptIterations(100);									// maximum iterations
	learningConfig.setParameterPrior(1);										// 1/variance
	learningConfig.setRuleMean(0.1);											// init weight value for rules
	learningConfig.setUnitRuleMean(1.0);										// init weight value for priors
	learningConfig.setActivationThreshold(1E-10);								// rule activation threshold

	/*
	 * Now we run the learning algorithm.
	 */
	print "\nStarting weight learning ... ";
	startTime = System.nanoTime();
	m.learn data, evidence:evidenceTrainingPartition, infered:targetTrainingPartition, close:[sameAuthor,samePaper], configuration:learningConfig, config:cb;
	endTime = System.nanoTime();
	println "done!"
	println "  Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + " secs";
	
	/*
	 * Now let's print the model to see the learned weights.
	 */
	println "Learned model:\n";
	println m;
}

/*** INFERENCE ***/

/*
 * Note: to run evaluation of ER inference, we need to specify the total number of
 * pairwise combinations of authors and papers, which we pass to evaluateModel() in an array.
 * This is for memory efficiency, since we don't want to actually load truth data for all
 * possible pairs (though one could).
 *
 * To get the total number of possible combinations, we'll scan the author/paper reference files,
 * counting the number of lines.
 */
int[] authorCnt = new int[2];
int[] paperCnt = new int[2];
FileReader rdr = null;
for (int i = 0; i < 2; i++) {
	rdr = new FileReader(datadir + "authorName." + i + ".txt");
	while (rdr.readLine() != null) authorCnt[i]++;
	println "Authors fold " + i + ": " + authorCnt[i];
	rdr = new FileReader(datadir + "paperTitle." + i + ".txt");
	while (rdr.readLine() != null) paperCnt[i]++;
	println "Papers  fold " + i + ": " + paperCnt[i];
}

/*
 * Let's create an instance of our evaluation class.
 */
def eval = new ModelEvaluation(data);

/*
 * Evalute inference on the training set.
 */
print "\nStarting inference on the training fold ... ";
startTime = System.nanoTime();
def trainingInference = m.mapInference(data.getDatabase(read: evidenceTrainingPartition), cb);
endTime = System.nanoTime();
println "done!";
println "  Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + " secs";
eval.evaluateModel(trainingInference, [sameAuthor, samePaper], targetTrainingPartition, [authorCnt[0]*(authorCnt[0]-1), paperCnt[0]*(paperCnt[0]-1)]);

/*
* Now evaluate inference on the testing set (to check model generalization).
*/
print "\nStarting inference on the testing fold ... ";
startTime = System.nanoTime();
def testingInference = m.mapInference(data.getDatabase(read: evidenceTestingPartition), cb);
endTime = System.nanoTime();
println "done!";
println "  Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + " secs";
eval.evaluateModel(testingInference, [sameAuthor, samePaper], targetTestingPartition, [authorCnt[1]*(authorCnt[1]-1), paperCnt[1]*(paperCnt[1]-1)]);

