#!/bin/sh


STARTTPS=10
INCTPS=1
STOPTPS=21
DURATIONSECONDS=180
INITIALCREDIT=100000
CREDITINTERVAL=5
TCOUNT=10
HNAME=192.168.0.20
USERCOUNT=100000

THISTPS=${STARTTPS}

(cd ../ddl; sqlcmd --servers=$HNAME < db.sql)
#(cd ../ddl; sqlcmd --servers=$HNAME < enabletask.sql)
PFILE=`date '+%Y%m%d%H%M'`perf.txt

#
# Kill any copies that were left running in the background because someone did ctrl-c last time...
#
kill -9 `ps -deaf | grep voltdb-chargingdemo-client.jar | grep -v grep | awk '{ print $2 }'` 2> /dev/null

#
# Calculate total number of users...
#
TOTAL_USERCOUNT=`expr ${USERCOUNT} \* ${TCOUNT}`

#
# Delete a re-create that many users at 50K
#
java -jar ../jars/voltdb-chargingdemo-client.jar $HNAME $TOTAL_USERCOUNT 0 50 DELETE 10 600 10 100000 5
java -jar ../jars/voltdb-chargingdemo-client.jar $HNAME $TOTAL_USERCOUNT 0 50 USERS 10 600 10 100000 5


# 
# loop through requested TPS's
#
while
	[ "${THISTPS}" -le "${STOPTPS}" ]
do
	echo TPS = ${THISTPS}

	# Divide requested TPS by number of threads. This makes 
        # things 'steppy' at low TPS and thread values
	TPS_PER_THREAD=`expr ${THISTPS}  / ${TCOUNT}`

	# Create a log file
	LFILE=chargingdemo_${THISTPS}_${TSK}
	rm ${LFILE}   2> /dev/null
	rm ${LFILE}_* 2> /dev/null


	# Spawn a copy of charging server demo for each thread..
	JCOUNT=0
	while
		[ "${JCOUNT}" -lt "${TCOUNT}" ]
	do
	
		# Calculate user range and offset
		THIS_OFFSET=`expr ${JCOUNT} \* ${USERCOUNT}`
		echo java -jar ../jars/voltdb-chargingdemo-client.jar $HNAME $USERCOUNT ${THIS_OFFSET}  $TPS_PER_THREAD  TRANSACTIONS 10 ${DURATIONSECONDS}  10 $INITIALCREDIT ${CREDITINTERVAL} > ${LFILE}_${JCOUNT}.lst  
		java -jar ../jars/voltdb-chargingdemo-client.jar $HNAME $USERCOUNT ${THIS_OFFSET} $TPS_PER_THREAD  TRANSACTIONS 10 ${DURATIONSECONDS}  10 $INITIALCREDIT ${CREDITINTERVAL} >> ${LFILE}_${JCOUNT}.lst  &
		JCOUNT=`expr ${JCOUNT} + 1 ` 
	done

	# 
	# Spawn reload script after delay...
	sleep 20
	(cd ../ddl; sqlcmd --servers=192.168.0.20 < dbprocs.sql)
	# wait until they have finished doing the TRANSACTIONS part...
	sleep ${DURATIONSECONDS}

	# See if DB still up...
	sqlcmd --servers=192.168.0.20 < /dev/null

	if 
		[ "$?" = 255 ]
	then
		echo REPRODUCED
		exit 1
	fi


	# wait until our background java processes finish...
	wait

	# Calculate TPS
	ACTUALREPORTEDTPS=0

	for i in ${LFILE}_[0123456789]*.lst 
	do
		TPSFORTHISFILE=`grep "entries per ms while doing transactions" $i | awk '{ print $3 }'`
		echo TPSFORTHISFILE=$TPSFORTHISFILE
		ACTUALREPORTEDTPS=`expr $ACTUALREPORTEDTPS + $TPSFORTHISFILE`
	done

	
	# consolidate each threads log file int the main one...
	cat ${LFILE}_[0123456789]*.lst >> $LFILE.lst
	
	# Get summary from the first thread...
	GS=`grep "GREPABLE SUMMARY" ${LFILE}_1.lst`
	rm ${LFILE}_[0123456789]*.lst

	# write this to master file...
  	echo $THISTPS:$ACTUALREPORTEDTPS:$DURATIONSECONDS:$INITIALCREDIT:$CREDITINTERVAL:$TCOUNT:$HNAME:$GS:$NETWORK:$ITYPE:$REMOTECPU:$LOCALCPU >> $PFILE

	# delete the records we created...
	sqlcmd --servers=$HNAME < del.sql

	# Move to next TPS value
	THISTPS=`expr ${THISTPS} + ${INCTPS}`		
done

