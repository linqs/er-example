package edu.umd.cs.psl.er.external;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;


public class DiceSimilarity {

   public static double similarity(String first, String second) {
        
	// Create two sets of character bigrams, one for each string.
	Set<String> s1 = splitIntoBigrams(first);
	Set<String> s2 = splitIntoBigrams(second);
        
	// Get the number of elements in each set.
	int n1 = s1.size();
	int n2 = s2.size();
                
	// Find the intersection, and get the number of elements in that set.
	s1.retainAll(s2);
	int nt = s1.size();
                
                
	// The coefficient is:
	// 
	//        2 ∙ | s1 ⋂ s2 |
	// D = ----------------------
	//        | s1 | + | s2 |
	// 
	return (2.0 * (double)nt) / ((double)(n1 + n2));
                
    }

        
    private static Set<String> splitIntoBigrams(String s) {
	ArrayList<String> bigrams = new ArrayList<String>();
        
	if (s.length() < 2) {
	    bigrams.add(s);
	}
	else {
	    for (int i = 1; i < s.length(); i++) {
		StringBuilder sb = new StringBuilder();
		sb.append(s.charAt(i-1));
		sb.append(s.charAt(i));
		bigrams.add(sb.toString());
	    }
	}
	return new TreeSet<String>(bigrams);
    }
        

}