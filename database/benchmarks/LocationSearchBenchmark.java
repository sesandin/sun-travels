package com.suntravels.benchmarks;

import java.sql.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Standalone JDBC benchmark. NEVER point this at an application database. */
public class LocationSearchBenchmark {
    private static final System.Logger LOG = System.getLogger(LocationSearchBenchmark.class.getName());
    private static final String BASELINE = "baseline";
    private static final String GENERATED = "generated";
    private static final String CHOSEN = "chosen";
    private static final String ANALYZE = "analyze table hotel,contract,contract_room_offering";
    private static final String AND = " and ";
    private static final String COLOMBO = "Colombo";
    static Connection db;
    static long thread;
    static final int SAMPLES = 25;
    static final String[] INDEXES = {"ix_hotel_status_name", "ix_hotel_status_location"};

    public static void main(String[] args) throws SQLException, IOException {
        Path output = Path.of(args.length == 0 ? "database/benchmarks/results" : args[0]);
        Files.createDirectories(output);
        db = DriverManager.getConnection(System.getenv("LOCATION_BENCH_URL"),
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        String catalog = db.getCatalog();
        if (!catalog.startsWith("suntravels_perf_") || !catalog.endsWith("_test"))
            throw new IllegalStateException("Requires a disposable suntravels_perf_*_test database");
        thread = Long.parseLong(rows("select thread_id from performance_schema.threads where processlist_id=connection_id()").get(0).get(0));
        Files.writeString(output.resolve("environment.txt"), "MySQL " + db.getMetaData().getDatabaseProductVersion()
                + "\nJDBC " + db.getMetaData().getDriverVersion() + "\nSamples: " + SAMPLES + ", warmups: 5\n");
        run("create temporary table bench_seq as select a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+1 n from "
                + "(select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) a "
                + "cross join (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) b "
                + "cross join (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) c "
                + "cross join (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) d "
                + "cross join (select 0 n union all select 1 union all select 2 union all select 3 union all select 4 union all select 5 union all select 6 union all select 7 union all select 8 union all select 9) e");
        StringBuilder csv = new StringBuilder("hotels,variant,query,samples,p50_ms,p95_ms,rows_examined,tmp_tables,sort_rows,result_json_bytes\n");
        for (int size : new int[]{500,10000,50000}) {
            dropBenchmarkIndexes();
            seed(size);
            Map<String,String> baselineResults = new HashMap<>();
            String[] variants = args.length > 1 && args[1].equals("options")
                    ? new String[]{BASELINE,"indexes_only","optimized",GENERATED}
                    : new String[]{BASELINE,CHOSEN};
            for (String variant : variants) {
                prepareVariant(variant);
                StringBuilder plans = new StringBuilder();
                for (var entry : queries(usesOptimizedQueries(variant)).entrySet()) {
                    measureQuery(size, variant, entry.getKey(), entry.getValue(), baselineResults, csv, plans);
                }
                Files.writeString(output.resolve(size + "-" + variant + "-plans.txt"),plans);
                Files.writeString(output.resolve("latency.csv"),csv);
                LOG.log(System.Logger.Level.INFO, size + " hotels: " + variant + " complete (all result payloads equal)");
            }
        }
        db.close();
    }

    private static boolean usesOptimizedQueries(String variant) {
        return variant.equals("optimized") || variant.equals(GENERATED) || variant.equals(CHOSEN);
    }

    private static void dropBenchmarkIndexes() throws SQLException {
        for (String index : INDEXES) {
            if (!rows("show index from hotel where Key_name='" + index + "'").isEmpty()) {
                run("drop index " + index + " on hotel");
            }
        }
    }

    private static void prepareVariant(String variant) throws SQLException {
        if (variant.equals("indexes_only")) {
            run("create index ix_hotel_status_name on hotel(status,name,hotel_id)");
            run("create index ix_hotel_status_location on hotel(status, (trim(city)), (nullif(trim(country),'')))");
            run(ANALYZE);
        }
        if (variant.equals(GENERATED) || variant.equals(CHOSEN)) {
            if(!rows("show index from hotel where Key_name='ix_hotel_status_location'").isEmpty())
                run("drop index ix_hotel_status_location on hotel");
            if(rows("show index from hotel where Key_name='ix_hotel_status_name'").isEmpty())
                run("create index ix_hotel_status_name on hotel(status,name,hotel_id)");
            if(rows("show columns from hotel like 'search_city'").isEmpty())
                run("alter table hotel add column search_city varchar(100) character set utf8mb3 collate utf8mb3_general_ci generated always as (trim(city)) virtual, add column search_country varchar(100) character set utf8mb3 collate utf8mb3_general_ci generated always as (nullif(trim(country),'')) virtual");
            run("create index ix_hotel_status_location on hotel(status,search_city,search_country)");
            run(ANALYZE);
        }
    }

    private static void measureQuery(int size, String variant, String label, String sql,
            Map<String,String> baselineResults, StringBuilder csv, StringBuilder plans) throws SQLException {
        if(variant.equals(GENERATED) || variant.equals(CHOSEN)) sql=sql.replace("nullif(trim(h.country),'')","h.search_country")
                .replace("trim(h.city)","h.search_city").replace("nullif(trim(country),'')","search_country").replace("trim(city)","search_city");
        if(variant.equals(CHOSEN) && label.startsWith("candidate"))
            sql=sql.replace("and exists (select 1", "and (select 1")
                    .replace(") order by h.hotel_id", " limit 1) is not null order by h.hotel_id");
        String result = json(sql);
        if (variant.equals(BASELINE)) baselineResults.put(label,result);
        else if (!result.equals(baselineResults.get(label))) throw new AssertionError("Changed results: " + size + "/" + variant + "/" + label);
        for(int i=0;i<5;i++) {
            json(sql);
        }
        double[] times = new double[SAMPLES];
        List<String> metrics = List.of();
        for(int i=0;i<SAMPLES;i++) {
            long start = System.nanoTime();
            json("/*location-bench*/ " + sql);
            times[i]=(System.nanoTime()-start)/1e6;
            metrics=rows("select rows_examined,created_tmp_tables,sort_rows from performance_schema.events_statements_history where thread_id=" + thread + " and sql_text like '/*location-bench*/%' order by event_id desc limit 1").get(0);
        }
        Arrays.sort(times);
        csv.append(String.format(Locale.ROOT,"%d,%s,%s,%d,%.3f,%.3f,%s,%s,%s,%d%n",size,variant,label,SAMPLES,times[12],times[23],metrics.get(0),metrics.get(1),metrics.get(2),result.getBytes(StandardCharsets.UTF_8).length));
        plans.append("\n=== ").append(label).append(" ===\n").append(sql).append("\n");
        for(var row:rows("explain " + sql)) {
            plans.append(row).append('\n');
        }
        for(var row:rows("explain analyze " + sql)) {
            plans.append(row.get(0)).append('\n');
        }
    }

    static void seed(int size) throws SQLException {
        run("delete from contract_room_offering"); run("delete from contract"); run("delete from hotel"); run("delete from room_type");
        run("insert into room_type(room_type_id,name) values (1,'Double'),(2,'Single')");
        run("insert into hotel(hotel_id,name,city,country,status) select n,concat(if(mod(n,1000)=999,'Zenith ','Hotel Common '),lpad(n,6,'0')),"
                + "concat(if(mod(n,17)=0,' ',''),if(mod(n,1000)=999,'Zeta Bay',if(mod(n,10)<6,'Colombo',concat('City ',lpad(mod(n,1000),4,'0')))),if(mod(n,17)=0,' ','')),"
                + "concat(if(mod(n,19)=0,' ',''),if(mod(n,7)=0,'United States','Sri Lanka'),if(mod(n,19)=0,' ','')),if(mod(n,5)=0,'INACTIVE','ACTIVE') from bench_seq where n<=" + size);
        run("insert into contract(contract_id,contract_code,valid_from,valid_to,markup_percentage,currency_code,status,hotel_id) select n,concat('B',n),'2026-01-01','2030-01-01',15,'USD',if(mod(n,7)=0,'DRAFT','APPROVED'),n from bench_seq where n<=" + size);
        run("insert into contract_room_offering(rate_per_person_per_night,contracted_room_count,max_adults,status,contract_id,room_type_id) select 100,10,2,'ENABLED',n,1 from bench_seq where n<=" + size);
        run("insert into contract_room_offering(rate_per_person_per_night,contracted_room_count,max_adults,status,contract_id,room_type_id) select 50,5,1,if(mod(n,11)=0,'SUSPENDED','ENABLED'),n,2 from bench_seq where n<=" + size);
        run(ANALYZE);
    }

    static Map<String,String> queries(boolean optimized) {
        Map<String,String> q=new LinkedHashMap<>();
        for(String[] c:new String[][]{{"city_common","co%","Sri Lanka"},{"city_rare","ze%",null},{"city_miss","zz%",null},{"city_country","ci%","United States"}}) {
            String sql="select distinct trim(city) as city,nullif(trim(country),'') as country from hotel where status='ACTIVE' and "
                    +(optimized?"trim(city)":"lower(trim(city))")+" like '"+c[1]+"' escape '!'";
            if(c[2]!=null) sql+=AND+(optimized?"nullif(trim(country),'')='":"lower(trim(country))=lower('")+c[2]+(optimized?"'":"')");
            q.put(c[0],sql+" order by city,country limit 15");
        }
        addHotelQueries(q, optimized);
        addCandidateQueries(q, optimized);
        return q;
    }

    private static void addHotelQueries(Map<String,String> q, boolean optimized) {
        for(String[] h:new String[][]{{"hotel_common","Hotel%",null},{"hotel_rare","Zenith%",null},{"hotel_miss","NoSuchHotel%",null},{"hotel_location","Hotel%",COLOMBO}}) {
            String sql="select hotel_id,name,trim(city) as city,nullif(trim(country),'') as country from hotel where status='ACTIVE' and name like '"+h[1]+"' escape '!'";
            if(h[2]!=null) sql+=AND+(optimized?"trim(city)='Colombo' and nullif(trim(country),'')='Sri Lanka'":"lower(trim(city))=lower('Colombo') and lower(trim(country))=lower('Sri Lanka')");
            q.put(h[0],sql+" order by name,hotel_id limit 20");
        }
    }

    private static void addCandidateQueries(Map<String,String> q, boolean optimized) {
        for(String[] p:new String[][]{{"candidate_common",COLOMBO,"0"},{"candidate_rare","Zeta Bay","0"},{"candidate_next",COLOMBO,"250"},{"candidate_deep",COLOMBO,"25000"}}) {
            String hotelFilter="h.status='ACTIVE' and h.hotel_id>"+p[2]+AND+(optimized?"trim(h.city)='"+p[1]+"' and nullif(trim(h.country),'')='Sri Lanka'":"lower(trim(h.city))=lower('"+p[1]+"') and lower(trim(h.country))=lower('Sri Lanka')");
            String eligibility="c.status='APPROVED' and o.status='ENABLED' and c.valid_from<='2026-11-01' and c.valid_to>='2026-11-03' and o.contracted_room_count>0";
            q.put(p[0],optimized?"select h.hotel_id from hotel h where "+hotelFilter+" and exists (select 1 from contract c join contract_room_offering o on o.contract_id=c.contract_id where c.hotel_id=h.hotel_id and "+eligibility+") order by h.hotel_id limit 21":
                    "select distinct h.hotel_id from hotel h join contract c on c.hotel_id=h.hotel_id join contract_room_offering o on o.contract_id=c.contract_id where "+hotelFilter+AND+eligibility+" order by h.hotel_id limit 21");
        }
    }

    private static String jsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static void run(String sql) throws SQLException { try(var s=db.createStatement()){ s.execute(sql); } }
    static List<List<String>> rows(String sql) throws SQLException {
        List<List<String>> rows=new ArrayList<>();
        try(var s=db.createStatement();var r=s.executeQuery(sql)){ while(r.next()){
            List<String> row=new ArrayList<>();
            for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                row.add(r.getString(i));
            }
            rows.add(row);
        } }
        return rows;
    }
    static String json(String sql) throws SQLException {
        StringJoiner result=new StringJoiner(",","[","]");
        try(var s=db.createStatement();var r=s.executeQuery(sql)){ while(r.next()) {
            StringJoiner row=new StringJoiner(",","{","}");
            for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                Object value=r.getObject(i);
                String key=r.getMetaData().getColumnLabel(i).replace("hotel_id","hotelId");
                row.add("\""+key+"\":"+jsonValue(value));
            }
            result.add(row.toString());
        } }
        return result.toString();
    }
}
