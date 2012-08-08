/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.umd.cs.psl.er.evaluation;

import edu.umd.cs.psl.evaluation.resultui.printer.AtomPrintStream;
import edu.umd.cs.psl.evaluation.debug.AtomPrinter;
import edu.umd.cs.psl.model.atom.Atom;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class FileAtomPrintStream implements AtomPrintStream {

	public FileAtomPrintStream(String outFile, String delim) {
		atomcount=0;
		try {
			out = new BufferedWriter(new FileWriter(outFile));
			this.delim = delim;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void close() {
		try { 
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void printAtom(Atom atom) {
		try {
			for (int i = 0; i < atom.getArity(); i++) 
				out.write(atom.getArguments()[i] + delim);

			for (int i = 0; i < atom.getNumberOfValues(); i++) {
				if (i > 0)
					out.write(delim);
				out.write("" + atom.getSoftValue(i));
			}
			out.newLine();

			atomcount++;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int atomcount;
	private String delim;
	private BufferedWriter out;
}
