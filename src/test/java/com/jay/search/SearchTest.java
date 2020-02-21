package com.jay.search;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

/**
 * 查询:
 *  https://juejin.im/post/5b7fe4a46fb9a019d92469a9
 *  https://juejin.im/post/5cdded58e51d4515b4778117
 *
 * @author xuweijie
 */
public class SearchTest {

    private TransportClient client;

    private static final String INDEX = "bookdb_index";

    private static final String TYPE = "book";

    @Before
    public void init() throws Exception {
        Settings settings = Settings.builder()
                // 集群名称
                .put("cluster.name", "elasticsearch")
                // 自动发现其他集群节点
                .put("client.transport.sniff", true).build();
        client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300));
    }

    /**
     * 1.1 对 "guide" 执行全文检索
     */
    @Test
    public void multiMatch() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        // 不指定字段，表示全字段搜索
        MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder("guide");
        searchRequestBuilder.setQuery(multiMatchQueryBuilder);
        SearchResponse searchResponse = searchRequestBuilder.get();

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 1.2 在标题字段(title)中搜索带有 "in action" 字样的图书
     */
    @Test
    public void match() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);

        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title", "in action");
        searchRequestBuilder.setQuery(matchQueryBuilder).setFrom(0).setSize(2)
                // _source
                .setFetchSource(new String[]{"title", "summary", "publish_date"}, null);
        HighlightBuilder highlightBuilder = new HighlightBuilder().field("title");
        searchRequestBuilder.highlighter(highlightBuilder);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 2、多字段检索 (Multi-field Search)
     */
    @Test
    public void multiFields() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        MultiMatchQueryBuilder queryBuilder = new MultiMatchQueryBuilder("guide").field("title").field("summary");
        searchRequestBuilder.setQuery(queryBuilder);
        SearchResponse searchResponse = searchRequestBuilder.get();

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 3、Boosting提升某字段得分的检索(Boosting)
     * 将“摘要”字段的得分提高3倍
     */
    @Test
    public void multiFieldsBoosting() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        MultiMatchQueryBuilder queryBuilder =
                new MultiMatchQueryBuilder("elasticsearch guide").field("title").field("summary", 3);
        searchRequestBuilder.setQuery(queryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publish_date"}, null);
        SearchResponse searchResponse = searchRequestBuilder.get();

        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 4、Bool检索(Bool Query) :
     * 在标题中搜索一本名为 "Elasticsearch" 或 "Solr" 的书，
     * AND由 "clinton gormley" 创作，但NOT由 "radu gheorge" 创作
     */
    @Test
    public void bool() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);

        BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
        queryBuilder.must().add(QueryBuilders.matchQuery("authors", "clinton gormely"));
        queryBuilder.mustNot().add(QueryBuilders.matchQuery("authors", "radu gheorge"));

        BoolQueryBuilder titleShouldBool = new BoolQueryBuilder();
        titleShouldBool.should().add(QueryBuilders.matchQuery("title", "Elasticsearch"));
        titleShouldBool.should().add(QueryBuilders.matchQuery("title", "Solr"));
        queryBuilder.must().add(titleShouldBool);

        searchRequestBuilder.setQuery(queryBuilder);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 5、Fuzzy 模糊检索( Fuzzy Queries)
     */
    @Test
    public void fuzzy() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder("comprihensiv guide")
                .field("title").field("summary").fuzziness(Fuzziness.AUTO);
        searchRequestBuilder.setSize(2).setQuery(multiMatchQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publish_date"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 6、Wildcard Query 通配符检索
     * 要查找具有以 "t" 字母开头的作者的所有记录
     */
    @Test
    public void wildcard() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        WildcardQueryBuilder wildcardQueryBuilder = new WildcardQueryBuilder("authors", "t*");
        searchRequestBuilder.setQuery(wildcardQueryBuilder);
        searchRequestBuilder.setFetchSource(new String[]{"title", "authors"}, null)
                .highlighter(new HighlightBuilder().field("authors", 200));

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 7、正则表达式检索( Regexp Query)
     */
    @Test
    public void regexp() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        RegexpQueryBuilder regexpQueryBuilder = new RegexpQueryBuilder("authors", "t[a-z]*y");
        searchRequestBuilder.setQuery(regexpQueryBuilder)
                .setFetchSource(new String[]{"title", "authors"}, null)
                .highlighter(new HighlightBuilder().field("authors"));

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 8、匹配短语检索(Match Phrase Query)
     */
    @Test
    public void phrase() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        MultiMatchQueryBuilder multiMatchQueryBuilder = new MultiMatchQueryBuilder("search engine")
                .field("title").field("summary").type(MultiMatchQueryBuilder.Type.PHRASE).slop(3);
        searchRequestBuilder.setQuery(multiMatchQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publish_date"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 9、匹配词组前缀检索
     */
    @Test
    public void phrasePrefix() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        MatchPhrasePrefixQueryBuilder matchPhrasePrefixQueryBuilder =
                new MatchPhrasePrefixQueryBuilder("summary", "search en");
        matchPhrasePrefixQueryBuilder.slop(3).maxExpansions(10);
        searchRequestBuilder.setQuery(matchPhrasePrefixQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publish_date"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 10、字符串检索（Query String）
     */
    @Test
    public void queryString() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        QueryStringQueryBuilder queryStringQueryBuilder =
                new QueryStringQueryBuilder("(saerch~1 algorithm~1) AND ((grant ingersoll)  OR (tom morton))");
        queryStringQueryBuilder.field("summary", 2).field("title").field("authors").field("publisher");
        searchRequestBuilder.setQuery(queryStringQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "authors"}, null)
                .highlighter(new HighlightBuilder().field("summary"));

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 11、简化的字符串检索（Simple Query String）
     */
    @Test
    public void simpleQueryString() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        SimpleQueryStringBuilder simpleQueryStringBuilder =
                new SimpleQueryStringBuilder("(saerch~1 algorithm~1) + ((grant ingersoll)  | (tom morton))");
        simpleQueryStringBuilder.field("summary", 2).field("title").field("authors").field("publisher");

        searchRequestBuilder.setQuery(simpleQueryStringBuilder)
                .setFetchSource(new String[]{"title", "summary", "authors"}, null)
                .highlighter(new HighlightBuilder().field("summary"));

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 12、Term/Terms检索（指定字段检索）
     */
    @Test
    public void term() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        TermQueryBuilder termQueryBuilder = new TermQueryBuilder("publisher", "manning");
        searchRequestBuilder.setQuery(termQueryBuilder)
                .setFetchSource(new String[]{"title", "publish_date", "publisher"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    @Test
    public void terms() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        TermsQueryBuilder termsQueryBuilder = new TermsQueryBuilder("publisher", "manning", "oreilly");
        searchRequestBuilder.setQuery(termsQueryBuilder)
                .setFetchSource(new String[]{"title", "publish_date", "publisher"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 13、Term排序检索-（Term Query - Sorted）
     */
    @Test
    public void termAndSort() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        TermQueryBuilder termQueryBuilder = new TermQueryBuilder("publisher", "manning");
        searchRequestBuilder.setQuery(termQueryBuilder)
                .setFetchSource(new String[]{"title", "publish_date", "publisher"}, null)
                .addSort("publisher.keyword", SortOrder.DESC)
                .addSort("title.keyword", SortOrder.ASC);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 14、范围检索（Range query）
     */
    @Test
    public void range() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);
        RangeQueryBuilder rangeQueryBuilder = new RangeQueryBuilder("publish_date")
                .gte("2015-01-01").lte("2015-12-31");
        searchRequestBuilder.setQuery(rangeQueryBuilder)
                .setFetchSource(new String[]{"title", "publish_date", "publisher"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 15. 过滤检索
     */
    @Test
    public void filter() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must().add(QueryBuilders.multiMatchQuery("elasticsearch", "title", "summary"));
        boolQueryBuilder.filter().add(QueryBuilders.rangeQuery("num_reviews").gte(20));

        searchRequestBuilder.setQuery(boolQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publisher", "num_reviews"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    /**
     * 16、多个过滤检索
     */
    @Test
    public void multiFilter() {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(INDEX).setTypes(TYPE);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must().add(QueryBuilders.multiMatchQuery("elasticsearch", "title", "summary"));

        BoolQueryBuilder filterBool = new BoolQueryBuilder();
        filterBool.must().add(QueryBuilders.rangeQuery("num_reviews").gte(20));
        filterBool.mustNot().add(QueryBuilders.rangeQuery("publish_date").lte("2014-12-31"));
        filterBool.should().add(QueryBuilders.termQuery("publisher", "oreilly"));

        boolQueryBuilder.filter().add(filterBool);

        searchRequestBuilder.setQuery(boolQueryBuilder)
                .setFetchSource(new String[]{"title", "summary", "publisher", "num_reviews", "publish_date"}, null);

        SearchResponse searchResponse = searchRequestBuilder.get();
        for (SearchHit searchHit : searchResponse.getHits().getHits()) {
            System.out.println(searchHit);
        }
    }

    @After
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

}
