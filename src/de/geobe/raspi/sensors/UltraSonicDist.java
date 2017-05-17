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

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.wiringpi.Gpio;
import com.pi4j.wiringpi.GpioUtil;

/**
 * main program for running ultrasonic distance sensors
 * @author Georg Beier <me@georg.beier.de>
 */
public class UltraSonicDist {

    private static final int REPEAT = 10;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println("<--Pi4J--> GPIO test program");

        // setup wiringPi
        if (Gpio.wiringPiSetup()
                == -1) {
            System.out.println(" ==>> GPIO SETUP FAILED");
            return;
        }
        int pulse = 25;
        int echo = 27;
        int trigger = 6;
        float[] distance = new float[REPEAT];
        long start, delay[] = new long[REPEAT];

//        GpioUtil.export(pulse, GpioUtil.DIRECTION_OUT);
        Gpio.pinMode(pulse, Gpio.OUTPUT);
//        GpioUtil.export(trigger, GpioUtil.DIRECTION_OUT);

        Gpio.pinMode(trigger, Gpio.OUTPUT);
//        GpioUtil.export(echo, GpioUtil.DIRECTION_IN);

        Gpio.pinMode(echo, Gpio.INPUT);
//        GpioUtil.setEdgeDetection(echo, GpioUtil.EDGE_NONE);

        Gpio.pullUpDnControl(echo, Gpio.PUD_UP);

        Gpio.delay(500);

//        Gpio.digitalWrite(trigger, true);
//        Gpio.delayMicroseconds(25);
//        Gpio.digitalWrite(trigger, false);
        for (int i = 0; i < REPEAT; i++) {
            Gpio.digitalWrite(pulse, true);
            Gpio.delayMicroseconds(10);
            Gpio.digitalWrite(pulse, false);
            while(Gpio.digitalRead(echo) == 0) ; // warte auf steigende Flanke
            start = Gpio.micros();
            while(Gpio.digitalRead(echo) != 0) ; // warte auf fallende Flanke
            delay[i] = Gpio.micros() - start;
            distance[i] = (delay[i] / 2) * 0.343f;
            Gpio.delay(10);
        }
        for (int i = 0; i < REPEAT; i++) {
            System.out.println(" delta t = " + delay[i] + " microsec, Abstand = " + distance[i]);
        }

    }
}
