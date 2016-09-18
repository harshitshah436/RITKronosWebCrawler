/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;

/**
 *
 * @author Harshit
 */
public class WebCrawler {

    public static String kronos_url, kronos_current_timeperiod_url, username, password;

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {

        System.out.println(Paths.get(".").toAbsolutePath().normalize().toString());
        getPropValues();

        Connection.Response loginForm = Jsoup.connect(kronos_url)
                .method(Connection.Method.GET)
                .execute();

        Connection.Response res = Jsoup.connect(kronos_url)
                .data("cookieexists", "false")
                .data("username", username)
                .data("password", password)
                .cookies(loginForm.cookies())
                .method(Method.POST)
                .execute();

        String json = Jsoup.connect(kronos_current_timeperiod_url)
                .ignoreContentType(true)
                .cookies(res.cookies())
                .execute()
                .body();

        // System.out.println(json);
        JSONParser parser = new JSONParser();

        try {
            JSONObject obj = (JSONObject) parser.parse(json);

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy");
            SimpleDateFormat sdf1 = new SimpleDateFormat("hh:mm a");

            Date start_date = new Date((long) obj.get("start_date"));
            Date end_date = new Date((long) obj.get("end_date"));

            JSONArray array = (JSONArray) obj.get("summaries");
            double wages = 0, total_time = 0;
            for (Object array1 : array) {
                JSONObject obj1 = (JSONObject) array1;
                wages += Double.parseDouble(obj1.get("wageamount").toString());
                if (obj1.get("name").toString().equalsIgnoreCase("Holiday RIT") && Double.parseDouble(obj1.get("duration").toString()) > 0 && Double.parseDouble(obj1.get("wageamount").toString()) > 0) {
                    total_time += Double.parseDouble(obj1.get("duration").toString());
                }
            }
            
            System.out.println();
            System.out.println(sdf.format(start_date) + " to " + sdf.format(end_date) + "\t Wages: $" + String.format("%.2f", wages));
            System.out.println();
            if(total_time > 0) {
                System.out.println("RIT Holiday hours: " + String.format("%6s", formatSeconds((int) total_time)));
                System.out.println();
            }
            System.out.println("========================================================");
            System.out.println("PUNCHES");
            System.out.println("========================================================");
            System.out.print(String.format("%12s", "Date"));
            System.out.print(String.format("%12s", "In Punch"));
            System.out.print(String.format("%12s", "Out Punch"));
            System.out.print(String.format("%10s", "Shift"));
            System.out.println(String.format("%10s", "Total"));

            //System.out.println(obj.get("punchlist"));
            boolean shift_over = false, new_shift = false;
            double today_time = 0;
            long in_time = 0;
            array = (JSONArray) obj.get("punchlist");
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj2 = (JSONObject) array.get(i);

                String startreason = (String) obj2.get("startreason");

                in_time = (long) obj2.get("in_datetime");

                if (startreason.equalsIgnoreCase("newShift")) {
                    System.out.println("--------------------------------------------------------");
                    today_time = 0;
                    new_shift = true;
                } else {
                    new_shift = false;
                }

                System.out.print(String.format("%12s", obj2.get("applydate")));

                Date in_datetime = new Date(in_time);
                System.out.print(String.format("%12s", sdf1.format(in_datetime)));

                long out_time = (long) obj2.get("out_datetime");
                Date out_datetime = new Date(out_time);
                double duration = (double) obj2.get("duration");
                if (obj2.get("endreason").equals("out")) {
                    System.out.print(String.format("%12s", sdf1.format(out_datetime)));
                    System.out.print(String.format("%10s", formatSeconds((int) duration)));
                    today_time += duration;
                    shift_over = true;
                } else {
                    System.out.print(String.format("%12s", ""));
                    System.out.print(String.format("%10s", ""));
                    shift_over = false;
                }

                total_time += duration;
                System.out.print(String.format("%10s", formatSeconds((int) total_time)));

                System.out.println();
            }
            System.out.println("--------------------------------------------------------");

            double time_40 = total_time > 144000 ? total_time - 144000 : total_time;
            System.out.println();
            System.out.print("Time to 40 hours: " + formatSeconds((int) (144000 - time_40)) + "\t\t");
            System.out.println("Time to 80 hours: " + formatSeconds((int) (288000 - total_time)) + "\t");
            if (144000 - time_40 <= 21600) {
                System.out.println();
                System.out.println("You should clock out at: " + sdf1.format(new Date((long) (in_time + (144000 - time_40) * 1000))));
            } else if (288000 - total_time <= 21600) {
                System.out.println();
                System.out.println("You should clock out at: " + sdf1.format(new Date((long) (in_time + (288000 - total_time) * 1000))));
            }

            if (!shift_over) {
                System.out.println();
                System.out.println("Total time worked today as of now (hours and minutes): " + formatSeconds((int) (today_time + (System.currentTimeMillis() - in_time) / 1000)));
                if (new_shift) {
                    System.out.println();
                    System.out.println("You should take a break before: " + sdf1.format(new Date((long) (in_time + (21600) * 1000))));
                }
            }

            System.out.println();
            System.out.print("Enter number of hours you want to work today (0 to exit): ");
            Scanner in = new Scanner(System.in);
            double input = in.nextDouble() * 3600;
            if (input > 0) {
                if (input > today_time) {
                    System.out.println();
                    System.out.println("You should clock out at: " + sdf1.format(new Date((long) (in_time + (input - today_time) * 1000))));
                } else {
                    System.out.println();
                    System.err.println("You have already completed entered number of hours.");
                }
            } else {
                System.exit(0);
            }

        } catch (ParseException pe) {

            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
        }
    }

    public static String formatSeconds(int timeInSeconds) {
        int hours = timeInSeconds / 3600;
        int secondsLeft = timeInSeconds - hours * 3600;
        int minutes = secondsLeft / 60;
        //int seconds = secondsLeft - minutes * 60;

        String formattedTime = "";
        if (hours < 10) {
            formattedTime += "0";
        }
        formattedTime += hours + ":";

        if (minutes < 10) {
            formattedTime += "0";
        }
        formattedTime += minutes;// + ":";

//        if (seconds < 10) {
//            formattedTime += "0";
//        }
//        formattedTime += seconds;
        return formattedTime;
    }

    private static void getPropValues() throws IOException {
        InputStream inputStream;
        try {
            Properties prop = new Properties();
            String propFileName = "config.properties";

            inputStream = new FileInputStream(propFileName);

            prop.load(inputStream);

            kronos_url = prop.getProperty("kronos_url");
            kronos_current_timeperiod_url = prop.getProperty("kronos_current_timeperiod_url");
            username = prop.getProperty("username");
            password = prop.getProperty("password");
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

    }
}
