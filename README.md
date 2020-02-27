# Non-connector based jdbc source client to Kafka

This application is not a Kafka Connect application. It is a simple JDBC application that converts a result set to JSON and publishes it to Kakfa. It will try to perform CDC by checking the latest update timestamp for the entire table. It will persist that timestamp for the next query defined by **wait** in the configuration. If the application is restarted, it will resume from the checkpoint (timestamp persisted in a file called **lastupdate.txt**). Look for this file when the application is running. This will only read 1 table. It will use the primary key you provide in the configuration file as the key in Kafka. This application only supports tables with a single primary key but can be modified to build a composite key from multiple columns. This implementation also does not support complex data types.

## Offset handling

In order for the offset handling to work, the SQL provided in the 

## Getting Started

### Populate the configuration file

Change the property values in **config.properties** to use your own database.

```properties
bootstrap-servers=localhost:9092
topic=mysql-customers
url=jdbc:mysql://localhost:3306/demo
driver=org.gjt.mm.mysql.Driver
user=connect_user
password=asgard
sql=select * from customers where UPDATE_TS > ? and id > ? order by UPDATE_TS, id
wait=10000
update_col=UPDATE_TS
primary_key=id

```

### Execute the maven commands

```bash
# install the JDBC driver. Copy your driver into the drivers directory and 
# replace this.is.my.driver.jar with that name.
mvn install:install-file -Dfile=drivers/this.is.my.driver.jar -DgroupId=jdbc.source.kafka -DartifactId=not.a.connector -Dversion=1 -Dpackaging=jar
# build the project
mvn package
# run the application
mvn exec:java -Dexec.mainClass="com.github.hdulay.App"
# consume the data. replace mytopic with the topic to which you're writing
kafka-console-consumer --bootstrap-server localhost:9092 --topic mytopic --property print.key=true --property key.separator=":"
```
