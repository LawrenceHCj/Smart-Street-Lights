package com.iot.simulator;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Generates a sensors.dat file that can be loaded directly by the Hongmeng sensor simulator. */
public final class BulkSensorDataGenerator {
    private static final String[] COLORS = {
            "#42A5F5", "#26C6DA", "#26A69A", "#66BB6A", "#9CCC65", "#FFCA28",
            "#FFA726", "#FF7043", "#EC407A", "#AB47BC", "#7E57C2", "#5C6BC0"
    };

    private record Site(String location, double longitude, double latitude) {}

    private static final List<Site> SITES = List.of(
            new Site("虎溪图书馆东门", 106.29812, 29.59498),
            new Site("第一教学楼南侧", 106.29780, 29.59818),
            new Site("第二教学楼北侧", 106.29678, 29.59782),
            new Site("第四综合教学楼", 106.29716, 29.59546),
            new Site("理科楼西侧", 106.29668, 29.59618),
            new Site("艺术楼广场", 106.29953, 29.59576),
            new Site("学生活动中心", 106.29501, 29.59311),
            new Site("一食堂北门", 106.29432, 29.60030),
            new Site("松园食堂", 106.29084, 29.59865),
            new Site("竹园食堂", 106.29204, 29.59908),
            new Site("兰园食堂", 106.29590, 29.59287),
            new Site("梅园宿舍区", 106.29659, 29.60015),
            new Site("松园宿舍区", 106.29030, 29.59970),
            new Site("竹园宿舍区", 106.29259, 29.60034),
            new Site("兰园宿舍区", 106.29573, 29.59184),
            new Site("荷园宿舍区", 106.29300, 29.59576),
            new Site("云湖东岸", 106.29912, 29.59649),
            new Site("云湖西岸", 106.29820, 29.59649),
            new Site("缙湖步道", 106.29504, 29.59578),
            new Site("风雨操场东门", 106.29972, 29.59902),
            new Site("体育馆南门", 106.29942, 29.59858),
            new Site("校医院门前", 106.29596, 29.60090),
            new Site("西一门", 106.28843, 29.59654),
            new Site("南门校车站", 106.29491, 29.58669)
    );

    private BulkSensorDataGenerator() {}

    public static void main(String[] args) throws Exception {
        Path datPath = args.length > 0 ? Path.of(args[0]) : Path.of("sensors.dat");
        Path jsonPath = args.length > 1 ? Path.of(args[1]) : Path.of("huxi-sensors.json");
        List<Sensor> sensors = buildSensors();
        writeDat(datPath, sensors);
        writeManifest(jsonPath, sensors);
        System.out.printf("Generated %d simulated lamps. HW-001 remains reserved for real hardware.%n", sensors.size());
    }

    private static List<Sensor> buildSensors() {
        List<Sensor> sensors = new ArrayList<>();
        for (int i = 0; i < SITES.size(); i++) {
            Site site = toGcj02(SITES.get(i));
            String code = String.format("SIM-HUXI-%03d", i + 1);
            double lux = 35 + (i % 8) * 18;
            double temperature = 25.0 + (i % 6) * 0.7;
            double humidity = 48 + (i % 7) * 3;
            double voltage = 218.5 + (i % 5) * 0.8;
            double current = 0.36 + (i % 6) * 0.04;
            double power = voltage * current * 0.92;
            double energy = 80 + i * 9.75;

            Sensor sensor = new Sensor();
            sensor.id = code;
            sensor.name = code + " " + site.location();
            sensor.topic = "device/${deviceId}/data";
            sensor.subscribeTopic = "device/${deviceId}/cmd";
            sensor.intervalMs = 5000L + (i % 4) * 1000L;
            sensor.enabled = false;
            sensor.color = COLORS[i % COLORS.length];
            sensor.payload = String.format(
                    "{\"deviceId\":\"${deviceId}\",\"name\":\"%s\",\"location\":\"%s\","
                            + "\"longitude\":\"%.5f\",\"latitude\":\"%.5f\","
                            + "\"lux\":%.2f,\"temperature\":%.2f,\"humidity\":%.2f,"
                            + "\"voltage\":%.2f,\"current\":%.2f,\"power\":%.2f,"
                            + "\"energy\":%.2f,\"lampStatus\":\"${status}\",\"ts\":${timestamp}}",
                    sensor.name, site.location(), site.longitude(), site.latitude(),
                    lux, temperature, humidity, voltage, current, power, energy);
            sensor.parseBaseValues();
            sensors.add(sensor);
        }
        return sensors;
    }

    private static void writeDat(Path path, List<Sensor> sensors) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
            output.writeObject(new ArrayList<>(sensors));
        }
    }

    private static void writeManifest(Path path, List<Sensor> sensors) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("{\n  \"reservedRealDeviceId\": \"HW-001\",\n  \"simulatedDevices\": [\n");
            for (int i = 0; i < sensors.size(); i++) {
                Sensor s = sensors.get(i);
                Site site = toGcj02(SITES.get(i));
                writer.write(String.format(
                        "    {\"deviceId\":\"%s\",\"name\":\"%s\",\"location\":\"%s\","
                                + "\"longitude\":%.5f,\"latitude\":%.5f,\"topic\":\"%s\","
                                + "\"controlTopic\":\"%s\",\"intervalMs\":%d,\"payload\":%s}%s\n",
                        s.id, s.name, site.location(), site.longitude(), site.latitude(),
                        s.topic.replace("${deviceId}", s.id),
                        s.subscribeTopic.replace("${deviceId}", s.id), s.intervalMs,
                        quoteJson(s.payload), i + 1 == sensors.size() ? "" : ","));
            }
            writer.write("  ]\n}\n");
        }
    }

    private static String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Converts OpenStreetMap WGS-84 coordinates to the GCJ-02 system used by AMap. */
    private static Site toGcj02(Site site) {
        double longitude = site.longitude();
        double latitude = site.latitude();
        double dLat = transformLatitude(longitude - 105.0, latitude - 35.0);
        double dLng = transformLongitude(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0
                / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI);
        dLng = dLng * 180.0 / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new Site(site.location(), longitude + dLng, latitude + dLat);
    }

    private static double transformLatitude(double x, double y) {
        double value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        value += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return value;
    }

    private static double transformLongitude(double x, double y) {
        double value = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        value += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return value;
    }
}
