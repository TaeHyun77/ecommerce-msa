package com.park.ecommerce.keyword;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 회차 기준 삭제(delete by query)와 키 조회는 실제 ES에서만 확인되므로 컨테이너로 검증
@Testcontainers
@DataElasticsearchTest
class SearchKeywordDocumentRepositoryTest {
    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse(new ImageFromDockerfile("ecommerce-elasticsearch-nori", false)
                            .withDockerfile(Path.of("../docker/elasticsearch/Dockerfile"))
                            .get())
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private SearchKeywordDocumentRepository repository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @BeforeEach
    void setUp() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(SearchKeywordDocument.class);
        indexOps.delete();
        indexOps.createWithMapping();
    }

    @Test
    @DisplayName("이번 회차보다 먼저 쓴 문서만 지우고, 이번 회차에 다시 쓴 문서는 남긴다")
    void deletesDocumentsFromPreviousRuns() {
        repository.saveAll(List.of(
                SearchKeywordDocument.of("고등어 구이", 5, 1L),
                SearchKeywordDocument.of("순살 고등어", 4, 1L)));
        repository.save(SearchKeywordDocument.of("고등어 구이", 6, 2L)); // 다음 회차에서 다시 씀

        repository.deleteByAggregatedAtLessThan(2L);

        assertThat(repository.findAll()).extracting(SearchKeywordDocument::getName).containsExactly("고등어 구이");
    }

    @Test
    @DisplayName("띄어쓰기·기호·대소문자만 다른 표기로도 같은 문서가 있는지 확인할 수 있다")
    void existsByKey() {
        repository.save(SearchKeywordDocument.of("A2 고등어 구이", 5, 1L));

        assertThat(repository.existsById(SearchKeywordDocument.keyOf("a2고등어구이"))).isTrue();
        assertThat(repository.existsById(SearchKeywordDocument.keyOf("고등어 조림"))).isFalse();
    }
}
