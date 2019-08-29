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

package org.voltdb.voltutil.stats;

public class StatsHistogram {

	final String NUMFORMAT_DECIMAL = "% ,16.0f";
	final String NUMFORMAT_INTEGER = "%16d";

	int maxSize = 1000;
	double[] latencyHistogram = new double[0];
	String[] latencyComment = new String[0];
	boolean isRolledOver = false;
	String name = "";
	long reports = 0;
	int maxUsedSize = 0;
	String description = "";
	


	public StatsHistogram(int maxSize) {
		init("", maxSize);
	}

	public StatsHistogram(String name, int maxSize) {
		init(name, maxSize);
	}

	public void init(String name, int maxSize) {

		this.name = name;
		this.maxSize = maxSize;

		latencyHistogram = new double[maxSize];
		latencyComment = new String[maxSize];

		for (int i = 0; i < latencyComment.length; i++) {
			latencyComment[i] = "";
		}

		resetLatency();

	}

	public void resetLatency() {
		for (int i = 0; i < maxSize; i++) {
			latencyHistogram[i] = 0;
		}

		reports = 0;
		maxUsedSize = 0;
	}

	public void report(int latency, String comment) {

		reports++;

		if (latency < 0) {
			latency = 0;
		}

		if (latency < maxSize) {
			if (latencyHistogram[latency] < Integer.MAX_VALUE) {
				latencyHistogram[latency]++;

				if (maxUsedSize < latency) {
					maxUsedSize = latency;
				}

			} else {
				isRolledOver = true;
				resetLatency();
			}

		} else {

			if (latencyHistogram[maxSize - 1] < Integer.MAX_VALUE) {
				latencyHistogram[maxSize - 1]++;

				if (maxUsedSize == maxSize - 1) {
					maxUsedSize = maxSize - 1;
				}

			} else {
				isRolledOver = true;
				resetLatency();
			}

		}

		if (comment != null && comment.length() > 0) {
			if (latency < maxSize) {
				if (latencyHistogram[latency] < Integer.MAX_VALUE) {
					latencyComment[latency] = comment;
				}

			} else {
				latencyComment[maxSize - 1] = comment;
			}
		}

	}

	public void reportLatency(long startTime, String comment) {

		int latency = (int) (System.currentTimeMillis() - startTime);

		report(latency, comment);

	}

	public double peekValue(int idx) {

		if (idx < maxSize) {
			return latencyHistogram[idx];
		}

		return 0.0;

	}

	public void pokeValue(int idx, double value) {

		if (idx < maxSize) {
			reports -= latencyHistogram[idx];
			reports += value;
			latencyHistogram[idx] = value;
		}

	}

	public void pokeReports(long reports) {

		this.reports = reports;
	}

	public double[] getLatencyHistogram() {
		return latencyHistogram;
	}

	public String[] getLatencyComment() {
		return latencyComment;
	}

	public int getMaxUsedSize() {

		return maxUsedSize;
	}

	public int getLatencyPct(double pct) {

		final double target = getLatencyTotal() * (pct / 100);
		double runningTotal = latencyHistogram[0];
		int matchValue = 0;

		for (int i = 1; i < latencyHistogram.length; i++) {
			
			if (runningTotal >= target) {
				break;
			}
			
			matchValue = i;
			runningTotal = runningTotal + (i * latencyHistogram[i]);

		}

		return matchValue;
	}

    public double getLatencyTotal() {

        double runningTotal = 0.0;

        for (int i = 0; i < latencyHistogram.length; i++) {
            runningTotal = runningTotal + (i * latencyHistogram[i]);
        }

        return runningTotal;
    }
    
    public double getLatencyAverage() {

        return getLatencyTotal() / reports ;
    }

	public double getEventTotal() {

		double runningTotal = 0.0;

		for (int i = 0; i < latencyHistogram.length; i++) {
			runningTotal += latencyHistogram[i] ;
		}

		return runningTotal;
	}
	
	public String toStringShort() {
		StringBuffer b = new StringBuffer(name);

		b.append(" ");
		b.append(description);
		b.append(System.lineSeparator());
		
        b.append(" Reports=");
        b.append(String.format(NUMFORMAT_INTEGER, reports));
        b.append(" Average=");
        b.append(String.format(NUMFORMAT_DECIMAL, getLatencyAverage() ));
		b.append(", Total=");
		b.append(String.format(NUMFORMAT_DECIMAL, getLatencyTotal()));
		b.append(", 50%=");
		b.append(String.format(NUMFORMAT_INTEGER, getLatencyPct(50)));
		b.append(", 95%=");
		b.append(String.format(NUMFORMAT_INTEGER, getLatencyPct(95)));
		b.append(", 99%=");
		b.append(String.format(NUMFORMAT_INTEGER, getLatencyPct(99)));
		b.append(", 99.5%=");
		b.append(String.format(NUMFORMAT_INTEGER, getLatencyPct(99.5)));
		b.append(", 99.95%=");
		b.append(String.format(NUMFORMAT_INTEGER, getLatencyPct(99.95)));
		b.append(", Max=");
		b.append(String.format(NUMFORMAT_INTEGER, maxUsedSize));

		if (isRolledOver) {
			b.append(" ROLLED OVER");
		}

		return b.toString();
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer(toStringShort());

		b.append("\n");

		for (int i = 0; i < latencyHistogram.length; i++) {
			if (latencyHistogram[i] != 0) {
				if (i == (latencyHistogram.length -1)) {
					b.append(">= ");
				}
				b.append(i);
				b.append("\t");
				b.append(latencyHistogram[i]);
				b.append("\t");
				b.append(latencyComment[i]);
				b.append("\n");
			}
		}

		return b.toString();
	}

	public boolean isHasRolledOver() {
		return isRolledOver;
	}

	public static StatsHistogram subtract(String name, StatsHistogram bigThing, StatsHistogram smallThing) {
		int size = bigThing.getMaxUsedSize();

		if (smallThing.getMaxUsedSize() > size) {
			size = smallThing.getMaxUsedSize();
		}

		StatsHistogram newHist = new StatsHistogram(name, size);

		for (int i = 0; i < size; i++) {
			double bigVal = bigThing.peekValue(i);
			double smallVal = smallThing.peekValue(i);
			newHist.pokeValue(i, (bigVal - smallVal));
		}

		newHist.pokeReports(bigThing.reports);

		return newHist;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

    public boolean hasReports() {
        if (reports > 0) {
            return true;
        }
        
        return false;
    }

}
