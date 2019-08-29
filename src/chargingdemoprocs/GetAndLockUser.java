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
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.types.TimestampType;

public class GetAndLockUser extends VoltProcedure {

  // @formatter:off

    public static final SQLStmt getUser = new SQLStmt("SELECT userid ,user_json_object " + 
        ",user_last_seen,user_softlock_sessionid,user_softlock_expiry, now the_current_timestamp "
        + "FROM user_table "
        + "WHERE userid = ?;");
    
    public static final SQLStmt getBalance = new SQLStmt("SELECT * FROM user_balances WHERE userid = ?;");
    
    public static final SQLStmt getUsage = new SQLStmt("SELECT * FROM user_usage_table WHERE userid = ? ORDER BY productid;");

    public static final SQLStmt getAllTxn = new SQLStmt("SELECT user_txn_id, txn_time "
        + "FROM user_recent_transactions "
        + "WHERE userid = ? ORDER BY txn_time, user_txn_id;");

    public static final SQLStmt upsertUserLock = new SQLStmt("UPDATE user_table "
        + "SET user_softlock_sessionid = ? "
        + "   ,user_softlock_expiry = DATEADD(MILLISECOND,?,?) "
        + "WHERE userid = ?;");

    // @formatter:on

  /**
   * Gets all the information we have about a user.
   * 
   * @param userId
   * @return
   * @throws VoltAbortException
   */
  public VoltTable[] run(long userId) throws VoltAbortException {

    voltQueueSQL(getUser, userId);

    VoltTable[] userRecord = voltExecuteSQL();

    // Sanity check: Does this user exist?
    if (!userRecord[0].advanceRow()) {
      throw new VoltAbortException("User " + userId + " does not exist");
    }

    final TimestampType currentTimestamp = userRecord[0].getTimestampAsTimestamp("the_current_timestamp");
    final TimestampType lockingSessionExpiryTimestamp = userRecord[0].getTimestampAsTimestamp("user_softlock_expiry");

    if (lockingSessionExpiryTimestamp != null && lockingSessionExpiryTimestamp.compareTo(currentTimestamp) > 0) {

      final long lockingSessionId = userRecord[0].getLong("user_softlock_sessionid");
      this.setAppStatusCode(ReferenceData.RECORD_ALREADY_SOFTLOCKED);
      this.setAppStatusString("User " + userId + " has already been locked by session " + lockingSessionId);

    } else {
      this.setAppStatusCode(ReferenceData.RECORD_HAS_BEEN_SOFTLOCKED);
      this.setAppStatusString("User " + userId + " newly locked by session " + getUniqueId());
      voltQueueSQL(upsertUserLock, getUniqueId(),  ReferenceData.LOCK_TIMEOUT_MS, currentTimestamp, userId);
    }

    voltQueueSQL(getUser, userId);
    voltQueueSQL(getBalance, userId);
    voltQueueSQL(getUsage, userId);
    voltQueueSQL(getAllTxn, userId);

    return voltExecuteSQL(true);

  }
}
