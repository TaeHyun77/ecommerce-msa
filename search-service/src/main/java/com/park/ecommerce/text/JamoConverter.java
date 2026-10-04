package com.park.ecommerce.text;

import java.text.Normalizer;
import java.util.Locale;

// 미완성 글자("고등ㅇ")와 초성("ㄱㄷㅇ")을 완성된 이름과 비교할 수 있게 자모로 바꿈
// 이름을 자모로 풀고(NFKD), 초성을 추출하고, 초성 전용 입력인지 판정
public final class JamoConverter {
    private static final char SYLLABLE_START = '가'; // '가'
    private static final char SYLLABLE_END = '힣'; // '힣'
    private static final int SYLLABLES_PER_CHOSUNG = 21 * 28; // 중성 21개 × 종성 28개
    private static final String CHOSUNGS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final char COMPAT_CONSONANT_START = 'ㄱ'; // 'ㄱ'
    private static final char COMPAT_CONSONANT_END = 'ㅎ'; // 'ㅎ'

    private JamoConverter() {}

    // 한글을 자모 단위로 풀어서, 고등ㅇ 같은 미완성 입력도 고등어와 앞부분이 일치하도록
    public static String toJamo(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
    }

    // 초성 검색용 - 한글이 아닌 글자와 공백은 버려서 "A2 우유"도 "ㅇㅇ"으로 찾도록 함
    public static String toChosung(String text) {
        StringBuilder chosung = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= SYLLABLE_START && c <= SYLLABLE_END) {
                chosung.append(CHOSUNGS.charAt((c - SYLLABLE_START) / SYLLABLES_PER_CHOSUNG));
            } else if (isCompatConsonant(c)) {
                chosung.append(c);
            }
        }
        return chosung.toString();
    }

    // 입력이 초성 자음으로만 이루어졌는지 여부
    public static boolean isChosungOnly(String text) {
        String compact = text.replaceAll("\\s+", "");
        return !compact.isEmpty() && compact.chars().allMatch(c -> isCompatConsonant((char) c));
    }

    // 글자 하나가 키보드로 친 낱자 자음인지 여부
    private static boolean isCompatConsonant(char c) {
        return c >= COMPAT_CONSONANT_START && c <= COMPAT_CONSONANT_END;
    }
}
