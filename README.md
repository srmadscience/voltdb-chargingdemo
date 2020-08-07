https://github.com/srmadscience/voltdb-chargingdemo# Charging Demo: A non-trivial telco focused example

## Introduction

In this post I&#39;d like to highlight [voltdb-chargingdemo](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src), which I&#39;ve recently made available on bitbucket. Most demos are designed to be as simplistic as possible. I&#39;ve always found that frustrating, as anyone who has ever written a real world application knows that what takes two lines in a demo can take about 50 in reality. With that in mind, I wrote [voltdb-chargingdemo](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src), which is intended to demonstrate how we can help in scenarios such as telco where users are working with shared and finite resources while meeting SLAs, such as SMS messages or bandwidth. Instead of simplifying things to the point of absurdity, it tries to be realistic yet still comprehensible to outsiders.

My own background is in telco, and the demo is a drastically simplified representation of what&#39;s known as a &#39;[charging](https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=1896)&#39; system. Every prepaid cell phone uses one of these - it decides what the user can do, how long they can do it for, and tells downstream systems what was used when the activity finishes. In such a system the following activities happen:

1. &quot;[Provision](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src/chargingdemoprocs/UpsertUser.java)&quot; a user.  This happens once, and is the part where they enter the number on your sim card into the computer at the store so the phone systems knows that that sim card is now 1-510-555-1212 or whatever your number is.

1. &quot;[Add Credit](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src/chargingdemoprocs/AddCredit.java)&quot;. This is when a third party system tells the phone company&#39;s computer that you have just gone to a recharging center and added US$20 in credit.

1. &quot;[Report Usage and Reserve More](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src/chargingdemoprocs/ReportQuotaUsage.java)&quot;. In real life this is several steps, but to keep things simple we use one. In this the phone system tells VoltDB how much of a resource you&#39;ve used (&quot;Usage&quot;), and how much they think you&#39;ll need over the next 30 seconds or so (&quot;Reserve&quot;). Normal practice is to hand over a larger chunk than we think you&#39;ll need, as if you run out we may have to freeze your ability to call or internet activity, depending on your usage, until we get more. For a given user this is happening about once every 30 seconds, for each activity.

## The challenges

All of this seems simple, but then we have to consider various other &#39;real world&#39; factors:

**Products**.

Our demo phone company has 4 products. As in real life, a user can use more than one at once:

| Product | Unit Cost |
| --- | --- |
| The phone company&#39;s  web site. Customers can always access the phone company&#39;s web site, even if they are out of money. | 0 |
| SMS messages, per message. | 1c |
| Domestic Internet Access per GB | 20c |
| Roaming Internet Access per GB | $3.42 |
| Domestic calls per minute | 3c |

This means that when serving requests we need to turn the incoming request for &#39;access per GB&#39; into real money and compare it to the user&#39;s balance when deciding how much access to grant .

**We have to factor in reserved balances when making decisions**

We shouldn&#39;t let you spend money you haven&#39;t got, so your usable balance has to take into account what you&#39;ve reserved. Note that any credit you&#39;ve reserved affects your balance immediately, so your balance can sometimes spike up slightly if you reserve 800 units and then come back reporting usage of 200.

**Sanity Checks**

Like any real world production code we need to be sure that the users and products we&#39;re talking about are real, and that somebody hasn&#39;t accidentally changed the order of the parameters being sent to the system.

**High Availability**

Although this demo doesn&#39;t include support for failovers (I&#39;m working on a HA/XDCR demo) the schema does. In any HA scenario you have to cope with a situation where you send a request to the database and then don&#39;t know whether it worked or not, as  you didn&#39;t get a message back. Given that when we report usage we&#39;re spending customer&#39;s money we can never, ever get into a situation where we charge them twice. This means that each call to &quot;Add Credit&quot; or &quot;Report Usage and Reserve More&quot; needs to include a unique identifier for the transaction, and the system needs to keep a list of successful transactions for long enough to make sure it&#39;s not a duplicate.

**Downstream Systems**

In addition to allowing or denying access we need to tell a downstream back office system when money is spent or added. This needs to be accurate and up to date. In our demo we implement this using an [Export Stream](https://docs.voltdb.com/UsingVoltDB/ChapExport.php) called &#39;user\_financial\_events&#39;, which goes to a logical destination called &#39;finevent&#39;. &#39;Finevent&#39; can be [Kafka](https://docs.voltdb.com/UsingVoltDB/ExportToKafka.php), [Kinesis](https://github.com/VoltDB/export-kinesis), [JDBC](https://docs.voltdb.com/UsingVoltDB/ExportToJdbc.php), etc.

**Value at Risk calculations**

We need to know:

1. What is the total amount of credit we are holding in this system?
2. How much is currently being reserved for each product?

This needs to be checked every few seconds without causing disruption. This is a classic [HTAP](https://www.gartner.com/it-glossary/htap-enabling-memory-computing-technologies)/[Translytics](https://www.forrester.com/report/Emerging+Technology+Translytical+Databases+Deliver+Analytics+At+The+Speed+Of+Transactions/-/E-RES116487) use case.

**Multiple Devices &amp; Sessions**

Although we don&#39;t show it in the demo, the schema and code support multiple devices sharing a balance. In the real world we see this in &#39;friends and family&#39; plans. This means that knowing you are user #42 and want to report usage for product #3 isn&#39;t enough, as two or more devices could be doing this at the same time. We thus have a requirement for system generated unique session identifiers.

More importantly we can never tolerate a situation where two devices on the same plan spend the same money twice!

**Latency Expectations**

The phone company&#39;s server has about 50ms (1/20th of a second) to decide what to do when a request shows up. Because we&#39;re only a small part of this decision making process, we can only spend between 5-10ms per request. Note that we need to be consistently fast. An average latency of 7ms is no good if the &#39;[99th percentile](https://www.elastic.co/blog/averages-can-dangerous-use-percentile)&#39; latency is 72ms.

**Scale Expectations**

It&#39;s not unusual for 20,000,000 users to be on one of these systems. In this demo we design for a [busy hour](https://en.wikipedia.org/wiki/Busy-hour_call_attempts) spike on New Year&#39;s eve where 25% of customers are active at the same moment. This implies 5,000,000 active devices, each  creating a &quot;Report Usage and Reserve More&quot; request every 30 seconds. 5,000,000 / 30 = 166,666 transactions per second, which is our design goal.

Note that in Use Cases where latency spikes are OK, scaling is usually a lot easier.

**Arbitrary Payload**

We also sometimes have to store device session data, which is presented to us as a JSON object. While the code allows you to [read, softlock](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src/chargingdemoprocs/GetAndLockUser.java) and [update](https://github.com/srmadscience/voltdb-chargingdemo/tree/master/src/chargingdemoprocs/UpdateLockedUser.java) this JSON it isn&#39;t currently part of the demo.

## Our Schema

![schema](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/results/chargingdemo_schema.png "Schema")

| Name | Type | Purpose | Partitioning |
| --- | --- | --- | --- |
| user\_table | Table | holds one record per user and the JSON payload. | userid |
| Product\_table | Table | Holds one record per product |   |
| User\_usage\_table | Table | holds information on active reservations of credit by a user for a product. | userid |
| User\_balances | View |  It has one row per user and always contains the user&#39;s current credit, before we allow for reservations in &quot;user\_usage\_table&quot;. | userid |
| User\_recent\_transactions | Table | allows us to spot duplicate transactions and also allows us to track what happened to a specific user during a run | userid |
| allocated\_by\_product | View | How much of each product is currently reserved |   |
| total\_balances | View | A single row listing how much credit the system holds. |   |
| User\_financial\_events | [Export stream](https://docs.voltdb.com/UsingVoltDB/ExportProjectFile.php) | inserted into when we add or spend money | userid |
| finevent | [Export target](https://docs.voltdb.com/UsingVoltDB/ExportProjectFile.php) | Where rows in user\_financial\_events end up - could be kafka, kinesis, HDFS etc | userid |



## How to run the demo

### Prerequisites

In the example below we assume we have access to a 4 node AWS cluster based on Ubuntu.

We used a cluster with the following configuration:

- 4 x AWS z1d.3xlarge nodes (1 client, 3 for the server)
- Command Logs and Snapshots on internal SSD drive
- [K factor](https://docs.voltdb.com/UsingVoltDB/KSafeEnable.php)of &#39;1&#39;.
- Default settings for [command log flush interval](https://docs.voltdb.com/UsingVoltDB/CmdLogConfig.php).
- Sitesperhost set to default value of 8.
- 20,000,000 users
- Use the script [sh](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/scripts/runtest.sh) to run 5 instances at the same time

### Goal

- Run 166,666 or more transactions per second.
- 99th percentile latency needs to be 10ms or under.
- The transactions will be  80% &quot;[Report Usage and Reserve More](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/ReportQuotaUsage.java)&quot; and 20% &quot;[Add Credit](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/AddCredit.java)&quot;
- We will also call &quot;showCurrentAllocations&quot; and &quot;getTotalBalance&quot; every 10 seconds.

### Steps

#### Obtain VoltDB

VoltDB can be downloaded [here](https://www.voltdb.com/try-voltdb/).

#### Create your cluster

Instructions for how to do this are [here](https://docs.voltdb.com/AdminGuide/). Alternatively we can give you access to the AWS CloudFormation scripts we used if you contact us.

#### Obtain the Demo

git clone https://github.com/srmadscience/voltdb-chargingdemo/voltdb-chargingdemo.git

#### Create the schema

cd voltdb-chargingdemo/ddl

sqlcmd --servers=vdb1 \&lt; [db.sql](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/ddl/db.sql)

Note that this code loads a jar file from voltdb-chargingdemo/jars.

### Run ChargingDemo

ChargingDemo lives in a JAR file called &#39;voltdb-chargingdemo-client.jar&#39; and takes the following parameters:

| Name | Purpose | Example |
| --- | --- | --- |
| hostnames | Comma delimited list of nodes that make up your VoltDB cluster | vdb1,vdb2,vdb3 |
| recordcount | How many users. | 200000 |
| offset | Used when we want to run multiple copies of ChargingDemo with different users. If recordcount is 2500000 calling a second copy of ChargingDemo with an offset of 3000000 will lead it to creating users in the range 3000000 to 5500000 | 0 |
| tpms | How many transactions per millisecond you want to achieve. Note that a single instance of ChargingDemo will only have a single VoltDB client, which will limit it to around 200 TPMS. To go beyond this you need to run more than one copy. | 83 |
| task | One of:DELETE - deletes users and dataUSERS - creates usersTRANSACTIONS - does testrun OrRUN - Does DELETE, USERS and then TRANSACTIONS | RUN |
| loblength | How long the arbitrary JSON payload is | 10 |
| durationseconds | How long TRANSACTIONS runs for in seconds | 300 |
| queryseconds | How often we query to check allocations and balances in seconds, along with an arbitrary query of a single user. | 10 |
| initialcredit | How much credit users start with. A high value for this will reduce the number of times AddCredit is called. | 1000 |
| addcreditinterval | How often we add credit based on the number of transactions we are doing - a value of &#39;6&#39; means every 6th transaction will be AddCredit. A value of 0 means that AddCredit is only called for a user when &#39;initialcredit&#39; is run down to zero. | 6 |

An invocation of the demo looks like this:

java -jar ../jars/voltdb-chargingdemo-client.jar vdb1,vdb2,vdb3 1000000 1000000 32 RUN 10 300 10 100000 5



To make things easier we use a file called &quot;[runtest.sh](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/scripts/runtest.sh)&quot;, which creates the users and then runs the workload at increasing intervals and puts the results in a file. Note that runtest.sh will need to be tweaked in order for you to use it.

&quot;[runtest.sh](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/scripts/runtest.sh)&quot; can be persuaded to do a series of runs at increasing TPS levels and put the results in a file for later analysis, which is what we did.

### Sample Results

In the graph below the green line is &quot;Requested TPMS&quot; - How many transactions per millisecond we were trying to do.


![graph](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/results/sorted_raw_data_chart190725.png "graph")

The red line is what we actually did. Due to the vagaries of how the test runs it&#39;s often slightly higher than &quot;Requested TPMS&quot; at the start, but then tracks it reasonably accurately.

The grey line is server CPU Busy %, which is on the right hand scale. We see that it accurately aligns with &quot;Actual TPMS&quot;, which is good.

The blue line is the 99th Percentile latency for [ReportQuotaUsage](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/ReportQuotaUsage.java). This is where we start to see the system hit its limits. Until 272 TPMS it&#39;s 1ms, but then it rapidly spikes to 9ms at 274 TPMS and breaks our SLA at 286 TPMS with 19ms. This is what we&#39;d expect, as the CPU is around 75% by then, and requests are starting to queue, which manifests itself as latency.

The Blue dashed line below is the average latency for  [ReportQuotaUsage](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/ReportQuotaUsage.java), and shows that if you didn&#39;t care about the 99th percentile and were willing to work with average latency instead, you could probably get around 25% more TPMS out of the system.

The Green lines show us that the profile of [AddCredit](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/AddCredit.java) is pretty much the same.

In practical terms this means we could  easily meet the requested workload of 166,666 TPS.



The data for the above graph can be found [here](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/results/sorted_raw_data190725.xls).

Putting the results in context

**Work per CPU**

Each [z1d.3xlarge](https://aws.amazon.com/ec2/instance-types/z1d/) provides 6 physical CPU cores, so in a 3 node cluster with k=1 we&#39;re actually using 12 cores, as all the work is being done twice on two servers. At 270K TPS each physical core is therefore processing 22,500 requests per second.

**Statements per call**

Each request  can and does issue multiple SQL statements. For example &quot;[Report Usage and Reserve More](https://github.com/srmadscience/voltdb-chargingdemo/blob/master/src/chargingdemoprocs/ReportQuotaUsage.java)&quot; issues between 7 and 14 each invocation, so if you want to look at this in terms of &quot;SQL statements per second&quot; the actual capacity is around 2,700,000 operations per second.

## Conclusion

In this blog post and accompanying demo I&#39;ve shown that VoltDB can be used to build very high performance ACID compliant applications that provide the benefits of a traditional RDBMS and run in a 100% virtualized cloud environment while providing high availability.