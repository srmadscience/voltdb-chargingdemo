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

public class AddCredit extends VoltProcedure {

  // @formatter:off

    public static final SQLStmt getUser = new SQLStmt("SELECT userid FROM user_table WHERE userid = ?;");
    
    public static final SQLStmt getTxn = new SQLStmt("SELECT txn_time FROM user_recent_transactions "
        + "WHERE userid = ? AND user_txn_id = ?;");
    
    public static final SQLStmt addTxn = new SQLStmt("INSERT INTO user_recent_transactions "
        + "(userid, user_txn_id, txn_time,amount) VALUES (?,?,NOW,?);");

    public static final SQLStmt getBalance = new SQLStmt("SELECT userid, balance FROM user_balances WHERE userid = ?;");
    
    public static final SQLStmt getRemainingCredit
        = new SQLStmt("select v.userid, v.balance - sum(uut.allocated_units * p.unit_cost )  balance "
                   + "from  user_balances v " 
                   + ", user_usage_table uut "
                   + ", product_table p "
                   + "where v.userid = ? "
                   + "and   v.userid = uut.userid "
                   + "and   p.productid = uut.productid "
                   + "group by v.userid, v.balance "
                   + "order by v.userid, v.balance;");
    
    public static final SQLStmt addCredit = new SQLStmt(
        "INSERT INTO user_financial_events (userid   ,amount, purpose)    VALUES (?,?,?);");

    public static final SQLStmt updBalance = new SQLStmt(
        "upsert into user_balances select userid, tran_count, balance from user_balance_total_view where userid = ?;");

   // @formatter:on

  /**
   * A VoltDB stored procedure to add credit to a user in the chargingdemo demo. It
   * checks that the user exists and also makes sure that this transaction hasn't
   * already happened.
   * 
   * @param userId
   * @param extraCredit
   * @param txnId
   * @return Balance and Credit info
   * @throws VoltAbortException
   */
  public VoltTable[] run(long userId, long extraCredit, String txnId) throws VoltAbortException {

    // See if we know about this user and transaction...
    voltQueueSQL(getUser, userId);
    voltQueueSQL(getTxn, userId, txnId);

    VoltTable[] results = voltExecuteSQL();

    // Sanity Check: Is this a real user?
    if (!results[0].advanceRow()) {
      throw new VoltAbortException("User " + userId + " does not exist");
    }

    // Sanity Check: Has this transaction already happened?
    if (results[1].advanceRow()) {

      this.setAppStatusCode(ReferenceData.TXN_ALREADY_HAPPENED);
      this.setAppStatusString("Event already happened at " + results[1].getTimestampAsTimestamp("txn_time").toString());
      
    } else {

      // Report credit add...
      
      this.setAppStatusCode(ReferenceData.CREDIT_ADDED);
      this.setAppStatusString(extraCredit + " added by Txn " + txnId);
      
      // Insert a row into the stream for each user's financial events. 
      // The view user_balances can then calculate actual credit
      voltQueueSQL(addCredit, userId, extraCredit, extraCredit + " added");
      voltQueueSQL(updBalance, userId);

      // get credit after transaction done - this will be returned
      // to the client.
      voltQueueSQL(getBalance, userId);
      voltQueueSQL(getRemainingCredit, userId);
      voltQueueSQL(addTxn, userId, txnId, extraCredit);

    }

    return voltExecuteSQL(true);
  }
}
