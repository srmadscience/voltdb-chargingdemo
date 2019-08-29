package org.voltdb.chargingdemo;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2018 VoltDB Inc.
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


import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.voltutil.stats.SafeHistogramCache;

public class ComplainOnDifferenceCallback implements ProcedureCallback {

    int expectedCount = 0;
    String message = "";
    SafeHistogramCache shc = SafeHistogramCache.getInstance();
    
    public ComplainOnDifferenceCallback(int expectedCount, String message) {
        
        super();
        this.expectedCount = expectedCount;
        this.message = message;
        
        shc.incCounter("CreateComplainOnDifferenceCallback");
    }

   
    @Override
    public void clientCallback(ClientResponse arg0) throws Exception {
        
        if (arg0.getStatus() != ClientResponse.SUCCESS) {
            shc.incCounter("call had error");
            ChargingDemo.msg("call had error:" + arg0.getStatusString());
        } else {
            if (expectedCount != arg0.getResults()[0].getRowCount()) {
                shc.incCounter("transaction_missing_when_checked_" + (expectedCount + - arg0.getResults()[0].getRowCount()));
                shc.incCounter("transaction_missing_when_checked");
                
                final String tableAsString = arg0.getResults()[0].toFormattedString();
                ChargingDemo.msg(message + ", got " + arg0.getResults()[0].getRowCount() + System.lineSeparator() + tableAsString);
                             
            } else {
                shc.incCounter("transaction_found_when_checked");
             }
            
        }

    }

 
}
