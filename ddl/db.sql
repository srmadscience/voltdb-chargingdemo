load classes ../jars/voltdb-chargingdemo.jar;

file -inlinebatch END_OF_BATCH

CREATE table product_table
(productid bigint not null primary key
,productname varchar(50) not null
,unit_cost bigint not null);

DR TABLE product_table;

CREATE table user_table
(userid bigint not null primary key
,user_json_object varchar(8000)
,user_last_seen TIMESTAMP DEFAULT NOW
,user_softlock_sessionid bigint 
,user_softlock_expiry TIMESTAMP);

create index ut_del on user_table(user_last_seen);

PARTITION TABLE user_table ON COLUMN userid;

DR table user_table;

create table user_recent_transactions
 MIGRATE TO TARGET user_transactions
(userid bigint not null 
,user_txn_id varchar(128)
,txn_time TIMESTAMP DEFAULT NOW  not null 
,productid bigint
,amount bigint 
,primary key (userid, user_txn_id))
USING TTL 1 HOURS ON COLUMN txn_time;

PARTITION TABLE user_recent_transactions ON COLUMN userid;

CREATE INDEX urt_del_idx ON user_recent_transactions(userid, txn_time);

CREATE INDEX urt_del_idx2 ON user_recent_transactions(txn_time);

DR table user_recent_transactions;

CREATE STREAM user_financial_events 
partition on column userid
export to target finevent
(userid bigint not null 
,amount bigint not null
,purpose varchar(80) not null);

create table user_usage_table
 MIGRATE TO TARGET user_usage_table_stale_entries
(userid bigint not null
,productid bigint not null
,allocated_units bigint not null
,sessionid bigint  not null
,lastdate timestamp not null
,primary key (userid, productid,sessionid));

CREATE INDEX uut_del_idx ON user_usage_table(lastdate,userid, productid,sessionid);

PARTITION TABLE user_usage_table ON COLUMN userid;

DR table user_usage_table;

create view allocated_by_product
as
select productid, count(*) how_many, sum(allocated_units) allocated_units
from user_usage_table
group by productid;

create view user_balance_total_view
as
select userid, count(*) tran_count, sum(amount) balance
from user_financial_events
group by userid;

create table user_balances
(userid bigint not null  primary key
,tran_count bigint not null
,balance bigint not null);

PARTITION TABLE user_balances ON COLUMN userid;

DR TABLE user_balances;

CREATE VIEW total_balances AS
SELECT sum(balance) balance 
FROM user_balances;

CREATE PROCEDURE getTotalBalance AS
SELECT * FROM total_balances;

CREATE PROCEDURE checkBalance
PARTITION ON TABLE user_table COLUMN userid
as
select v.userid, v.balance - sum(uut.allocated_units * p.unit_cost )  credit
from  user_balances v 
   , user_usage_table uut
   , product_table p
where v.userid = ?
and   v.userid = uut.userid
and   p.productid = uut.productid
group by v.userid, v.balance;

create procedure showTransactions
PARTITION ON TABLE user_table COLUMN userid
as 
select * from user_recent_transactions where userid = ? ORDER BY txn_time, user_txn_id;

CREATE PROCEDURE showCurrentAllocations AS
select p.productid, p.productname, a.allocated_units, a.allocated_units * p.unit_cost value_at_risk
from product_table p, allocated_by_product a
where p.productid = a.productid
order by p.productid;
  
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.GetUser;
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.GetAndLockUser;
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.UpdateLockedUser;
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.UpsertUser;
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.DelUser;
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.ReportQuotaUsage;  
   
CREATE PROCEDURE 
   PARTITION ON TABLE user_table COLUMN userid
   FROM CLASS chargingdemoprocs.AddCredit;  

DROP TASK DeleteStaleAllocationsTask IF EXISTS;
   
DROP PROCEDURE DeleteStaleAllocations IF EXISTS;
  
CREATE PROCEDURE DIRECTED
   FROM CLASS chargingdemoprocs.DeleteStaleAllocations;  
   
CREATE TASK DeleteStaleAllocationsTask
ON SCHEDULE DELAY 1 SECONDS
PROCEDURE DeleteStaleAllocations ON ERROR LOG
RUN ON PARTITIONS;

DROP TASK DeleteStaleAllocationsTask;

DROP TASK PurgeWrangler IF EXISTS;

CREATE TASK PurgeWrangler  FROM CLASS chargingdemotasks.PurgeWrangler WITH (10,30000) ON ERROR LOG RUN ON PARTITIONS;


END_OF_BATCH
