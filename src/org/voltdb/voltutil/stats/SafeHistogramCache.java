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

import java.util.HashMap;

public class SafeHistogramCache {

	private static SafeHistogramCache instance = null;

	HashMap<String, StatsHistogram> theHistogramMap = new HashMap<String, StatsHistogram>();
	HashMap<String, Long> theCounterMap = new HashMap<String, Long>();
	HashMap<String, SizeHistogram> theSizeHistogramMap = new HashMap<String, SizeHistogram>();

	final int DEFAULT_SIZE = 100;

	long lastStatsTime = System.currentTimeMillis();

	protected SafeHistogramCache() {
		// Exists only to defeat instantiation.
	}

	public static SafeHistogramCache getInstance() {
		if (instance == null) {
			instance = new SafeHistogramCache();
		}
		return instance;
	}

	public void reset() {
		synchronized (theHistogramMap) {
			synchronized (theCounterMap) {
				synchronized (theSizeHistogramMap) {
					theHistogramMap = new HashMap<String, StatsHistogram>();
					theCounterMap = new HashMap<String, Long>();
					theSizeHistogramMap = new HashMap<String, SizeHistogram>();

				}
			}
		}
	}


	public StatsHistogram get(String type) {
		StatsHistogram h = null;

		synchronized (theHistogramMap) {
			h = theHistogramMap.get(type);

			if (h == null) {
				h = new StatsHistogram(DEFAULT_SIZE);
				theHistogramMap.put(type, h);
			}
		}

		return h;
	}

	public void clear(String type) {
		
		StatsHistogram oldH = null;
		StatsHistogram newH = null;

		synchronized (theHistogramMap) {
			oldH = theHistogramMap.remove(type);
			
			if (oldH == null) {
				newH = new StatsHistogram(100);
			} else {
				newH = new StatsHistogram(oldH.maxSize);
			}
			theHistogramMap.put(type, newH);

			
		}

	}

	public SizeHistogram getSize(String type) {
		SizeHistogram h = null;

		synchronized (theSizeHistogramMap) {
			h = theSizeHistogramMap.get(type);

			if (h == null) {
				h = new SizeHistogram(type, DEFAULT_SIZE);
				theSizeHistogramMap.put(type, h);
			}
		}

		return h;
	}

	public long getCounter(String type) {
		Long l = new Long(0);

		synchronized (theCounterMap) {
			l = theCounterMap.get(type);
			if (l == null) {
				return 0;
			}
		}

		return l.longValue();
	}

	public void setCounter(String type, long value) {

		synchronized (theCounterMap) {
			Long l = theCounterMap.get(type);
			if (l == null) {
				l = new Long(value);

			}
			theCounterMap.put(type, l);
		}

	}

	public void incCounter(String type) {

		synchronized (theCounterMap) {
			Long l = theCounterMap.get(type);
			if (l == null) {
				l = new Long(0);
			}
			theCounterMap.put(type, l.longValue() + 1);
		}

	}

	public void report(String type, int value, String comment, int defaultSize) {

		synchronized (theHistogramMap) {
			StatsHistogram h = theHistogramMap.get(type);
			if (h == null) {
				h = new StatsHistogram(type, defaultSize);
				theHistogramMap.put(type, h);
			}
			h.report(value, comment);

		}

	}

	public void reportSize(String type, int size, String comment, int defaultSize) {

		synchronized (theSizeHistogramMap) {
			SizeHistogram h = theSizeHistogramMap.get(type);
			if (h == null) {
				h = new SizeHistogram(type, defaultSize);
				theSizeHistogramMap.put(type, h);
			}
			
			h.inc(size, comment);

		}

	}

	public void reportLatency(String type, long start, String comment, int defaultSize) {

		synchronized (theHistogramMap) {
			StatsHistogram h = theHistogramMap.get(type);
			if (h == null) {
				h = new StatsHistogram(type, defaultSize);

			}
			h.reportLatency(start, comment);
			theHistogramMap.put(type, h);
		}

	}

	public StatsHistogram subtractTimes(String bigHist, String smallHist, String name) {

		StatsHistogram hBig;
		StatsHistogram hSmall;
		synchronized (theHistogramMap) {
			hBig = theHistogramMap.get(bigHist);
			hSmall = theHistogramMap.get(smallHist);
		}
		StatsHistogram delta = StatsHistogram.subtract(name, hBig, hSmall);

		synchronized (theHistogramMap) {
			theHistogramMap.put(name, delta);
		}

		return delta;

	}

	/**
	 * @return true if we have stats to report...
	 */
	public boolean hasStats() {
		
		synchronized (theHistogramMap) {
			synchronized (theCounterMap) {
				synchronized (theSizeHistogramMap) {

					if (! theHistogramMap.isEmpty()) {
						return true;
					}

					if (! theCounterMap.isEmpty()) {
						return true;
					}

					if (! theSizeHistogramMap.isEmpty()) {
						return true;
					}

					
				}
			}
		}		
		return false;
		
	}
	@Override
	public String toString() {
		String data = "";
		synchronized (theHistogramMap) {
			synchronized (theCounterMap) {
				synchronized (theSizeHistogramMap) {

					data = theHistogramMap.toString() + System.lineSeparator() + theCounterMap.toString()
							+ System.lineSeparator() + theSizeHistogramMap.toString();
				}
			}
		}

		return data;
	}

	public String toStringIfOlderThanMs(int statsInterval) {

		String data = "";

		if (lastStatsTime + statsInterval < System.currentTimeMillis()) {
			synchronized (theHistogramMap) {

				data = theHistogramMap.toString();
				lastStatsTime = System.currentTimeMillis();
			}

		}
		return data;
	}

	public void initSize(String name, int batchSize, String description) {

		synchronized (theSizeHistogramMap) {
			SizeHistogram h = theSizeHistogramMap.get(name);
			if (h == null) {
				
				h = new SizeHistogram(name, batchSize);
				h.setDescription(description);
				theSizeHistogramMap.put(name, h);
				
			}

		}

	}
	
	public void init(String name, int batchSize, String description) {

		synchronized (theHistogramMap) {
			StatsHistogram h = theHistogramMap.get(name);
			if (h == null) {
				h = new StatsHistogram(name, batchSize);
				h.setDescription(description);
				theHistogramMap.put(name, h);
			}

		}

	}

}