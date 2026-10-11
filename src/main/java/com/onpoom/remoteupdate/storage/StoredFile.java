package com.onpoom.remoteupdate.storage;

/**
 * 저장된 파일 정보.
 *
 * @param key          서버 저장 키 (DB 에 저장, 클라이언트에는 노출하지 않음)
 * @param originalName 사용자가 올린 원본 파일명 (경로 제거)
 * @param size         바이트 수
 * @param sha256       SHA-256 체크섬 (소문자 hex 64자)
 */
public record StoredFile(String key, String originalName, long size, String sha256) {
}
