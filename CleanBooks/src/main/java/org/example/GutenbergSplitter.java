package org.example;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GutenbergSplitter implements DocumentSplitter {
    GutenbergFileReader bookReader;
    GuttenbergDatalakeCreator gutenbergDatalakeCreator;
    GutenbergCleaner gutenbergCleaner;
    UTF8FileChecker utf8FileChecker;
    private Map<String, String> extensionDictionary;

    public GutenbergSplitter() {
        extensionDictionary = new HashMap<>();
        extensionDictionary.put("raw", ".txt");
        extensionDictionary.put("content", ".txt");
        extensionDictionary.put("metadata", ".json");
        this.bookReader = new GutenbergFileReader();
        this.gutenbergCleaner = new GutenbergCleaner();
        this.gutenbergDatalakeCreator = new GuttenbergDatalakeCreator();
        this.utf8FileChecker = new UTF8FileChecker();
    }
    @Override
    public void splitDocument(String path) throws IOException {
        String text = bookReader.read(path, "raw");
        String pathToFile = bookReader.getFilePath(path, "raw");
        File file = new File(pathToFile);
        if (utf8FileChecker.isUTF8(file)) {
            String lastNotice = "before using this eBook.";

            int metadataStartIndex = text.indexOf(lastNotice);
            int firstDelimiterIndex = text.indexOf("*** START");
            int secondDelimiterIndex = text.indexOf("***", firstDelimiterIndex + 3);
            int thirdDelimiterIndex = text.indexOf("*** END");

            String metadata = text.substring(metadataStartIndex + lastNotice.length() + 1, firstDelimiterIndex);
            String content = text.substring(secondDelimiterIndex + 4, thirdDelimiterIndex);
            storeFile(path, gutenbergCleaner.cleanText(content), "content");
            storeFile(path, prepareMetadata(metadata), "metadata");
        }
    }

    @Override
    public String prepareMetadata(String text) {
        text = text.trim().replaceAll(" +", " ");
        Pattern pattern = Pattern.compile("([^:]+):\\s*([^\\n]+(?:\\n(?!\\w:)[^\\n]+)*)");

        Matcher matcher = pattern.matcher(text);
        Map<String, String> dictionary = new HashMap<>();
        while (matcher.find()) {
            String field = matcher.group(1).trim();
            String value = matcher.group(2).trim().replaceAll("\\s+", " ");
            dictionary.put(field, value);
        }
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        return gson.toJson(dictionary);
    }

    @Override
    public void storeFile(String name, String content, String type) {
        String path = gutenbergDatalakeCreator.setFilePath(name, new Date(), type);
        String fullpath = path + name + extensionDictionary.get(type);
        try {
            FileWriter writer = new FileWriter(fullpath);
            writer.write(content);
            writer.close();
            System.out.println("File saved successfully at: " + path);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to save the file.");
        }
    }
}

