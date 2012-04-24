package edu.umd.cs.psl.er.evaluation

import java.util.List;

import edu.umd.cs.psl.evaluation.resultui.UIFullInferenceResult;
import edu.umd.cs.psl.evaluation.statistics.ResultComparator;
import edu.umd.cs.psl.evaluation.statistics.ResultComparison.BinaryClass;
import edu.umd.cs.psl.groovy.DataStore;
import edu.umd.cs.psl.model.predicate.Predicate;

/*
 * This class contains code to perform evaluation, specific to our ER example project.
 */
class ModelEvaluation
{
	private DataStore dataStore;
	
	//constructor
	public ModelEvaluation(DataStore newDataStore){
		this.dataStore = newDataStore;
	}

	// data store
	public DataStore getDataStore() {
		return dataStore;
	}
	public void setDataStore(DataStore newDataStore) {
		this.dataStore = newDataStore;
	}
	
	/*
	 * This overload requires the *full* ground truth (both positives and negatives) to exist in the DB. 
	 */
	public void evaluateModel(UIFullInferenceResult result, List<Predicate> openPredicates, int baselinePartition) {
		ResultComparator comparator = result.compareResults();
		comparator.setBaseline(dataStore.getDatabase(read: baselinePartition));
		println("Model Evaluation: ");
		for (predicate in openPredicates) {
			println("  Predicate: " + predicate.getName());
			for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
				comparator.setTolerance(tol);
				def comparison = comparator.compare(predicate);
				println("    Threshold = " + tol);
				println("      Pos: Prec = " + comparison.getPrecision(BinaryClass.POSITIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.POSITIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.POSITIVE));
					
				/*
				println("      Neg: Prec = " + comparison.getPrecision(BinaryClass.NEGATIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.NEGATIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.NEGATIVE));
				*/
			}
		}	
	}
	
	/*
	 * This is the *efficient* evaluation method, in which the number of pairwise combinations are specified.
	 */
	public void evaluateModel(UIFullInferenceResult result, List<Predicate> openPredicates, int baselinePartition, List<Integer> maxBaseAtoms) {
		ResultComparator comparator = result.compareResults();
		comparator.setBaseline(dataStore.getDatabase(read: baselinePartition));
		println("Model Evaluation: ");
		for (int i = 0; i < openPredicates.size; i++) {
			def predicate = openPredicates[i];
			println("  Predicate: " + predicate.getName());
			for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
				comparator.setTolerance(tol);
				def comparison = comparator.compare(predicate, maxBaseAtoms[i]);
				println("    Threshold = " + tol);
				println("      Pos: Prec = " + comparison.getPrecision(BinaryClass.POSITIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.POSITIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.POSITIVE));
				/*
				println("      Neg: Prec = " + comparison.getPrecision(BinaryClass.NEGATIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.NEGATIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.NEGATIVE));
				*/
			}
		}
	}

}
