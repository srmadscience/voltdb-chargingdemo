package chargingdemoprocs;

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

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class DeleteStaleAllocations extends VoltProcedure {

  // @formatter:off

    private static final long TIMEOUT_MS = 900000;
    
    private static final long DELETE_TRANSACTION_HOURS = 5;
    
    private static final long MAX_DELETE_ROWS = 500;

    public static final SQLStmt deleteOldTxns = new SQLStmt("DELETE FROM user_recent_transactions WHERE txn_time < DATEADD(HOUR,?,NOW) ORDER BY txn_time, userid, user_txn_id LIMIT ? ;");
      
    public static final SQLStmt deleteStaleAllocation = new SQLStmt("DELETE FROM user_usage_table "
        + "WHERE lastdate < DATEADD(MILLISECOND,?,NOW) ORDER BY lastdate, userid, productid,sessionid LIMIT ?");
    
    // @formatter:on

    public VoltTable[] run() throws VoltAbortException {

        // Housekeeping: Delete allocations for this user that are older than
        // TIMEOUT_MS
        voltQueueSQL(deleteStaleAllocation, -1 * TIMEOUT_MS, MAX_DELETE_ROWS);

        // Housekeeping: Delete old transactions for this user that are older
        // than
        // DELETE_TRANSACTION_HOURS
        voltQueueSQL(deleteOldTxns, -1 * DELETE_TRANSACTION_HOURS, MAX_DELETE_ROWS);

        return voltExecuteSQL(true);
    }
}
