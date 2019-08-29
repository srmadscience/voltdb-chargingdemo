package chargingdemoprocs;

/* This file is part of VoltDB.
 * Copyright (C) 2008-2019 VoltDB Inc.
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
import org.voltdb.types.TimestampType;

public class DelUser extends VoltProcedure {

    // @formatter:off

    public static final SQLStmt delUser = new SQLStmt("DELETE FROM user_table WHERE userid = ?;");
    
    public static final SQLStmt delUsage = new SQLStmt("DELETE FROM user_usage_table WHERE userid = ?;");
    
    public static final SQLStmt delBalance = new SQLStmt("DELETE FROM user_balances WHERE userid = ?;");
    
    public static final SQLStmt delTxns = new SQLStmt("DELETE FROM user_recent_transactions WHERE userid = ?;");
    
    public static final SQLStmt delTotals = new SQLStmt("DELETE FROM USER_BALANCE_TOTAL_VIEW WHERE userid = ?;");
    
    
    
    // @formatter:on

    /**
     * Deletes all information we have about a user.
     * @param userId
     * @return
     * @throws VoltAbortException
     */
    public VoltTable[] run(long userId) throws VoltAbortException {

        voltQueueSQL(delUser, userId);
        voltQueueSQL(delUsage, userId);
        voltQueueSQL(delBalance, userId);
        voltQueueSQL(delTxns, userId);
        voltQueueSQL(delTotals, userId);

        return voltExecuteSQL(true);
    }
}
