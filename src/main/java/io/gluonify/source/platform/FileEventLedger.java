package io.gluonify.source.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Ledger in {@code /distributed/std}, shared by all replicas. Each event is ONE NEW file, written directly under its final name
 * ({@code sha256(eventId).evt}) with an exclusive create ({@code CREATE_NEW}): exactly one replica wins. Nothing relies on a folder
 * listing (it can lag by ~3 s on another replica), and a file is never rewritten nor renamed.
 */
public class FileEventLedger implements EventLedger {
    private final Path dir;

    public FileEventLedger(Path dir) {
        this.dir = dir;
    }

    @Override
    public boolean firstSeen(String eventId) {
        try {
            Files.createDirectories(dir);
            // hashed: the identifier comes from the network and is never a file name
            Files.writeString(dir.resolve(sha256(eventId) + ".evt"), eventId, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot record the event in " + dir, e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
