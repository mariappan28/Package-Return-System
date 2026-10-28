BAG RETURN & DEPOSIT REFUND SYSTEM  (normal version)
Java 21 | Hibernate 5.6 | XAMPP MySQL | Maven

ECLIPSE + XAMPP STEPS
1. XAMPP Control Panel -> Start MySQL (port 3306).
2. Extract the zip.
3. Eclipse -> File -> Import -> Maven -> Existing Maven Projects -> select the folder -> Finish
   (internet needed once to download jars)
4. Right click Main.java (com.bagreturn) -> Run As -> Java Application
   (database "bagreturn_db" and tables are created automatically)

FOLDER STRUCTURE
  model/  -> Product, Customer, CustomerOrder, OrderItem, DepositContainer  (tables)
  util/   -> HibernateUtil                                                  (SessionFactory)
  Main.java                                                                 (menu + all Hibernate code, no DAO)

Config is XAMPP-ready: user=root, password=empty.
If port is not 3306, change it in src/main/resources/hibernate.cfg.xml.

DEMO FLOW
 2 -> Place order (add products, name, phone, address, channel)
 5 -> View pending containers
 3 -> Return container CNT-1 -> refund = 10% deposit
 4 -> View all orders -> container status RETURNED
 7 -> View deposit & refund summary (deposit, refund, REFUNDED / NOT REFUNDED)
