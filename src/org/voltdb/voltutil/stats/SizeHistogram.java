package org.voltdb.voltutil.stats;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

public class SizeHistogram {

	String description = "";
	String name = "";

	int[] theHistogram = new int[0];
	String[] theHistogramComment = new String[0];

	public SizeHistogram(String name, int size) {
		this.name = name;
		theHistogram = new int[size];
		theHistogramComment = new String[size];
	}

	public void inc(int size, String comment) {

		if (size >= 0 && size < theHistogram.length) {
			theHistogram[size]++;
			theHistogramComment[size] = comment;
		} else if (size >= theHistogram.length) {
			theHistogram[theHistogram.length - 1]++;
			theHistogramComment[theHistogram.length - 1] = comment;
		}
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer(name);
		b.append(" ");
		b.append(description);
		b.append(" ");

		for (int i = 0; i < theHistogram.length; i++) {
			if (theHistogram[i] > 0) {
				b.append(System.lineSeparator());
				b.append(i);
				b.append(' ');
				b.append(theHistogram[i]);
				
			}
		}

		return b.toString();
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
