package com.jay.aggs;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.missing.Missing;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.*;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

/**
 * 聚合测试：https://www.cnblogs.com/leeSmall/p/9215909.html
 *
 * @author xuweijie
 */
public class AggregationTest {

    private static final String COMMA = ", ";

    private static final String INDEX = "bank";

    private static final String TYPE = "account";

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
    public void test01() {
        SearchRequestBuilder builder = client.prepareSearch(INDEX).setTypes(TYPE).setSize(5);
        builder.addAggregation(AggregationBuilders.terms("age_term").field("age"));
        SearchResponse response = builder.get();
        Terms terms = response.getAggregations().get("age_term");
        for (Terms.Bucket bucket : terms.getBuckets()) {
            System.out.println(bucket.getKeyAsString() + "--" + bucket.getDocCount());
        }
    }

    // -----指标聚合metrics----- //

    // min max sum avg
    // 查询所有客户中余额的最小值
    @Test
    public void minBalance() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.min("min_balance").field("balance"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Min min = searchResponse.getAggregations().get("min_balance");
        System.out.println(min.getName() + COMMA + min.getValue() + COMMA + min.getType());
    }

    // 查询所有客户中余额的最大值
    @Test
    public void maxBalance() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.max("max_balance").field("balance"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Max max = searchResponse.getAggregations().get("max_balance");
        System.out.println(max.getName() + COMMA + max.getValue() + COMMA + max.getType());
    }

    // 查询年龄为24岁的客户中的余额最大值
    @Test
    public void maxBalanceInAge24() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(2);
        searchRequestBuilder.setQuery(QueryBuilders.matchQuery("age", 24));
        searchRequestBuilder.addSort("balance", SortOrder.DESC);
        searchRequestBuilder.addAggregation(AggregationBuilders.max("max_balance").field("balance"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        // Hits
        SearchHits searchHits = searchResponse.getHits();
        for (SearchHit searchHit : searchHits) {
            System.out.println(searchHit.getSourceAsString());
        }

        // Agges
        Max max = searchResponse.getAggregations().get("max_balance");
        System.out.println(max.getName() + COMMA + max.getValue() + COMMA + max.getType());
    }

    // 值来源于脚本，查询所有客户的平均年龄是多少，并对平均年龄加10
    @Test
    public void avgAge() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.avg("avg_age").script(new Script("doc.age.value")));
        searchRequestBuilder.addAggregation(
                AggregationBuilders.avg("avg_age_plus_10").script(new Script("doc.age.value + 10")));
        SearchResponse searchResponse = searchRequestBuilder.get();

        // Agges
        Avg avg = searchResponse.getAggregations().get("avg_age");
        System.out.println(avg.getName() + COMMA + avg.getValue() + COMMA + avg.getType());

        Avg avg_plus_10 = searchResponse.getAggregations().get("avg_age_plus_10");
        System.out.println(avg_plus_10.getName() + COMMA + avg_plus_10.getValue() + COMMA + avg_plus_10.getType());
    }

    // 指定field，在脚本中用 _value 取字段的值
    @Test
    public void sumBalance() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.sum("sum_balance").field("balance").script(new Script("_value * 1.03")));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Sum sum = searchResponse.getAggregations().get("sum_balance");
        System.out.println(sum.getName() + COMMA + sum.getValue() + COMMA + sum.getType());
    }

    @Test
    public void avgAgeMiss() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.avg("avg_age").field("age").missing(18));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Avg avg = searchResponse.getAggregations().get("avg_age");
        System.out.println(avg.getName() + COMMA + avg.getValue() + COMMA + avg.getType());
    }

    // 统计某字段有值的文档数 value_count
    @Test
    public void count() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.count("age_count").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        ValueCount valueCount = searchResponse.getAggregations().get("age_count");
        System.out.println(valueCount.getName() + COMMA + valueCount.getValue() + COMMA + valueCount.getType());
    }

    // cardinality 值去重计数
    @Test
    public void cardinality() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.cardinality("age_count").field("age"));
        searchRequestBuilder.addAggregation(AggregationBuilders.cardinality("state_count").field("state.keyword"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Cardinality age = searchResponse.getAggregations().get("age_count");
        System.out.println(age.getName() + COMMA + age.getValue() + COMMA + age.getType());

        Cardinality state = searchResponse.getAggregations().get("state_count");
        System.out.println(state.getName() + COMMA + state.getValue() + COMMA + state.getType());
    }


    // stats 统计 count max min avg sum 5个值
    @Test
    public void stats() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.stats("age_stats").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Stats age = searchResponse.getAggregations().get("age_stats");
        System.out.println(age);
    }


    // extended_stats 统计 count max min avg sum + 平方和、方差、标准差、平均值加/减两个标准差的区间
    @Test
    public void extended_stats() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.extendedStats("age_stats").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Stats age = searchResponse.getAggregations().get("age_stats");
        System.out.println(age);
    }

    // 占比百分位对应的值统计
    @Test
    public void percentiles() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.percentiles("age_percentiles").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Percentiles percentile = searchResponse.getAggregations().get("age_percentiles");
        System.out.println(percentile);
    }

    // 占比百分位对应的值统计，指定分位值
    @Test
    public void percentilesTwo() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.percentiles("age_percentiles").field("age").percentiles(95, 99, 99.9));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Percentiles percentile = searchResponse.getAggregations().get("age_percentiles");
        System.out.println(percentile);
    }

    // Percentiles rank 统计值小于等于指定值的文档占比
    @Test
    public void percentileRanks() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.percentileRanks("age_percentiles_rank", new double[]{25, 30}).field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        PercentileRanks percentile = searchResponse.getAggregations().get("age_percentiles_rank");
        System.out.println(percentile);
    }

    // ------桶聚合----- //

    // -----Terms Aggregation  根据字段值项分组聚合
    @Test
    public void terms1() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // size 指定返回多少个分组
    @Test
    public void terms2() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age").size(20));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // 显示偏差
    @Test
    public void terms3() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age").showTermDocCountError(true).size(5));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // shard_size指定每个分片上返回多少个分组
    @Test
    public void terms4() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age").shardSize(20).size(5));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // order 指定分组的排序
    @Test
    public void term5() {
        // 根据文档计数排序
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age").order(BucketOrder.count(true)));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    @Test
    public void term6() {
        // 根据分组值排序
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term").field("age").order(BucketOrder.key(true)));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // 取分组指标值排序(子聚合)
    @Test
    public void term7() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);

        TermsAggregationBuilder termsAggregationBuilder =
                AggregationBuilders.terms("age_term").field("age")
                        .order(BucketOrder.aggregation("max_balance", true));
        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("max_balance").field("balance");
        MinAggregationBuilder minAggregationBuilder = AggregationBuilders.min("min_balance").field("balance");
        termsAggregationBuilder.subAggregation(maxAggregationBuilder).subAggregation(minAggregationBuilder);

        searchRequestBuilder.addAggregation(termsAggregationBuilder);
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // 根据脚本计算值分组
    @Test
    public void termScript() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.terms("age_term")
                        .script(new Script("doc.age.value + 2")));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Terms terms = searchResponse.getAggregations().get("age_term");
        System.out.println(terms);
    }

    // -----filter Aggregation  对满足过滤查询的文档进行聚合计算
    @Test
    public void filter() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        AggregationBuilder aggregationBuilder =
                AggregationBuilders.filter("gender_filter", QueryBuilders.matchQuery("gender", "F"));
        AggregationBuilder subAggregationBuilder = AggregationBuilders.avg("avg_age").field("age");
        aggregationBuilder.subAggregation(subAggregationBuilder);
        searchRequestBuilder.addAggregation(aggregationBuilder);

        SearchResponse searchResponse = searchRequestBuilder.get();
        Filter filter = searchResponse.getAggregations().get("gender_filter");
        System.out.println(filter);
    }

    // -----Filters Aggregation  多个过滤组聚合计算
    @Test
    public void filters() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch("logs").setSize(0);
        FiltersAggregationBuilder filtersAggregationBuilder = AggregationBuilders.filters("messages",
                new FiltersAggregator.KeyedFilter("errors", QueryBuilders.matchQuery("body", "error")),
                new FiltersAggregator.KeyedFilter("warning", QueryBuilders.matchQuery("body", "warning")))
                .otherBucketKey("other_messages").otherBucket(true);
        searchRequestBuilder.addAggregation(filtersAggregationBuilder);

        SearchResponse searchResponse = searchRequestBuilder.get();
        Filters filters = searchResponse.getAggregations().get("messages");
        System.out.println(filters);
    }

    // -----Range Aggregation 范围分组聚合
    @Test
    public void range() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);

        RangeAggregationBuilder rangeAggregationBuilder =
                AggregationBuilders.range("age_range").field("age")
                        .addUnboundedTo("Ld", 25).addRange("Md", 25, 35).addUnboundedFrom("Od", 35);
        MaxAggregationBuilder maxAggregationBuilder = AggregationBuilders.max("bmax").field("balance");
        rangeAggregationBuilder.subAggregation(maxAggregationBuilder);
        // 聚合
        searchRequestBuilder.addAggregation(rangeAggregationBuilder);

        SearchResponse searchResponse = searchRequestBuilder.get();
        Range range = searchResponse.getAggregations().get("age_range");
        System.out.println(range);
    }

    // -----Date Range Aggregation  时间范围分组聚合
    @Test
    public void dateRange() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.dateRange("date_range").field("date").
                        addUnboundedTo("now-10M/M").addUnboundedFrom("now-10M/M").format("yyyy-MM-dd"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Range range = searchResponse.getAggregations().get("date_range");
        System.out.println(range);
    }

    // -----Date Histogram Aggregation  时间直方图（柱状）聚合
    @Test
    public void dateHistogram() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(
                AggregationBuilders.dateHistogram("date_histogram")
                        .field("date").calendarInterval(DateHistogramInterval.MONTH));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Histogram histogram = searchResponse.getAggregations().get("date_histogram");
        System.out.println(histogram);
    }

    // ----- Missing Aggregation  缺失值的桶聚合
    @Test
    public void missing() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.missing("age_missing").field("age"));
        SearchResponse searchResponse = searchRequestBuilder.get();

        Missing missing = searchResponse.getAggregations().get("age_missing");
        System.out.println(missing);
    }

    @After
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

}
