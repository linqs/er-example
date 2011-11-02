package edu.umd.cs.psl.er.modelevaluation

import java.util.List;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.psl.model.predicate.Predicate;

import edu.umd.cs.psl.evaluation.resultui.UIFullInferenceResult
import edu.umd.cs.psl.evaluation.statistics.ResultComparator
import edu.umd.cs.psl.evaluation.statistics.ResultComparison.BinaryClass;
import edu.umd.cs.psl.groovy.DataStore;
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.config.ConfigBundle;

class ModelEvaluation {
	private ConfigBundle cb;
	private PSLModel model;
	private DataStore dataStore;
	UIFullInferenceResult evaluationResult;
	//statistics
	long inferenceStartTime;
	long inferenceEndTime;
	
	//constructor
	public ModelEvaluation(PSLModel newModel, DataStore newDataStore){
		this.model = newModel;
		this.dataStore = newDataStore;
	}
	
	//getters
	public PSLModel getModel() {
		return model;
	}
	
	public PSLModel getDataStore() {
		return dataStore;
	}
	
	//setters
	public void setConfigBundle(ConfigBundle cb) {
		this.cb = cb;
	}
	
	public void setDataStore(DataStore newDataStore) {
		this.dataStore = newDataStore;
	}
	
	public void setPSLModel(PSLModel newModel) {
		this.model = newModel;
	}
	
	//other methods
	public UIFullInferenceResult runInference(int evidencePartition) {
		inferenceStartTime = System.nanoTime();
		UIFullInferenceResult result = model.mapInference(dataStore.getDatabase(read: evidencePartition), cb);
		inferenceEndTime = System.nanoTime();
		return result;
	}
	
	public void evaluateModel(List<Predicate> openPredicates, int evidencePartition, int baselinePartition) {
		UIFullInferenceResult result = runInference(evidencePartition);
		println("Inference running time: " + getRunningTime() + " sec");
		ResultComparator comparator = result.compareResults();
		comparator.setBaseline(dataStore.getDatabase(read: baselinePartition));
		println("Evaluate model perforance: ");
		for (predicate in openPredicates) {
			//result.printAtoms(predicate);
			println("\n\nPerformance for predicate: " + predicate.getName() + "\n");
			//NOTE: the following function does not exist in the core (yet)
			//println "Number of ground atoms: " + result.getNumGroundAtoms(predicate);
			for (double tol = 0.1; tol <= 0.9; tol += 0.1) {
				comparator.setTolerance(tol);
				def comparison = comparator.compare(predicate);
				println("Tolerance = " + tol);
				println("  Pos: Prec = " + comparison.getPrecision(BinaryClass.POSITIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.POSITIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.POSITIVE));
					
				println("  Neg: Prec = " + comparison.getPrecision(BinaryClass.NEGATIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.NEGATIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.NEGATIVE));
			}
		}	
	}
	
	public void evaluateModel(List<Predicate> openPredicates, int evidencePartition, int baselinePartition, List<Integer> maxBaseAtoms) {
		UIFullInferenceResult result = runInference(evidencePartition);
		println("Inference running time: " + getRunningTime() + " sec");
		ResultComparator comparator = result.compareResults();
		comparator.setBaseline(dataStore.getDatabase(read: baselinePartition));
		println("Evaluate model perforance: ");
		for (int i = 0; i < openPredicates.size; i++) {
			def predicate = openPredicates[i];
			//result.printAtoms(predicate);
			println("\n\nPerformance for predicate: " + predicate.getName() + "\n");
			//NOTE: the following function does not exist in the core (yet)
			//println "Number of ground atoms: " + result.getNumGroundAtoms(predicate);
			for (double tol = 0.1; tol <= 0.9; tol += 0.1) {
				comparator.setTolerance(tol);
				def comparison = comparator.compare(predicate, maxBaseAtoms[i]);
				println("Tolerance = " + tol);
				println("  Pos: Prec = " + comparison.getPrecision(BinaryClass.POSITIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.POSITIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.POSITIVE));
					
				println("  Neg: Prec = " + comparison.getPrecision(BinaryClass.NEGATIVE)
						+ ", Rec = " + comparison.getRecall(BinaryClass.NEGATIVE)
						+ ", F1 = " + comparison.getF1(BinaryClass.NEGATIVE));
			}
		}
	}

	public void printInferedPredicates(List<Predicate> predicatesToPrint) {
		predicatesToPrint.each() { item -> evaluationResult.printAtoms(${item},true) };
	}
	
	public String getRunningTime() {
		return TimeUnit.NANOSECONDS.toSeconds(inferenceEndTime - inferenceStartTime).toString();
	}
	
}
