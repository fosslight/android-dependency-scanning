package com.lge.android.licensetools;

public class NotEnoughInformationException extends RuntimeException {
    public final LibraryInfo libraryInfo;

    public NotEnoughInformationException(LibraryInfo libraryInfo) {
        this.libraryInfo = libraryInfo;
    }
}
