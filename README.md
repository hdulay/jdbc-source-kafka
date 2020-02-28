# Non-connector based jdbc source client to Kafka

This application is not a Kafka Connect application. It is a simple JDBC application that converts a result set to JSON and publishes it to Kakfa. It will try to perform CDC by checking the latest update timestamp for the entire table. It will persist that timestamp for the next query defined by **wait** in the configuration. If the application is restarted, it will resume from the checkpoint (timestamp persisted in a file called **lastupdate.txt**). Look for this file when the application is running. This will only read 1 table. It will use the primary key you provide in the configuration file as the key in Kafka. This application only supports tables with a single primary key but can be modified to build a composite key from multiple columns. This implementation also does not support complex data types.

## Offset handling

In order for the offset handling to work, the SQL provided in the 

## Getting Started

### Create a config.properties file in the local directory

```properties
bootstrap-servers=localhost:9092
topic=mysql-customers
url=jdbc:mysql://localhost:3306/demo
driver=org.gjt.mm.mysql.Driver
user=connect_user
password=asgard
sql=select * from customers where UPDATE_TS > ? and id > ? order by UPDATE_TS
wait=10000
update_col=UPDATE_TS
primary_key=id
```

### Create a confluent.properties file in the local directory

For Confluent Cloud

```properties
ssl.endpoint.identification.algorithm=https
sasl.mechanism=PLAIN
request.timeout.ms=20000
bootstrap.servers=CHANGE_ME.confluent.cloud:9092
retry.backoff.ms=500
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="KEY" password="SECRET";
security.protocol=SASL_SSL
```

For Confluent Platform

```properties
bootstrap.servers=localhost:9092
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
## for confluent platform
kafka-console-consumer --bootstrap-server localhost:9092 --topic mytopic --property print.key=true --property key.separator=":"
## for confluent cloud ( make sure you'ved logged in )
ccloud kafka topic consume mytopic
```

## ORACLE

Run sqlplus to connect to database

```bash
docker run -v ${PWD}:/tmp -e URL=${USERNAME}/${PASSWORD}@//${HOSTNAME}:1521/DATABASE -ti sflyr/sqlplus
```

```sql
CREATE TABLE test (
    id NUMBER,
    first_name VARCHAR2(50) NOT NULL,
    info VARCHAR2(50),
    last_update TIMESTAMP,
    PRIMARY KEY(id)
);  
set autocommit on
insert into test values (1, 'hubert', 'test', CURRENT_TIMESTAMP);
update test set info = 'updated value2', last_update = CURRENT_TIMESTAMP where id = 1;
```

## SQL SERVER ISSUE

https://docs.microsoft.com/en-us/sql/connect/jdbc/using-basic-data-types?view=sql-server-ver15

The TIMESTAMP data type in sql server is mapped to BINARY data type in JDBC. datetime and datetime2 are mapped to TIMESTAMP in JDBC. This application does not set the prepared statement value to BINARY. datetime2 is more precise than datetime and will always return because it's greater than the offset.

* Sql server TIMESTAMP -> JDBC BINARY
* datetime2 is too percise and always returns in the results
* datatime is an older datatype. it does not seem to respect greater than

In order to make this successful, a custom SQL Server producer will need to be created that does not conform to JDBC.
