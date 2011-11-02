package edu.umd.cs.psl.er.dataprep;

import java.io.*;
import java.util.ArrayList;

/**
 * @@author Stanley Kok Date: 3/5/11 Time: 12:17 PM
 */
public class Util {
	public static BufferedReader openFileR(String fileName) {
		File file = new File(fileName);
		String fn = file.getAbsolutePath();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(fn);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Failed to open " + fn);
			System.exit(-1);
		}
		return new BufferedReader(new InputStreamReader(fis));
	}

	public static BufferedWriter openFileW(String fileName) {
		return openFileW(fileName, false);
	}

	public static BufferedWriter openFileW(String fileName, final boolean append) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(fileName, append);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Failed to open " + fileName);
			System.exit(-1);
		}
		return new BufferedWriter(new OutputStreamWriter(fos));
	}

	public static String readLine(BufferedReader in) {
		try {
			return in.readLine();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to read from BufferedReader");
			System.exit(-1);
		}
		return null;
	}

	public static String readLineAndTrim(BufferedReader in) {
		try {
			String buf = in.readLine();
			if (buf != null)
				return buf.trim();
			return null;
		} catch (IOException e) {
			System.out.println("ERROR: Failed to read from BufferedReader");
			System.exit(-1);
		}
		return null;
	}

	public static ArrayList<String> readLines(String inFile) {
		ArrayList<String> lines = new ArrayList<String>();
		BufferedReader in = Util.openFileR(inFile);
		String buf;
		while ((buf = Util.readLine(in)) != null) {
			buf = buf.trim();
			if (buf.length() > 0)
				lines.add(buf);
		}
		Util.closeFile(in);
		return lines;
	}

	public static void write(BufferedWriter out, String str) {
		try {
			out.write(str);
		} catch (IOException e) {
			System.out.println("ERROR: Failed to write to BufferedWriter: "
					+ str);
			System.exit(-1);
		}
	}

	public static void writeln(BufferedWriter out, String str) {
		try {
			out.write(str + '\n');
		} catch (IOException e) {
			System.out.println("ERROR: Failed to write to BufferedWriter: "
					+ str);
			System.exit(-1);
		}
	}

	public static void closeFile(BufferedReader in) {
		try {
			in.close();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to close file.");
			System.exit(-1);
		}
	}

	public static void closeFile(BufferedWriter out) {
		try {
			out.close();
		} catch (IOException e) {
			System.out.println("ERROR: Failed to close file.");
			System.exit(-1);
		}
	}

	public static void mergeFiles(final String file0, final String file1,
			final String outMergeFile) {
		BufferedReader in0 = openFileR(file0);
		BufferedReader in1 = openFileR(file1);
		BufferedWriter out = openFileW(outMergeFile);
		String buf;
		while ((buf = readLine(in0)) != null)
			writeln(out, buf);
		while ((buf = readLine(in1)) != null)
			writeln(out, buf);
		closeFile(in0);
		closeFile(in1);
		closeFile(out);
	}

	public static void clearFile(final String file) {
		closeFile(openFileW(file));
	}

	public static void sout(String s) {
		System.out.println(s);
	}

	public static char lastChar(String s) {
		return s.charAt(s.length() - 1);
	}

}
