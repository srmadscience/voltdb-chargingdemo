package org.voltdb.chargingdemo;

import java.util.Arrays;

import org.voltdb.voltutil.stats.SafeHistogramCache;

import chargingdemoprocs.ReferenceData;

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

/**
 * Class to keep track of how many transactions a given user has. It also keeps
 * track of whether a transaction is in progress and when it started.
 * 
 * @author drolfe
 *
 */
public class UserState {

  /**
   * Identifies this as a new user
   */
  static final int STATUS_NEW_USER = 0;

  /**
   * Used when we don't know session yet...
   */
  public static final long SESSION_NOT_STARTED = -1;

  /**
   * Used to report stats
   */
  SafeHistogramCache shc = SafeHistogramCache.getInstance();

  /**
   * ID of user.
   */
  int id = 0;

  /**
   * How many transactions we've for this user. Increments by 1 each time...
   */
  int userStatus = 0;

  /**
   * Each user has multiple products, each of which has its own sessionId, which
   * is created by ReportQuotaUsage. The session Ids are used so we know to
   * cancel reservations when we report usage.
   */
  private long[] productSessionIds = new long[ChargingDemo.PRODUCT_COUNT];

  private long[] productAllocations = new long[ChargingDemo.PRODUCT_COUNT];

  /**
   * When a transaction started, or zero if there isn't one.
   */
  long txStartMs = 0;

  /**
   * Balance, not including effects of in flight transactions. Is updated
   * by AddCredit and ReportQuotaUsage.
   */
  long balance = 0;

  /**
   * Create a record for a user.
   * 
   * @param id
   */
  public UserState(int id, int balance) {
    this.id = id;
    this.balance = balance;
    userStatus = STATUS_NEW_USER;

    for (int i = 0; i < productSessionIds.length; i++) {
      productSessionIds[i] = SESSION_NOT_STARTED;
      productAllocations[i] = 0;
    }
  }

  /**
   * Report start of transaction.
   */
  public void startTran() {

    if (isTxInFlight()) {
      shc.incCounter("Multiple Transactions in flight at once");
    }

    txStartMs = System.currentTimeMillis();
  }

  /**
   * Get the VoltDb generated Session ID for this user/product.
   * 
   * @param productId
   * @return session Id
   * @throws SessionidNotAvailableYetException
   *           if a transaction is in flight.
   */
  public long getProductSessionId(int productId) {
    
    return productSessionIds[productId];
  }
  
public long getProductAllocation(int productId) {
    
    return productSessionIds[productId];
  }

  /**
   * @return the txInFlight
   */
  public boolean isTxInFlight() {

    if (txStartMs > 0) {
      return true;
    }

    return false;
  }

  /**
   * We measure latency by comparing when this call happens to when startTran
   * was called.
   * 
   * @param productId
   * @param sessionid
   * @param statusByte
   */
  public void reportEndTransaction(int productId, long sessionid, byte statusByte, long allocation) {

    if (productSessionIds[productId] != SESSION_NOT_STARTED) {
      // We don't track the latency for the first call as mutiple requests
      // in flight at once...
      shc.reportLatency("ReportQuotaUsage", txStartMs, "", 250);
    }

    productSessionIds[productId] = sessionid;
    productSessionIds[productId] = allocation;

    if (statusByte == ReferenceData.STATUS_ALL_UNITS_ALLOCATED) {
      shc.reportLatency("STATUS_ALL_UNITS_ALLOCATED", txStartMs, "", 50);
    } else if (statusByte == ReferenceData.STATUS_SOME_UNITS_ALLOCATED) {
      shc.reportLatency("STATUS_SOME_UNITS_ALLOCATED", txStartMs, "", 50);
    } else if (statusByte == ReferenceData.STATUS_NO_MONEY) {
      shc.reportLatency("STATUS_NO_MONEY", txStartMs, "", 50);
    }

    txStartMs = 0;

  }

  public int getUserStatus() {
    return userStatus;
  }

  public void IncUserStatus() {
    userStatus++;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    String desc = "UserState [id=" + id + ", userStatus=" + userStatus + ", productSessionIds="
        + Arrays.toString(productSessionIds) + ", txStartMs=" + txStartMs + ", balance=" + balance + "]";

    return desc;
  }

  public void reportBalance(long balance) {
    this.balance = balance;

  }

  public long getBalance() {
    return balance;
  }

}
