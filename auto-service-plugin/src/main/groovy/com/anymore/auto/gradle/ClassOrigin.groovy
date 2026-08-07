package com.anymore.auto.gradle

final class ClassOrigin {
    final String className
    final String containerName
    final String entryName
    final String contentHash

    ClassOrigin(String className, String containerName, String entryName, String contentHash) {
        this.className = className
        this.containerName = containerName
        this.entryName = entryName
        this.contentHash = contentHash
    }

    boolean sameInput(ClassOrigin other) {
        other != null &&
                containerName == other.containerName &&
                entryName == other.entryName &&
                contentHash == other.contentHash
    }

    String displayName() {
        "${containerName}!/${entryName}"
    }
}
