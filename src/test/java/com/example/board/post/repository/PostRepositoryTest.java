package com.example.board.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.board.global.config.JpaAuditingConfig;
import com.example.board.post.domain.Post;
import com.example.board.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
class PostRepositoryTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    PostRepository postRepository;

    @Autowired
    EntityManager em;

    private Post savePost(String title) {
        return postRepository.save(Post.create(title, "본문 " + title, "author"));
    }

    private List<Post> savePosts(int count) {
        List<Post> posts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            posts.add(savePost("제목" + i));
        }
        em.flush();
        em.clear();
        return posts;
    }

    private void setCreatedAt(Long id, Instant createdAt) {
        em.createNativeQuery("update posts set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private Statistics statistics() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("incrementViewCount는 갱신된 행 수 1을 반환하고 DB의 조회수를 1 증가시킨다")
    void incrementViewCountIncreasesViewCount() {
        // given
        Post post = savePost("제목");
        em.flush();
        em.clear();

        // when
        int updated = postRepository.incrementViewCount(post.getId());

        // then
        assertThat(updated).isEqualTo(1);
        em.clear();
        Post found = postRepository.findById(post.getId()).orElseThrow();
        assertThat(found.getViewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("incrementViewCount를 여러 번 호출하면 호출 횟수만큼 조회수가 누적된다")
    void incrementViewCountAccumulates() {
        // given
        Post post = savePost("제목");
        em.flush();
        em.clear();

        // when
        postRepository.incrementViewCount(post.getId());
        postRepository.incrementViewCount(post.getId());
        postRepository.incrementViewCount(post.getId());

        // then
        em.clear();
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViewCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("incrementViewCount는 영속성 컨텍스트에 로딩된 엔티티가 있어도 재조회 시 증가된 값을 반환한다")
    void incrementViewCountReflectsAfterLoadedEntity() {
        // given
        Post post = savePost("제목");
        em.flush();
        postRepository.findById(post.getId()).orElseThrow();

        // when
        postRepository.incrementViewCount(post.getId());

        // then
        assertThat(postRepository.findById(post.getId()).orElseThrow().getViewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 id로 incrementViewCount를 호출하면 0을 반환한다")
    void incrementViewCountReturnsZeroWhenNotExists() {
        // given
        Long notExistingId = 999_999L;

        // when
        int updated = postRepository.incrementViewCount(notExistingId);

        // then
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("deleteByIdReturningCount는 1을 반환하고 게시글을 삭제한다")
    void deleteByIdReturningCountDeletesPost() {
        // given
        Post post = savePost("제목");
        em.flush();
        em.clear();

        // when
        int deleted = postRepository.deleteByIdReturningCount(post.getId());

        // then
        assertThat(deleted).isEqualTo(1);
        em.clear();
        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 id로 deleteByIdReturningCount를 호출하면 0을 반환한다")
    void deleteByIdReturningCountReturnsZeroWhenNotExists() {
        // given
        Post post = savePost("제목");
        em.flush();
        em.clear();

        // when
        int deleted = postRepository.deleteByIdReturningCount(post.getId() + 1000);

        // then
        assertThat(deleted).isZero();
        assertThat(postRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("목록 페이징은 totalElements, totalPages와 페이지별 항목 수를 올바르게 반환한다")
    void findAllReturnsPageMetadata() {
        // given
        List<Post> posts = savePosts(5);

        // when
        Page<Post> first = postRepository.findAll(PageRequest.of(0, 2, DEFAULT_SORT));
        Page<Post> last = postRepository.findAll(PageRequest.of(2, 2, DEFAULT_SORT));
        Page<Post> beyond = postRepository.findAll(PageRequest.of(3, 2, DEFAULT_SORT));

        // then
        assertThat(first.getTotalElements()).isEqualTo(5L);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).hasSize(2);
        assertThat(first.isFirst()).isTrue();
        assertThat(first.hasNext()).isTrue();

        assertThat(last.getContent()).hasSize(1);
        assertThat(last.isLast()).isTrue();
        assertThat(last.getContent().getFirst().getId()).isEqualTo(posts.getFirst().getId());

        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(5L);
    }

    @Test
    @DisplayName("목록은 페이지 경계에서 항목이 중복되거나 누락되지 않고 최신순으로 이어진다")
    void findAllPagesAreContiguous() {
        // given
        List<Post> posts = savePosts(5);
        List<Long> expectedIds = posts.stream().map(Post::getId).sorted((a, b) -> Long.compare(b, a)).toList();

        // when
        List<Long> page0 = postRepository.findAll(PageRequest.of(0, 2, DEFAULT_SORT)).map(Post::getId).getContent();
        List<Long> page1 = postRepository.findAll(PageRequest.of(1, 2, DEFAULT_SORT)).map(Post::getId).getContent();
        List<Long> page2 = postRepository.findAll(PageRequest.of(2, 2, DEFAULT_SORT)).map(Post::getId).getContent();

        // then
        List<Long> all = new ArrayList<>();
        all.addAll(page0);
        all.addAll(page1);
        all.addAll(page2);
        assertThat(all).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("목록은 createdAt 내림차순으로 정렬되고 createdAt이 같으면 id 내림차순으로 정렬된다")
    void findAllSortsByCreatedAtDescThenIdDesc() {
        // given
        List<Post> posts = savePosts(4);
        Instant base = Instant.parse("2026-10-23T03:15:00Z");
        // id가 가장 작은 글을 가장 최신으로, 2·3번은 같은 시각으로 둔다
        setCreatedAt(posts.get(0).getId(), base.plus(1, ChronoUnit.HOURS));
        setCreatedAt(posts.get(1).getId(), base);
        setCreatedAt(posts.get(2).getId(), base);
        setCreatedAt(posts.get(3).getId(), base.minus(1, ChronoUnit.HOURS));
        em.clear();

        // when
        Page<Post> page = postRepository.findAll(PageRequest.of(0, 10, DEFAULT_SORT));

        // then
        assertThat(page.getContent()).extracting(Post::getId).containsExactly(
                posts.get(0).getId(),
                posts.get(2).getId(),
                posts.get(1).getId(),
                posts.get(3).getId());
    }

    @Test
    @DisplayName("목록 조회 시 실행되는 쿼리는 목록과 count를 합쳐 페이지당 2개 이하다")
    void findAllExecutesAtMostTwoQueriesPerPage() {
        // given
        savePosts(25);
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // when
        Page<Post> page = postRepository.findAll(PageRequest.of(1, 10, DEFAULT_SORT));
        page.getContent().forEach(post -> {
            post.getTitle();
            post.getAuthor();
            post.getViewCount();
            post.getCreatedAt();
        });

        // then
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(25L);
        // 통계가 꺼져 0으로 통과하는 경우를 막기 위해 하한도 함께 검증한다
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
    }

    @Test
    @DisplayName("저장 시 createdAt/updatedAt이 채워지고 재조회 값과 마이크로초 정밀도로 일치한다")
    void auditingFieldsArePopulatedWithMicrosecondPrecision() {
        // given
        Post post = Post.create("제목", "본문", "author");

        // when
        Post saved = postRepository.saveAndFlush(post);
        Instant createdAt = saved.getCreatedAt();
        Instant updatedAt = saved.getUpdatedAt();
        em.clear();
        Post found = postRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(createdAt).isEqualTo(updatedAt);
        assertThat(createdAt.getNano() % 1_000).isZero();
        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
        assertThat(found.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("수정 시 updatedAt은 갱신되고 createdAt과 author는 변경되지 않는다")
    void updateRefreshesUpdatedAtOnly() {
        // given
        Post saved = postRepository.saveAndFlush(Post.create("제목", "본문", "author"));
        Instant createdAt = saved.getCreatedAt();
        em.clear();

        // when
        Post post = postRepository.findById(saved.getId()).orElseThrow();
        post.update("수정된 제목", "수정된 본문");
        em.flush();
        em.clear();

        // then
        Post found = postRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("수정된 제목");
        assertThat(found.getContent()).isEqualTo("수정된 본문");
        assertThat(found.getAuthor()).isEqualTo("author");
        assertThat(found.getCreatedAt()).isEqualTo(createdAt);
        assertThat(found.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
