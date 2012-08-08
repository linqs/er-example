package edu.umd.cs.psl.er

import java.io.FileReader
import java.util.concurrent.TimeUnit

import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver
import edu.umd.cs.psl.er.evaluation.ModelEvaluation
import edu.umd.cs.psl.er.similarity.DiceSimilarity
import edu.umd.cs.psl.er.similarity.JaroWinklerSimilarity
import edu.umd.cs.psl.er.similarity.JaccardSimilarity
import edu.umd.cs.psl.er.similarity.JaroSimilarity
import edu.umd.cs.psl.er.similarity.Level2JaroWinklerSimilarity
import edu.umd.cs.psl.er.similarity.Level2LevensteinSimilarity
import edu.umd.cs.psl.er.similarity.Level2MongeElkanSimilarity
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

/* We now iterate through all models. To run all three models, uncomment the all models line
* for now, we only run the attribute and basic relational model
* adding the coauthor logic creates many more variables in the optimization so can be 
* considerably more expensive.
*/
//for (String model : ["attribute", "relational", "relational_coauthor"]) {
for (String model : ["attribute", "relational"]) {

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

	/* 
	 * Here we define the first similarity function. We declare the implementation as an external class, which takes 
	 * in its constructor a threshold value. All similarity scores below the threshold value are clipped to have similarity 0.0,
	 * which reduces the number of active variables during inference. Setting this threshold too low can activate too many variables,
	 * but setting it too high can clip too many possibly relevant pairs. 
     */
	m.add function:  "simName"    , name1   : Text,    name2   : Text	, implementation: new LevenshteinStringSimilarity(0.5);
	//m.add function:  "simName"    , name1   : Text,    name2   : Text	, implementation: new JaroSimilarity(0.5);
	//m.add function:  "simName"    , name1   : Text,    name2   : Text	, implementation: new Level2MongeElkanSimilarity(0.5);
	m.add function:  "simTitle"   , title1  : Text,    title2  : Text	, implementation: new DiceSimilarity(0.5);
	m.add function:  "sameInitials", name1  : Text,    name2   : Text	, implementation: new SameInitials();
	m.add function:  "sameNumTokens", title1: Text,    title2  : Text	, implementation: new SameNumTokens();
	m.add predicate: "sameAuthor" , author1 : Entity,  author2 : Entity, open: true;
	m.add predicate: "samePaper"  , paper1  : Entity,  paper2  : Entity, open: true;
	m.add predicate: "coauthor"	, author1: Entity, author2: Entity, open: true


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

	if (model.equals("relational") || model.equals("relational_coauthor")) {
		/*
		 * Here are some relational rules.
		 * To see the benefit of the relational rules, comment this section out and re-run the script.
		 */
		// if two references share a common publication, and have the same initials, then => same author
		m.add rule : (authorOf(A1,P1)   & authorOf(A2,P2)   & samePaper(P1,P2) &
		              authorName(A1,N1) & authorName(A2,N2) & sameInitials(N1,N2)) >> sameAuthor(A1,A2), weight : 1.0;
		// if two papers have a common set of authors, and the same number of tokens in the title, then => same paper
		// Note the usage of set operations for the sameAuthorSet set similarity. The inv operator represents the set of 
		// instances related via the authorOf relation. 
		m.add rule : (sameAuthorSet({P1.authorOf(inv)},{P2.authorOf(inv)}) & paperTitle(P1,T1) & paperTitle(P2,T2) & 
		             sameNumTokens(T1,T2)) >> samePaper(P1,P2),  weight : 1.0;
	}

	if (model.equals("relational_coauthor")) {
		m.add Prior.Simple, on : coauthor, weight: 1E-6

		m.add rule : (authorOf(A1,P) & authorOf(A2,P)) >> coauthor(A1,A2), weight : 1.0
		m.add rule : (coauthor(A1,A2) & sameAuthor(A2,A3) & authorName(A2,N2) & authorName(A3,N3) & sameInitials(N2,N3)) >> coauthor(A1,A3), weight: 1.0
		m.add rule : (coauthor(A1,A2) & coauthor(A2,A3) & authorName(A1,N1) & authorName(A3,N3) & sameInitials(N1,N3)) >> sameAuthor(A1,A3), weight: 1.0
	}

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
	int testingFold = 0;
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
	for (Predicate p1 : [authorName,paperTitle,authorOf])
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
	for (Predicate p3 : [sameAuthor,samePaper])
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
	 * Note: to run evaluation of ER inference, we need to specify the total number of
	 * pairwise combinations of authors and papers, which we pass to evaluateModel() in an array.
	 * This is for memory efficiency, since we don't want to actually load truth data for all
	 * possible pairs (though one could).
	 *
	 * To get the total number of possible combinations, we'll scan the author/paper reference files,
	 * counting the number of lines.
	 */
	int[] authorCnt = new int[1];
	int[] paperCnt = new int[1];
	FileReader rdr = null;
	for (int i = 0; i < 1; i++) {
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
	* Now evaluate inference on the testing set (to check model generalization).
	*/
	print "\nStarting inference on the testing fold ... ";
	startTime = System.nanoTime();
	def testingInference = m.mapInference(data.getDatabase(read: evidenceTestingPartition), cb);
	endTime = System.nanoTime();
	println "done!";
	println "  Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds(endTime - startTime) + " secs";
	//eval.evaluateModel(testingInference, [sameAuthor, samePaper], targetTestingPartition, [authorCnt[0]*(authorCnt[0]-1), paperCnt[1]*(paperCnt[1]-1)]);

	/*****
	Output predictions to file
	******/
	def writer = new FileAtomPrintStream("outputs/"+model+".txt", " ")
	testingInference.printAtoms(sameAuthor, writer, false)
}
