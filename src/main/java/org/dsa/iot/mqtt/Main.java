package org.dsa.iot.mqtt;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkFactory;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Samuel Grenier
 */
public class Main extends DSLinkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private Broker broker;
    private DSLink dslink;

    @Override
    public boolean isResponder() {
        return true;
    }

    @Override
    public void stop() {
        if (broker != null) {
            broker.stop();
        }
        if (dslink == null) {
            return;
        }
        Node node = dslink.getNodeManager().getSuperRoot();
        Map<String, Node> children = node.getChildren();
        if (children != null) {
            for (Node n : children.values()) {
                Mqtt mqtt = n.getMetaData();
                if (mqtt != null) {
                    mqtt.disconnect();
                }
            }
        }
    }

    @Override
    public void onResponderInitialized(DSLink link) {
        Node root = link.getNodeManager().getSuperRoot();

        dslink = link;
        broker = Broker.init(root);
        Mqtt.init(root);
        LOGGER.info("Initialized");
    }

    @Override
    public void onResponderConnected(DSLink link) {
        LOGGER.info("Connected");
    }

    @Override
    public Node onSubscriptionFail(final String path) {
        final String[] split = NodeManager.splitPath(path);

        final NodeManager manager = dslink.getNodeManager();
        final Node node = manager.getNode(split[0]).getNode();
        final Mqtt mqtt = node.getMetaData();
        mqtt.get(new Handler<MqttClient>() {
            @Override
            public void handle(MqttClient event) {
                String topic = path.substring(node.getPath().length() + 5);
                try {
                    event.subscribe(topic, mqtt.getQos());
                } catch (MqttException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Node n = manager.getNode(path, true).getNode();
        n.setSerializable(false);
        n.setValueType(ValueType.STRING);
        n.setValue(new Value((String) null));
        return n;
    }

    @Override
    public void onSetFail(final String path, final Value value) {
        final String[] split = NodeManager.splitPath(path);

        final NodeManager manager = dslink.getNodeManager();
        final Node node = manager.getNode(split[0]).getNode();
        final Mqtt mqtt = node.getMetaData();
        String topic = path.substring(node.getPath().length() + 6);
        mqtt.publish(topic, value.toString(), false);
    }

    public static void main(String[] args) {
        DSLinkFactory.start(args, new Main());
    }
}
