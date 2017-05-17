package de.geobe.raspi.sensors;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author georg beier
 *
 * class to read raspberry pi 1-wire sensors
 */
public class OneWireScanner {

    /**
     * location of device reading files
     */
    private static final String ONE_WIRE_DIR = "/sys/bus/w1/devices";
    private static final String SENSOR_FILE = "w1_slave";

    /**
     * a list of subdirectories == devices
     */
    private List<File> deviceDirs = new ArrayList<>();
    private File basedir;
    /**
     * filter sensor directories
     */
    private FilenameFilter filter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals(SENSOR_FILE);
        }
    };

    /**
     * find all 1-wire sensors by looking for their directories
     */
    public void lookForDevices() {
        basedir = new File(ONE_WIRE_DIR);
        deviceDirs.clear();
        if (basedir.isDirectory()) {
            File[] devdirs = basedir.listFiles();
            for (File devdir : devdirs) {
                if (devdir.isDirectory() && devdir.getName().matches("[0-9].*")) {
                    deviceDirs.add(devdir);
                }
            }
        }
    }

    public List<File> getDeviceDirs() {
        return deviceDirs;
    }

    /**
     * read all 1-wire sensor values from their device files
     *
     * @return a map of device readings
     */
    public Map<String, DeviceReading> readSensors() {
        Map<String, DeviceReading> values = new TreeMap<>();
        for (File deviceDir : deviceDirs) {
            String sensor = deviceDir.getName();
            File[] devFiles = deviceDir.listFiles(filter);
            for (File devFile : devFiles) {
                List<String> lines = readSensorFile(devFile);
                DeviceReading reading = parseReading(lines, sensor);
                values.put(reading.id, reading);
            }
        }
        return values;
    }

    /**
     * read all lines from a single 1-wire sensor file
     *
     * @param devFile path to device file
     * @return file content a list of strings
     */
    private List<String> readSensorFile(File devFile) {
        List<String> readings = null;
        try {
            Path path = Paths.get(devFile.getCanonicalPath());
            readings = Files.readAllLines(path);
        } catch (IOException ex) {
            Logger.getLogger(OneWireScanner.class.getName()).log(Level.SEVERE, null, ex);
        }
        return readings;
    }

    /**
     * parse the lines read for a 1-wire sensor. there should be exactly 2 lines
     *
     * @param lines as read from device file
     * @param sensor sensor name
     * @return sensor data ready for further processing
     */
    private DeviceReading parseReading(List<String> lines, String sensor) {
        sensor = sensor.replaceFirst("\\d*-0*", "");
        if (lines.size() != 2) {
            return new DeviceReading();
        }
        boolean ok = lines.get(0).endsWith("YES");
        float value = 0;
        long time = System.currentTimeMillis();
        if (ok) {
            value = Float.parseFloat(lines.get(1).split("=")[1]);
            value /= 100.f;
            value = Math.round(value) / 10.f;
        }
        return new DeviceReading(sensor, value, time, ok);
    }

    /**
     * demonstration of the temperature sensor interface
     *
     * @param args ignored
     */
    public static void main(String[] args) throws InterruptedException {
        // we need a scanner
        OneWireScanner oneWireScanner = new OneWireScanner();
        // first look for devices
        oneWireScanner.lookForDevices();
        System.out.println("found " + oneWireScanner.getDeviceDirs().size()
                + " devices");
        // now lets read
        for (int i = 0; i < 5; i++) {
            System.out.println("start reading 1-wire sensors");
            Map<String, DeviceReading> sensors = oneWireScanner.readSensors();
            for (String sensor : sensors.keySet()) {
                DeviceReading reading = sensors.get(sensor);
                System.out.println("reading sensor " + reading);
                // reading took time, now wait a little
                Thread.sleep(2000);
            }
        }
    }

    /**
     * Helper class takes reading of a single 1-wire device.
     * In addition to the pure sensor readings, also the time of reading and
     * an identifying id is stored
     */
    public static class DeviceReading {

        /** sensor id string */
        public String id = "";
        /** time of reading */
        public long time = 0;
        /** temperature value in °C */
        public float value = 0;
        /** was reading successful? */
        public boolean status = false;

        public DeviceReading() {

        }

        public DeviceReading(String id, float value, long time, boolean status) {
            this.id = id;
            this.time = time;
            this.value = value;
            this.status = status;
        }

        @Override
        public String toString() {
            return id + ": " + value + (status ? " OK" : " FAIL");
        }

    }
}
