# RaspiSensorInterfaces
A collection of java classes that access several different raspberry pi sensors 
on the GPIO interface:

* DHT11 and DHT22 temperature and humidity sensors
* DS18B20 and similar 1-Wire temperature sensors
* HcSr04 ultrasonic distance sensors

Classes use mostly pi4j for gpio access. Also [javolution-core](http://javolution.org/)
is used for realtime reading of DHTxx sensor signals.
![libraries](project-libraries.png "needed libraries")
