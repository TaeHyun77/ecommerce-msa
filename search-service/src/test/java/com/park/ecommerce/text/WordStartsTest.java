package com.park.ecommerce.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordStartsTest {
    @Test
    @DisplayName("단어마다 그 단어부터 끝까지를 공백·기호 없이 붙인 값을 만든다")
    void buildsSuffixFromEachWord() {
        assertThat(WordStarts.of("[바다소리] 숯불 고등어구이 70g"))
                .containsExactly("바다소리숯불고등어구이70g", "숯불고등어구이70g", "고등어구이70g", "70g");
    }

    @Test
    @DisplayName("기호로 이어진 단어도 따로 나누고 영문은 소문자로 통일한다")
    void splitsBySymbolsAndLowercases() {
        assertThat(WordStarts.of("고소&아삭한 KF365")).containsExactly("고소아삭한kf365", "아삭한kf365", "kf365");
    }

    @Test
    @DisplayName("입력은 공백·기호를 빼고 소문자로 붙여 단어 시작 값과 비교할 수 있게 한다")
    void compactsInput() {
        assertThat(WordStarts.compact(" 고등어  구")).isEqualTo("고등어구");
        assertThat(WordStarts.compact("KF 365")).isEqualTo("kf365");
    }

    @Test
    @DisplayName("단어가 없으면 빈 값을 돌려준다")
    void returnsEmptyWithoutWords() {
        assertThat(WordStarts.of("[] ()")).isEmpty();
        assertThat(WordStarts.compact("[]")).isEmpty();
    }
}
