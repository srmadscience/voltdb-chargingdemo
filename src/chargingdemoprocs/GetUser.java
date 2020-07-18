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

public class GetUser extends VoltProcedure {

    // @formatter:off

    public static final SQLStmt getUser = new SQLStmt("SELECT * FROM user_table WHERE userid = ?;");
    
    public static final SQLStmt getBalance = new SQLStmt("SELECT * FROM user_balances WHERE userid = ?;");
    
    public static final SQLStmt getUsage = new SQLStmt("SELECT * FROM user_usage_table WHERE userid = ? ORDER BY productid, sessionid;");

    public static final SQLStmt getAllTxn = new SQLStmt("SELECT user_txn_id, txn_time, productid, amount "
        + "FROM user_recent_transactions "
        + "WHERE userid = ? ORDER BY txn_time, user_txn_id, productid, amount;");
    
    public static final SQLStmt getRemainingCredit
    = new SQLStmt("select v.userid, v.balance - sum(uut.allocated_units * p.unit_cost )  balance "
               + "from  user_balances v " 
               + ", user_usage_table uut "
               + ", product_table p "
               + "where v.userid = ? "
               + "and   v.userid = uut.userid "
               + "and   p.productid = uut.productid "
               + "group by v.userid, v.balance;");
    
    public static final SQLStmt getTotalView = new SQLStmt("SELECT * FROM user_balance_total_view WHERE userid = ?;");    


    // @formatter:on

    /**
     * Gets all the information we have about a user.
     * @param userId
     * @return
     * @throws VoltAbortException
     */
    public VoltTable[] run(long userId) throws VoltAbortException {

        voltQueueSQL(getUser, userId);
        voltQueueSQL(getBalance, userId);
        voltQueueSQL(getUsage, userId);
        voltQueueSQL(getAllTxn, userId);
        voltQueueSQL(getRemainingCredit, userId);
        
        return voltExecuteSQL(true);

    }
}
