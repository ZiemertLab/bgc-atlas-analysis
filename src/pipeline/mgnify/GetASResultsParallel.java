package pipeline.mgnify;

import dbutil.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.*;

public class GetASResultsParallel {

    public static final int N_THREADS = 4;
    static Database database = new Database();

    public static void main(String[] args) {
        String resDir = args[0];
        String regionsOut = args[1];
        analyzeResults(resDir, regionsOut);
    }

    private static void analyzeResults(String resDir, String regionsOut) {
        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        List<String> finishedRuns = database.getAllFinishedRuns();
        System.out.println("Total finished runs: " + finishedRuns.size());

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(regionsOut), true))) {
            for (String run : finishedRuns) {
                AnalysisChecker checker = new AnalysisChecker(resDir, run, bw);
                executor.submit(checker);
            }
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS); // Wait for all tasks to finish
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            System.err.println("Unhandeled exception: " + t.getMessage());
            t.printStackTrace();
        }
    }


    static class AnalysisChecker implements Runnable {

        private final String resDir;
        private final String run;
        private final BufferedWriter bw;

        public AnalysisChecker(String resDir, String run, BufferedWriter bw) {
            this.resDir = resDir;
            this.run = run;
            this.bw = bw;
        }

        @Override
        public void run() {
            try {
//                System.out.println("Analyzing " + run);
                File antismashDir = new File(resDir + File.separator + run + File.separator + "antismash");

                File regionsFile = new File(antismashDir + File.separator + "regions.js");

                if(!regionsFile.exists()) {
                    System.out.println("No regions file for " + run);
                    return;
                }

                String content = new String(Files.readAllBytes(Paths.get(antismashDir + File.separator + "regions.js")));

//                System.out.println(content);

                JSONArray records = new JSONArray(content.replace("var recordData = ", ""));

//                System.out.println(records.length());

                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    JSONArray regions = record.getJSONArray("regions");
                    if(regions.length() == 0)
                        continue;
                    String recordName = record.getString("seq_id");
                    int length = record.getInt("length");
                    for (int j = 0; j < regions.length(); j++) {
                        JSONObject region = regions.getJSONObject(j);
//                        JSONArray orfs = region.getJSONArray("orfs"); //get orfs when needed
                        JSONArray productCategories = region.getJSONArray("product_categories");
                        String anchor = region.getString("anchor");
                        int start = region.getInt("start");
                        int end = region.getInt("end");
                        String type = region.getString("type");
                        JSONArray clusters = region.getJSONArray("clusters");
                        JSONArray products = region.getJSONArray("products");
                        ArrayList<String> productArr = new ArrayList<>();
                        products.forEach(p -> productArr.add(p.toString()));

                        boolean isContigEdge = false;
                        if(start == 1 || end == length)
                            isContigEdge = true;

//                        database.insertRegion(run, recordName, length, productCategories.toString(), anchor, start, end, isContigEdge, type, productArr.toString(), (j+1));

                        String productCategoriesString = "{" + productCategories.join(",") + "}";
                        String productArrString = "{" + String.join(",", productArr) + "}";

                        bw.write(run + "\t" + recordName + "\t" + length + "\t" + productCategoriesString + "\t" +
                                anchor + "\t" + start + "\t" + end + "\t" + isContigEdge + "\t" + type + "\t" +
                                productArrString + "\t" + (j+1) + "\n");

//
//                        System.out.println(run + "\t" + recordName + "\t" + length + "\t" + productCategories.toString() + "\t" + anchor + "\t" + start + "\t" + end + "\t" + type + "\t" + clusters.toString() + "\t" + productArr.toString());
//                        bw.write(run + "\t" + recordName + "\t" + length + "\t" + productCategories.toString() + "\t" + anchor + "\t" + start + "\t" + end + "\t" + isContigEdge + "\t" + type + "\t" + clusters.toString() + "\t" + productArr.toString() + "\t" + (j+1));
//                        bw.write(run + "\t" + recordName + "\t" + length + "\t" + productCategories.toString() + "\t" + anchor + "\t" + start + "\t" + end + "\t" + isContigEdge + "\t" + type + "\t" + productArr.toString() + "\t" + (j+1));
//                        bw.newLine();
                        bw.flush();

                    }
                }
            } catch (Throwable t) {
                System.err.println("Unhandeled exception: " + t.getMessage());
                t.printStackTrace();
            }
        }


    }

}
