package rs.luka.belgradeunderground.model;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.io.BaseIO;

import java.util.HashSet;
import java.util.Set;

/**
 * Predstavlja par double-ova koji odredjuju jednu tacku na zemlji i metode za njihovu manipulaciju. Veliki broj metoda
 * koje se ovde nalaze matematicki ne razumem, vec su preuzeti sa nekog sajta (uglavnom movable-type.co.uk)
 * Created by luka on 14.9.15.
 */
public class LatLng {
    public static Set<BusySpot> expensiveSpots = new HashSet<>(); //tacke koje treba izbegavati
    static {
        expensiveSpots.add(new BusySpot(new LatLng(44.810465, 20.466102), 800, 3.5)); //raskrsnica kod Skupstine
        expensiveSpots.add(new BusySpot(new LatLng(44.810465, 20.466102), 1500, 2)); //E70, deo pre Gazele (treba da obuhvata i Gazelu)
        //todo dodati jos tacaka, testirati vrednosti
    }

    private static class BusySpot {
        private final LatLng location;
        private final int radius; //radijus u kojem se 'oseti' guzva
        private final double cost; //koliko puta je saobracaj sporiji u datoj tacki (opada s udaljenoscu)

        private BusySpot(LatLng location, int radius, double cost) {
            this.location = location;
            this.radius = radius;
            this.cost = cost;
        }
    }

    public static final double BELGRADE_LATITUDE = Math.toRadians(44.818611); //nepotrebna aproksimacija, bolje koristiti tacnu vrednost
    public static final double EARTH_RADIUS = 6371.01;
    public static final double PARALLEL_RADIUS = EARTH_RADIUS * BELGRADE_LATITUDE;
    private final double lat;
    private final double lng;
    private Boolean isExpensive = null;

    public LatLng(double lat, double lng) {
        this.lat = Math.toRadians(lat);
        this.lng = Math.toRadians(lng);
    }

    public LatLng(String machineString) {
        String[] fields = machineString.split(BaseIO.ARRAY_SEPARATOR);
        lat = Double.parseDouble(fields[0]);
        lng = Double.parseDouble(fields[1]);
        if(!fields[2].equals("null"))
            isExpensive = Boolean.parseBoolean(fields[2]);
    }

    /**
     * Equirectangular approximation. Koliko sam shvatio, haversine i kosinusna teorema nisu neophodni, s obzirom da
     * mi nije bitno da mi rastojanja budu tacna u metar i takodje se ne radi se o prevelikim udaljenostima (zapravo,
     * veliki deo bi trebalo da bude <1km, s izuzetkom poziva iz klase Paths/PathQueue za racunanje efikasnosti)
     *
     * @param other
     * @return
     */
    public double distanceBetween(LatLng other) {
        double dLat = other.lat - lat;
        double dLng = other.lng - lng;
        double x = dLng * Math.cos((lat + other.lat)/2);
        return Math.sqrt(x*x + dLat*dLat) * EARTH_RADIUS * 1000;
    }

    /**
     * Ugao izmedju linije odredjene ovom i datom tackom i linije do severnog pola
     * @param other data tacka
     * @return Ugao u stepenima
     */
    public double bearingTo(LatLng other) {
        double y = Math.sin(other.lng - lng) * Math.cos(other.lat);
        double x = Math.cos(lat) * Math.sin(other.lat) -
                Math.sin(lat) * Math.cos(other.lat) * Math.cos(other.lng - lng);
        double initial =  Math.toDegrees(Math.atan2(y, x));
        return (initial + 360) % 360;
    }

    /**
     * Linearno opada, moze i bolje
     * @return koeficijent ove tacke (1 ako tacka nije 'skupa')
     * @see #isExpensive
     */
    private double calculateSpotCoefficient() {
        double distance;
        if(isExpensive == null ? isNearBusySpot() : isExpensive) {
            for (BusySpot expensiveSpot : expensiveSpots) {
                distance = distanceBetween(expensiveSpot.location);
                if (distance < expensiveSpot.radius) {
                    return ((expensiveSpot.radius - distance) / expensiveSpot.radius) * expensiveSpot.cost;
                }
            }
        }
        return 1;
    }

    /**
     * Geografski trosak (uzima u obzir udaljenost, tip vozila i 'skupocu' tacke) od ove do date tacke koristeci
     * datu liniju.
     * @param other data tacka
     * @param l data linija
     * @return trosak koji se kasnije moze koristiti za aproksimiranje vremena
     */
    public double costTo(LatLng other, Line l) {
        return distanceBetween(other) * l.typeCostCoefficient() * calculateSpotCoefficient();
    }

    private static final double INSCRIBED_SQUARE = Math.sqrt(2);
    private static final double OUTSCRIBED_SQUARE = 1;
    public static final double RADIUS_TO_SQUARE_SIDE_RATIO = OUTSCRIBED_SQUARE; //ili je SQUARE_SIDE_TO_RADIUS?
    /**
     * Neprecizna metoda (!)
     * Koristiti samo za procene
     * Nemam pojma kako zapravo funkcionise (matematicki)
     * @return da li se ova tacka nalazi blizu neke skupe lokacije
     * @see #RADIUS_TO_SQUARE_SIDE_RATIO
     */
    public boolean isNearBusySpot() {
        if(isExpensive != null) return isExpensive;

        for(BusySpot bs : expensiveSpots) {
            double dist = (bs.radius/ RADIUS_TO_SQUARE_SIDE_RATIO) / (EARTH_RADIUS*1000); //EARTH_RADIUS: double

            double minLat = bs.location.lat - dist;
            double maxLat = bs.location.lat + dist;

            double minLng, maxLng;
            //double deltaLon = Math.asin(Math.sin(dist) /
            //        Math.cos(lat));
            double deltaLng = dist/(Config.USE_PRECISE_PARALLEL_RADIUS ? Math.cos(bs.location.lng) : PARALLEL_RADIUS);
            //nisam siguran je l' ovo gore ima smisla
            minLng = bs.location.lng - deltaLng;
            maxLng = bs.location.lng + deltaLng;

            if( lat > minLat && lat < maxLat && lng > minLng && lng < maxLng) {
                isExpensive = true;
                return isExpensive;
            }
        }
        isExpensive = false;
        return isExpensive;
    }

    /**
     * Za serijalizaciju
     */
    public String toMachineString() {
        return lat + BaseIO.ARRAY_SEPARATOR + lng + BaseIO.ARRAY_SEPARATOR + isExpensive;
    }
}
