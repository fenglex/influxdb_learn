package com.fenglex;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import okhttp3.OkHttpClient;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author haifeng
 * @version 1.0
 * @date 2020/8/10 16:53
 */
public class InfluxTest {

    private static InfluxDB influxDB;

    @BeforeAll
    public static void before() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)//设置链接超时
                .writeTimeout(60, TimeUnit.SECONDS) // 设置写数据超时
                .readTimeout(60, TimeUnit.SECONDS) // 设置读数据超时
                .build();
        influxDB = InfluxDBFactory.connect("http://localhost:8086", client.newBuilder());


        influxDB.setDatabase("test");
        influxDB.setLogLevel(InfluxDB.LogLevel.HEADERS);
        influxDB.setConsistency(InfluxDB.ConsistencyLevel.ALL);
        influxDB.enableGzip();
        influxDB.enableBatch();
    }

    @Test
    public void createDatabase() {
        Query query = new Query("CREATE DATABASE test");
        influxDB.query(query);
    }


    @Test
    public void showDataBase() {
        Query query = new Query("SHOW DATABASES");
        QueryResult queryResult = influxDB.query(query);
        for (QueryResult.Result result : queryResult.getResults()) {
            System.out.println(JSON.toJSONString(result));
        }
    }

    @Test
    public void insert() {
        DateTime dateTime = DateUtil.parseDate("2020-08-11");
        Date date = dateTime.toJdkDate();
        for (int i = 0; i < 8; i++) {
            DateTime time = DateUtil.offsetDay(date, i);
            int day = Integer.parseInt(DateUtil.format(time.toJdkDate(), "yyyyMMdd"));
            String code = "600520.SH";
            Map<String, Object> field = new HashMap<String, Object>();
            field.put("value", i);
            Map<String, String> tag = new HashMap<String, String>();
            tag.put("trade_day", String.valueOf(day));
            tag.put("stock_code", code);
            Point point = Point.measurement("tb_stock").fields(field).tag(tag).time(time.getTime(), TimeUnit.MILLISECONDS).build();
            influxDB.write("test", "rp_1d", point);
        }
        influxDB.flush();
    }

    @Test
    public void batchWrite() {
        DateTime dateTime = DateUtil.parseDate("2020-08-11");
        List<Point> points = new ArrayList<Point>(2000);
        Date date = dateTime.toJdkDate();
        for (int i = 0; i < 1000; i++) {
            DateTime time = DateUtil.offsetDay(date, i);
            int day = Integer.parseInt(DateUtil.format(time.toJdkDate(), "yyyyMMdd"));
            String code = "600519.SH";
            Map<String, Object> field = new HashMap<String, Object>();
            field.put("value", i);
            Map<String, String> tag = new HashMap<String, String>();
            tag.put("trade_day", String.valueOf(day));
            tag.put("stock_code", code);
            Point point = Point.measurement("tb_stock").fields(field).tag(tag).time(time.getTime(), TimeUnit.MILLISECONDS).build();
            points.add(point);
        }
        influxDB.writeWithRetry(BatchPoints.builder().points(points).build());
    }

    @Test
    public void dropTable() {
        Query query = new Query("DELETE FROM tb_stock WHERE stock_code='600520.SH'", "test");
        QueryResult result = influxDB.query(query);
        JSON.toJSONString(result, true);
    }

    @Test
    public void createRetention() {
        Query query = new Query("CREATE RETENTION POLICY \"rp_1d\" ON \"test\" DURATION 1d REPLICATION 1 DEFAULT");
        influxDB.query(query);
    }


    @Test
    public void testQueryTopN() {
        Query query = new Query("SELECT stock_code,trade_day,value FROM tb_stock GROUP BY stock_code order by time desc limit 2");
        QueryResult queryResult = influxDB.query(query);
        for (QueryResult.Result result : queryResult.getResults()) {
            System.out.println(JSON.toJSONString(result, true));
        }
    }

    @Test
    public void testQueryLastDay() {
        Query query = new Query("SELECT * FROM tb_stock", "test");
        QueryResult queryResult = influxDB.query(query);
        for (QueryResult.Result result : queryResult.getResults()) {
            System.out.println(JSON.toJSONString(result, true));
        }
        System.out.println("--------------");
        // 查询最新一天的数据的数据
        query = new Query("SELECT FIRST(day),stock_code,value FROM tb_stock where stock_code='600519.SH'", "test");
        queryResult = influxDB.query(query);
        for (QueryResult.Result result : queryResult.getResults()) {
            System.out.println(JSON.toJSONString(result, true));
        }
    }

    @Test
    public void count() {
        QueryResult queryResult = influxDB.query(new Query("SELECT COUNT(*) FROM tb_stock"));
        System.out.println(JSON.toJSONString(queryResult, true));
    }

    @Test
    public void inQuery() {
        QueryResult queryResult = influxDB.query(new Query("SELECT * FROM tb_stock WHERE stock_code='600519.SH' or stock_code='600520.SH' order by time desc limit 3"));
        System.out.println(JSON.toJSONString(queryResult, true));
    }
}
