package com.park.ecommerce.text;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

// 자동완성에서 입력이 상품명의 어느 단어 시작부터 이어지는지 판정하기 위한 값을 만듦
// 상품명이 "[브랜드] ..."로 시작하여, 이름 전체의 시작은 거의 일치하지 않으므로 단어마다 시작점을 두도록 함
public final class WordStarts {
    // 문자(자모 결합 문자 포함)와 숫자가 아닌 것을 단어 경계로 봄 - "[바다소리]", "고소&아삭한"의 기호도 경계
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");

    private WordStarts() {}

    // 단어마다 그 단어부터 끝까지를 공백,기호 없이 붙인 값 - 입력이 이 값들 중 하나의 앞부분이면 단어 시작부터 이어진 것
    public static List<String> of(String text) {
        List<String> words = words(text);
        return IntStream.range(0, words.size())
                .mapToObj(i -> String.join("", words.subList(i, words.size())))
                .toList();
    }

    // 입력도 같은 규칙으로 붙여 띄어쓰기와 기호에 관계없이 비교되게 함
    public static String compact(String text) {
        return String.join("", words(text));
    }

    private static List<String> words(String text) {
        return Arrays.stream(NON_WORD.split(text.toLowerCase(Locale.ROOT)))
                .filter(word -> !word.isEmpty())
                .toList();
    }
}
