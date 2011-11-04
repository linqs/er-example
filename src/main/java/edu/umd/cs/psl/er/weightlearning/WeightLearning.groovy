package edu.umd.cs.psl.er.weightlearning

import java.util.concurrent.TimeUnit;

import edu.umd.cs.psl.config.WeightLearningConfiguration
import edu.umd.cs.psl.groovy.DataStore
import edu.umd.cs.psl.groovy.PSLModel
import edu.umd.cs.psl.model.predicate.Predicate
import edu.umd.cs.psl.config.ConfigBundle;

class WeightLearning {
	private ConfigBundle cb;
	private PSLModel model;
	private DataStore dataStore;
	private WeightLearningConfiguration learningConfig;
	private long learningStartTime;
	private long learningEndTime;
	
	//constructor 
	public WeightLearning(PSLModel newModel, DataStore newDataStore){
		this.model = newModel;
		this.dataStore = newDataStore;
		initLearningConfig();
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
	public void initLearningConfig() {
		learningConfig = new WeightLearningConfiguration();
		learningConfig.setLearningType(WeightLearningConfiguration.Type.LBFGSB);
		learningConfig.setPointMoveConvergenceThres(1E-5);
		learningConfig.setMaxOptIterations(100);
		learningConfig.setParameterPrior(1); // 1/variance
		learningConfig.setRuleMean(1);
		learningConfig.setUnitRuleMean(1);
		learningConfig.setActivationThreshold(1E-10);
		learningConfig.setInitialParameter(1.0);
	}
	
	public PSLModel learnModel(List<Predicate> openPredicates, int evidencePartition, int trainingPartition) {
		learningStartTime = System.nanoTime();
		model.learn dataStore, evidence: evidencePartition, infered: trainingPartition, close: openPredicates, configuration: learningConfig, config: cb;
		learningEndTime = System.nanoTime();
		return model;
	}
	
	public void printRunningTime(){
		println("Weight learning running time: " + TimeUnit.NANOSECONDS.toSeconds(learningEndTime - learningStartTime) + " sec");
	}
	
}
