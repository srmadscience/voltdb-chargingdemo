
DROP TASK DeleteStaleAllocationsTask IF EXISTS;
   

DROP TASK PurgeWrangler IF EXISTS;

DROP PROCEDURE getTotalBalance IF EXISTS;

DROP PROCEDURE checkBalance IF EXISTS;

drop procedure showTransactions IF EXISTS;

DROP PROCEDURE showCurrentAllocations IF EXISTS;
  
DROP PROCEDURE chargingdemoprocs.GetUser IF EXISTS;
   
DROP PROCEDURE chargingdemoprocs.GetAndLockUser IF EXISTS;
   
DROP PROCEDURE chargingdemoprocs.UpdateLockedUser IF EXISTS;
   
DROP PROCEDURE chargingdemoprocs.UpsertUser IF EXISTS;
   
DROP PROCEDURE chargingdemoprocs.DelUser IF EXISTS;
   
DROP PROCEDURE ReportQuotaUsage IF EXISTS;  
   
DROP PROCEDURE chargingdemoprocs.AddCredit IF EXISTS;  

DROP PROCEDURE DeleteStaleAllocations IF EXISTS;
    

drop view allocated_by_product IF EXISTS;

drop view user_balance_total_view IF EXISTS;

drop VIEW total_balances IF EXISTS;


drop table product_table IF EXISTS;

drop table user_table IF EXISTS;

drop table user_recent_transactions IF EXISTS;

DROP STREAM user_financial_events IF EXISTS ;

drop table user_usage_table IF EXISTS;

drop table user_balances IF EXISTS;



