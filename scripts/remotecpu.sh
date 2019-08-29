#!/bin/sh

sleep 60
ssh -i /home/ubuntu/VDB01.pem ubuntu@vdb1 ' top -b -n 3 ' | grep Cpu | tail -1 | awk '{ print $8 }' > remotecpu.txt
top -b -n 3 | grep Cpu | tail -1 | awk '{ print $8 }' > localcpu.txt
