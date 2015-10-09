package rs.luka.belgradeunderground.io;

import rs.luka.belgradeunderground.Config;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Created by luka on 6.10.15.
 */
public class LogUtils {
    public static Handler getDefaultFileHandler() {
        String homePath = UserIO.getHomeDir().getPath();
        Handler handler;
        try {
            if(homePath.endsWith(File.pathSeparator)) { //nikad necu upamtiti cime se zavrsava
                handler = new FileHandler(homePath + "log");
            } else {
                handler = new FileHandler(homePath + "/log");
            }
        } catch (IOException e) {
            if(Config.DEBUG) throw new RuntimeException(e); //don't really care
            else return null;
        }
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        return handler;
    }
}
