package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class Controller {
    private final GutenbergSplitter splitter;
    private final MissingFilesManagement missingFilesManagement;
    private final SimpleDateFormat dateFormat;

    public Controller() {
        this.splitter = new GutenbergSplitter();
        this.missingFilesManagement = new MissingFilesManagement();
        this.dateFormat = new SimpleDateFormat("yyyyMMdd");
    }

    public void execute() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Date currentDate = new Date();
            Path datalakePath = Paths.get("datalake", dateFormat.format(currentDate));
            datalakePath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();

                        System.out.println("BookId: " + fileName);
                        String folderName = fileName.toString();
                        sleep(15000);
                        splitter.splitDocument(folderName);
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void startListening() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path parentDirectory = Paths.get("datalake");

            parentDirectory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE
            );

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();

                        String folderName = fileName.toString();
                        if (isyyyyMMddFormat(folderName)) {
                            System.out.println("Detected folder: " + folderName);
                            activateExecution();
                        }
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean isyyyyMMddFormat(String folderName) {
        try {
            dateFormat.parse(folderName);
            return folderName.length() == 8;
        } catch (Exception e) {
            return false;
        }
    }

    private static void activateExecution() {
        Controller controller = new Controller();
        controller.execute();
    }

    public void checkTodayFolderAndExecute() {
        Date currentDate = new Date();
        String folderName = dateFormat.format(currentDate);

        Path todayFolder = Paths.get("datalake", folderName);

        if (Files.exists(todayFolder) && Files.isDirectory(todayFolder)) {
            activateExecution();
        } else {
            startListening();
        }
    }

    public void scheduleMissingFilesCheck() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                missingFilesManagement.splitMissingFiles();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 24, 24, TimeUnit.HOURS);
    }

    public void datalakeChecker() {
        Path datalakeFolder = Paths.get("datalake");
        if (!Files.exists(datalakeFolder)) {
            try {
                Files.createDirectory(datalakeFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
