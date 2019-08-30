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

public class UpdateSession extends VoltProcedure {

  // @formatter:off

  public static final SQLStmt getSession = new SQLStmt("SELECT * FROM session_table WHERE session_id = ?;");
  
  public static final SQLStmt upsertSession = new SQLStmt("UPSERT INTO session_table (session_id, session_clob_object, session_touchdate) VALUES (?,?,NOW);");
  
     // @formatter:on

  public VoltTable[] run(long sessionId, String overwriteSession, String sessionAppend) throws VoltAbortException {

    if (overwriteSession != null && overwriteSession.length() > 0) {
      // Queue request to upsert current session
      voltQueueSQL(upsertSession, sessionId, overwriteSession);

    } else {
      // Queue request to get current session. Throws VoltAbortException if we're asked to append a session that doesn't exist.
      voltQueueSQL(getSession, EXPECT_ONE_ROW, sessionId);

      // Get first round of results. voltExecuteSQL always returns an array of VoltTable.
      VoltTable[] interimResults = voltExecuteSQL();
      VoltTable currentSessionTable = interimResults[0];
      currentSessionTable.advanceRow();
      final String existingSession = currentSessionTable.getString("session_clob_object");

      // Append data
      voltQueueSQL(upsertSession, sessionId, existingSession + sessionAppend);

    }

    return voltExecuteSQL(true);
  }
}
