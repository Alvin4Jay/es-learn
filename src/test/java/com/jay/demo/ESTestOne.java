package com.jay.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * ES客户端测试
 *
 * @author xuweijie
 */
public class ESTestOne {

    private static final String INDEX = "people";
    private static final String TYPE = "student";
    private TransportClient client;

    @Before
    public void init() throws Exception {
        Settings settings = Settings.builder()
                // 集群名称
                .put("cluster.name", "elasticsearch")
                // 自动发现其他集群节点
                .put("client.transport.sniff", true)
                .build();
        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300));
    }

    @Test
    public void sweet() {
        SearchResponse res = client.prepareSearch("tt_user").get();
        SearchHits searchHits = res.getHits();
        for (SearchHit searchHit : searchHits) {
            System.out.println(searchHit.getSourceAsString());
        }
    }

    // json在当前版本不可用
//    @Test
//    public void indexByJSON() {
//        // language=JSON
//        String source = "{\n" +
//                "  \"user\": \"xuanjian\",\n" +
//                "  \"postDate\": \"2020-02-16\",\n" +
//                "  \"message\": \"hello world\"\n" +
//                "}";
//        IndexResponse response = client.prepareIndex("people", "student")
//                .setSource(source).get();
//        System.out.println(response);
//    }

    @Test
    public void indexByMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("user", "xuanjian");
        map.put("postDate", "2020-02-16");
        map.put("message", "yellow");

        IndexResponse response = client.prepareIndex(INDEX, TYPE, "1").setSource(map).get();
        System.out.println(response);
    }

    @Test
    public void indexByJackson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        Student student = new Student();
        student.setUser("zhongshuo");
        student.setPostDate("2019-10-20");
        student.setMessage("green");

        byte[] source = mapper.writeValueAsBytes(student);
        IndexResponse response = client.prepareIndex(INDEX, TYPE, "2").setSource(source, XContentType.JSON).get();
        System.out.println(response);
    }

    @Test
    public void indexByXContentBuilder() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject()
                .field("user", "zhangsan")
                .field("postDate", "2020-02-20")
                .field("message", "es is good").endObject();
        IndexResponse response = client.prepareIndex(INDEX, TYPE, "3").setSource(builder).get();
        System.out.println(response);
    }

    @Test
    public void getDocument() {
        GetResponse response = client.prepareGet(INDEX, TYPE, "1").get();
        System.out.println(response.getSourceAsString());
    }

    @Test
    public void deleteDocument() {
        DeleteResponse response = client.prepareDelete(INDEX, TYPE, "3").get();
        System.out.println(response);
    }

    @Test
    public void updateOne() throws IOException, ExecutionException, InterruptedException {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index(INDEX);
        updateRequest.type(TYPE);
        updateRequest.id("1");
        updateRequest.doc(XContentFactory.jsonBuilder().startObject().field("gender", "male").endObject());

        UpdateResponse response = client.update(updateRequest).get();
        System.out.println(response);
    }

    @Test
    public void updateTwo() throws IOException {
        UpdateResponse response = client.prepareUpdate(INDEX, TYPE, "1")
                .setDoc(XContentFactory.jsonBuilder().startObject().field("gender", "female").endObject())
                .get();
        System.out.println(response);
    }

    @Test
    public void updateThree() throws ExecutionException, InterruptedException {
        // 使用脚本更新文档
        UpdateRequest updateRequest = new UpdateRequest(INDEX, TYPE, "1")
                .script(new Script("ctx._source.gender = \"male-1\""));
        UpdateResponse response = client.update(updateRequest).get();
        System.out.println(response);
    }

    @Test
    public void upsert() throws IOException, ExecutionException, InterruptedException {
        // upsert：存在文档就更新，否则插入文档
        IndexRequest indexRequest = new IndexRequest(INDEX, TYPE, "3")
                .source(XContentFactory.jsonBuilder()
                        .startObject()
                        .field("name", "Joe Smith")
                        .field("gender", "male")
                        .endObject());
        UpdateRequest updateRequest = new UpdateRequest(INDEX, TYPE, "3")
                .doc(XContentFactory.jsonBuilder()
                        .startObject()
                        .field("gender", "female")
                        .endObject())
                .upsert(indexRequest); // 如果不存在此文档 ，就增加 `indexRequest`
        System.out.println(client.update(updateRequest).get());
    }

    @Test
    public void mGet() {
        MultiGetResponse responses = client.prepareMultiGet()
                .add(INDEX, TYPE, "1", "2", "3")
                .get();
        // 遍历
        for (MultiGetItemResponse itemResponse : responses) {
            GetResponse response = itemResponse.getResponse();
            if (response.isExists()) { // 文档是否存在
                System.out.println(response.getSourceAsString()); // _source字段
            }
        }
    }

    @Test
    public void bulk() throws IOException {
        BulkRequestBuilder bulkRequest = client.prepareBulk();

        // either use client#prepare, or use Requests# to directly build index/delete requests
        bulkRequest.add(client.prepareIndex(INDEX, TYPE, "4")
                .setSource(XContentFactory.jsonBuilder()
                        .startObject()
                        .field("user", "kimchy")
                        .field("postDate", "2019-01-01")
                        .field("message", "trying out Elasticsearch")
                        .endObject()
                )
        );

        bulkRequest.add(client.prepareIndex(INDEX, TYPE, "5")
                .setSource(XContentFactory.jsonBuilder()
                        .startObject()
                        .field("user", "kimchy")
                        .field("postDate", "2020-02-02")
                        .field("message", "another post")
                        .endObject()
                )
        );

        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            // process failures by iterating through each bulk response item
            // 处理失败
            System.out.println(bulkResponse.buildFailureMessage());
        }
    }

    // 使用Bulk Processor
    @Test
    public void bulkProcessor() throws IOException, InterruptedException {
        BulkProcessor bulkProcessor = BulkProcessor.builder(
                client,  // elasticsearch客户端
                new BulkProcessor.Listener() {
                    @Override
                    public void beforeBulk(long executionId,
                                           BulkRequest request) {
                    } // 调用bulk之前执行 ，例如你可以通过request.numberOfActions()方法知道numberOfActions

                    @Override
                    public void afterBulk(long executionId,
                                          BulkRequest request,
                                          BulkResponse response) {
                    } // 调用bulk之后执行 ，例如你可以通过request.hasFailures()方法知道是否执行失败

                    @Override
                    public void afterBulk(long executionId,
                                          BulkRequest request,
                                          Throwable failure) {
                    } // 调用失败抛Throwable
                })
                .setBulkActions(10000) // 每次10000请求
                .setBulkSize(new ByteSizeValue(5, ByteSizeUnit.MB)) // 拆成5mb一块
                .setFlushInterval(TimeValue.timeValueSeconds(5)) // 无论请求数量多少，每5秒钟请求一次。
                .setConcurrentRequests(1) // 设置并发请求的数量。值为0意味着只允许执行一个请求。值为1意味着允许1并发请求。
                .setBackoffPolicy(
                        // 设置自定义重复请求机制，最开始等待100毫秒，之后成倍更加，重试3次，当一次或多次重复请求失败后因为
                        // 计算资源不够抛出 EsRejectedExecutionException 异常，可以通过BackoffPolicy.noBackoff()方法关闭重试机制
                        BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 3))
                .build();

        // 插入文档
        bulkProcessor.add(new IndexRequest(INDEX, TYPE, "6")
                .source(XContentFactory.jsonBuilder().startObject().field("aa", "bb").endObject()));
        // 删除文档
        bulkProcessor.add(new DeleteRequest(INDEX, TYPE, "3"));

        // 批量请求
        bulkProcessor.flush();

        // 关闭BulkProcessor
        bulkProcessor.awaitClose(10, TimeUnit.MINUTES);
    }

    @Data
    private static class Student {
        private String user;
        private String postDate;
        private String message;
    }

    @After
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }


}
