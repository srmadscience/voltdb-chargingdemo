/* This file is part of VoltDB.
 * Copyright (C) 2020 VoltDB Inc.
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
package chargingdemotasks;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.voltdb.VoltTable;
import org.voltdb.client.ClientResponse;
import org.voltdb.task.ActionResult;
import org.voltdb.task.ActionScheduler;
import org.voltdb.task.ScheduledAction;
import org.voltdb.task.TaskHelper;

/**
 * Runnable task that maintains a cache of results from
 * 
 * @Statistics and SystemCatalog, as well as the tasks that create and use this
 *             data.
 * 
 */
public class PurgeWrangler implements ActionScheduler {

    /**
     * delay in ms between individual job calls
     */
    long m_shortTimeInterval = 10;

    /**
     * delay in ms between cycle of job calls restarting
     */
    long m_longInterval = 20000;

    /**
     * TaskHelper is a utiity class that among other things allows us to write
     * to volt.log...
     */
    TaskHelper m_helper;

    /**
     * Called as a consequence of the CREATE TASK DDL, with the 'helper' being
     * provided by VoltDB:
     * <p>
     * <code>
     * CREATE TASK PurgeWrangler  FROM CLASS watchermanager.StatisticsWrangler WITH (10,30000) ON ERROR LOG;
     * </code>
     * 
     * @param m_helper
     *            A TaskHelper that gives us access to volt.log etc.
     * @param m_shortTimeInterval
     *            how long to wait between task invocations (ms)
     * @param m_longInterval
     *            how long as set of tasks should take to run in total (ms)
     */
    public void initialize(TaskHelper helper, int shortInterval, int longInterval) {

        this.m_shortTimeInterval = shortInterval;
        this.m_longInterval = longInterval;
        this.m_helper = helper;

        msg(TaskMessageType.INFO,
                "PurgeWrangler started with long/short delays of " + longInterval + "/" + shortInterval);

    }

    /**
     * Jumpstart the task queue by calling @SystemInformation OVERVIEW, which
     * will tell us cluster id.
     */
    @Override
    public ScheduledAction getFirstScheduledAction() {

        final Object[] m_overviewParams = {};
        return ScheduledAction.procedureCall(m_shortTimeInterval, TimeUnit.MILLISECONDS, this::getNextScheduledAction,
                "DeleteStaleAllocations", m_overviewParams);
    }

    /**
     * Get next thing to do.
     * 
     * @return ScheduledAction
     */
    public ScheduledAction getNextScheduledAction(ActionResult ar) {

        long delay = m_longInterval;

        final Object[] m_overviewParams = {};

        if (ar.getResponse().getStatus() == ClientResponse.SUCCESS && ar.getResponse().getResults().length > 50) {
            delay = m_shortTimeInterval;
        }

        msg(TaskMessageType.INFO, "Calling DeleteStaleAllocations. Last pass removed "
                + ar.getResponse().getResults().length + " records. Delay is " + delay + " ms");

        return ScheduledAction.procedureCall(delay, TimeUnit.MILLISECONDS, this::getNextScheduledAction,
                "DeleteStaleAllocations", m_overviewParams);

    }

    /**
     * Write a message to volt.log or standard output.
     * 
     * @param messageType
     *            a type defined in TaskMessageType
     * @param message
     *            message text
     */
    public void msg(TaskMessageType messageType, String message) {

        if (m_helper != null) {

            switch (messageType) {
            case DEBUG:
                m_helper.logDebug(message);
                break;
            case INFO:
                m_helper.logInfo(message);
                break;
            case WARNING:
                m_helper.logWarning(message);
                break;
            case ERROR:
                m_helper.logError(message);
                break;
            }

        } else {
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date now = new Date();
            String strDate = sdfDate.format(now);
            System.out.println(strDate + ":" + message);
        }

    }

}
