package me.vektory79.dh.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Dh2MqttRelay implements MqttCallback {
    private static final Logger LOGGER = Logger.getLogger(Dh2MqttRelay.class.getName());

    private MqttClient mqttClient;
    private final MqttConnectOptions connOpts = new MqttConnectOptions();

    public Dh2MqttRelay(String serverURI, String clientId, String user, String password) {
        try {
            MqttClientPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(serverURI, clientId, persistence);
        } catch (MqttException e) {
            throw new RuntimeException("Error creating MQTT client", e);
        }

        connOpts.setCleanSession(true);
        connOpts.setUserName(user);
        connOpts.setPassword(password.toCharArray());
        mqttClient.setCallback(this);
    }

    public void connect() throws MqttException {
        mqttClient.connect(connOpts);
        subscribe();
    }

    public static void main(String[] args) throws MqttException, InterruptedException, IOException {
        Properties props = new Properties();
        if (args.length == 1) {
            try (InputStream is = Files.newInputStream(Paths.get(args[0]), StandardOpenOption.READ)) {
                props.load(is);
            }
        } else {
            fallToHelp();
        }

        String serverURL = props.getProperty("server.url");
        String clientID = props.getProperty("client.id");
        String serverUser = props.getProperty("server.user");
        String serverPassword = props.getProperty("server.password");

        if (serverURL == null || clientID == null || serverUser == null || serverPassword == null) {
            fallToHelp();
        }

        Dh2MqttRelay relay = new Dh2MqttRelay(serverURL, clientID, serverUser, serverPassword);
        relay.connect();

        while (true) {
            Thread.sleep(1000);
        }
    }

    private void subscribe() throws MqttException {
        mqttClient.subscribe("dh/#");
    }

    private static void fallToHelp() {
        System.out.println("Incorrect parameter.");
        System.out.println();
        System.out.println("Execution example:");
        System.out.println("\tjava -jar dh2mqtt.jar <path/to/config.properties>");
        System.out.println();
        System.out.println("Required parameters in the file is:");
        System.out.println("\tserver.url - URL to the MQTT server. Ex: tcp://example.com:1883");
        System.out.println("\tserver.user - User name on the MQTT server");
        System.out.println("\tserver.password - User password on the MQTT server");
        System.out.println("\tclient.id - MQTT client ID");
        System.exit(-1);
    }

    public void connectionLost(Throwable cause) {
        LOGGER.log(Level.SEVERE, "connection lost", cause);
        while (true) {
            try {
                Thread.sleep(5000);
                try {
                    mqttClient.disconnect();
                } catch (Throwable e1) {
                    LOGGER.log(Level.WARNING, e1, () ->"Can't disconnect");
                }
                connect();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, e, () -> "Can't restore connection");
                continue;
            }
            break;
        }
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
        LOGGER.fine("delivery complete");
    }

    public void messageArrived(String topic, MqttMessage message) {
        if (!topic.equals("dh/request")) {
            return;
        }

        LOGGER.fine(() -> "message arrived [" + topic + "]: " + new String(message.getPayload()) + "'");

        StringBuilder mqttTopic = new StringBuilder(1024);
        mqttTopic.append("devices").append('/');

        JSONObject obj = new JSONObject(message.toString());
        int requestId = obj.getInt("requestId");
        if (obj.has("deviceId")) {
            String deviceId = obj.getString("deviceId");
            mqttTopic.append(deviceId).append('/');

            if (obj.has("notification")) {
                JSONObject notifObj = obj.getJSONObject("notification");
                mqttTopic.append("notification").append('/');

                String notification = notifObj.getString("notification");
                mqttTopic.append(notification);

                JSONObject params = notifObj.getJSONObject("parameters");
                params.accumulate("requestId", requestId);

                MqttMessage mqttRequest = new MqttMessage(params.toString().getBytes());
                mqttRequest.setId(requestId);
                LOGGER.info(() -> mqttTopic.toString() + ": " + params.toString());
                try {
                    mqttClient.publish(mqttTopic.toString(), mqttRequest);
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, e, () -> "Error publish");
                }
            }
        }
    }
}

