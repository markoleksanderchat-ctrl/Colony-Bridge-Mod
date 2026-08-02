package com.colonybridge.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    public static void writeUtf8(Path destination, String content) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException("Destination has no parent: " + destination);
        }
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("Refusing to write through symbolic link: " + parent);
        }
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing to replace symbolic link: " + destination);
        }

        Path temp = parent.resolve(destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicMoveUnsupported) {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static boolean isRegularChild(Path root, Path child) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        return !normalizedChild.equals(normalizedRoot)
                && normalizedChild.startsWith(normalizedRoot)
                && !Files.isSymbolicLink(normalizedChild)
                && Files.isRegularFile(normalizedChild, LinkOption.NOFOLLOW_LINKS);
    }
}
