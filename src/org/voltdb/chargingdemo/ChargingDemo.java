package org.voltdb.chargingdemo;

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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.voltutil.stats.SafeHistogramCache;
import org.voltdb.voltutil.stats.StatsHistogram;

public class ChargingDemo {

  // Possible values for 'TASK'
  private static final String TASK_TRANSACTIONS = "TRANSACTIONS";
  private static final String TASK_USERS = "USERS";
  private static final String TASK_RUN = "RUN";
  private static final String TASK_DELETE = "DELETE";

  /**
   * How many products exist
   */
  public static final int PRODUCT_COUNT = 5;

  /**
   * We use a counter to track progress. Unsurprisingly it starts at zero.
   */
  private static final long NEW_USER = 0;

  /**
   * @param args
   */
  public static void main(String[] args) {

    SafeHistogramCache shc = SafeHistogramCache.getInstance();

    msg("Parameters:" + Arrays.toString(args));

    Random r = new Random();

    if (args.length < 8) {
      msg("Usage: hostnames recordcount offset tpms task loblength durationseconds queryseconds initialcredit addcreditinterval");
      System.exit(1);
    }

    // Comma delimited list of hosts...
    String hostlist = args[0];

    // How many users
    int userCount = Integer.parseInt(args[1]);

    // Used to allow multiple copies of client to run at once. Makes demo start
    // creating ids
    // from 'offset' instead of zero.
    int offset = Integer.parseInt(args[2]);

    // Target transactions per millisecond.
    int tpMs = Integer.parseInt(args[3]);

    // 'RUN' will try and do everything. 'USERS' creates users, 'DELETE' deletes
    // 'USERS'
    // and 'TRANSACTIONS' does the actual benchmark.
    String task = args[4];

    if (!(task.equalsIgnoreCase(TASK_TRANSACTIONS) || task.equalsIgnoreCase(TASK_USERS)
        || task.equalsIgnoreCase(TASK_RUN) || task.equalsIgnoreCase(TASK_DELETE))) {
      msg("Legal values for TASK: ");
      msg(TASK_TRANSACTIONS);
      msg(TASK_USERS);
      msg(TASK_RUN);
      msg(TASK_DELETE);
      System.exit(2);
    }

    // How long our arbitrary JSON payload will be.
    int loblength = Integer.parseInt(args[5]);
    final String ourJson = getArbitraryJsonString(loblength);

    // Runtime for TRANSACTIONS in seconds.
    int durationSeconds = Integer.parseInt(args[6]);

    // How often we do global queries...
    int globalQueryFreqSeconds = Integer.parseInt(args[7]);

    // Default credit users are 'born' with
    int initialCredit = Integer.parseInt(args[8]);

    // How often we add credit regardless of how much is left
    int addCreditInterval = Integer.parseInt(args[9]);

    long lastGlobalQueryMs = 0;

    // In some cases we might want to run a check at the
    // end of the benchmark that all of our transactions did in fact happen.
    // the 'state' array contains a model of what things *ought* to look like.
    UserState[] state = new UserState[userCount];

    try {
      // A VoltDB Client object maintains multiple connections to all the
      // servers in the cluster.
      Client mainClient = connectVoltDB(hostlist);

      // UpdateSessionStateCallback examines responses and updates the sessionId
      // for a
      // user. SessionId is created inside a VoltDB procedure.
      UpdateSessionStateCallback ussc = new UpdateSessionStateCallback(state, offset);

      // Delete users if asked...
      if (task.equalsIgnoreCase(TASK_DELETE) || task.equalsIgnoreCase(TASK_RUN)) {

        final long startMsDelete = System.currentTimeMillis();
        long currentMs = System.currentTimeMillis();

        // To make sure we do things at a consistent rate (tpMs) we
        // track how many transactions we've queued this ms and sleep if
        // we've reached our limit.
        int tpThisMs = 0;

        // So we iterate through all our users...
        for (int i = 0; i < userCount; i++) {

          if (tpThisMs++ > tpMs) {

            // but sleep if we're moving too fast...
            while (currentMs == System.currentTimeMillis()) {
              Thread.sleep(0, 50000);
            }

            currentMs = System.currentTimeMillis();
            tpThisMs = 0;
          }

          // Put a request to delete a user into the queue.
          ReportLatencyCallback deleteUserCallback = new ReportLatencyCallback("DelUser");
          mainClient.callProcedure(deleteUserCallback, "DelUser", i + offset);

          if (i % 100000 == 1) {
            msg("Deleted " + i + " users...");
          }

        }

        // Because we've put messages into the clients queue we
        // need to wait for them to be processed.
        msg("All entries in queue, waiting for it to drain...");
        mainClient.drain();

        final long entriesPerMs = userCount / (System.currentTimeMillis() - startMsDelete);
        msg("Deleted " + entriesPerMs + " users per ms...");

      }

      // Create users if needed.
      if (task.equalsIgnoreCase(TASK_USERS) || task.equalsIgnoreCase(TASK_RUN)) {

        final long startMsUpsert = System.currentTimeMillis();
        long currentMs = System.currentTimeMillis();
        int tpThisMs = 0;

        for (int i = 0; i < userCount; i++) {

          if (tpThisMs++ > tpMs) {

            while (currentMs == System.currentTimeMillis()) {
              Thread.sleep(0, 50000);
            }

            currentMs = System.currentTimeMillis();
            tpThisMs = 0;
          }

          ReportLatencyCallback upsertUserCallback = new ReportLatencyCallback("UpsertUser");

          mainClient.callProcedure(upsertUserCallback, "UpsertUser", i + offset, initialCredit, "Y", ourJson, "Created",
              new Date(startMsUpsert), "Create_" + i);

          if (i % 100000 == 1) {
            msg("Upserted " + i + " users...");

          }

          state[i] = new UserState(i, initialCredit);

        }

        msg("All entries in queue, waiting for it to drain...");
        mainClient.drain();

        long entriesPerMs = userCount / (System.currentTimeMillis() - startMsUpsert);
        msg("Upserted " + entriesPerMs + " users per ms...");

      }

      // Now do the actual benchmark bit....
      if (task.equalsIgnoreCase(TASK_TRANSACTIONS) || task.equalsIgnoreCase(TASK_RUN)) {

        for (int i = 0; i < userCount; i++) {

          if (state[i] == null) {
            state[i] = new UserState(i, initialCredit);
            state[i].IncUserStatus();
          }
        }

        final long startMsRun = System.currentTimeMillis();
        long currentMs = System.currentTimeMillis();
        int tpThisMs = 0;

        final long endtimeMs = System.currentTimeMillis() + (durationSeconds * 1000);

        // How many transactions we've done...
        int tranCount = 0;
        int inFlightCount = 0;

        while (endtimeMs > System.currentTimeMillis()) {

          if (tpThisMs++ > tpMs) {

            while (currentMs == System.currentTimeMillis()) {
              Thread.sleep(0, 50000);
            }

            currentMs = System.currentTimeMillis();
            tpThisMs = 0;
          }

          // Find session to do a transaction for...
          int oursession = r.nextInt(userCount);

          // See if session already has an active transaction and avoid
          // it if it does.

          if (state[oursession].isTxInFlight()) {
            inFlightCount++;
          } else {

            int ourProduct = r.nextInt(PRODUCT_COUNT);
            long sessionId = UserState.SESSION_NOT_STARTED;

            // Come up with reports on how much we used and how much we want...

            // usedUnits is usually less than what we requested last time.

            final int requestUnits = 50 + r.nextInt(49);
            long usedUnits = r.nextInt(50);

            // state[oursession].getUserStatus() will be zero (STATUS_NEW_USER)
            // the first time we access a session.

            sessionId = state[oursession].getProductSessionId(ourProduct);

            if (sessionId == UserState.SESSION_NOT_STARTED) {
              usedUnits = 0;
            } else if (state[oursession].getProductAllocation(ourProduct) < usedUnits) {
              usedUnits = state[oursession].getProductAllocation(ourProduct);
            }

            // Every ADD_CREDIT_INTERVAL we add credit instead of using it...
            if (addCreditInterval == 0 && state[oursession].getBalance() < 20) {

              final long extraCredit = chooseTopUpAmount(state[oursession].getBalance(), r);

              AddCreditCallback addCreditCallback = new AddCreditCallback("AddCredit", state, oursession, offset);
              mainClient.callProcedure(addCreditCallback, "AddCredit", oursession + offset, extraCredit,
                  "AddCreditOnShortage" + "_" + state[oursession].getUserStatus() + "_" + tranCount + "_"
                      + extraCredit);

            } else if (addCreditInterval > 0 && state[oursession].getUserStatus() >= addCreditInterval
                && state[oursession].getUserStatus() % addCreditInterval == 0) {

              final long extraCredit = chooseTopUpAmount(state[oursession].getBalance(), r);

              ReportLatencyCallback addCreditCallback = new ReportLatencyCallback("AddCredit");
              mainClient.callProcedure(addCreditCallback, "AddCredit", oursession + offset, extraCredit,
                  "AddCreditAtInterval" + "_" + state[oursession].getUserStatus() + "_" + tranCount + "_"
                      + extraCredit);

            } else {
              // Otherwise report how much credit we used and ask for more...
              state[oursession].startTran();

              mainClient.callProcedure(ussc, "ReportQuotaUsage", oursession + offset, ourProduct, usedUnits,
                  requestUnits, sessionId, "ReportQuotaUsage" + "_" + state[oursession].getUserStatus() + "_"
                      + tranCount + "_" + usedUnits + "_" + ourProduct);

            }

            state[oursession].IncUserStatus();

            tranCount++;
          }

          if (tranCount % 100000 == 1) {
            msg("Transaction " + tranCount);

          }

          // See if we need to do global queries...
          if (lastGlobalQueryMs + (globalQueryFreqSeconds * 1000) < System.currentTimeMillis()) {
            lastGlobalQueryMs = System.currentTimeMillis();

            final int queryUserId = 42;
            // Query user #queryUserId...
            msg("Query user #" + queryUserId + "...");
            final long startQueryUserMs = System.currentTimeMillis();
            ClientResponse userResponse = mainClient.callProcedure("GetUser", queryUserId);
            shc.reportLatency("GetUser", startQueryUserMs, "", 50);

            for (int i = 0; i < userResponse.getResults().length; i++) {
              msg(System.lineSeparator() + userResponse.getResults()[i].toFormattedString());
            }

            msg("Show amount of credit currently reserved for products...");
            final long startQueryAllocationsMs = System.currentTimeMillis();
            ClientResponse allocResponse = mainClient.callProcedure("showCurrentAllocations");
            shc.reportLatency("showCurrentAllocations", startQueryAllocationsMs, "", 50);

            for (int i = 0; i < allocResponse.getResults().length; i++) {
              msg(System.lineSeparator() + allocResponse.getResults()[i].toFormattedString());
            }

            msg("Show total credit held by system...");
            final long startQueryTotalBalanceMs = System.currentTimeMillis();
            ClientResponse balanceResponse = mainClient.callProcedure("getTotalBalance");
            shc.reportLatency("getTotalBalance", startQueryTotalBalanceMs, "", 50);

            for (int i = 0; i < balanceResponse.getResults().length; i++) {
              msg(System.lineSeparator() + balanceResponse.getResults()[i].toFormattedString());
            }

          }

        }

        msg(tranCount + " transactions done...");
        msg("All entries in queue, waiting for it to drain...");
        mainClient.drain();
        msg("Queue drained...");
        long transactionsPerMs = tranCount / (System.currentTimeMillis() - startMsRun);
        msg("processed " + transactionsPerMs + " entries per ms while doing transactions...");
        msg(inFlightCount + " events where a tx was in flgiht were observed");
        msg("Waiting 10 seconds - if we are using XDCR we need to wait for remote transactions to reach us");

        Thread.sleep(10000);

        // having done all our transactions we now check to see if they all made
        // it to the
        // database....

        msg("Checking Transactions and Users. Count= " + tranCount + "/" + state.length);

        final long startCheckRun = System.currentTimeMillis();

        for (int i = 0; i < state.length; i++) {
          shc.incCounter("stateloop");
          if (state[i].getUserStatus() == NEW_USER) {
            shc.incCounter("neverstarted");
          } else {
            shc.incCounter("started");
            ComplainOnDifferenceCallback codc = new ComplainOnDifferenceCallback(
                state[i].getUserStatus() + 1 /* Status 0 was used */ ,
                state[i] + ":" + i + ": Expecting " + (state[i].getUserStatus()));
            mainClient.callProcedure(codc, "showTransactions", i + offset);

          }

          if (i % 100000 == 0) {
            msg("User=" + i);

          }

        }

        mainClient.drain();

        long entriesPerMs = tranCount / (System.currentTimeMillis() - startCheckRun);
        msg("processed " + entriesPerMs + " entries per ms while checking...");

        StringBuffer oneLineSummary = new StringBuffer("GREPABLE SUMMARY:");

        oneLineSummary.append(tpMs);
        oneLineSummary.append(':');

        oneLineSummary.append(transactionsPerMs);
        oneLineSummary.append(':');

        getProcPercentiles(shc, oneLineSummary, "ReportQuotaUsage");

        getProcPercentiles(shc, oneLineSummary, "UpdateSession");

        getProcPercentiles(shc, oneLineSummary, "GetUser");

        getProcPercentiles(shc, oneLineSummary, "showCurrentAllocations");

        getProcPercentiles(shc, oneLineSummary, "getTotalBalance");

        msg(oneLineSummary.toString());

      }

      msg("Closing connection...");
      mainClient.close();

      msg("Stats Histogram:");
      msg(shc.toString());

    } catch (Exception e) {
      msg(e.getMessage());
    }

  }

  private static long chooseTopUpAmount(long balance, Random r) {
    if (balance > 0) {
      return 100 + r.nextInt(300);
    }
    return 100 + r.nextInt(300) + (-1 * balance);

  }

  /**
   * @param shc
   * @param oneLineSummary
   */
  private static void getProcPercentiles(SafeHistogramCache shc, StringBuffer oneLineSummary, String procName) {
    StatsHistogram rqu = shc.get(procName);
    oneLineSummary.append((int) rqu.getLatencyAverage());
    oneLineSummary.append(':');

    oneLineSummary.append(rqu.getLatencyPct(50));
    oneLineSummary.append(':');

    oneLineSummary.append(rqu.getLatencyPct(99));
    oneLineSummary.append(':');
  }

  /**
   * Print a formatted message.
   * 
   * @param message
   */
  public static void msg(String message) {

    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Date now = new Date();
    String strDate = sdfDate.format(now);
    System.out.println(strDate + ":" + message);

  }

  /**
   * Connect to VoltDB using a comma delimited hostname list.
   * 
   * @param commaDelimitedHostnames
   * @return
   * @throws Exception
   */
  private static Client connectVoltDB(String commaDelimitedHostnames) throws Exception {
    Client client = null;
    ClientConfig config = null;

    try {
      msg("Logging into VoltDB");

      config = new ClientConfig(); // "admin", "idontknow");
      config.setTopologyChangeAware(true);
      config.setReconnectOnConnectionLoss(true);

      client = ClientFactory.createClient(config);

      String[] hostnameArray = commaDelimitedHostnames.split(",");

      for (int i = 0; i < hostnameArray.length; i++) {
        msg("Connect to " + hostnameArray[i] + "...");
        try {
          client.createConnection(hostnameArray[i]);
        } catch (Exception e) {
          msg(e.getMessage());
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
      throw new Exception("VoltDB connection failed.." + e.getMessage(), e);
    }

    return client;

  }

  /**
   * Convenience method to generate a JSON payload.
   * 
   * @param length
   * @return
   */
  private static String getArbitraryJsonString(int length) {

    final String startJson = "{ \"payload\":\"";
    final String endJson = "\" }";
    final int charsNeeded = length - startJson.length() - endJson.length();

    StringBuffer ourText = new StringBuffer(startJson);

    for (int i = 0; i < charsNeeded; i++) {
      ourText.append('x');
    }

    ourText.append(endJson);

    return ourText.toString();
  }

}
