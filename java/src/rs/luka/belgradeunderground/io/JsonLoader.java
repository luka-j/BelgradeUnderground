package rs.luka.belgradeunderground.io;

import com.google.gson.stream.JsonReader;
import rs.luka.belgradeunderground.data.Base;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ucitava podatke iz JSON fajlova. Pisano prema primerima iz dokumentacije (prilicno defanzivno).
 * Sto se spomenutih nepravilnosti tice, nisam ih primetio, jedino ako se kruzne linije racunaju u to. Sva sreca pa
 * nisam morao da brinem da li je slovo 'o' na cirilici.
 * Created by luka on 14.9.15..
 */
public class JsonLoader {

    private static final String LINES_FILENAME = "lines.txt";
    private static final String STATIONS_FILENAME = "st_names.txt";
    private static final String LOCATIONS_FILENAME = "st_locations.txt";

    public static Base load() {
        Base base = Base.getInstance();
        File workingDir = UserIO.getHomeDir();
        try {
            loadStations(base, new JsonReader(new FileReader(new File(workingDir, STATIONS_FILENAME))));
            loadLocations(base, new JsonReader(new FileReader(new File(workingDir, LOCATIONS_FILENAME))));
            loadLines(base, new JsonReader(new FileReader(new File(workingDir, LINES_FILENAME)))); //stations before lines
            base.setWalkingPaths();
        } catch (IOException ex) {
            String msg;
            if (ex instanceof FileNotFoundException)
                msg = "Nisu pronađeni odgovarajući fajlovi na datoj lokaciji";
            else
                msg = "Došlo je do greške pri učitavanju podataka iz json fajlova";
            if (UserIO.isLaunchedFromTerminal()) System.out.println(msg);
            else JOptionPane.showMessageDialog(null, msg, "Greška", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        return base;
    }

    private static Base loadLines(Base base, JsonReader reader) throws IOException {
        reader.beginArray();
        while(reader.hasNext()) {
            base.addLine(readLine(reader));
        }
        reader.endArray();
        reader.close();
        return base;
    }

    private static Base loadStations(Base base, JsonReader reader) throws IOException {
        reader.beginArray();
        while(reader.hasNext()) {
            base.addStation(readStation(reader));
        }
        reader.endArray();
        reader.close();
        return base;
    }

    private static Base loadLocations(Base base, JsonReader reader) throws IOException {
        reader.beginObject();
        String stationId;
        while(reader.hasNext()) {
            double lat=0, lon=0;
            stationId = reader.nextName();
            reader.beginObject();
            while(reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "lat":
                        lat = reader.nextDouble();
                        break;
                    case "lon":
                        lon = reader.nextDouble();
                        break;
                    default:
                        reader.skipValue();
                        System.err.println("Skipped value: " + name);
                }
            }
            base.getStation(Integer.parseInt(stationId)).setLocation(lat, lon);
            reader.endObject();
        }
        reader.endObject();
        return base;
    }

    private static List<Integer> readIntsArray(JsonReader reader) throws IOException {
        List<Integer> ints = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            ints.add(reader.nextInt());
        }
        reader.endArray();
        return ints;
    }

    private static Line readLine(JsonReader reader) throws IOException {
        String type = null, id = "0";
        List<Integer> smerA = null, smerB = null;
        reader.beginObject();
        while(reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "id":
                    id = reader.nextString();
                    break;
                case "type":
                    type = reader.nextString();
                    break;
                case "stations":
                    reader.beginArray();
                    smerA = readIntsArray(reader);
                    smerB = readIntsArray(reader);
                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    System.err.println("Skipped value " + name);
            }
        }
        reader.endObject();
        return new Line(id, type, smerA, smerB).registerLineToStations();
    }

    private static Station readStation(JsonReader reader) throws IOException {
        reader.beginObject();
        int id=-1; String stationName=null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "id": id = reader.nextInt();
                    break;
                case "name": stationName = reader.nextString();
                    break;
                default: reader.skipValue();
                    System.err.println("Skipped value " + name);
            }
        }
        reader.endObject();
        return new Station(id, stationName);
    }
}
