package TextParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Documented;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.management.DescriptorKey;
import javax.sound.sampled.Line;

import java.util.concurrent.ConcurrentHashMap;

public final class JavaTextParser {

    // Buffer
    protected String fileName;
    private ArrayList<TextLine> buffered_text; // Remember that the lines will be indexed from 1 line a real text file would be

    // LineParser Executioner
    private int threadsAvailable = Runtime.getRuntime().availableProcessors();
    private int completeLinesProcessed = 0;

    // Statistics
    private ConcurrentHashMap<String, Integer> wordRecord;
    private ConcurrentHashMap<Character, Integer> charRecord;

    private String mostUsedWord = null;
    private char mostUsedCharacter = '\0';

    // For searching 
    ArrayList<StringLocation> locations = null;


    // ****** / Constructors / ****** // // TODO: Add a constructor that takes in a string
    // Default
    public JavaTextParser(String filePath) {
        File file = new File(filePath);

        // Check to see if the file exists first
        if(!file.exists()) {
            try {
                throw new IOException("[JavaTextParser]: This file does not exist!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.fileName = file.getName();
        this.buffered_text = fileToTextBuffer(file);
        gatherInfo();
    }

    // File Constructor
    public JavaTextParser(File textFile) {
        // Check to see if the file exists first
        if(!textFile.exists()) {
            try {
                throw new IOException("[JavaTextParser]: This file does not exist!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.fileName = textFile.getName();
        this.buffered_text = fileToTextBuffer(textFile);
        gatherInfo();
    }

    // Stream Parser
    public JavaTextParser(String name, InputStream textInput) {
        this.fileName = name;
        try {
            this.buffered_text = inputStreamToTextBuffer(textInput);
            gatherInfo();
        } catch (InvalidText e) {
            System.err.println("[JavaTextParser]: Error reading text from input stream!");
        }        
    }


    // ****** / Loader Methods / ****** //
    // Convert input stream to text buffer
    private ArrayList<TextLine> inputStreamToTextBuffer(InputStream inputStream) throws InvalidText {
        ArrayList<TextLine> textBuffer = new ArrayList<>();

        // Prepare buffer to assist larger files!
        BufferedInputStream bufferedIn = new BufferedInputStream(inputStream);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(bufferedIn));

        // Extract text
        try {
            String line = bufferedReader.readLine();
            System.out.println("Loading Buffer...");

            int lineCounter = 0;
            while(line != null) {
                TextLine textLine = new TextLine(lineCounter, line);
                textBuffer.add(textLine);
                lineCounter++; // Count up which line we are currently one!
                line = bufferedReader.readLine();
                System.out.print("Lines Found: " + lineCounter + '\r');
            } System.out.println();

            System.out.println("Finished Loading Buffer!");
        } catch (IOException e) {
            throw new InvalidText("[JavaTextParser]: This text is not supported!");
        }

        return textBuffer;
    }

    // Convert file to text buffer
    private ArrayList<TextLine> fileToTextBuffer(File file) {
        // Try to get file input stream
        try {
            InputStream fileIn = new FileInputStream(file.getAbsolutePath());
            return inputStreamToTextBuffer(fileIn);
        } catch (FileNotFoundException e) {
            System.err.println("[JavaTextParser]: Your File is unable to be found!");
            System.exit(-1);
        } catch (InvalidText e) {
            System.err.println("[JavaTextParser]: Error Extracting Text!!");
        }

        // Return empty array list if anything else
        return new ArrayList<>();
    }

    // Cache Statistics on loaded file
    private void gatherInfo() {
        // Initialize Storage!
        wordRecord = new ConcurrentHashMap<>();
        charRecord = new ConcurrentHashMap<>(26); // Letters in the alphabet, which we know is 26 ;)

        // Initialize Threads
        System.out.println("Parsing File " + this.fileName + "...");
        this.completeLinesProcessed = 0;
        ExecutorService lineProcessorThreads = Executors.newFixedThreadPool(this.threadsAvailable/2); // Use half of the available threads to not overbear the computer

        // Set up the line parsers
        int createdLineProcessors = 0;
        for (TextLine line : buffered_text) {
            LineProcessor lineProcessor = new LineProcessor(this, line);
            lineProcessorThreads.submit(lineProcessor);
            createdLineProcessors++;
        }

        // Start the phase of waiting for all threads to complete
        lineProcessorThreads.shutdown();

        while(this.completeLinesProcessed < createdLineProcessors) {
            double percentage = Math.floor( ( (this.completeLinesProcessed / (double) createdLineProcessors)*100 ) * 100 ) / 100;
            System.out.print("Progress: " + percentage + "%" + '\r');
        } System.out.print('\r' + "                                 "); // Clear line

        System.out.print('\r' + "Done: 100%" + '\n');
        System.out.println("File has been fully parsed!");
    }


    // ****** / Private Getters/Setters / ****** //
    // Add word to record
    private synchronized void addWord_Stats(String word) {
        if( this.wordRecord.containsKey(word) ) {
            int newCount = this.wordRecord.get(word) + 1;
            this.wordRecord.put(word, newCount); // Add a key to the list of locations
        } else {
            // Create a new list of locations for this word
            this.wordRecord.put(word, 1);
        }
    }

    // Add characters to the record
    private synchronized void addCharacter_Stats(char letter) {
        if( this.charRecord.containsKey(letter) ) {
            int newCount = this.charRecord.get(letter) + 1; // Add location to list
            this.charRecord.put(letter, newCount);
        } else {
            // Add the new found character to the array
            this.charRecord.put(letter, 1);
        }
    }

    // Add to total complete processes
    private synchronized void addProcessorCompleted() {
        this.completeLinesProcessed++;
    }

    // Add to arrayListSearch
    private synchronized void addStringLocation_StringSearch(ArrayList<StringLocation> locationSet) {
        this.locations.addAll(locationSet);
    }

    // ****** / Getter Methods / ****** //
    public String getName() {
        return this.fileName;
    }

    public int getCharCount(char character) {
        return this.charRecord.get(character);
    }

    public int getWordCount(String word) {
        return this.wordRecord.get(word);
    }

    public String getMostUsedWord() { 
        // See if we already got the most used word or not
        if(this.mostUsedWord != null) {
            return this.mostUsedWord;
        }
        
        int mostOccurred = 0;
        int total = this.wordRecord.size();
        int searched = 0;

        System.out.println("Finding most used word...");
        Enumeration<String> stringSet = this.wordRecord.keys();
        while( stringSet.hasMoreElements() ) {
            String currentString = stringSet.nextElement();
            if(this.wordRecord.get(currentString) > mostOccurred) {
                mostOccurred = this.wordRecord.get(currentString);
                this.mostUsedWord = currentString;
            }

            searched++;
            double percentage = Math.floor( ( (searched / (double) total)*100 ) * 100 ) / 100;
            System.out.print("Progress: " + percentage + "%" + '\r');
        } System.out.print('\r' + "                                 " + '\r'); // Clear line
        System.out.println("Found most used Word!");

        return this.mostUsedWord;
    }

    public char getMostUsedCharacter() {
        // See if we already got the most used word or not
        if(this.mostUsedCharacter != '\0' && this.mostUsedCharacter != ' ') {
            return this.mostUsedCharacter;
        }
        
        int mostOccurred = 0;
        int total = this.wordRecord.size();
        int searched = 0;

        System.out.println("Finding most used word...");
        Enumeration<Character> stringSet = this.charRecord.keys();
        while( stringSet.hasMoreElements() ) {
            char currentChar = stringSet.nextElement();
            
            if(currentChar == ' ') { continue; }
            if(this.charRecord.get(currentChar) > mostOccurred) {
                mostOccurred = this.charRecord.get(currentChar);
                this.mostUsedCharacter = currentChar;
            }

            searched++;
            double percentage = Math.floor( ( (searched / (double) total)*100 ) * 100 ) / 100;
            System.out.print("Progress: " + percentage + "%" + '\r');
        } System.out.print('\r' + "                                 " + '\r'); // Clear line
        System.out.println("Found most used Word!");

        return this.mostUsedCharacter;
    }

    public String[] searchForString_partialLine(String string, int surrounding) {

        this.locations = new ArrayList<>();
        ExecutorService searchingService = Executors.newFixedThreadPool(threadsAvailable/2);

        // For each line, submit a searching task
        buffered_text.forEach(line -> { 
            searchingService.submit(new Runnable() {

                @Override
                public void run() {
                    boolean isFound = LineProcessor.searchLine(string, line.lineText);
                    if(isFound) {
                        addStringLocation_StringSearch(LineProcessor.getStringLocations(string, line));
                    }
                }
                
            });
        });

        searchingService.shutdown();
        System.out.println("Searching for text...");
        try {
            searchingService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.err.println("Failed to await thread completion!!");
            e.printStackTrace();
        }

        // Check to see if anything was found
        if(this.locations.isEmpty()) {
            this.locations = null;
            return null;
        }

        ArrayList<String> strings = new ArrayList<>();
        for (StringLocation stringLocation : this.locations) {
            String line = buffered_text.get(stringLocation.LineNumber).lineText;

            // Set up parameter variables
            int endParameter = 0;
            int beginParameter = 0;
            int beginIndex = stringLocation.Column-beginParameter;
            int endIndex = stringLocation.Column+string.length()+endParameter;

            // Set beginning and end
            while(endIndex < line.length() && endParameter < surrounding) {
                endParameter++;
                endIndex = stringLocation.Column+string.length()+endParameter;
            }
                
            while(beginIndex > 0 && beginParameter < surrounding) {
                beginParameter++;
                beginIndex = stringLocation.Column-beginParameter;
            }
                
            String stringOccurrence = line.substring(beginIndex, endIndex);

            // Add dots ;)
            if(beginIndex != 0) 
                stringOccurrence = "..." + stringOccurrence;
            

            if(endIndex != line.length()) 
                stringOccurrence = stringOccurrence + "...";
            
            strings.add(stringOccurrence);
        }

        this.locations = null;
        return strings.toArray( new String[strings.size()] );
    }

    public String[] searchForString_fullLine(String string) {
        this.locations = new ArrayList<>();

        ExecutorService searchingService = Executors.newFixedThreadPool(threadsAvailable/2);

        // For each line, submit a searching task
        buffered_text.forEach(line -> { 
            searchingService.submit(new Runnable() {

                @Override
                public void run() {
                    boolean isFound = LineProcessor.searchLine(string, line.lineText);
                    if(isFound) {
                        addStringLocation_StringSearch(LineProcessor.getStringLocations(string, line));
                    }
                }
                
            });
        });

        searchingService.shutdown();
        System.out.println("Searching for text...");
        try {
            searchingService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.err.println("Failed to await thread completion!!");
            e.printStackTrace();
        }

        // Check to see if anything was found
        if(this.locations.isEmpty()) {
            this.locations = null;
            return null;
        }

        ArrayList<String> strings = new ArrayList<>();
        for (StringLocation stringLocation : this.locations) {
            String line = buffered_text.get(stringLocation.LineNumber).lineText;
            strings.add(line);
        }

        this.locations = null;
        return strings.toArray( new String[strings.size()] );
    }


    // ****** / Custom Data Types / ****** //
    // Used to record a line of text
    private class TextLine {
        public int lineNumber;
        public String lineText;

        public TextLine(int LineNumber, String LineText) {
            this.lineNumber = LineNumber;
            this.lineText = LineText;
        }
    }

    // Used to mark first character location in string
    public final static class StringLocation {
        public int Column;
        public int LineNumber;

        public StringLocation() {
            this.Column = 0;
            this.LineNumber = 0;
        }

        public StringLocation(int Column, int LineNumber) {
            this.Column = Column;
            this.LineNumber = LineNumber;
        }
    }

    // Custom Exceptions
    public class InvalidText extends Exception { 
        public InvalidText(String errorMessage) {
            super(errorMessage);
        }
    }

    // Additional Classes Needed
    private final class LineProcessor extends Thread {

        private JavaTextParser parser;
        private TextLine line;

        private int lineNumber;

        LineProcessor(JavaTextParser textParser, TextLine textLine) {
            this.parser = textParser;
            this.line = textLine;
            this.lineNumber = textLine.lineNumber;
        }

        // Debug Note: Remember arrays start at index 0, but columns in text files start at 1
        @Override
        public void run() {

            /*  
            *  // Legend Name: ninhjs-dev 
            *  // From: https://stackoverflow.com/questions/4674850/converting-a-sentence-string-to-a-string-array-of-words-in-java#4674887 //  
            */

            BreakIterator breakIterator = BreakIterator.getWordInstance();
            breakIterator.setText(line.lineText);
            int lastIndex = breakIterator.first();
            while (BreakIterator.DONE != lastIndex) {
                int firstIndex = lastIndex;
                lastIndex = breakIterator.next();

                // Get the current character
                if(firstIndex < line.lineText.length()) {
                    char currentChar = line.lineText.charAt(firstIndex);
                    this.parser.addCharacter_Stats(currentChar);
                }

                // Get the current word
                if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(line.lineText.charAt(firstIndex))) {
                    String word = line.lineText.substring(firstIndex, lastIndex);
                    this.parser.addWord_Stats(word);
                }
            }

            this.parser.addProcessorCompleted();
        }

        // Search Line
        public static boolean searchLine(String string, String line) {
            if(line.contains(string)) 
                return true;

            return false;
        }

        // Get word locations on line
        public static ArrayList<StringLocation> getStringLocations(String string, TextLine text) {
            String line = text.lineText;
            ArrayList<StringLocation> locations = new ArrayList<>();

            while( line.length() > string.length() ) {
                if( !line.contains(string) ) // First see if the string is in here
                    break;
                
                int indexOfWord = line.indexOf(string);
                StringLocation location = new StringLocation(indexOfWord, text.lineNumber);
                locations.add(location);
                line = line.substring(indexOfWord+string.length());
            }

            return locations;
        }
    }

}