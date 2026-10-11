package com.onpoom.remoteupdate.storage;

/** 저장 위치 구분 (저장 키의 첫 경로) */
public enum FileCategory {
    UPDATES("updates"),
    GUIDES("guides");

    private final String directory;

    FileCategory(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
