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
import org.voltdb.types.TimestampType;

public class UpsertUser extends VoltProcedure {

    // @formatter:off

    public static final SQLStmt getUser = new SQLStmt("SELECT userid FROM user_table WHERE userid = ?;");
    
    public static final SQLStmt getBalance = new SQLStmt("SELECT balance FROM user_balances WHERE userid = ?;");
    
    public static final SQLStmt getTxn = new SQLStmt("SELECT txn_time FROM user_recent_transactions WHERE userid = ? AND user_txn_id = ?;");

    public static final SQLStmt addTxn = new SQLStmt("INSERT INTO user_recent_transactions (userid, user_txn_id, txn_time, amount) VALUES (?,?,NOW,?);");

    public static final SQLStmt purgeTxn = new SQLStmt("DELETE FROM user_recent_transactions "
            + "WHERE userid = ? AND user_txn_id != ? AND txn_time < DATEADD( MINUTE, ?, NOW);");
    
    public static final SQLStmt upsertUser = new SQLStmt("UPSERT INTO user_table (userid, user_json_object,user_last_seen) VALUES (?,?,?);");
    
    public static final SQLStmt addCredit = new SQLStmt(
            "INSERT INTO user_financial_events (userid   ,amount, purpose)    VALUES (?,?,?);");

    public static final SQLStmt updBalance = new SQLStmt(
            "upsert into user_balances select userid, tran_count, balance from user_balance_total_view where userid = ?;");

    private static final int PURGE_MINUTES = -120;

    // @formatter:on

    /**
     * Upsert a user.
     * 
     * @param userId
     * @param addBalance
     * @param isNew
     * @param json
     * @param purpose
     * @param lastSeen
     * @param txnId
     * @return
     * @throws VoltAbortException
     */
    public VoltTable[] run(long userId, long addBalance, String isNew, String json, String purpose,
            TimestampType lastSeen, String txnId) throws VoltAbortException {

        long currentBalance = 0;

        voltQueueSQL(getUser, userId);
        voltQueueSQL(getBalance, userId);
        voltQueueSQL(getTxn, userId, txnId);

        VoltTable[] results = voltExecuteSQL();

        if (results[2].advanceRow()) {

            this.setAppStatusCode(ReferenceData.TXN_ALREADY_HAPPENED);
            this.setAppStatusString(
                    "Event already happened at " + results[2].getTimestampAsTimestamp("txn_time").toString());

        } else {

            
            voltQueueSQL(addTxn, userId, txnId, addBalance);
            
            if (isNew.equalsIgnoreCase("Y")) {

                if (results[0].advanceRow()) {
                    throw new VoltAbortException("User " + userId + " exists but shouldn't");
                }

                currentBalance = addBalance;

                final String status = "Created user " + userId + " with opening credit of " + addBalance;
                voltQueueSQL(upsertUser, userId, json, lastSeen);
                voltQueueSQL(addCredit, userId, addBalance, status);
                voltQueueSQL(updBalance, userId);
                this.setAppStatusCode(ReferenceData.STATUS_OK);
                this.setAppStatusString(status);

            } else {
                
                if (!results[0].advanceRow()) {
                    throw new VoltAbortException("User " + userId + " does not exist");
                }

                if (!results[1].advanceRow()) {
                    throw new VoltAbortException("User " + userId + " exists but has no financial history...");
                }

                currentBalance = results[1].getLong("BALANCE") + addBalance;

                final String status = "Updated user " + userId + " - added credit of " + addBalance + "; balance now "
                        + currentBalance;

                voltQueueSQL(upsertUser, userId, json, lastSeen);
                voltQueueSQL(addCredit, userId, addBalance, status);
                voltQueueSQL(updBalance, userId);
                voltQueueSQL(purgeTxn, userId, txnId, PURGE_MINUTES);

                this.setAppStatusCode(ReferenceData.STATUS_OK);
                this.setAppStatusString(status);

            }
        }
        return voltExecuteSQL(true);
    }
}
