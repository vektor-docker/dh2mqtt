package me.vektory79.dh.mqtt;

public class WatchDogException extends RuntimeException {
    public WatchDogException() {
        super("Device Hive message watch dog triggered");
    }
}
