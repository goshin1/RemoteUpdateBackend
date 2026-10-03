package com.onpoom.remoteupdate.user;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * 임시 비밀번호 생성기.
 * <p>
 * java.util.Random 은 다음 값을 예측할 수 있어 비밀번호에 쓰면 안 된다.
 * SecureRandom 은 운영체제의 암호학적 난수원을 써서 예측이 불가능하다.
 * <p>
 * 비밀번호 규칙(영문+숫자 포함 8자 이상)을 항상 만족하도록, 영문 1자와 숫자 1자를 먼저 넣고 나머지를 채운 뒤 섞는다.
 * 헷갈리는 문자(0/O, 1/l/I)는 빼서 관리자가 말로 전달하기 쉽게 했다.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] chars = new char[LENGTH];
        chars[0] = LETTERS.charAt(random.nextInt(LETTERS.length()));
        chars[1] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        for (int i = 2; i < LENGTH; i++) {
            chars[i] = ALL.charAt(random.nextInt(ALL.length()));
        }
        // Fisher-Yates 섞기: 첫 두 글자 위치가 항상 영문·숫자로 고정되지 않게
        for (int i = LENGTH - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
