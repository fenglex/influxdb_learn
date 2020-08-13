package com.fenglex;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.jvm.hotspot.memory.Generation;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author haifeng
 * @version 1.0
 * @date 2020/8/12 10:05
 */
@Slf4j
public class GenerateData {
    private static InfluxDB influxDB;

    @BeforeAll
    public static void before() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(600, TimeUnit.SECONDS)//设置链接超时
                .writeTimeout(600, TimeUnit.SECONDS) // 设置写数据超时
                .readTimeout(600, TimeUnit.SECONDS) // 设置读数据超时
                .build();
        //influxDB = InfluxDBFactory.connect("http://localhost:8086", client.newBuilder());

        influxDB = InfluxDBFactory.connect("http://localhost:8086", "influxdb", "wIx87kbD9hycJyrG", client.newBuilder());
        influxDB.setDatabase("db_quote");
        influxDB.setLogLevel(InfluxDB.LogLevel.BASIC);
        influxDB.setConsistency(InfluxDB.ConsistencyLevel.ALL);
        influxDB.setRetentionPolicy("rp_month");
        influxDB.enableGzip();
        influxDB.enableBatch();
    }

    private long start;
    private long end;

    @BeforeEach
    public void beforeEach() {
        start = System.currentTimeMillis();
    }

    @AfterEach
    public void afterEach() {
        end = System.currentTimeMillis();
        System.out.println("执行时长：" + (end - start) + "毫秒");
    }

    @Test
    public void clearData() {
        Query query = new Query("DROP measurement tb_index");
        influxDB.query(query);
    }

    @Test
    public void createDataBase() {
        influxDB.query(new Query("CREATE DATABASE db_quote"));
    }

    @Test
    public void createRp() {
        influxDB.query(new Query("CREATE RETENTION POLICY \"rp_month\" ON \"test\" DURATION 30d REPLICATION 1 DEFAULT"));
    }

    @Test
    public void createData() {
        log.warn("批量写入");
        DateTime dateTime = DateUtil.parseDateTime("2020-08-13 10:00:00");
        List<Point> points = new ArrayList<Point>(2000);
        BigDecimal decimal = new BigDecimal("100");
        Date date = dateTime.toJdkDate();
        for (int i = 1000; i < 1300; i++) {
            for (int j = 0; j < 3000; j++) {
                final DateTime time = DateUtil.offsetMinute(date, j);
                String code = String.valueOf(i);
                Map<String, Object> field = new HashMap<String, Object>();
                field.put("price", String.valueOf(RandomUtil.randomBigDecimal(decimal)));
                Map<String, String> tag = new HashMap<String, String>();
                tag.put("code", code);
                tag.put("id", i + "" + j);
                tag.put("trade_day", DateUtil.format(time.toJdkDate(), "yyyyMMdd"));
                tag.put("trade_time", DateUtil.format(time.toJdkDate(), "HHmm"));
                Point point = Point.measurement("tb_stock").fields(field).tag(tag).time(time.getTime(),
                        TimeUnit.MILLISECONDS).build();
                points.add(point);
                if (points.size() > 500) {
                    influxDB.writeWithRetry(BatchPoints.builder().points(points).build());
                    influxDB.flush();
                    points.clear();
                }
            }
            if (!points.isEmpty()) {
                influxDB.writeWithRetry(BatchPoints.builder().points(points).build());
                influxDB.flush();
                points.clear();
            }
        }
        // 执行时长：147062毫秒
    }

    @Test
    public void queryTest() {
        Query query = new Query("SELECT * FROM tb_stock WHERE code='1020' order by time desc limit 1000");
        QueryResult query1 = influxDB.query(query);
        System.out.println(JSON.toJSONString(query1));
    }

    @Test
    public void queryM() {
        StringBuilder orStr = new StringBuilder();
        for (int i = 1003; i < 1250; i++) {
            orStr.append(" code='").append(i).append("' or");
        }
        Query query = new Query("SELECT count(code) FROM tb_stock where " + orStr + " code='1000'");
        QueryResult query1 = influxDB.query(query);
        System.out.println(JSON.toJSONString(query1));
    }

    @Test
    public void count() {
        Query query = new Query("SELECT distinct(code) FROM tb_stock ");
        QueryResult queryResult = influxDB.query(query);
        System.out.println(JSON.toJSONString(queryResult, true));
        query = new Query("show measurements");
        queryResult = influxDB.query(query);
        System.out.println(JSON.toJSONString(queryResult));

    }

    @Test
    public void indexWrite() throws IOException {
        String file = "/Users/fenglex/data/index-20200807.data";
        BufferedReader reader = FileUtil.getReader(file, "utf-8");
        String format = "yyyyMMddHHmmssSSS";
        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            String[] split = line.split("\001");
            String tradeTime = split[3];
            String tradeDay = split[0];
            String code = split[4];
            String price = split[12];
            String preClose = split[15];
            DateTime dateTime = DateUtil.parse(tradeDay + tradeTime, format);
            Map<String, Object> field = new HashMap<String, Object>();
            field.put("price", price);
            field.put("preClose", preClose);
            Map<String, String> tag = new HashMap<String, String>();
            tag.put("code", code);
            tag.put("trade_day", tradeDay);
            tag.put("trade_time", DateUtil.format(dateTime.toJdkDate(), "HHmm"));
            Point point = Point.measurement("tb_index").fields(field).tag(tag)
                    .time(dateTime.getTime(), TimeUnit.MILLISECONDS).build();
            influxDB.write(point);
            //influxDB.flush();
            System.out.println(count++);
            //FileUtil.writeString(code + "," + tradeDay + "," + tradeTime + "," + price + "," + preClose, "沪深.csv", "utf-8");

        }
        reader.close();
        influxDB.close();
    }
}
