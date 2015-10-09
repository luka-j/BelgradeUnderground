package rs.luka.belgradeunderground.io;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.data.Base;
import rs.luka.belgradeunderground.Main;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Scanner;

/**
 * Quick and dirty. Stedi par sekundi.
 * Ako je ukljucen, menjanje parametara vezanih za vrednosti koje se racunaju pre ulaza nece imati efekta
 * (npr. walking, smerovi kretanja linija, expensiveSpots/isExpensive)
 * Created by luka on 17.9.15.
 */
public class BaseIO {
    public static final String TYPE_SEPARATOR = "$";
    public static final String OBJECT_SEPARATOR = "\n";
    public static final String FIELD_SEPARATOR = "#"; //jer je GSP bio toliko ljubazan da mi uzme '/'
    public static final String ARRAY_SEPARATOR = ",";
    public static final String MINOR_SEPARATOR = ":";
    private static File baseFile = new File(UserIO.getHomeDir(), "cache");

    public static void saveBase(Base base) {
        //long start = System.currentTimeMillis();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(baseFile));
            Iterator<Station> stations = base.getStationIterator();
            while(stations.hasNext())
                writer.append(stations.next().toMachineString()).append(OBJECT_SEPARATOR);
            writer.append(TYPE_SEPARATOR);
            Iterator<Line> lines = base.getLineIterator();
            while(lines.hasNext())
                writer.append(lines.next().toMachineReadableString()).append(OBJECT_SEPARATOR);
            writer.close();
        } catch (IOException e) {
            if(Config.DEBUG) e.printStackTrace();
        }
        //long end = System.currentTimeMillis();
        //System.out.println("saving base done. Time: " + (end-start)/1000.0);
        if(Config.RUN_SAVEBASE_IN_SEPARATE_THREAD)
            Thread.currentThread().interrupt(); //nece da lepo izadje iz threada u executoru u vecini slucajeva
            //verovatno ne radim nesto kako treba
            //todo pronaci razlog
    }

    public static void loadBase(Base base) throws IOException {
        Scanner scan = new Scanner(baseFile);
        scan.useDelimiter("\\$");
        String[] stationStrs = scan.next().split(OBJECT_SEPARATOR);
        for(String station : stationStrs)
            base.addStation(new Station(station));
        scan.nextByte(); //pretpostavljam da je TYPE_SEPARATOR 1 bajt sirok
        scan.useDelimiter("\\Z");
        String[] lines = scan.next().split(OBJECT_SEPARATOR);
        scan.close();
        for(String line : lines)
            base.addLine(new Line(line));
        Iterator<Station> stations = base.getStationIterator();
        while(stations.hasNext()) stations.next().loadLinks();
    }

    public static boolean exists() {
        return baseFile.exists();
    }

    public static void removeFile() {
        baseFile.delete();
    }
}
