package edu.umd.cs.psl.er.dataprep;

import uk.ac.shef.wit.simmetrics.similaritymetrics.DiceSimilarity;
import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.reflect.Array;
import java.util.*;

import edu.umd.cs.psl.er.dataprep.Util;

/**
 * @@author Stanley Kok Date: 7/20/11 Time: 12:07 PM
 */
public class ProcessCiteseer {
	class PaperInfo {
		int authorId;
		int authClustId;
		String authorName;
		int paperId;
		int papClustId;
		String title;
	}

	// maps author cluster ID to paper cluster ID, and vice versa
	private Map<Integer, ArrayList<Integer>> authCIdToPapCIdMap = new HashMap<Integer, ArrayList<Integer>>();
	private Map<Integer, ArrayList<Integer>> papCIdToAuthCIdMap = new HashMap<Integer, ArrayList<Integer>>();
	// maps author cluster ID to paper cluster ID
	private Map<Integer, ArrayList<PaperInfo>> authCIdToPaperInfoMap = new HashMap<Integer, ArrayList<PaperInfo>>();

	private Random random = null;

	private Levenshtein nameSimilarity = new Levenshtein();
	private DiceSimilarity titleSimilarity = new DiceSimilarity();

	public void run(final String inFileName, final int numFolds,
			final int seed, final String outDir, final double simThresh,
			final double probMangle) {
		random = new Random(seed);
		createMaps(inFileName);
		/* each partition contains author cluster IDs
		 */
		ArrayList<Set<Integer>> partitions = createPartitions();
		mangleNamesOrTitles(partitions, probMangle);
		ArrayList<Integer>[] folds = createFolds(partitions, numFolds);
		createOutputFiles(folds, outDir, simThresh);
	}

	private void createMaps(String inFileName) {
		ArrayList<String> lines = Util.readLines(inFileName);
		System.out.println("number of bib entries = " + lines.size());

		// store the map
		for (String line : lines) {
			String[] tokens = line.split("\\s+\\|\\s+");
			assert tokens.length == 8;
			PaperInfo pi = new PaperInfo();

			pi.authorId = Integer.parseInt(tokens[0]);
			pi.authClustId = Integer.parseInt(tokens[1]);
			pi.authorName = tokens[2];
			pi.paperId = Integer.parseInt(tokens[5]);
			pi.papClustId = Integer.parseInt(tokens[6]);
			pi.title = tokens[7];

			pi.authorName = pi.authorName.replace("_", " ");

			addToMap(authCIdToPapCIdMap, pi.authClustId, pi.papClustId);
			addToMap(papCIdToAuthCIdMap, pi.papClustId, pi.authClustId);
			addToMap(authCIdToPaperInfoMap, pi.authClustId, pi);
		}
	}

	private void addToMap(Map map, Object key, Object value) {
		ArrayList arrayList = (ArrayList) map.get(key);
		if (arrayList == null) {
			arrayList = new ArrayList();
			map.put(key, arrayList);
		}
		arrayList.add(value);
	}

	private ArrayList<Set<Integer>> createPartitions() {
		/* each partition is a collection of author cluster IDs that are linked 
		 * (via a path of paper cluster IDs and other author cluster IDs)
		 */
		ArrayList<Set<Integer>> partitions = new ArrayList<Set<Integer>>();
		Set<Integer> authClustIds = new HashSet<Integer>(
				authCIdToPapCIdMap.keySet());
		while (!authClustIds.isEmpty()) {
			int acId = -1;
			for (int i : authClustIds) {
				acId = i;
				break;
			} // get a author cluster ID
			Set<Integer> partition = new HashSet<Integer>();
			partitions.add(partition);
			createPartitions(authClustIds, acId, partition);
		}

		System.out.println("#Partitions = " + partitions.size());
		return partitions;
	}

	private void createPartitions(Set<Integer> authClustIds, final int acId, Set<Integer> partition) {
		if (authClustIds.remove(acId)) // author cluster id has not been removed
		{
			boolean added = partition.add(acId);
			assert added;
			ArrayList<Integer> papCIds = authCIdToPapCIdMap.get(acId);
			for (int papCId : papCIds) {
				ArrayList<Integer> aCIds = papCIdToAuthCIdMap.get(papCId);
				for (int aCId2 : aCIds)
					createPartitions(authClustIds, aCId2, partition);
			}
		}
	}

	private void mangleNamesOrTitles(ArrayList<Set<Integer>> partitions, final double probMangle) {
		for (Set<Integer> authClustIds : partitions) // for each partition
		{
			// get all the papers in this partition
			Set<Integer> paperIds = new HashSet<Integer>();
			ArrayList<PaperInfo> paperInfos = new ArrayList<PaperInfo>();
			for (int authClustId : authClustIds) // for each cluster of author ids in partition
			{
				ArrayList<PaperInfo> paperInfos2 = authCIdToPaperInfoMap
						.get(authClustId);
				for (PaperInfo pi : paperInfos2)
					if (paperIds.add(pi.paperId))
						paperInfos.add(pi);
			}

			boolean mangleName = random.nextBoolean();
			for (PaperInfo pi : paperInfos) {
				if (mangleName)
					pi.authorName = mangleString(pi.authorName, probMangle);
				else
					pi.title = mangleString(pi.title, probMangle);
			}
		}
	}

	private String mangleString(final String str, final double probMangle) {
		String[] tokens = str.split("\\s+");

		String retStr = "";
		for (int j = 0; j < tokens.length; j++) {
			String s = tokens[j];
			char[] chars = s.toCharArray();
			for (int i = 1; i < chars.length; i++) // don't mangle first char of token
			{
				if (random.nextDouble() <= probMangle)
					chars[i] = (char) (random.nextInt(26) + 'a');
			}

			if (j > 0)
				retStr += " ";
			retStr += new String(chars);
		}
		return retStr;
	}

	@SuppressWarnings({ "unsafe", "unchecked" })
	private ArrayList<Integer>[] createFolds(final ArrayList<Set<Integer>> partitions, final int numFolds) {
		ArrayList<Integer>[] folds = (ArrayList<Integer>[]) Array.newInstance(
				ArrayList.class, numFolds);

		for (int i = 0; i < folds.length; i++)
			folds[i] = new ArrayList<Integer>();

		while (!partitions.isEmpty()) {
			for (int i = 0; i < folds.length; i++) {
				ArrayList<Integer> fold = folds[i];
				if (partitions.isEmpty())
					break;
				int partIdx = random.nextInt(partitions.size());

				Set<Integer> partition = partitions.get(partIdx);
				Set<Integer> last = partitions.get(partitions.size() - 1);
				partitions.set(partIdx, last);
				partitions.remove(partitions.size() - 1);

				for (int authClustId : partition)
					fold.add(authClustId);
			}
		}

		for (int i = 0; i < folds.length; i++)
			System.out.println("#AuthorClustIDs in Fold " + i + " = " + folds[i].size());
		return folds;
	}

	private void createOutputFiles(final ArrayList<Integer>[] folds, final String outDir, final double simThresh) {
		final String authorNameFile = outDir + File.separator + "authorName";
		final String paperTitleFile = outDir + File.separator + "paperTitle";
		final String authorOfFile = outDir + File.separator + "authorOf";
		final String sameAuthorFile = outDir + File.separator + "sameAuthor";
		final String samePaperFile = outDir + File.separator + "samePaper";
		final String simNameFile = outDir + File.separator + "simName";
		final String simTitleFile = outDir + File.separator + "simTitle";
		final String sameInitialsFile = outDir + File.separator + "sameInitials";
		final String sameNumTokensFile = outDir + File.separator + "sameNumTokens";
		final String sameAuthorTruthFile = outDir + File.separator + "sameAuthor_truth";
		final String samePaperTruthFile = outDir + File.separator + "samePaper_truth";
		
		// TODO: BEN, REMEMBER TO CHANGE THIS BACK TO folds.length!!!!!
		for (int i = 0; i < 2/*folds.length*/; i++) {
			String filePostfix = "." + i + ".txt";

			ArrayList<Integer> fold = folds[i];

			Set<String> authorNames = new HashSet<String>();
			Set<String> titles = new HashSet<String>();
			Set<String> paperTitles = new HashSet<String>();

			BufferedWriter outAuthorName = Util.openFileW(authorNameFile + filePostfix);
			BufferedWriter outPaperTitle = Util.openFileW(paperTitleFile + filePostfix);
			BufferedWriter outAuthorOf = Util.openFileW(authorOfFile + filePostfix);
			BufferedWriter outSameAuthor = Util.openFileW(sameAuthorFile + filePostfix);
			BufferedWriter outSamePaper = Util.openFileW(samePaperFile + filePostfix);
			BufferedWriter outSimName = Util.openFileW(simNameFile + filePostfix);
			BufferedWriter outSimTitle = Util.openFileW(simTitleFile + filePostfix);
			BufferedWriter outSameInit = Util.openFileW(sameInitialsFile + filePostfix);
			BufferedWriter outSameNumTokens = Util.openFileW(sameNumTokensFile + filePostfix);

			Map<Integer, ArrayList<PaperInfo>> papCIdToPaperInfoMap = new HashMap<Integer, ArrayList<PaperInfo>>();
			Set<String> trueAuthorPairs = new HashSet<String>();
			Set<String> truePaperPairs = new HashSet<String>();
			for (int authClustId : fold)
			{				
				Set<Integer> authorIds = new HashSet<Integer>();

				ArrayList<PaperInfo> paperInfos = authCIdToPaperInfoMap.get(authClustId);
				for (PaperInfo pi : paperInfos)
				{
					Util.writeln(outAuthorName, pi.authorId + "\t" + pi.authorName);
					Util.writeln(outAuthorOf, pi.authorId + "\t" + pi.paperId);

					String s = pi.paperId + "\t" + pi.title;
					if (paperTitles.add(s))
						Util.writeln(outPaperTitle, s);

					authorIds.add(pi.authorId);
					addToMap(papCIdToPaperInfoMap, pi.papClustId, pi);

					authorNames.add(pi.authorName);
					titles.add(pi.title);
				}

				for (int aid0 : authorIds)
					for (int aid1 : authorIds) {
						String authorPair = aid0 + "/" + aid1;
						trueAuthorPairs.add(authorPair);
						Util.writeln(outSameAuthor, aid0 + "\t" + aid1 + "\t1.0");
					}
			}

			for (Map.Entry<Integer, ArrayList<PaperInfo>> entry : papCIdToPaperInfoMap
					.entrySet()) {
				ArrayList<PaperInfo> paperInfos = entry.getValue();
				Set<Integer> paperIds = new HashSet<Integer>();
				for (PaperInfo pi : paperInfos)
					paperIds.add(pi.paperId);

				for (int pid0 : paperIds)
					for (int pid1 : paperIds) {
						String paperPair = pid0 + "/" + pid1;
						truePaperPairs.add(paperPair);
						Util.writeln(outSamePaper, pid0 + "\t" + pid1 + "\t1.0");
					}
			}

			for (String name0 : authorNames)
				for (String name1 : authorNames) {
					double sim = nameSimilarity.getSimilarity(name0, name1);
					if (sim > simThresh)
						Util.writeln(outSimName, name0 + "\t" + name1 + "\t" + sim);
					if (sameInitials(name0, name1))
						Util.writeln(outSameInit, name0 + "\t" + name1);
				}

			for (String title0 : titles)
				for (String title1 : titles) {
					double sim = titleSimilarity.getSimilarity(title0, title1);
					if (sim > simThresh)
						Util.writeln(outSimTitle, title0 + "\t" + title1 + "\t" + sim);
					if (sameNumTokens(title0, title1))
						Util.writeln(outSameNumTokens, title0 + "\t" + title1);
				}

			// we only need to print this truth data if you load the _truth.txt files
			//writeObjPairs(authorNameFile + filePostfix, trueAuthorPairs, sameAuthorTruthFile + filePostfix);
			//writeObjPairs(paperTitleFile + filePostfix, truePaperPairs, samePaperTruthFile + filePostfix);

			Util.closeFile(outAuthorName);
			Util.closeFile(outPaperTitle);
			Util.closeFile(outAuthorOf);
			Util.closeFile(outSameAuthor);
			Util.closeFile(outSamePaper);
			Util.closeFile(outSimName);
			Util.closeFile(outSimTitle);
			Util.closeFile(outSameInit);
			Util.closeFile(outSameNumTokens);
			
			removeDuplicates(authorNameFile + filePostfix);
			removeDuplicates(paperTitleFile + filePostfix);
			removeDuplicates(authorOfFile + filePostfix);
			removeDuplicates(sameAuthorFile + filePostfix);
			removeDuplicates(samePaperFile + filePostfix);
			removeDuplicates(simNameFile + filePostfix);
			removeDuplicates(simTitleFile + filePostfix);
			removeDuplicates(sameInitialsFile + filePostfix);
			removeDuplicates(sameNumTokensFile + filePostfix);
			// only need this for _truth.txt files
			//removeDuplicates(sameAuthorTruthFile + filePostfix);
			//removeDuplicates(samePaperTruthFile + filePostfix);
		}
	}

	private void writeObjPairs(final String objNameFile, final Set<String> truePairs, final String outFile) {
		ArrayList<String> objNames = Util.readLines(objNameFile);
		Set<String> objs = new HashSet<String>();
		for (String objName : objNames) {
			String[] tokens = objName.split("\\t+");
			String obj = tokens[0];
			objs.add(obj);
		}
		System.out.println("#number of objs = " + objs.size());

		BufferedWriter outStr = Util.openFileW(outFile);
		long numPairs = 0;
		for (String x0 : objs) {
			for (String x1 : objs) {
				String pair0 = x0 + "/" + x1;
				String pair1 = x1 + "/" + x0;
				double truthValue;
				if (truePairs.contains(pair0) || truePairs.contains(pair1)) {
					truthValue = 1.0;
				} else
					truthValue = 0.0;
				Util.writeln(outStr, x0 + "\t" + x1 + "\t" + truthValue);
				++numPairs;
			}
		}
		Util.closeFile(outStr);

		System.out.println("#number of obj pairs = " + numPairs);
	}

	private boolean sameInitials(final String s0, final String s1) {
		String[] tokens0 = s0.split("\\s+");
		String[] tokens1 = s1.split("\\s+");
		if (tokens0.length != tokens1.length)
			return false;
		for (int i = 0; i < tokens0.length; i++)
			if (tokens0[i].charAt(0) != tokens1[i].charAt(0))
				return false;
		return true;
	}

	private boolean sameNumTokens(final String s0, final String s1) {
		String[] tokens0 = s0.split("\\s+");
		String[] tokens1 = s1.split("\\s+");
		return tokens0.length == tokens1.length;
	}

	private void removeDuplicates(final String file) {
		ArrayList<String> lines = Util.readLines(file);
		Set<String> uniqLines = new HashSet<String>();
		for (String line : lines)
			uniqLines.add(line);
		BufferedWriter out = Util.openFileW(file);
		for (String line : uniqLines)
			Util.writeln(out, line);
		Util.closeFile(out);
	}

	public static void main(String[] args) {
		// if (args.length != 6)
		// {
		// System.out.println("usage: java ProcessCiteseer3 <inFile> <numFolds> <seed> <outDir> <simThresh> <probNameOrTitleMangle>");
		// System.out.println("java ProcessCiteseer3 citeseer.dat 3 1 ./ 0.5");
		// System.exit(-1);
		// }
		// final String inFileName = args[0];
		// final int numFolds = Integer.parseInt(args[1]);
		// final int seed = Integer.parseInt(args[2]);
		// final String outDir = args[3];
		// final double simThresh = Double.parseDouble(args[4]);
		// final double probMangled= Double.parseDouble(args[5]);

		final String inFileName = "data/CiteSeer/citeseer.dat";
		final int numFolds = 2;
		final int seed = 1;
		final String outDir = "data/CiteSeer/medium";
		final double simThresh = 0.5;
		final double probMangled = 0.7;

		ProcessCiteseer pc = new ProcessCiteseer();
		pc.run(inFileName, numFolds, seed, outDir, simThresh, probMangled);
		
		System.out.println("Done data prep!");
	}
}
