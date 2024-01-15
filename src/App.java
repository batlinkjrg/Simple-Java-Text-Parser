import java.io.File;

import TextParser.JavaTextParser;

public class App {

    public static void main(String[] args) throws Exception {
        long startTime = System.nanoTime();

        System.out.println();
        File file = new File("Test_Files/hello.txt");
        JavaTextParser parser = new JavaTextParser(file);
        int usedChar = parser.getCharCount('a');
        String word = parser.getMostUsedWord();

        System.out.println();
        System.out.println("Most Used Word: " + word);
        System.out.println("Occurrences of A: " + usedChar);

        String[] foundLines = parser.searchForString_partialLine("for (", 6);
        if(foundLines == null) {
            System.out.println("Phrase not found!");
        } else {
            System.out.println("Phrase Found: " + foundLines.length);
        }

        String[] foundLines2 = parser.searchForString_fullLine("for (");
        if(foundLines == null) {
            System.out.println("Phrase not found!");
        } else {
            System.out.println("Phrase Found: " + foundLines2.length);
        }
        

        long endTime = System.nanoTime();

        System.out.println();
        float duration = (((endTime - startTime)/(float)10000000)/(float)100);  //divide by 1000000 to get milliseconds.
        System.out.println("Duration: " + duration + " seconds");
    }
}
