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

public class Dh2MqttRelay {
    private static final Logger LOGGER = Logger.getLogger(Dh2MqttRelay.class.getName());

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

        MemoryPersistence persistence = new MemoryPersistence();
        MqttClient mqttClient = new MqttClient(serverURL, clientID, persistence);

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setUserName(serverUser);
        connOpts.setPassword(serverPassword.toCharArray());

        mqttClient.setCallback(new MessageListener(mqttClient));
        mqttClient.connect(connOpts);

        mqttClient.subscribe("dh/#");

        while (true) {
            try {
                checkConnection(mqttClient);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Can't restore connection", e);
            }
            Thread.sleep(1000);
        }
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

    private static void checkConnection(MqttClient mqttClient) throws MqttException {
        if (!mqttClient.isConnected()) {
            mqttClient.reconnect();
        }
    }

    private static class MessageListener implements MqttCallback {
        private final MqttClient mqttClient;
        public MessageListener(MqttClient mqttClient) {
            this.mqttClient = mqttClient;
        }

        public void connectionLost(Throwable cause) {
            LOGGER.log(Level.SEVERE, "connection lost", cause);
            while (true) {
                try {
                    mqttClient.reconnect();
                } catch (MqttException e) {
                    LOGGER.log(Level.SEVERE, "Can't restore connection", e);
                    continue;
                }
                break;
            }
        }

        /**
         * @param token
         */
        public void deliveryComplete(IMqttDeliveryToken token) {
            LOGGER.fine("delivery complete");
        }

        /**
         * @param topic
         * @param message
         */
        public void messageArrived(String topic, MqttMessage message) {
            if (!topic.equals("dh/request")) {
                return;
            }

            LOGGER.fine("message arrived [" + topic + "]: " + new String(message.getPayload()) + "'");

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
                    LOGGER.info(mqttTopic.toString() + ": " + params.toString());
                    try {
                        mqttClient.publish(mqttTopic.toString(), mqttRequest);
                    } catch (MqttException e) {
                        LOGGER.log(Level.SEVERE, "Error publish", e);
                    }
                }
            }
        }
    }
}
