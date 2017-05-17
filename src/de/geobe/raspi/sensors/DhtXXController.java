/*
 * The MIT License
 *
 * Copyright 2016 Georg Beier <me@georg.beier.de>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.geobe.raspi.sensors;

import com.pi4j.wiringpi.Gpio;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javolution.context.ConcurrentContext;

/**
 * Java controller for DHT11 and DHT22 (= AM2302) humidity and temperature
 * sensors. Sensors differ im precision, range and data encoding. See resp. data
 * sheets
 *
 * @author Georg Beier <me@georg.beier.de>
 */
public class DhtXXController {

    public static final boolean DHT11 = false;
    public static final boolean DHT22 = !DHT11;
    public static final int PIN = 3;

    private volatile long startRead;
    private volatile long tnow;
    private volatile int[] tlow = new int[N_BITS];
    private volatile int[] thigh = new int[N_BITS];
    private volatile boolean stop = false;

    private Map<String, Float> result = new HashMap<>(2);
//    private int[] tlow = new int[N_BITS];
//    private int[] thigh = new int[N_BITS];
    private boolean isDht22;

    private static final int N_BITS = 40;
    private static final int T_BE = 28;   //18
    private int dht = 0;
    private final int trigger = 2;

    public int getDht() {
        return dht;
    }

    public int getTrigger() {
        return trigger;
    }

    public DhtXXController() {
        this(false);
    }

    public DhtXXController(int pin) {
        this.dht = pin;
    }
    public DhtXXController(boolean isDht22) {
        this.isDht22 = isDht22;
    }

    public DhtXXController(int pin, boolean isDht22) {
        this.isDht22 = isDht22;
        this.dht = pin;
    }

    public void stop() {
        stop = true;
    }

    public boolean isRunning() {
        return !stop;
    }

    private long lastRead = 0;

    public boolean readDht() {
        return readDht(false);
    }

    public boolean readDht(boolean t) {

        Gpio.pinMode(trigger, Gpio.OUTPUT);
        Gpio.pullUpDnControl(trigger, Gpio.PUD_DOWN);

        long loops = 0, tprev = 0;
        boolean dataOk = true;
        stop = false;

        for (int i = 0; i < N_BITS; i++) {
            thigh[i] = 0;
            tlow[i] = 0;
        }

        // initialize dht from raspi
        Gpio.pullUpDnControl(dht, Gpio.PUD_UP);
        Gpio.pinMode(dht, Gpio.OUTPUT);

        sync:
        for (int i = 0; i < 1; i++) {
//            retries = i + 1;
            Gpio.digitalWrite(dht, false);
            Gpio.delay(T_BE);

            // trigger 1
            if (t) {
                Gpio.digitalWrite(trigger, true);
                Gpio.digitalWrite(trigger, false);
            }
            // now listen to dht
            startRead = Gpio.micros();
            Gpio.pinMode(dht, Gpio.INPUT);
            Gpio.pullUpDnControl(dht, Gpio.PUD_UP);
            //  wait for dht ready
            waitReady:
            for (loops = 1; loops < 5001; loops++) {
                boolean isNull = Gpio.digitalRead(dht) == 0;
                long now = Gpio.micros();
                if (isNull) {
//                found = now;

                    // trigger 2
                    if (t) {
                        Gpio.digitalWrite(trigger, true);
                        Gpio.digitalWrite(trigger, false);
                    }
                    break sync;
                } else if (!isNull && (now - startRead) < 50000) {
                    Gpio.delayMicroseconds(1);
                } else {
                    System.out.println("start not found");
                    return false;
//                dataOk = false;
//                break;
                }
            }
            Gpio.pinMode(dht, Gpio.OUTPUT);
        }
        while (Gpio.digitalRead(dht) == 0) ;// while in ResponseLow
        // trigger 3
        if (t) {
            Gpio.digitalWrite(trigger, true);
            Gpio.digitalWrite(trigger, false);
        }
        startRead = tnow = Gpio.micros();
        tprev = tnow;
        while (Gpio.digitalRead(dht) == 1) ;// while in ResponseHigh
        // trigger 4
        if (t) {
            Gpio.digitalWrite(trigger, true);
        }
        tnow = Gpio.micros();
        if (t) {
            Gpio.digitalWrite(trigger, false);
        }
        tprev = tnow;
        for (int in = 0; in < N_BITS; in++) {
            while (Gpio.digitalRead(dht) == 0) {// while in TLOW
                if (stop) {
                    dataOk = false;
                    break;
                }
            }
            if (t) {
                Gpio.digitalWrite(trigger, true);
                Gpio.digitalWrite(trigger, false);
            }
            tnow = Gpio.micros();
            if (t) {
                Gpio.digitalWrite(trigger, true);
                Gpio.digitalWrite(trigger, false);
            }
            tlow[in] = (int) (tnow - tprev);
            if (tlow[in] > 85 || tlow[in] < 20) {
                dataOk = false;
                System.out.println("thigh=" + (in > 0 ? thigh[in - 1] : "") + ", tlow=" + tlow[in]);
                break;
            }
            tprev = tnow;
            while (Gpio.digitalRead(dht) == 1) {// while in TH
                if (stop) {
                    dataOk = false;
                    break;
                }
            }
            if (t) {
                Gpio.digitalWrite(trigger, true);
                Gpio.digitalWrite(trigger, false);
            }
            tnow = Gpio.micros();
            if (t) {
                Gpio.digitalWrite(trigger, true);
                Gpio.digitalWrite(trigger, false);
            }
            thigh[in] = (int) (tnow - tprev);
            if (thigh[in] > 140) {
                dataOk = false;
                System.out.println("tlow=" + tlow[in] + ", thigh=" + thigh[in]);
                break;
            }
            tprev = tnow;
        }
        stop = true;
        if (dataOk) {
            long rawValues = analyze(tlow, thigh);
            parse(rawValues);
        }
        return dataOk;
    }

    private long analyze(int[] tlow, int[] thigh) {
        long bits = 0;
        for (int in = 0; in < N_BITS; in++) {
            long tsum = tlow[in] + thigh[in];
            bits <<= 1;
            bits += tsum < 100 ? 0 : 1;
//            System.out.println("tlow-" + tlow[in]);
//            System.out.println("thigh-" + thigh[in]);
        }
        return bits;
    }

    private Map<String, Float> parse(long rawValues) {
//        Map<String, Float> result = new HashMap<>(2);
//        System.out.format("raw value: %x\n", rawValues);
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.putLong(rawValues);
        byte[] bytes = byteBuffer.array();
        int[] values = new int[5];
        for (int i = 3; i < bytes.length; i++) {
//            System.out.format("byte[%d]: %x\n", i, bytes[i]);
            values[i - 3] = bytes[i] & 0xff;
        }
        int checksum = values[0] + values[1] + values[2] + values[3];
        int crc = values[4];
        float humidity = 0, temperature = 0;
        if (crc == checksum) {
            if (isDht22) {
                humidity = (values[0] * 256 + values[1]) / 10.f;
                temperature = ((values[2] & 0x7f) * 256 + values[3]) / 10.f;
                if ((values[2] & 0x80) != 0) {
                    temperature = -temperature;
                }
            } else {
                humidity = (values[0] * 10 + values[1]) / 10.f;
                temperature = (values[2] * 10 + values[3]) / 10.f;
            }
            result.put("temperature", temperature);
            result.put("humidity", humidity);
        }
        return result;
    }

    public static void main(String[] args) {
        // setup wiringPi
        if (Gpio.wiringPiSetup()
                == -1) {
            System.out.println(" ==>> GPIO SETUP FAILED");
            return;
        }

        // choose the pin where controller is wired
        DhtXXController controller = new DhtXXController(PIN, DHT11);
        ExecutorService executor = Executors.newFixedThreadPool(1);

        Gpio.pinMode(controller.getDht(), Gpio.INPUT);
        Gpio.pullUpDnControl(controller.getDht(), Gpio.PUD_UP);
        Gpio.delay(2000);
        ConcurrentContext ctx = ConcurrentContext.enter();

        for (int i = 0; i < 15; i++) {
            final int ix = i;
            ctx.execute(() -> {
//            executor.submit(() -> {
//            Thread t = new Thread(() -> {
                if (controller.readDht(true)) {
                    for (String key : controller.result.keySet()) {
                        System.out.format("%s %.1f\n", key, controller.result.get(key));
                    }
                } else {
                    System.out.println("Read error @ " + ix);
                }
            });
//            t.start();
            if (i == 4) {
                Gpio.delay(10000); //wait 1 sec
                System.out.println("pause");
            } else {
                Gpio.delay(1000); //wait 1 sec
            }
            if (controller.isRunning()) {
                controller.stop();
                System.out.println("thread stop()\"");
            }
        }
        ctx.exit();
//        executor.shutdown();
//        Gpio.pinMode(controller.getDht(), Gpio.OUTPUT);
//        Gpio.digitalWrite(controller.getDht(), true);
    }

}
