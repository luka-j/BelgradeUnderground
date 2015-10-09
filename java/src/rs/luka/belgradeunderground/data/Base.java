package rs.luka.belgradeunderground.data;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.io.BaseIO;
import rs.luka.belgradeunderground.io.JsonLoader;
import rs.luka.belgradeunderground.model.LatLng;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import java.util.*;
import java.util.concurrent.Executors;

/**
 * Singleton u kojem se nalaze svi podaci. S obzirom da bi trebalo da odavde poticu svi objekti, bezbedno je koristiti
 * == za poredjenje. Ja to nisam radio iz navike (negde nije overridovan equals, pa se svodi na isto).
 * Created by luka on 14.9.15.
 */
public class Base {
    private static Base instance;
    private Base() {}

    private Map<Integer, Station> stations = new HashMap<>();
    private HashMap<String, Line> lines = new HashMap<>();
    private Station goal;
    private Station start;

    public static Base getInstance() {
        if(instance == null) {
            instance = new Base();
        }
        return instance;
    }

    public void addStation(Station s) {
        stations.put(s.getId(), s);
    }
    public void addLine(Line l) {
        lines.put(l.getId(), l);
    }

    /**
     * Najsporiji deo programa (za first run i {@link Config#USE_CACHE}=false)
     */
    public void setWalkingPaths() {
        long start = System.currentTimeMillis();
        Collection<Station> allStations = stations.values();
        for(Station st : allStations) {
            st.registerStations(allStations);
        }
        long end = System.currentTimeMillis();
        if(Config.DEBUG) System.out.println("Time spent in setWalkingPaths: " + (end - start) / 1000.0);
    }

    /*
     * Dobijanje linija i stanica na osnovu id-a je O(1)
     */

    public Station getStation(int id) {
        return stations.get(id);
    }

    public Line getLine(String id) {
        if(Line.getSpecial(id) == null)
            return lines.get(id);
        return Line.getSpecial(id);
    }

    /*
     * Izbegavati upotrebu iteratora kad nije neophodan (koriscen samo za serijalizaciju)
     */
    public Iterator<Line> getLineIterator() {
        return lines.values().iterator();
    }
    public Iterator<Station> getStationIterator() {
        return stations.values().iterator();
    }

    public void setGoal(int goalStationId) {
        goal = getStation(goalStationId);
        /**
         * @deprecated presporo, pomalo besmisleno, a postoji bolji nacin
         */
        if(Config.PRECALCULATE_DIRECTIONS) {
            final LatLng goalLocation = goal.getLocation();
            long start = System.currentTimeMillis();
            stations.values().forEach((Station s) -> s.sortLinksAccordingTo(goalLocation));
            long end = System.currentTimeMillis();
            if(Config.DEBUG) System.out.println("Time spent in setGoal: " + (end - start) / 1000);
        }
    }

    public Station getGoal() {
        return goal;
    }

    public void setStart(int startId) {
        this.start = getStation(startId);
    }

    public Station getStart() {
        return start;
    }

    /**
     * Ucitava podatke, ako postoje iz cache-a, ako ne iz json fajlova i zatim ih cuva u cache.
     * @see BaseIO#loadBase(Base)
     * @see JsonLoader#load()
     */
    public void load() {
        long start = System.currentTimeMillis();
        if(Config.USE_CACHE) {
            if (BaseIO.exists())
                try {
                    BaseIO.loadBase(this);
                } catch(Exception e) { //ako ne moze da ucita, vratiti se na 'regularan' put (JSON)
                    if(Config.DEBUG) e.printStackTrace();
                    BaseIO.removeFile();
                    JsonLoader.load();
                    if(Config.RUN_SAVEBASE_IN_SEPARATE_THREAD)
                        Executors.newSingleThreadExecutor().execute(() -> BaseIO.saveBase(this));
                    else
                        BaseIO.saveBase(this);
                    //probavam opet, u slucaju da je ono bila samo slucajna greska (ostecenje fajla)
                }
            else {
                JsonLoader.load();
                if(Config.RUN_SAVEBASE_IN_SEPARATE_THREAD)
                    Executors.newSingleThreadExecutor().execute(() -> BaseIO.saveBase(this));
                else
                    BaseIO.saveBase(this);
            }
        } else {
            JsonLoader.load();
        }
        long end = System.currentTimeMillis();
        if(Config.DEBUG) System.out.println("Loading time: " + (end-start)/1000.0);
    }
}
