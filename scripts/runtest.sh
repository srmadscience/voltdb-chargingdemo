#!/bin/sh

if 
	[ "$#" -ne "9" ]
then
	echo Usage: startps tps_increment stoptps duration_in_seconds initial_credit how_often_we_add_credit thread_count hostname user_count_per_thread 
	echo Example: sh -x runtest.sh 10 1 21 180 100000 5 10 localhost 100000

	exit 1
fi

STARTTPS=$1
INCTPS=$2
STOPTPS=$3
DURATIONSECONDS=$4
INITIALCREDIT=$5
CREDITINTERVAL=$6
TCOUNT=$7
HNAME=$8
USERCOUNT=$9

THISTPS=${STARTTPS}

PFILE=`date '+%Y%m%d%H%M'`perf.txt

#
# Kill any copies that were left running in the background because someone did ctrl-c last time...
#
kill -9 `ps -deaf | grep voltdb-chargingdemo-server.jar | grep -v grep | awk '{ print $2 }'` 2> /dev/null

#
# Calculate total number of users...
#
TOTAL_USERCOUNT=`expr ${USERCOUNT} \* ${TCOUNT}`

#
# Delete a re-create that many users at 50K
#
java -jar ../jars/voltdb-chargingdemo-server.jar $HNAME $TOTAL_USERCOUNT 0 50 DELETE 10 600 10 100000 5
java -jar ../jars/voltdb-chargingdemo-server.jar $HNAME $TOTAL_USERCOUNT 0 50 USERS 10 600 10 100000 5


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

	# Start a network monitor in the background. This monitors
	# traffic to and from this client, not in the cluster as a whole
  	bmon -o ascii > bmon.out &
  	BPROC=$!

	# Spawn a copy of charging server demo for each thread..
	JCOUNT=0
	while
		[ "${JCOUNT}" -lt "${TCOUNT}" ]
	do
	
		# grab CPU 60 secs in future
		sh remotecpu.sh & 
		# Calculate user range and offset
		THIS_OFFSET=`expr ${JCOUNT} \* ${USERCOUNT}`
		echo java -jar ../jars/voltdb-chargingdemo-server.jar $HNAME $USERCOUNT ${THIS_OFFSET}  $TPS_PER_THREAD  TRANSACTIONS 10 ${DURATIONSECONDS}  10 $INITIALCREDIT ${CREDITINTERVAL} > ${LFILE}_${JCOUNT}.lst  
		java -jar ../jars/voltdb-chargingdemo-server.jar $HNAME $USERCOUNT ${THIS_OFFSET} $TPS_PER_THREAD  TRANSACTIONS 10 ${DURATIONSECONDS}  10 $INITIALCREDIT ${CREDITINTERVAL} >> ${LFILE}_${JCOUNT}.lst  &
		JCOUNT=`expr ${JCOUNT} + 1 ` 
	done

	# wait until they have finished doing the TRANSACTIONS part...
	sleep ${DURATIONSECONDS}

	# stop monitoring the network...
  	kill $BPROC

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

	# Get network data. Note horrible bug - we assume it's all on ens5...
	#
	# This needs to be chaanged!
	#
  	cat bmon.out  | grep ens5 >> $LFILE.lst
	rm bmon.out

	# get network numbers...
 	NETWORK=`cat ${LFILE}.lst | grep ens5 | awk '{ print $2, ":", $4 }' | tail -1`

	# Get AWS instance type (if on AWS)
  	ITYPE=`curl http://169.254.169.254/latest/meta-data/instance-type`

	# Remote CPU
	REMOTECPU=`cat remotecpu.txt`

	# local CPU
	LOCALCPU=`cat localcpu.txt` 

	# write this to master file...
  	echo $THISTPS:$ACTUALREPORTEDTPS:$DURATIONSECONDS:$INITIALCREDIT:$CREDITINTERVAL:$TCOUNT:$HNAME:$GS:$NETWORK:$ITYPE:$REMOTECPU:$LOCALCPU >> $PFILE

	# delete the records we created...
	sqlcmd --servers=$HNAME < del.sql

	# Move to next TPS value
	THISTPS=`expr ${THISTPS} + ${INCTPS}`		
done

