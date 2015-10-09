package rs.luka.belgradeunderground;

/**
 * Odredjuje globalnu konfiguraciju programa
 * Created by luka on 18.9.15.
 */
public class Config {
    public static boolean DEBUG = true; //logging, default home, neki ispisi na System.out i sl.

    public static final boolean WALKING_ENABLED = true; //za primer b) postaviti na false
    /**@deprecated */ public static final boolean PRECALCULATE_DIRECTIONS = false; //up to 1.5min @ first run, ne koristi se
    /**@deprecated */ public static final boolean USE_PRECISE_PARALLEL_RADIUS = true; //takodje, ostalo od ranije
    public static final boolean USE_CACHE = false; //see BaseIO
    public static final boolean USE_PRECISE_DIRECTIONS = true; //false oznacava parcijalno racunanje smera, see Line
    public static final boolean USE_AVERAGE_BEARING_DIRECTIONS = true; //za ovo nisam siguran, treba vise testirati, see Line
    public static final boolean RUN_SAVEBASE_IN_SEPARATE_THREAD = true; //razlika je oko 0.1s, nista veoma primetno

    public static final int PATHTREE_SIMPLEFIND = 1; //legacy
    public static final int PATHTREE_QUICKFIND = 2; //legacy
    public static final int PATHS = 3; //koristi Paths klasu za pronalazenje optimalne rute, needs tuning&optimizing
    public static final int PATHQUEUE = 4; //koristi PathQueue za pronalazenje rute, most stable
    public static final int FINDING_METHOD = PATHQUEUE; //odredjuje metodu kojom se pronalazi ruta
}
