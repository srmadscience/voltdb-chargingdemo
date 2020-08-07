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

    public static final long TIMEOUT_MS = 300000;
    
    public static final SQLStmt findStaleAllocation = new SQLStmt("SELECT * FROM user_usage_table "
            + "WHERE lastdate < DATEADD(MILLISECOND,?,NOW) "
            + "ORDER BY lastdate,userid, productid,sessionid LIMIT 1000;");
        
    public static final SQLStmt deleteAllocation = new SQLStmt("DELETE FROM user_usage_table WHERE userid = ? AND productid = ? AND sessionid = ?");
    
       
    // @formatter:on

    public VoltTable[] run() throws VoltAbortException {

        // Housekeeping: Delete allocations for this user that are older than
        // TIMEOUT_MS
        voltQueueSQL(findStaleAllocation, -1 * TIMEOUT_MS);
        VoltTable[] staleSessions = voltExecuteSQL();

        while (staleSessions[0].advanceRow()) {

            long userid = staleSessions[0].getLong("userid");
            long productid = staleSessions[0].getLong("productid");
            long sessionid = staleSessions[0].getLong("sessionid");
            voltQueueSQL(deleteAllocation, userid, productid, sessionid);

        }

        return voltExecuteSQL(true);
    }
}
