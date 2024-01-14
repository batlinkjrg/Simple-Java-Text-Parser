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
    private ConcurrentHashMap<String, ArrayList<StringLocation>> wordRecord;
    private ConcurrentHashMap<Character, Integer> charRecord;

    private String mostUsedWord = null;
    private char mostUsedCharacter = '\0';


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
    private synchronized void addWord_Stats(String word, StringLocation location) {
        if( this.wordRecord.containsKey(word) ) {
            this.wordRecord.get(word).add(location); // Add a key to the list of locations
        } else {
            // Create a new list of locations for this word
            ArrayList<StringLocation> locations = new ArrayList<>();
            locations.add(location);
            this.wordRecord.put(word, locations);
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


    // ****** / Getter Methods / ****** //
    public String getName() {
        return this.fileName;
    }

    public int getCharCount(char character) {
        return this.charRecord.get(character);
    }

    public int getWordCount(String word) {
        return this.wordRecord.get(word).size();
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
            if(this.wordRecord.get(currentString).size() > mostOccurred) {
                mostOccurred = this.wordRecord.get(currentString).size();
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

    public StringSearchInfo searchForString(String string) {
        // First see if the string on its own is in the word record
        if(this.wordRecord.containsKey(string)) 
            return new StringSearchInfo(
                string.length(),
                this.wordRecord.get( string ).toArray( new StringLocation[ this.wordRecord.get( string ).size() ] )
                );
        

        // Now see if it is a single word
        if(LineProcessor.textLineWordCount( string) == 1 ) {
            String[] words = LineProcessor.getWordsList(string);
            ArrayList<String> keysFound = new ArrayList<>();
            ArrayList<StringLocation> locationsFound = new ArrayList<>();

            // Search each key in the set for our word
            this.wordRecord.keySet().forEach(key -> { 
                for(String word : words)
                    if(key.contains(word)) 
                        keysFound.add(key);
            });

            // Check to see if we found anything!
            if(keysFound.isEmpty()) 
                return null;
            

            for( String key : keysFound ) {
                locationsFound.addAll( this.wordRecord.get(key) );
            }

            return new StringSearchInfo(string.length(), locationsFound.toArray(new StringLocation[locationsFound.size()]) );
        }

        // Otherwise now, we will assume it is phrase we are searching for
        String[] wordsInPhrase = LineProcessor.getWordsList(string);
        String leastUsedWord = null;
        int leastUsedWordCount = Integer.MAX_VALUE;

        for(int i = 0; i < wordsInPhrase.length; i++) {
            String currentWord = wordsInPhrase[i];
            if(!this.wordRecord.containsKey(currentWord)) { continue; }
            int wordOccurrences = this.wordRecord.get(currentWord).size();
            if(wordOccurrences < leastUsedWordCount) { leastUsedWordCount = wordOccurrences; leastUsedWord = currentWord; }
        }

        ArrayList<StringLocation> allowedLocations = this.wordRecord.get(leastUsedWord);
        ArrayList<StringLocation> validLocations = new ArrayList<>();

        for (StringLocation stringLocation : allowedLocations) {
            int currentLine = stringLocation.LineNumber;

            String line = buffered_text.get(currentLine-1).lineText;
            
            if(line.contains(string)) { validLocations.add(stringLocation); }
        }

        if(validLocations.isEmpty()) { return null; }

        return new StringSearchInfo(string.length(), validLocations.toArray(new StringLocation[validLocations.size()]));
    }

    public String[] getTextFromLocations(StringSearchInfo searchInfo) {
        ArrayList<String> strings = new ArrayList<>();
        for (StringLocation location : searchInfo.locationSet) {
            String line = buffered_text.get(location.LineNumber-1).lineText;
            int backAmount = 0;
            while (backAmount < location.Column && backAmount < 6) {
                backAmount++;
            }

            int forwardAmount = 0;
            while ( forwardAmount < line.length()-searchInfo.stringLength && forwardAmount < 6 ) {
                forwardAmount++;
            }

            String string = line.substring(location.Column-backAmount, searchInfo.stringLength+forwardAmount);
            strings.add(string);
        }

        return strings.toArray( new String[strings.size()] );
    }

    public static void printStringArray(String[] stringArray) {
        for (int i = 0; i < stringArray.length; i++) {
            System.out.println("[Line Number " + i + "]: " + stringArray[i]);
        }
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

    public final class StringSearchInfo {
        public StringLocation[] locationSet;
        public int stringLength;

        StringSearchInfo(int stringLength, StringLocation[] locations) {
            this.stringLength = stringLength;
            this.locationSet = locationSet;
        }
    }

    // Used to mark first character location in string
    public final class StringLocation {
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
                    StringLocation wordLocation = new StringLocation(firstIndex, lineNumber+1); // plus 1 to line number because text files start at 1 not 0
                    this.parser.addWord_Stats(word, wordLocation);
                }
            }

            this.parser.addProcessorCompleted();
        }

        public static int textLineWordCount(String textLine) {
            int wordCount = 0;

            BreakIterator breakIterator = BreakIterator.getWordInstance();
            breakIterator.setText(textLine);
            int lastIndex = breakIterator.first();
            while (BreakIterator.DONE != lastIndex) {
                int firstIndex = lastIndex;
                lastIndex = breakIterator.next();

                // Get the current word
                if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(textLine.charAt(firstIndex))) {
                    wordCount++;
                }
            }

            return wordCount;
        }

        // For convert a string to a list of words
        public static String[] getWordsList(String string) {
            ArrayList<String> wordsFound = new ArrayList<String>();

            BreakIterator breakIterator = BreakIterator.getWordInstance();
            breakIterator.setText(string);
            int lastIndex = breakIterator.first();

            while (BreakIterator.DONE != lastIndex) {
                int firstIndex = lastIndex;
                lastIndex = breakIterator.next();

                // Get the current word
                if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(string.charAt(firstIndex))) {
                    String word = string.substring(firstIndex, lastIndex);
                    wordsFound.add(word);
                }
            }

            return wordsFound.toArray(new String[wordsFound.size()]);
        }

    }

}