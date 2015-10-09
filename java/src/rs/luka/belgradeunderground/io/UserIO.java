package rs.luka.belgradeunderground.io;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.data.Base;

import javax.swing.*;
import java.io.File;

/**
 * Created by luka on 4.10.15.
 */
public class UserIO {

    private static final String DEFAULT_HOME = "/home/luka/Downloads/02-belgrade-ug";
    private static boolean LAUNCHED_FROM_TERMINAL;
    private static String HOME_PATH;

    public static void read(String[] args) {
        int src, dest;
        if(args.length > 2) {
            HOME_PATH = args[0];
            src = Integer.parseInt(args[1]);
            dest = Integer.parseInt(args[2]);
            LAUNCHED_FROM_TERMINAL = true;
        } else if (args.length > 1) {
            src = Integer.parseInt(args[0]);
            dest = Integer.parseInt(args[1]);
            HOME_PATH = JOptionPane.showInputDialog("Unesite putanju do direktorijuma sa podacima o trasama");
            LAUNCHED_FROM_TERMINAL = true;
        } else {
            if(Config.DEBUG) {
                HOME_PATH = DEFAULT_HOME;
            } else {
                HOME_PATH = JOptionPane.showInputDialog("Unesite putanju do direktorijuma sa podacima o trasama");
            }
            Base.getInstance().load(); //ovim redosledom doprinosi osećaju da je JVM zabagovao
            String srcDest = JOptionPane.showInputDialog("Unesite identifikacione brojeve početne i krajnje stanice, razdvojene razmakom");
            String[] idTokens = srcDest.split("\\s+");
            src = Integer.parseInt(idTokens[0]);
            dest = Integer.parseInt(idTokens[1]);
            LAUNCHED_FROM_TERMINAL = false;
        }

        try {
            Base.getInstance().setStart(src);
        } catch (NullPointerException ex) {
            String msg = "Stanica s identifikacionim brojem " + src + " nije pronađena";
            if (LAUNCHED_FROM_TERMINAL) System.out.println(msg);
            else JOptionPane.showMessageDialog(null, msg, "Greška", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        try {
            Base.getInstance().setGoal(dest);
        } catch (NullPointerException ex) {
            String msg = "Stanica s identifikacionim brojem " + dest + " nije pronađena";
            if (LAUNCHED_FROM_TERMINAL) System.out.println(msg);
            else JOptionPane.showMessageDialog(null, msg, "Greška", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static File getHomeDir() {
        return new File(HOME_PATH);
    }

    public static boolean isLaunchedFromTerminal() {
        return LAUNCHED_FROM_TERMINAL;
    }
}
