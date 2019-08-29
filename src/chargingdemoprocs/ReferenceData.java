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

/**
 * Location for possible response codes.
 * 
 * @author drolfe
 *
 */
public class ReferenceData {
    
    public static final byte STATUS_OK = 42;
    
    public static final byte STATUS_NO_MONEY = 43;
    public static final byte STATUS_SOME_UNITS_ALLOCATED = 44;
    public static final byte STATUS_ALL_UNITS_ALLOCATED = 45;
    public static final byte TXN_ALREADY_HAPPENED = 46;
    
    public static final byte USER_DOESNT_EXIST = 50;
    public static final byte PRODUCT_DOESNT_EXIST = 51;
    public static final byte USER_NO_FINHIST = 52;
    public static final byte RECORD_ALREADY_SOFTLOCKED = 53;
    public static final byte RECORD_HAS_BEEN_SOFTLOCKED = 54;
    public static final byte USER_EXISTS_BUT_SHOULDNT = 55;
    
    public static final byte CREDIT_ADDED = 55;
    
    public static final int  LOCK_TIMEOUT_MS = 50;

}
