# Non-connector based jdbc source client to Kafka

This application is not a Kafka Connect application. It is a simple JDBC application that converts a result set to JSON and publishes it to Kakfa. It will try to perform CDC by checking the latest update timestamp for the entire table. It will persist that timestamp for the next query defined by **waite** in the configuration. This will only read 1 table. It will use the primary key you provide in the configuration file as the key in Kafka. This application only supports tables with a single primary key but can be modified to build a composite key from multiple columns. This implementation also does not support complex data types.

## Getting Started

### Populate the configuration file

Change the property values to use you own database.

```properties
bootstrap-servers=localhost:9092
topic=mysql-customers
url=jdbc:mysql://localhost:3306/demo
driver=org.gjt.mm.mysql.Driver
user=connect_user
password=asgard
sql=select * from customers where UPDATE_TS > ?
wait=10000
update_col=UPDATE_TS
primary_key=id

```

### Execute the maven commands

```bash
mvn package
mvn exec:java -Dexec.mainClass="com.github.hdulay.App"
```
