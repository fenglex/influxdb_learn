基本概念
--


| Infulxdb概念 | 传统数据库概念 |
| ---- | ---- |
|database|数据库|
|measurement|表|
|points|表里的一行数据|

> Point由时间戳（time）、数据（field）、标签（tags）组成。

|points属性|传统数据库中的概念|
|----|----|
|time|每个数据记录时间，是数据库中的主索引(会自动生成)|
|fields|各种记录值（没有索引的属性）也就是记录的值：温度， 湿度|
|tags|各种有索引的属性：地区，海拔|

> series(序列)  

TSDB For InfluxDB®数据结构中，有相同measurement、tag set和保留策略（retention policy）的数据集合。

>常用操作    

创建数据库  
CREATE DATABASE test  
创建存储策略  
CREATE RETENTION POLICY "rp_w" ON "test" DURATION 1d REPLICATION 1 DEFAULT  
插入数据  
单条写入  
```
DateTime dateTime = DateUtil.parseDate("2020-08-11");
Date date = dateTime.toJdkDate();
for (int i = 0; i < 3; i++) {
    DateTime time = DateUtil.offsetDay(date, i);
    int day = Integer.parseInt(DateUtil.format(time.toJdkDate(), "yyyyMMdd"));
    String code = "600519.SH";
    Map<String, Object> field = new HashMap<String, Object>();
    field.put("value", i);
    Map<String, String> tag = new HashMap<String, String>();
    tag.put("trade_day", String.valueOf(day));
    tag.put("stock_code", code);
    Point point = Point.measurement("tb_stock").fields(field).tag(tag).time(time.getTime(), TimeUnit.MILLISECONDS).build();
    influxDB.write("test", "rp_1d", point);
}
# 如果开启批量插入，需要执行flush
influxDB.flush();
```
单次批量写入
```
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
```
删除数据
```
Query query = new Query("DELETE FROM tb_stock WHERE stock_code='600520.SH'", "test");
QueryResult result = influxDB.query(query);
```
>数据查询

分组取TopN
```
// 默认是升序，需要调整顺序
SELECT stock_code,trade_day,value FROM tb_stock GROUP BY stock_code order by time desc limit 2
```
获取执行个股的最近一个交易日数据
```
# 暂未找到获取最大日期的方式，无法使用子查询,查询结果后进行过滤
SELECT * FROM tb_stock WHERE stock_code='600519' order by time desc limit 240;
```
多个个股查询
```
SELECT * FROM tb_stock WHERE stock_code='600519.SH' or stock_code='600520.SH' order by time desc limit 3
```

```
#创建数据库
create database "db_name"
 
#显示所有的数据库
show databases
 
#删除数据库
drop database "db_name"
 
#使用数据库
use db_name
 
#显示该数据库中所有的表
show measurements
 
#创建表，直接在插入数据的时候指定表名
insert test,host=127.0.0.1,monitor_name=test count=1
 
#删除表
drop measurement "measurement_name"

#查看当前数据库Retention Policies
show retention policies on "db_name"


#创建新的Retention Policies
create retention policy "rp_name" on "db_name" duration 3w replication 1 default

    rp_name：策略名
    db_name：具体的数据库名
    3w：保存3周，3周之前的数据将被删除，influxdb具有各种事件参数，比如：h（小时），d（天），w（星期）
    replication 1：副本个数，一般为1就可以了
    default：设置为默认策略
#修改Retention Policies
alter retention policy "rp_name" on "db_name" duration 30d default
#删除Retention Policies
drop retention policy "rp_name"
```
>连续查询（Continous Queries）  

influxdb提供了连续查询，可以做数据统计采样。
```
# 查看数据库的Continous Queries
show continuous queries

创建
create continous query cq_name on db_name begin select sum(count) into new_table_name from table_name group by time(30m) end
    cq_name：连续查询名字
    db_name：数据库名字
    sum(count)：计算总和
    table_name：当前表名
    new_table_name：存新的数据的表名
    30m：时间间隔为30分钟
删除
drop continous query cp_name on db_name
```



> 重复数据点 

 measurement的名字、tag set和时间戳唯一标识一个数据点。如果您提交的数据具有相同measurement、tag set和时间戳但具有不同field set，那么数据的field set会变为旧field set和新field set的并集，如果有任何冲突以新field set为准。


>LIMIT及SLIMIT子句

LIMIT和SLIMIT分别限制每个查询返回的数据点个数和序列个数。  
例如：
SELECT * FROM tb_stock GROUP BY stock_code LIMIT 4 SLIMIT 3;  
limit用于限制每个 stock_code返回结果的个数，SLIMIT限制返回3个stock_code的数据
可参考：https://help.aliyun.com/document_detail/172187.html?spm=a2c4g.11186623.6.741.116113ed8cMhqS

>OFFSET及SOFFSET子句

OFFSET和SOFFSET分别标记数据点和序列返回的位置。
```
SELECT "water_level","location" FROM "h2o_feet" LIMIT 3 OFFSET 3
该查询从measurement h2o_feet中返回第四、第五和第六个数据点。如果以上查询语句中没有使用OFFSET 3，那么查询将返回该measurement的第一、第二和第三个数据点。

SELECT MEAN("water_level") FROM "h2o_feet" WHERE time >= '2015-08-18T00:00:00Z' AND time <= '2015-08-18T00:42:00Z' GROUP BY *,time(12m) ORDER BY time DESC LIMIT 2 OFFSET 2 SLIMIT 1
    SELECT子句指定了一个InfluxQL函数；
    FROM子句指定了measurement；
    WHERE子句指定了查询的时间范围；
    GROUP BY子句将查询结果按所有tag（*）和12分钟的时间间隔进行分组；
    ORDER BY time DESC子句按递减的时间顺序返回结果；
    LIMIT 2子句将返回的数据点个数限制为2；
    OFFSET 2子句使查询结果的前两个平均值不返回；
    SLIMIT 1子句将返回的序列个数限制为1。


SOFFSET <N>表示从查询结果中的第N个序列开始返回。
SELECT "water_level" FROM "h2o_feet" GROUP BY * SLIMIT 1 SOFFSET 1
该查询返回measurement为h2o_feet、tag为location = santa_monica的序列中的数据。如果以上查询语句中没有使用SOFFSET 1，那么查询将返回measurement为h2o_feet、tag为location = coyote_creek的序列中的数据。

SELECT MEAN("water_level") FROM "h2o_feet" WHERE time >= '2015-08-18T00:00:00Z' AND time <= '2015-08-18T00:42:00Z' GROUP BY *,time(12m) ORDER BY time DESC LIMIT 2 OFFSET 2 SLIMIT 1 SOFFSET 1
    
    SELECT子句指定了一个InfluxQL函数；
    FROM子句指定了measurement；
    WHERE子句指定了查询的时间范围；
    GROUP BY子句将查询结果按所有tag(*)和12分钟的时间间隔进行分组；
    ORDER BY time DESC子句按递减的时间顺序返回结果；
    LIMIT 2子句将返回的数据点个数限制为2；
    OFFSET 2子句使查询结果的前两个平均值不返回；
    SLIMIT 1子句将返回的序列个数限制为1；
    SOFFSET 1子句使查询结果中第一个序列的数据不返回。


```



参考：
```
https://help.aliyun.com/document_detail/113093.html?spm=a2c4g.11186623.6.706.7e0e37e2Cij5zc
```

