package com.park.ecommerce.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JamoConverterTest {
    @Test
    @DisplayName("완성형 글자를 초성·중성·종성 자모로 푼다")
    void decomposesSyllables() {
        assertThat(JamoConverter.toJamo("고등어")).isEqualTo("고등어");
    }

    @Test
    @DisplayName("입력 중인 미완성 글자는 완성된 이름의 앞부분이 된다")
    void incompleteInputBecomesPrefix() {
        String name = JamoConverter.toJamo("고등어");

        assertThat(name).startsWith(JamoConverter.toJamo("고등ㅇ")); // 다음 글자의 초성만 친 상태
        assertThat(name).startsWith(JamoConverter.toJamo("고드")); // 받침을 치기 전 상태
        assertThat(name).startsWith(JamoConverter.toJamo("고ㄷ"));
    }

    @Test
    @DisplayName("영문은 소문자로 통일하고 공백은 그대로 둔다")
    void lowercasesAndKeepsSpaces() {
        assertThat(JamoConverter.toJamo("A2 우유")).startsWith("a2 ᄋ");
    }

    @Test
    @DisplayName("이름에서 초성만 뽑아 공백과 한글이 아닌 글자는 버린다")
    void extractsChosung() {
        assertThat(JamoConverter.toChosung("고등어 구이")).isEqualTo("ㄱㄷㅇㄱㅇ");
        assertThat(JamoConverter.toChosung("A2 우유")).isEqualTo("ㅇㅇ");
    }

    @Test
    @DisplayName("공백을 뺀 입력이 모두 초성이면 초성 검색으로 본다")
    void detectsChosungOnlyInput() {
        assertThat(JamoConverter.isChosungOnly("ㄱㄷㅇ")).isTrue();
        assertThat(JamoConverter.isChosungOnly("ㄱ ㄷ")).isTrue();
    }

    @Test
    @DisplayName("완성형이나 모음이 섞였거나 비어 있으면 초성 검색이 아니다")
    void rejectsMixedInput() {
        assertThat(JamoConverter.isChosungOnly("고ㄷ")).isFalse();
        assertThat(JamoConverter.isChosungOnly("ㅏ")).isFalse();
        assertThat(JamoConverter.isChosungOnly("a")).isFalse();
        assertThat(JamoConverter.isChosungOnly(" ")).isFalse();
    }
}
