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
import java.util.Formatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A controller for one or more HC-SR04 ultrasonic distance sensors
 *
 * @author Georg Beier <me@georg.beier.de>
 */
public class HcSr04Controller {

    private static final int DEFAULT_SWEEPS = 4;
    private static final int DEFAULT_DISCARD = 2;
    private static final int DEFAULT_PAUSE = 1;
    private static final float SPEED_OF_SOUND = 0.343f;

    private int trigger, echo;
    private final ExecutorService executor;

    /**
     * initialize ultrasonic distance sensor in a scenario with more than one
     * sensor
     *
     * @param trigger GPIO output pin for 10 µs trigger signal
     * @param echo GPIO input pin to read distance signal
     * @param executor thread pool that can be cooperatively used by several
     * sensors
     */
    public HcSr04Controller(int trigger, int echo, ExecutorService executor) {
        this.executor = executor;
        initialize(trigger, echo);
    }

    /**
     * initialize ultrasonic distance sensor
     *
     * @param trigger GPIO output pin for 10 µs trigger signal
     * @param echo GPIO input pin to read distance signal
     */
    public HcSr04Controller(int trigger, int echo) {
        // reuse reading thread
        executor = Executors.newFixedThreadPool(1);
        initialize(trigger, echo);
    }

    private void initialize(int trigger1, int echo1) throws RuntimeException {
        this.trigger = trigger1;
        this.echo = echo1;
        // setup wiringPi
        if (Gpio.wiringPiSetup()
                == -1) {
            throw new RuntimeException(" ==>> GPIO SETUP FAILED");
        }
        // initialize pins
        Gpio.pinMode(trigger1, Gpio.OUTPUT);
        Gpio.pinMode(echo1, Gpio.INPUT);
    }

    /**
     * Repeatedly run distance measurement
     *
     * @param sweeps number of measurements
     * @param discard discard first few measurements , first two are usually not
     * correct
     * @param pause pause between measurements in milliseconds
     * @return Future object holding raw echo time in microseconds
     */
    public Future<Long> getEchoTime(final int sweeps, final int discard, final int pause) {
        Future<Long> pulseWidth = executor.submit(() -> {
            long result = 0;
            long start;
            for (int loop = 0; loop < sweeps + discard; loop++) {
                Gpio.digitalWrite(trigger, true);
                Gpio.delayMicroseconds(10);
                Gpio.digitalWrite(trigger, false);
                while (Gpio.digitalRead(echo) == 0) ; // wait for raising edge
                start = Gpio.micros();
                while (Gpio.digitalRead(echo) != 0) ; // wait for falling edge
                if (loop >= discard) {
                    result += Gpio.micros() - start;
                }
                Gpio.delay(pause);
            }
            result /= sweeps;
            return new Long(result);
        });
        return pulseWidth;
    }

    /**
     * Run distance measurement with default settings
     *
     * @return Future object holding raw echo time in microseconds
     */
    public Future<Long> getEchoTime() {
        return getEchoTime(DEFAULT_SWEEPS, DEFAULT_DISCARD, DEFAULT_PAUSE);
    }

    /**
     * convert echo runtime to millimeters
     *
     * @param echoTime measured time
     * @return distance in millimeters
     */
    public static float echoTimeToDistance(long echoTime) {
        return (echoTime / 2) * SPEED_OF_SOUND;
    }

    /**
     * demonstrates the reading of a HC-SR04 ultrasonic distance sensor
     *
     * @param args optional, may define alternative pin numbers for trigger and
     * echo
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        int trigger = 4, echo = 5;
        if (args.length > 0 && args.length != 2) {
            System.out.println("parameters: [int GPIO_trigger_pin, GPIO_echo_pin]");
            System.out.println("defaults are 4, 5");
            return;
        } else if (args.length == 2) {
            try {
                trigger = Integer.parseInt(args[0]);
                echo = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                System.out.println("parameters: [int GPIO_trigger_pin, GPIO_echo_pin]");
                System.out.println("defaults are 4, 5");
                return;
            }
        }
        HcSr04Controller controller = new HcSr04Controller(trigger, echo);
        Future<Long> echoTime;
        long time;
        float dist;
        while (true) {
            echoTime = controller.getEchoTime(5, 3, 125);
            time = echoTime.get();
            dist = HcSr04Controller.echoTimeToDistance(time);
            System.out.format("Runtime [usec]: %d, distance [mm] %.1f\n", time, dist);
        }
    }
}
