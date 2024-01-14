import java.io.File;

import TextParser.JavaTextParser;

public class App {

    public static void main(String[] args) throws Exception {
        long startTime = System.nanoTime();

        System.out.println();
        File file = new File("Test_Files/hello.txt");
        JavaTextParser parser = new JavaTextParser(file);
        JavaTextParser.StringSearchInfo locationInfo = parser.searchForString("Deal with");
        
        if(locationInfo == null) {
            System.out.println("Nothing Found");
        } else {
            String[] stringsFound = parser.getTextFromLocations(locationInfo);
            JavaTextParser.printStringArray(stringsFound);
        }



        long endTime = System.nanoTime();

        System.out.println();
        float duration = (((endTime - startTime)/(float)10000000)/(float)100);  //divide by 1000000 to get milliseconds.
        System.out.println("Duration: " + duration + " seconds");
    }
}
