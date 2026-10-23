package com.example.board.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    @DisplayName("Page를 content와 페이지 정보로 변환한다")
    void convertsPageToPageResponse() {
        // given
        PageImpl<String> page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

        // when
        PageResponse<String> response = PageResponse.from(page);

        // then
        assertThat(response.content()).containsExactly("c", "d");
        assertThat(response.page().number()).isEqualTo(1);
        assertThat(response.page().size()).isEqualTo(2);
        assertThat(response.page().totalElements()).isEqualTo(5);
        assertThat(response.page().totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 Page는 빈 content와 0건 페이지 정보로 변환한다")
    void convertsEmptyPage() {
        // given
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        // when
        PageResponse<String> response = PageResponse.from(page);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.page().number()).isZero();
        assertThat(response.page().size()).isEqualTo(10);
        assertThat(response.page().totalElements()).isZero();
        assertThat(response.page().totalPages()).isZero();
    }
}
