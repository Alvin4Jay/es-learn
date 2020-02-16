package com.jay.demo;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author xuweijie
 */
public class Demo {

    public static void main(String[] args) throws UnknownHostException {
        Settings esSettings = Settings.builder()
                // 设置ES实例的名称
                .put("cluster.name", "elasticsearch")
                // 自动嗅探整个集群的状态，把集群中其他ES节点的ip添加到本地的客户端列表中
                .put("client.transport.sniff", true)
                .build();

        Client client = new PreBuiltTransportClient(esSettings)
                .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300));

        indexGet(client);
    }

    private static void indexGet(Client client) {
        SearchResponse res = client.prepareSearch("tt_user").get();
        System.out.println(res);
        // on shutdown
        client.close();
    }
}
