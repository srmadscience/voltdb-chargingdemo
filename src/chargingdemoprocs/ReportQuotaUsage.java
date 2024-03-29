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

public class ReportQuotaUsage extends VoltProcedure {

  // @formatter:off

    public static final SQLStmt getUser = new SQLStmt("SELECT userid FROM user_table WHERE userid = ?;");
    
    public static final SQLStmt getTxn = new SQLStmt("SELECT txn_time FROM user_recent_transactions WHERE userid = ? AND user_txn_id = ?;");
       
    public static final SQLStmt addTxn = new SQLStmt("INSERT INTO user_recent_transactions (userid, user_txn_id, txn_time, productid, amount) VALUES (?,?,NOW,?,?);");

    public static final SQLStmt getBalance = new SQLStmt("SELECT balance, CAST (? AS BIGINT) product_id,"
        + " CAST (? AS BIGINT) session_id, userid FROM user_balances WHERE userid = ?;");
    
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
    
    public static final SQLStmt getProduct = new SQLStmt("SELECT unit_cost FROM product_table WHERE productid = ?;");
    
    public static final SQLStmt createAllocation = new SQLStmt("INSERT INTO user_usage_table "
        + "(userid, productid, allocated_units,sessionid, lastdate) VALUES (?,?,?,?,NOW);");
        
    public static final SQLStmt getCurrentAllocation = new SQLStmt("SELECT allocated_units, sessionid, lastdate, userid, productid "
        + "FROM user_usage_table WHERE userid = ? AND productid = ? AND sessionid = ?;");

    public static final SQLStmt deleteAllocation = new SQLStmt("DELETE FROM user_usage_table WHERE userid = ? AND productid = ? AND sessionid = ?");
    
    public static final SQLStmt reportSpending = new SQLStmt(
            "INSERT INTO user_financial_events (userid   ,amount, purpose)    VALUES (?,?,?);");
  
    public static final SQLStmt updBalance = new SQLStmt(
        "upsert into user_balances select userid, tran_count, balance from user_balance_total_view where userid = ?;");

    public static final SQLStmt removeOldestTransaction = new SQLStmt("DELETE " 
            + "FROM user_recent_transactions "
            + "WHERE userid = ? "
            + "AND SINCE_EPOCH(Second,txn_time) < SINCE_EPOCH(Second,DATEADD(MILLISECOND, ?,NOW))"
            + "ORDER BY txn_time,userid,user_txn_id LIMIT 1;");

    private static final long FIVE_MINUTES_AGO_IN_MS = 1000 * 60 * -5;

    
    // @formatter:on

  public VoltTable[] run(long userId, long productId, int unitsUsed, int unitsWanted, long inputSessionId, String txnId)
      throws VoltAbortException {

    long currentBalance = 0;
    long unitCost = 0;
    long sessionId = inputSessionId;

    if (sessionId <= 0) {
      sessionId = this.getUniqueId();
    }

    voltQueueSQL(getUser, userId);
    voltQueueSQL(getProduct, productId);
    voltQueueSQL(getTxn, userId, txnId);
    voltQueueSQL(removeOldestTransaction, userId, FIVE_MINUTES_AGO_IN_MS);

    VoltTable[] results = voltExecuteSQL();

    // Sanity check: Does this user exist?
    if (!results[0].advanceRow()) {
      throw new VoltAbortException("User " + userId + " does not exist");
    }

    // Sanity Check: Does this product exist?
    if (!results[1].advanceRow()) {
      throw new VoltAbortException("Product " + productId + " does not exist");
    } else {
      unitCost = results[1].getLong("UNIT_COST");
    }

    // Sanity Check: Is this a re-send of a transaction we've already done?
    if (results[2].advanceRow()) {
      this.setAppStatusCode(ReferenceData.TXN_ALREADY_HAPPENED);
      this.setAppStatusString("Event already happened at " + results[2].getTimestampAsTimestamp("txn_time").toString());
      return voltExecuteSQL(true);
    }
    
 
    

    long amountSpent = unitsUsed * unitCost * -1;

    if (unitsUsed > 0) {

       // Report spending...
      voltQueueSQL(reportSpending, userId, amountSpent, unitsUsed + " units of product " + productId);
      voltQueueSQL(updBalance, userId);

      voltExecuteSQL();

    }

    // Delete allocation record for current product/session
    voltQueueSQL(deleteAllocation, userId, productId, sessionId);

    // Note that transaction is now 'official'
    voltQueueSQL(addTxn, userId, txnId, productId, amountSpent);

    voltQueueSQL(getCurrentAllocation, userId, productId, sessionId);   
    voltQueueSQL(getRemainingCredit, userId);
    voltQueueSQL(getBalance, productId, sessionId, userId);

    final VoltTable[] interimResults = voltExecuteSQL();

    // Check the balance...
    interimResults[4].advanceRow();
    currentBalance = interimResults[4].getLong("BALANCE");

    // If we have any reservations use them instead.
    if (interimResults[3].advanceRow()) {
      currentBalance = interimResults[3].getLong("BALANCE");
    }

    // if unitsWanted is 0 or less then this transaction is finished...

    if (unitsWanted <= 0) {
      return interimResults;
    }

    long wantToSpend = unitCost * unitsWanted;

    // Calculate how much we can afford ..
    long whatWeCanAfford = Long.MAX_VALUE;

    if (unitCost > 0) {
      whatWeCanAfford = currentBalance / unitCost;
    }

    if (currentBalance <= 0 || whatWeCanAfford == 0) {

      this.setAppStatusString("Not enough money");
      this.setAppStatusCode(ReferenceData.STATUS_NO_MONEY);

    } else if (wantToSpend > currentBalance) {

      this.setAppStatusString("Allocated " + whatWeCanAfford + " units");
      this.setAppStatusCode(ReferenceData.STATUS_SOME_UNITS_ALLOCATED);
      voltQueueSQL(createAllocation, userId, productId, whatWeCanAfford, sessionId);

    } else {

      this.setAppStatusString("Allocated " + unitsWanted + " units");
      this.setAppStatusCode(ReferenceData.STATUS_ALL_UNITS_ALLOCATED);
      voltQueueSQL(createAllocation, userId, productId, unitsWanted, sessionId);

    }

    voltQueueSQL(getCurrentAllocation, userId, productId, sessionId);
    voltQueueSQL(getRemainingCredit, userId);
    voltQueueSQL(getBalance, productId, sessionId, userId);

    return voltExecuteSQL(true);
  }
}
