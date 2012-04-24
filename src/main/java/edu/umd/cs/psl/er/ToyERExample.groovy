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

/*
 * We'll use the ConfigManager to access configurable properties.
 * This file is usually in <project>/src/main/resources/psl.properties
 */
ConfigManager cm = ConfigManager.getManager();
ConfigBundle cb = cm.getBundle("er");

/*** MODEL DEFINITION ***/
println "Setting up psl model"
PSLModel m = new PSLModel(this);



/*
 * These are the predicates for our model.
 * Predicates are precomputed.
 * Functions are computed online.
 * "Open" predicates are ones that must be inferred.
 */
m.add predicate: "authorName",	author  : Entity,	name    : Text;
m.add predicate: "coauthor",	author1 : Entity,	author2 : Entity;
m.add function:  "similar", 	name1   : Text,		name2   : Text, implementation: new LevenshteinStringSimilarity();

m.add predicate: "equal", author1 : Entity,  author2 : Entity, open: true;
m.add predicate: "sameName", name1 : Text, name2 : Text, open: true;



/*
 * Here are some basic rules.
 */
// symmetry
m.add rule : equal(A1,A2) >> equal(A2,A1), constraint: true;

// similar names => same author
m.add rule : (authorName(A1,N1) & authorName(A2,N2) & similar(N1,N2)) >> equal(A1,A2), weight : 0.1;

/*
 * Here are some relational rules.
 * To see the benefit of the relational rules, comment this section out, recompile, and re-run.
 */
// coauthors of the same author are likely the same person
//m.add rule : (coauthor(A1,C1) & coauthor(A2,C2) & equal(C1,C2)) >> equal(A1,A2), weight : 0.1;



// extra constraints allows easily readable output pairs
m.add rule : (authorName(A1,N1) & authorName(A2,N2) & equal(A1,A2)) >> sameName(N1,N2), constraint: true;

/* 
 * Now we'll add a prior to the open predicates.
 */
m.add Prior.Simple, on : equal, weight: 0.01;



/*** LOAD DATA ***/
println "Creating a new DB and loading data"

/*
 * We'll create a new relational DB.
 */
DataStore data = new RelationalDataStore(m);
data.setup db: DatabaseDriver.H2, type: "memory";

/* 
 * Now we'll load some data from tab-delimited files into the DB.
 * Note that the second parameter to each call to loadFromFile() determines the DB partition.
 */
def insert;

/* 
 * We start by reading in the non-target (i.e. evidence) predicate data.
 */

nameFile = "toy_examples/A/authorName.txt";
coauthorFile = "toy_examples/A/coauthor.txt"

insert = data.getInserter(authorName,0);
insert.loadFromFile(nameFile);
insert = data.getInserter(coauthor,0);
insert.loadFromFile(coauthorFile);


/*** INFERENCE ***/

println "Running inference"

def trainingInference = m.mapInference(data.getDatabase(read: 0), cb);

// Print out results

println "sameName:"
trainingInference.printAtoms(sameName,true);





