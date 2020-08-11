SECS=$1
java -jar voltdb-chargingdemo-client.jar 192.168.0.14 100 0 1 TRANSACTIONS 100 5 $SECS 1000 100 &
java -jar voltdb-chargingdemo-client.jar 192.168.0.16 100 0 1 TRANSACTIONS 100 5 $SECS 1000 100
