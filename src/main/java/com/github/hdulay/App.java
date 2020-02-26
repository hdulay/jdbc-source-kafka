package com.github.hdulay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import org.apache.commons.cli.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Hello world!
 *
 */
public class App
{

    Logger log = LoggerFactory.getLogger(App.class);

    public App(Properties props) throws IOException, ClassNotFoundException, SQLException {
        String bootstrapServers = props.getProperty("bootstrap-servers");
        String topic = props.getProperty("topic");
        String url = props.getProperty("url");
        String driver = props.getProperty("driver");
        String user = props.getProperty("user");
        String password = props.getProperty("password");
        String sql = props.getProperty("sql");
        String update_col = props.getProperty("update_col");
        String primary_key = props.getProperty("primary_key");
        long wait = Long.parseLong(props.getProperty("wait"));

        Properties config = new Properties();
        config.put("client.id", InetAddress.getLocalHost().getHostName());
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class.getName());
        config.put("value.serializer", StringSerializer.class.getName());
        config.put("acks", "all");
        KafkaProducer producer = new KafkaProducer<String, String>(config);

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try(Connection c = getConnection(driver, url, user, password)) {
                    log.info("got connection");
                    final File lastupdate = new File("lastupdate.txt");
                    long last_update = lastupdate.exists() ?
                            Long.parseLong(Files.readFirstLine(lastupdate, Charset.defaultCharset()))
                            : -1;
                    Timestamp ts = new Timestamp(last_update);
                    PreparedStatement ps = c.prepareStatement(sql);
                    ps.setTimestamp(1, ts);
                    ResultSet rs = ps.executeQuery();
                    Timestamp last = send(producer, rs, topic, primary_key, update_col);
                    if(last != null) {
                        String lastUpdate = Long.toString(last.getTime());
                        Files.write(lastUpdate.getBytes(), lastupdate);
                    }

                } catch (Exception e) {
                    cancel();
                    e.printStackTrace();
                }
            }
        };
        timer.schedule(task, 0, wait);

        /*
        select * from foo where last_update >= ?
         */

    }

    private Timestamp send(KafkaProducer producer, ResultSet rs, String topic, String primary_key, String update_col) throws SQLException, JsonProcessingException {
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount() + 1;
        ObjectMapper objectMapper = new ObjectMapper();
        Timestamp lastUpdate = null;
        while(rs.next()) {
            Map<String, Object> map = new HashMap<>();
            for (int i = 1; i < count; i++) {
                String colname = md.getColumnName(i);
                switch(md.getColumnType(i)) {
                    case Types.BIGINT: map.put(colname, rs.getLong(i)); break;
                    case Types.BOOLEAN: map.put(colname, rs.getBoolean(i)); break;
                    case Types.DATE: map.put(colname, rs.getDate(i)); break;
                    case Types.DECIMAL: map.put(colname, rs.getBigDecimal(i)); break;
                    case Types.DOUBLE: map.put(colname, rs.getDouble(i)); break;
                    case Types.FLOAT: map.put(colname, rs.getFloat(i)); break;
                    case Types.INTEGER: map.put(colname, rs.getInt(i)); break;
                    default: map.put(colname, rs.getString(i)); break;
                }
            }
            Timestamp update = rs.getTimestamp(update_col);
            lastUpdate = lastUpdate == null ? update : lastUpdate.before(update) ? update : lastUpdate;
            String key = rs.getString(primary_key);

            String json = objectMapper.writeValueAsString(map);
            ProducerRecord record = new ProducerRecord(topic, key, json);
            Future<RecordMetadata> future = producer.send(record, (rmd, e) -> {
                if(e != null) {
                    log.info("got error sending message "+e.getMessage());
                    e.printStackTrace();

                    ObjectMapper om = new ObjectMapper();
                    Map<String, Object> error = new HashMap<>();
                    error.put("exception", e);
                    error.put("metadata", rmd);
                    try {
                        String errorj = om.writeValueAsString(error);
                        Files.append(errorj, new File("errors.txt"), Charset.defaultCharset());
                    } catch (JsonProcessingException ex) {
                        ex.printStackTrace();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    log.info("set message to "+rmd.topic());
                }
            });
        }

        return lastUpdate;

    }

    public static void main( String[] args ) throws IOException, ParseException, SQLException, ClassNotFoundException {

        // create Options object
        Options options = new Options();
        options.addOption("p", "prop", true, "properties file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);

        String filename = cmd.hasOption('p') ? cmd.getOptionValue("p") : "config.properties";
        Properties props = new Properties();
        props.load(new FileReader(filename));

        App a = new App(props);
    }

    public Connection getConnection(String driver, String url, String user, String pass) throws ClassNotFoundException, SQLException {
        //STEP 2: Register JDBC driver
        Class.forName(driver);

        //STEP 3: Open a connection
        System.out.println("Connecting to database...");
        return DriverManager.getConnection(url, user ,pass);
    }
}
